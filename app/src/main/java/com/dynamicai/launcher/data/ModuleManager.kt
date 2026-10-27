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
        // 1. Перевірка всередині APK (assets)
        try {
            // Для Wild Rift перевіряємо конкретний файл в assets
            if (module.id == "wildrift") {
                context.assets.open("wildrift/heroes.json").use { return true }
            }
            // Для інших модулів перевіряємо, чи є папка в assets
            val assetList = context.assets.list(module.targetDirName)
            if (!assetList.isNullOrEmpty()) {
                return module.expectedFiles.all { expected ->
                    assetList.contains(expected)
                }
            }
        } catch (_: Exception) {
            // Файлів в assets немає, йдемо далі
        }

        // 2. Перевірка в зовнішньому сховищі (filesDir)
        val dir = File(context.filesDir, module.targetDirName)
        if (dir.exists() && module.expectedFiles.all { File(dir, it).exists() }) {
            return true
        }

        // 3. Специфічна перевірка для LLM (моделі лежать прямо в filesDir)
        if (module.id == "llm") {
            val libFile = File(context.filesDir, "llm_libs/liblitertlm_jni.so")
            val modelExists = context.filesDir.listFiles()?.any { it.name.endsWith(".litertlm") } == true
            if (libFile.exists() || modelExists) return true
        }

        return false
    }

    fun getModuleSizeMb(context: Context, module: AppModule): Long {
        var totalSize = 0L
        // 1. Розмір в assets (APK) - для Wild Rift
        try {
            val assetList = context.assets.list(module.targetDirName)
            if (!assetList.isNullOrEmpty()) {
                // Ми не можемо легко дізнатися розмір assets без відкриття,
                // але для Wild Rift це не критично, тому ставимо 0
            }
        } catch (_: Exception) {}

        // 2. Розмір в filesDir
        val dir = File(context.filesDir, module.targetDirName)
        if (dir.exists()) {
            dir.walkTopDown().forEach { if (it.isFile) totalSize += it.length() }
        }

        // 3. Для LLM додаємо розмір моделей
        if (module.id == "llm") {
            context.filesDir.listFiles()?.filter { it.name.endsWith(".litertlm") }?.forEach { totalSize += it.length() }
        }

        return totalSize / (1024 * 1024)
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
                        // Зрізаємо префікс targetDirName/, щоб уникнути подвійної вкладеності (wildrift/wildrift/)
                        val relativeName = entry.name.removePrefix("${module.targetDirName}/")
                        
                        if (relativeName.isNotEmpty()) {
                            val file = File(targetDir, relativeName)
                            if (entry.isDirectory) {
                                file.mkdirs()
                            } else {
                                file.parentFile?.mkdirs()
                                file.outputStream().use { fos -> zis.copyTo(fos) }
                            }
                        }
                        zis.closeEntry()
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
