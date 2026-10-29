package com.dynamicai.launcher.macro

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Єдина точка запуску макросів за подіями: викликається службою, слухачем сповіщень і NFC-активністю. */
object MacroDispatcher {
    private const val TAG = "MacroTriggers"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val lastRun = HashMap<String, Long>()

    fun fire(ctx: Context, event: String, payload: Map<String, String> = emptyMap()) {
        val app = ctx.applicationContext
        scope.launch {
            val storage = MacroStorage(app)
            if (!storage.autoEnabled()) return@launch
            val checker = MacroRunner(app, { "" })
            for (m in storage.get()) {
                if (!m.enabled || m.triggers.none { MacroTriggerService.matches(it, event, payload) }) continue
                if (m.constraints.isNotBlank() && !m.constraints.split(',').all { checker.check(it) }) continue
                val now = System.currentTimeMillis()
                val allowed = synchronized(lastRun) {
                    if (now - (lastRun[m.id] ?: 0L) < 3000) false else { lastRun[m.id] = now; true }
                }
                if (!allowed) continue
                launch {
                    Log.e(TAG, "▶ macro '${m.name}' on $event $payload")
                    try {
                        val agent = com.dynamicai.launcher.llm.AgentEngine(app, null)
                        val tools = MacroTools(app)
                        val vars = mutableMapOf("trigger" to event, "device" to "", "level" to "")
                        payload.forEach { (k, v) -> vars[k] = v }
                        MacroRunner(app, { agent.runTool(it) }, { tools.notify(m.name, it) }, vars).run(m)
                    } catch (t: Throwable) { Log.e(TAG, "macro failed", t) }
                }
            }
        }
    }
}
