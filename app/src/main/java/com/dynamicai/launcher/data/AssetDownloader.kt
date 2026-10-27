package com.dynamicai.launcher.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

object AssetDownloader {
    private const val TAG = "AssetDownloader"
    
    // URL до архіву з GitHub Releases (заміни на свій, якщо потрібно)
    private const val WILDRIFT_ZIP_URL = "https://github.com/blackorange-sketch/DynamicAI/releases/download/v1.0-assets/wildrift_assets.zip"

    suspend fun downloadWildRiftAssets(context: Context, onProgress: (Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        val targetDir = File(context.filesDir, "wildrift")
        if (targetDir.exists() && targetDir.listFiles()?.isNotEmpty() == true) {
            Log.d(TAG, "WildRift assets already exist")
            return@withContext true
        }

        try {
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(WILDRIFT_ZIP_URL).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed: ${response.code}")
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val totalBytes = body.contentLength()
            val inputStream = body.byteStream()
            
            targetDir.mkdirs()
            
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val file = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            
            Log.d(TAG, "WildRift assets unpacked successfully")
            onProgress(100)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            return@withContext false
        }
    }
}
