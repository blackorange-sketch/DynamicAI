package com.dynamicai.launcher.macro

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Ручний запуск макросів і окремих інструментів з віджетів головного екрана (+ облік частоти для пропозицій). */
object MacroLauncher {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _running = MutableStateFlow<Set<String>>(emptySet())
    val running: StateFlow<Set<String>> = _running

    private fun toast(ctx: Context, text: String) {
        Handler(Looper.getMainLooper()).post { try { Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show() } catch (_: Throwable) {} }
    }

    fun usage(ctx: Context): Map<String, Int> {
        val p = ctx.getSharedPreferences("macro_usage", Context.MODE_PRIVATE)
        return p.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()
    }

    fun bump(ctx: Context, key: String) {
        val p = ctx.getSharedPreferences("macro_usage", Context.MODE_PRIVATE)
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }

    fun run(ctx: Context, macro: Macro) {
        val app = ctx.applicationContext
        if (macro.id in _running.value) return
        _running.value = _running.value + macro.id
        bump(app, "macro:" + macro.id)
        scope.launch {
            try {
                val agent = com.dynamicai.launcher.llm.AgentEngine(app, null)
                val tools = MacroTools(app)
                MacroRunner(app, { agent.runTool(it) }, { tools.notify(macro.name, it) }, mutableMapOf("trigger" to "manual")).run(macro)
                toast(app, "«${macro.name}» виконано")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                toast(app, "«${macro.name}»: ${t.message}")
            } finally {
                _running.value = _running.value - macro.id
            }
        }
    }

    /** Виконує один інструмент («setDnd(state="on")») і показує результат тостом. */
    fun runTool(ctx: Context, call: String, usageKey: String = "") {
        val app = ctx.applicationContext
        if (usageKey.isNotBlank()) bump(app, usageKey)
        scope.launch {
            val r = try {
                com.dynamicai.launcher.llm.AgentEngine(app, null).runTool(call)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (t: Throwable) { "Помилка: ${t.message}" }
            toast(app, r.take(120))
        }
    }
}
