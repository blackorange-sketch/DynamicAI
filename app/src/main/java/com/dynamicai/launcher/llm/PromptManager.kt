package com.dynamicai.launcher.llm

import android.content.Context
import java.io.File

object PromptManager {
    private const val TAG = "PromptManager"
    
    private val DEFAULT_MNN = "{{question}}"
    private val DEFAULT_LITERT = "<start_of_turn>user\n{{question}}<end_of_turn>\n<start_of_turn>model\n"
    
    private fun mnnFile(ctx: Context) = File(ctx.filesDir, "prompts/mnn.txt")
    private fun litertFile(ctx: Context) = File(ctx.filesDir, "prompts/litert.txt")
    
    fun loadMnn(ctx: Context): String = readOrCreate(mnnFile(ctx), DEFAULT_MNN)
    fun loadLitert(ctx: Context): String = readOrCreate(litertFile(ctx), DEFAULT_LITERT)
    
    fun saveMnn(ctx: Context, text: String) = write(mnnFile(ctx), text)
    fun saveLitert(ctx: Context, text: String) = write(litertFile(ctx), text)
    
    private fun readOrCreate(file: File, default: String): String {
        return try {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.writeText(default)
                android.util.Log.e(TAG, "Created default: ${file.absolutePath}")
                default
            } else {
                val content = file.readText()
                android.util.Log.e(TAG, "Loaded ${file.absolutePath} (${content.length} chars): ${content.take(80)}")
                content
            }
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Failed to read ${file.absolutePath}: ${t.message}")
            default
        }
    }
    
    private fun write(file: File, text: String) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(text)
            // Перечитуємо для перевірки
            val verify = file.readText()
            android.util.Log.e(TAG, "Saved ${file.absolutePath}: written=${text.length}, readback=${verify.length}, match=${verify == text}")
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Failed to write ${file.absolutePath}: ${t.message}")
        }
    }
}
