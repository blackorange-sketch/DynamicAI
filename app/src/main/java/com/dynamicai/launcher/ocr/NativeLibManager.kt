package com.dynamicai.launcher.ocr

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

object NativeLibManager {
    private const val TAG = "NativeLibManager"
    private const val URL = "https://github.com/blackorange-sketch/DynamicAI/releases/download/v1.0-native/native_libs.zip"
    private const val LLM_URL = "https://github.com/blackorange-sketch/DynamicAI/releases/download/v1.0-llm/llm_libs.zip"
    
    @Volatile private var isLoaded = false

    suspend fun ensureLoaded(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext true

        val libDir = File(context.filesDir, "native_libs")
        libDir.mkdirs()

        val requiredLibs = listOf("libjpeg.so", "libpngx.so", "libleptonica.so", "libtesseract.so", "libtranslate_jni.so")
        val allExist = requiredLibs.all { File(libDir, it).exists() }

        if (!allExist) {
            Log.d(TAG, "Downloading native libs...")
            try {
                val client = OkHttpClient.Builder().build()
                val request = Request.Builder().url(URL).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext false

                response.body?.byteStream()?.let { input ->
                    ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val file = File(libDir, entry.name)
                            file.parentFile?.mkdirs()
                            file.outputStream().use { fos -> zis.copyTo(fos) }
                            entry = zis.nextEntry
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                return@withContext false
            }
        }

        // Завантажуємо в правильному порядку (залежності спочатку)
        try {
            for (lib in requiredLibs) {
                val file = File(libDir, lib)
                if (file.exists()) {
                    System.load(file.absolutePath)
                    Log.d(TAG, "Loaded $lib")
                } else {
                    Log.e(TAG, "Missing $lib")
                    return@withContext false
                }
            }
            isLoaded = true
            return@withContext true
        } catch (e: Throwable) {
            Log.e(TAG, "Load failed: ${e.message}", e)
            return@withContext false
        }
    }

    suspend fun ensureLlmLoaded(context: Context): Boolean = withContext(Dispatchers.IO) {
        val libDir = File(context.filesDir, "llm_libs")
        libDir.mkdirs()
        val libFile = File(libDir, "liblitertlm_jni.so")

        if (!libFile.exists()) {
            Log.d(TAG, "Downloading LLM lib...")
            try {
                val client = OkHttpClient.Builder().build()
                val request = Request.Builder().url(LLM_URL).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext false

                response.body?.byteStream()?.let { input ->
                    java.util.zip.ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val file = File(libDir, entry.name)
                            file.parentFile?.mkdirs()
                            file.outputStream().use { fos -> zis.copyTo(fos) }
                            entry = zis.nextEntry
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "LLM download failed", e)
                return@withContext false
            }
        }

        try {
            System.load(libFile.absolutePath)
            Log.d(TAG, "LLM lib loaded")
            return@withContext true
        } catch (e: Throwable) {
            Log.e(TAG, "LLM load failed", e)
            return@withContext false
        }
    }
}
