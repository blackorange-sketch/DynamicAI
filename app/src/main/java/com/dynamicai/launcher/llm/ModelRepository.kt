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
            id = "gemma-4-E2B-cpu",
            name = "Gemma 4 E2B (CPU)",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            sizeBytes = 1_800_000_000L,
            fileName = "gemma-4-E2B-it.litertlm"
        ),
        LlmModel(
            id = "gemma-4-E2B-gpu",
            name = "Gemma 4 E2B (GPU)",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-gpu.litertlm",
            sizeBytes = 2_008_000_000L,
            fileName = "gemma-4-E2B-it-gpu.litertlm"
        ),
        LlmModel(
            id = "gemma-4-E2B-npu",
            name = "Gemma 4 E2B (NPU QCS8275)",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_Qualcomm_QCS8275.litertlm",
            sizeBytes = 2_722_000_000L,
            fileName = "gemma-4-E2B-it_Qualcomm_QCS8275.litertlm"
        ),
        LlmModel(
            id = "gemma-4-E2B-npu-jit",
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
        ),
        LlmModel(
            id = "qwen3-0.6b",
            name = "Qwen3 0.6B (Крихітна)",
            url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            sizeBytes = 614_000_000L,
            fileName = "Qwen3-0.6B.litertlm"
        ),
        LlmModel(
            id = "gemma3-1b-it",
            name = "Gemma3 1B IT",
            url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_q4_ekv1280_mt6991.litertlm",
            sizeBytes = 1_100_000_000L,
            fileName = "Gemma3-1B-IT_q4_ekv1280_mt6991.litertlm"
        ),
        LlmModel(
            id = "phi-4-mini",
            name = "Phi-4 Mini Instruct",
            url = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct.litertlm",
            sizeBytes = 2_300_000_000L,
            fileName = "Phi-4-mini-instruct.litertlm"
        ),
        LlmModel(
            id = "qwen2.5-1.5b",
            name = "Qwen2.5 1.5B Instruct",
            url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            sizeBytes = 1_600_000_000L,
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
        ),
        LlmModel(
            id = "smollm-135m",
            name = "SmolLM 135M (Найлегша)",
            url = "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/SmolLM-135M-Instruct.litertlm",
            sizeBytes = 135_000_000L,
            fileName = "SmolLM-135M-Instruct.litertlm"
        ),
        LlmModel(
            id = "tinyllama-1.1b",
            name = "TinyLlama 1.1B Chat",
            url = "https://huggingface.co/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/TinyLlama-1.1B-Chat-v1.0.litertlm",
            sizeBytes = 700_000_000L,
            fileName = "TinyLlama-1.1B-Chat-v1.0.litertlm"
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
