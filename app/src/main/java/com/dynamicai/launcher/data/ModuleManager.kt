package com.dynamicai.launcher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

data class AppModule(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val targetDirName: String,
    val expectedFiles: List<String>
)

object ModuleManager {
    val modules = listOf(
        AppModule(
            id = "wildrift",
            name = "Wild Rift Assets",
            description = "Іконки, JSON-файли чемпіонів та предметів",
            url = "https://github.com/blackorange-sketch/DynamicAI/releases/download/v1.0-assets/wildrift_assets.zip",
            targetDirName = "wildrift",
            expectedFiles = listOf("heroes.json")
        ),
        AppModule(
            id = "ocr",
            name = "OCR & Translate Engine",
            description = "Нативні бібліотеки для розпізнавання та перекладу тексту",
            url = "https://github.com/blackorange-sketch/DynamicAI/releases/download/v1.0-native/native_libs.zip",
            targetDirName = "native_libs",
            expectedFiles = listOf("libtesseract.so")
        ),
        AppModule(
            id = "llm",
            name = "LLM Engine (LiteRT)",
            description = "Рушій для роботи штучного інтелекту",
            url = "https://github.com/blackorange-sketch/DynamicAI/releases/download/v1.0-llm/llm_libs.zip",
            targetDirName = "llm_libs",
            expectedFiles = listOf("liblitertlm_jni.so")
        )
    )

    fun isInstalled(context: Context, module: AppModule): Boolean {
        val dir = File(context.filesDir, module.targetDirName)
        return dir.exists() && module.expectedFiles.all { File(dir, it).exists() }
    }

    fun getModuleSizeMb(context: Context, module: AppModule): Long {
        val dir = File(context.filesDir, module.targetDirName)
        if (!dir.exists()) return 0
        var size = 0L
        dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        return size / (1024 * 1024) // MB
    }

    suspend fun downloadAndInstall(context: Context, module: AppModule, onProgress: (Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(module.url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false

            val targetDir = File(context.filesDir, module.targetDirName)
            targetDir.mkdirs()

            response.body?.byteStream()?.let { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val file = File(targetDir, entry.name)
                        if (entry.isDirectory) file.mkdirs()
                        else {
                            file.parentFile?.mkdirs()
                            file.outputStream().use { fos -> zis.copyTo(fos) }
                        }
                        entry = zis.nextEntry
                    }
                }
            }
            onProgress(100)
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }

    fun deleteModule(context: Context, module: AppModule) {
        val dir = File(context.filesDir, module.targetDirName)
        dir.deleteRecursively()
    }
}
