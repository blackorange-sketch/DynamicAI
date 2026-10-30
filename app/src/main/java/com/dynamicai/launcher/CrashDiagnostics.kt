package com.dynamicai.launcher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Діагностика без adb: Termux не бачить логів чужого додатка, зате сам додаток читає власний logcat
 * (включно з рядками попереднього, впалого процесу) та причини завершення процесів (ApplicationExitInfo).
 */
object CrashDiagnostics {
    private const val MAX_LOG_CHARS = 120_000

    fun collect(ctx: Context): String {
        val sb = StringBuilder()
        sb.append(MemLog.report(ctx))
        val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
        sb.appendLine("=== Причини завершення процесу (останні) ===")
        try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getHistoricalProcessExitReasons(ctx.packageName, 0, 6).forEach { e ->
                sb.appendLine(
                    "${fmt.format(Date(e.timestamp))} proc=${e.processName} reason=${e.reason} status=${e.status} " +
                        "importance=${e.importance} pssKb=${e.pss} rssKb=${e.rss} desc=${e.description}"
                )
            }
        } catch (t: Throwable) {
            sb.appendLine("exit info failed: ${t.message}")
        }
        sb.appendLine()
        sb.appendLine("=== logcat (власні рядки додатка) ===")
        try {
            val p = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", "4000")
                .redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText()
            p.waitFor()
            // Лишаємо суттєве, відкидаємо шум системних компонентів
            val keep = Regex("MNN|LLM|Llm|Litert|LiteRt|AndroidRuntime|FATAL|signal|backtrace|SIGSEGV|SIGABRT|libc|DEBUG|OOM|lowmemory|dynamicai|Voice|Chat", RegexOption.IGNORE_CASE)
            val filtered = text.lineSequence().filter { keep.containsMatchIn(it) }.joinToString("\n")
            sb.append(filtered.takeLast(MAX_LOG_CHARS))
        } catch (t: Throwable) {
            sb.appendLine("logcat failed: ${t.message}")
        }
        return sb.toString()
    }

    /** Відкриває системне меню «Поділитись» з текстом логу (Telegram, Збережені, пошта...). */
    fun share(ctx: Context) {
        val text = collect(ctx)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "DynamicAI log")
            putExtra(Intent.EXTRA_TEXT, text.takeLast(200_000))
        }
        ctx.startActivity(Intent.createChooser(send, "Надіслати лог").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
