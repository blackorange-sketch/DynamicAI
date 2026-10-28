package com.dynamicai.launcher.llm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dynamicai.launcher.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.io.File

class ModelDownloadService : Service() {

    companion object {
        private const val TAG = "MnnDlService"
        const val CHANNEL_ID = "model_download_channel"
        const val NOTIFICATION_ID = 42

        const val ACTION_START = "com.dynamicai.MODEL_DOWNLOAD_START"
        const val ACTION_CANCEL = "com.dynamicai.MODEL_DOWNLOAD_CANCEL"
        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_REPO_NAME = "repo_name"
        const val EXTRA_FOLDER_NAME = "folder_name"

        const val BROADCAST_COMPLETE = "com.dynamicai.MODEL_DOWNLOAD_COMPLETE"
        const val BROADCAST_ERROR = "com.dynamicai.MODEL_DOWNLOAD_ERROR"
        const val BROADCAST_PROGRESS = "com.dynamicai.MODEL_DOWNLOAD_PROGRESS"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var cancelRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                Log.e(TAG, "Cancel requested")
                cancelRequested = true
                scope.coroutineContext.cancelChildren()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
                val repoName = intent.getStringExtra(EXTRA_REPO_NAME) ?: return START_NOT_STICKY
                val folderName = intent.getStringExtra(EXTRA_FOLDER_NAME) ?: return START_NOT_STICKY

                cancelRequested = false
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification("Підготовка...", 0, 0, 0, true),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification("Підготовка...", 0, 0, 0, true))
                }

                scope.launch { doDownload(modelId, repoName, folderName) }
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun doDownload(modelId: String, repoName: String, folderName: String) {
        try {
            val baseDir = File(filesDir, "mnn_models")
            val targetDir = File(baseDir, folderName)
            if (!targetDir.exists()) targetDir.mkdirs()

            cleanupOldWeights(targetDir)

            val files = linkedMapOf(
                "config.json" to 50L,
                "llm_config.json" to 50L,
                "llm.mnn" to 100_000L,
                "tokenizer.txt" to 100_000L,
                "llm.mnn.weight" to 100_000_000L
            )
            val optionalFiles = listOf("llm.mnn.json")
            val correctUrl = "https://huggingface.co/taobao-mnn/$repoName/resolve/main"

            val downloader = ModelDownloader(applicationContext)

            // Загальна кількість файлів (для прогресу 1/N)
            val totalFilesToDownload = files.entries.count { (file, minSize) ->
                val t = File(targetDir, file)
                !(t.exists() && t.length() >= minSize)
            } + optionalFiles.size
            var filesDone = 0

            for ((file, minSize) in files) {
                if (cancelRequested) return
                val target = File(targetDir, file)
                if (target.exists() && target.length() >= minSize) {
                    Log.e(TAG, "Skip $file (${target.length()} bytes)")
                    continue
                }

                val url = "$correctUrl/$file"
                Log.e(TAG, "Downloading $file")
                var lastError: String? = null

                downloader.downloadUrl(url, "mnn_models/$folderName/$file").collect { progress ->
                    if (cancelRequested) return@collect
                    when (progress) {
                        is ModelDownloader.Progress.Downloading -> {
                            val baseP = (filesDone * 100) / totalFilesToDownload.coerceAtLeast(1)
                            val fileP = (progress.percent * (100 / totalFilesToDownload.coerceAtLeast(1)))
                            updateNotification(
                                title = "$file • ${progress.percent}%",
                                percent = baseP + fileP,
                                downloadedMb = progress.downloadedMb,
                                totalMb = progress.totalMb,
                                indeterminate = progress.totalMb == 0L
                            )
                        }
                        is ModelDownloader.Progress.Complete -> {
                            Log.e(TAG, "✅ $file")
                        }
                        is ModelDownloader.Progress.Error -> {
                            lastError = progress.message
                        }
                    }
                }

                if (lastError != null) throw Exception("$file: $lastError")
                filesDone++
            }

            for (file in optionalFiles) {
                if (cancelRequested) return
                val target = File(targetDir, file)
                if (target.exists() && target.length() > 1000) continue

                val url = "$correctUrl/$file"
                var failed = false
                downloader.downloadUrl(url, "mnn_models/$folderName/$file").collect { progress ->
                    if (progress is ModelDownloader.Progress.Error) failed = true
                }
                if (failed) Log.w(TAG, "Optional $file failed, skipping")
                filesDone++
            }

            Log.e(TAG, "✅ Download complete: $modelId")
            stopForegroundCompat()
            stopSelf()

            val completeIntent = Intent(BROADCAST_COMPLETE).setPackage(packageName)
                .putExtra(EXTRA_MODEL_ID, modelId)
            sendBroadcast(completeIntent)

        } catch (t: Throwable) {
            Log.e(TAG, "❌ Download failed: ${t.message}", t)
            stopForegroundCompat()
            stopSelf()

            val errorIntent = Intent(BROADCAST_ERROR).setPackage(packageName)
                .putExtra(EXTRA_MODEL_ID, modelId)
                .putExtra("error", t.message ?: "Unknown error")
            sendBroadcast(errorIntent)
        }
    }

    private fun cleanupOldWeights(targetDir: File) {
        try {
            targetDir.listFiles()?.forEach { f ->
                if (f.name.endsWith(".opencl.weight") || f.name == "mnn_cachefile.bin") {
                    val deleted = f.delete()
                    Log.e(TAG, "${if (deleted) "🗑️" else "❌"} ${f.name} (${f.length()} bytes)")
                }
            }
        } catch (_: Throwable) {}
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildNotification(
        title: String, percent: Int, downloadedMb: Long, totalMb: Long, indeterminate: Boolean
    ): Notification {
        val cancelIntent = PendingIntent.getService(
            this, 0,
            Intent(this, ModelDownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (totalMb > 0) "$downloadedMb / $totalMb МБ" else "Завантаження..."
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Завантаження LLM")
            .setContentText("$title ($text)")
            .setProgress(100, percent.coerceIn(0, 100), indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_delete, "Скасувати", cancelIntent)
            .build()
    }

    private fun updateNotification(
        title: String, percent: Int, downloadedMb: Long, totalMb: Long, indeterminate: Boolean = false
    ) {
        try {
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, buildNotification(title, percent, downloadedMb, totalMb, indeterminate))
        } catch (e: Exception) {
            Log.e(TAG, "notify failed: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Завантаження моделей", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Прогрес завантаження LLM-моделей"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }
}
