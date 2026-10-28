package com.dynamicai.launcher.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LlmModel(
    val id: String,
    val name: String,
    val url: String,
    val sizeBytes: Long,
    val fileName: String
)

object ModelRepository {
    val AVAILABLE_MODELS = listOf(
        LlmModel(
            id = "gemma-4-E2B",
            name = "Gemma 4 E2B (NPU JIT)",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_NPU_Uncompiled.litertlm",
            sizeBytes = 2_615_000_000L,
            fileName = "gemma-4-E2B-it_NPU_Uncompiled.litertlm"
        ),
        LlmModel(
            id = "qwen3.5-4b-int4",
            name = "Qwen 3.5 4B (Розумна, INT4)",
            url = "https://huggingface.co/litert-community/Qwen3.5-4B/resolve/main/Qwen3.5-4B_mixed_int4.litertlm",
            sizeBytes = 2_570_000_000L,
            fileName = "Qwen3.5-4B_mixed_int4.litertlm"
        ),
        LlmModel(
            id = "qwen3.5-4b-int8",
            name = "Qwen 3.5 4B (Максимальна, INT8)",
            url = "https://huggingface.co/litert-community/Qwen3.5-4B/resolve/main/Qwen3.5-4B_int8.litertlm",
            sizeBytes = 4_100_000_000L,
            fileName = "Qwen3.5-4B_int8.litertlm"
        )
    )

    fun getDownloadedModels(context: Context): List<LlmModel> {
        return AVAILABLE_MODELS.filter { File(context.filesDir, it.fileName).exists() }
    }

    fun getModelFile(context: Context, model: LlmModel): File {
        return File(context.filesDir, model.fileName)
    }

    suspend fun deleteModel(context: Context, model: LlmModel): Boolean = withContext(Dispatchers.IO) {
        val file = getModelFile(context, model)
        if (file.exists()) file.delete() else false
    }
}
