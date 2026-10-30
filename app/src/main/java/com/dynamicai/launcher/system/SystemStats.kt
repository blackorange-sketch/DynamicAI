package com.dynamicai.launcher.system

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import kotlin.math.abs

/** Збір показників системи для віджета «Ресурси». Список процесів і ЦП — через Shizuku, решта — без особливих прав. */
object SystemStats {
    data class Snapshot(
        val ramUsedMb: Long, val ramTotalMb: Long,
        val swapUsedMb: Long, val swapTotalMb: Long,
        val storageUsedGb: Float, val storageTotalGb: Float,
        val batteryPct: Int, val batteryTempC: Float, val charging: Boolean, val currentMa: Int,
        val cpuPct: Int // -1 = недоступно
    )

    data class ProcRow(val pkg: String, val label: String, val value: String, val sub: String = "")

    @Volatile private var shizuku: ShizukuManager? = null
    private fun shizuku(ctx: Context): ShizukuManager =
        shizuku ?: ShizukuManager(ctx.applicationContext).also { shizuku = it }

    fun shizukuReady(ctx: Context): Boolean = try {
        val m = shizuku(ctx); m.isShizukuAvailable() && m.hasPermission()
    } catch (_: Throwable) { false }

    suspend fun stopPackage(ctx: Context, pkg: String): Boolean = shizuku(ctx).forceStop(pkg)

    fun fmtMb(mb: Long): String = if (mb >= 1024) "%.1f ГБ".format(mb / 1024f) else "$mb МБ"

    // ---------- Зведення ----------

    private var prevTotal = 0L
    private var prevIdle = 0L

    private suspend fun readStatLine(ctx: Context): String? {
        try {
            File("/proc/stat").bufferedReader().use { r -> r.readLine()?.let { if (it.startsWith("cpu")) return it } }
        } catch (_: Throwable) {}
        if (!shizukuReady(ctx)) return null
        val out = shizuku(ctx).execShell("head -n 1 /proc/stat")
        return out.takeIf { it.startsWith("cpu") }
    }

    private suspend fun cpuPercent(ctx: Context): Int {
        val line = readStatLine(ctx) ?: return -1
        val p = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (p.size < 5) return -1
        val idle = p[3] + p[4]
        val total = p.sum()
        val dt = total - prevTotal
        val di = idle - prevIdle
        val first = prevTotal == 0L
        prevTotal = total; prevIdle = idle
        return if (first || dt <= 0) -1 else (100L * (dt - di) / dt).toInt().coerceIn(0, 100)
    }

    private fun meminfo(): Map<String, Long> {
        val m = HashMap<String, Long>()
        try {
            File("/proc/meminfo").forEachLine { l ->
                val parts = l.split(':')
                if (parts.size == 2) parts[1].trim().split(' ').firstOrNull()?.toLongOrNull()?.let { m[parts[0]] = it }
            }
        } catch (_: Throwable) {}
        return m
    }

    suspend fun snapshot(ctx: Context): Snapshot = withContext(Dispatchers.IO) {
        val mi = meminfo()
        val total = (mi["MemTotal"] ?: 0L) / 1024
        val avail = (mi["MemAvailable"] ?: 0L) / 1024
        val swapT = (mi["SwapTotal"] ?: 0L) / 1024
        val swapU = swapT - (mi["SwapFree"] ?: 0L) / 1024
        val sf = try { StatFs(android.os.Environment.getDataDirectory().path) } catch (_: Throwable) { null }
        val stTotal = sf?.let { it.blockCountLong * it.blockSizeLong / 1e9f } ?: 0f
        val stFree = sf?.let { it.availableBlocksLong * it.blockSizeLong / 1e9f } ?: 0f
        val b: Intent? = try { ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (_: Throwable) { null }
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val cur = try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            abs(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)) / 1000
        } catch (_: Throwable) { 0 }
        Snapshot(
            total - avail, total, swapU.coerceAtLeast(0), swapT,
            (stTotal - stFree).coerceAtLeast(0f), stTotal,
            if (level >= 0) level * 100 / scale else -1, temp, charging, cur,
            cpuPercent(ctx)
        )
    }

    // ---------- Деталі ----------

    private fun labelOf(ctx: Context, pkg: String): String = try {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Throwable) { pkg }

    fun isSystemApp(ctx: Context, pkg: String): Boolean = try {
        pkg == "android" || pkg == ctx.packageName ||
            (ctx.packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
    } catch (_: Throwable) { true }

    /** Процеси за пам'яттю (PSS) з `dumpsys meminfo`. */
    suspend fun topMemory(ctx: Context): List<ProcRow>? = withContext(Dispatchers.IO) {
        if (!shizukuReady(ctx)) return@withContext null
        val out = shizuku(ctx).execShell("dumpsys meminfo 2>/dev/null | sed -n '/Total PSS by process:/,/^\$/p' | head -n 40")
        val re = Regex("^\\s*([\\d,.]+)K:\\s+(\\S+)\\s+\\(pid")
        val sums = LinkedHashMap<String, Long>()
        out.lineSequence().forEach { l ->
            val m = re.find(l) ?: return@forEach
            val kb = m.groupValues[1].replace(",", "").replace(".", "").toLongOrNull() ?: return@forEach
            val pkg = m.groupValues[2].substringBefore(':')
            sums[pkg] = (sums[pkg] ?: 0L) + kb / 1024
        }
        sums.entries.sortedByDescending { it.value }.take(15)
            .map { ProcRow(it.key, labelOf(ctx, it.key), fmtMb(it.value)) }
    }

    /** Процеси за завантаженням ЦП (`top`). */
    suspend fun topCpu(ctx: Context): List<ProcRow>? = withContext(Dispatchers.IO) {
        if (!shizukuReady(ctx)) return@withContext null
        val out = shizuku(ctx).execShell("top -b -n 1 -m 15 2>/dev/null")
        val rows = ArrayList<Triple<String, Double, String>>()
        out.lineSequence().forEach { l ->
            val t = l.trim().split(Regex("\\s+"))
            if (t.size < 12 || t[0].toIntOrNull() == null) return@forEach
            val cpu = t[8].replace(',', '.').toDoubleOrNull() ?: return@forEach
            val args = t.drop(11).joinToString(" ")
            if (args == "top" || args.startsWith("sh")) return@forEach
            rows.add(Triple(args.substringBefore(':'), cpu, t[5]))
        }
        rows.groupBy { it.first }
            .map { (pkg, v) -> Triple(pkg, v.sumOf { it.second }, v.first().third) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }.take(12)
            .map { ProcRow(it.first, labelOf(ctx, it.first), "%.1f%%".format(it.second), "RAM ${it.third}") }
    }

    /** Найбільші за розміром додатки (потрібен доступ до статистики використання). */
    suspend fun storageByApp(ctx: Context): List<ProcRow>? = withContext(Dispatchers.IO) {
        try {
            val ssm = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val user = Process.myUserHandle()
            val apps = ctx.packageManager.getInstalledApplications(0)
            val list = apps.mapNotNull { a ->
                try {
                    val s = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, a.packageName, user)
                    Triple(a.packageName, s.appBytes + s.dataBytes, s.cacheBytes)
                } catch (_: SecurityException) { throw SecurityException() } catch (_: Throwable) { null }
            }
            list.sortedByDescending { it.second }.take(15).map {
                ProcRow(it.first, labelOf(ctx, it.first), fmtMb(it.second / 1_048_576), "кеш ${fmtMb(it.third / 1_048_576)}")
            }
        } catch (_: SecurityException) { null } catch (_: Throwable) { emptyList() }
    }

    /** Екранний час за сьогодні (потрібен доступ до статистики використання). */
    suspend fun screenTime(ctx: Context): List<ProcRow>? = withContext(Dispatchers.IO) {
        try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val start = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, System.currentTimeMillis())
            if (stats.isNullOrEmpty()) return@withContext null
            stats.groupBy { it.packageName }
                .map { (p, v) -> p to v.sumOf { it.totalTimeInForeground } }
                .filter { it.second > 60_000 }
                .sortedByDescending { it.second }.take(15)
                .map {
                    val min = it.second / 60_000
                    ProcRow(it.first, labelOf(ctx, it.first), if (min >= 60) "${min / 60} г ${min % 60} хв" else "$min хв")
                }
        } catch (_: Throwable) { null }
    }
}
