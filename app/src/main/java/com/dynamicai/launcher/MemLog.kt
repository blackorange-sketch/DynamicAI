package com.dynamicai.launcher

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Журнал пам'яті та життєвого циклу екранів: допомагає побачити витік (ріст PSS з часом)
 * і те, чи система знищує Activity, не вбиваючи процес. Показується в «Лог».
 */
object MemLog {
    private const val PREFS = "mem_log"
    private const val KEY = "lines"
    private const val MAX_LINES = 60
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun snapshot(ctx: Context): String {
        val mi = Debug.MemoryInfo()
        Debug.getMemoryInfo(mi)
        val rt = Runtime.getRuntime()
        val javaUsed = (rt.totalMemory() - rt.freeMemory()) shr 20
        val native = Debug.getNativeHeapAllocatedSize() shr 20
        fun kb(key: String) = (mi.memoryStats[key]?.toIntOrNull() ?: 0) / 1024
        return "pss=${mi.totalPss / 1024}MB java=${javaUsed}MB native=${native}MB " +
            "gfx=${kb("summary.graphics")}MB code=${kb("summary.code")}MB threads=${Thread.getAllStackTraces().size}"
    }

    fun note(ctx: Context, event: String) {
        try {
            val up = (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 1000
            val line = "${fmt.format(Date())} pid=${Process.myPid()} up=${up}s $event ${snapshot(ctx)}"
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lines = (p.getString(KEY, "") ?: "").split('\n').filter { it.isNotBlank() }.takeLast(MAX_LINES - 1) + line
            p.edit().putString(KEY, lines.joinToString("\n")).apply()
        } catch (_: Throwable) {}
    }

    fun report(ctx: Context): String {
        val sb = StringBuilder()
        sb.appendLine("=== Пам'ять зараз ===")
        sb.appendLine(snapshot(ctx))
        try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val m = ActivityManager.MemoryInfo()
            am.getMemoryInfo(m)
            sb.appendLine("система: вільно=${m.availMem shr 20}MB з ${m.totalMem shr 20}MB, поріг=${m.threshold shr 20}MB, lowMemory=${m.lowMemory}")
            sb.appendLine("java max=${Runtime.getRuntime().maxMemory() shr 20}MB, memoryClass=${am.memoryClass}MB, large=${am.largeMemoryClass}MB")
        } catch (_: Throwable) {}
        try {
            java.io.File("/proc/self/status").readLines()
                .filter { it.startsWith("VmRSS") || it.startsWith("VmSwap") || it.startsWith("Threads") }
                .forEach { sb.appendLine(it.replace(Regex("\\s+"), " ")) }
        } catch (_: Throwable) {}
        sb.appendLine()
        sb.appendLine("=== Журнал екранів і пам'яті (останні події) ===")
        sb.appendLine(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "(порожньо)"))
        sb.appendLine()
        return sb.toString()
    }
}
