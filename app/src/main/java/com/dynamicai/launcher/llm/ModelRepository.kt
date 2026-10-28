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
    val fileName: String,
    val supportedBackends: List<String> = listOf("CPU", "GPU")
)

object ModelRepository {
    val AVAILABLE_MODELS = listOf(
        LlmModel(
            id = "gemma-4-E2B-cpu",
            name = "Gemma 4 E2B (CPU)",
            supportedBackends = listOf("CPU", "GPU"),
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            sizeBytes = 1_800_000_000L,
            fileName = "gemma-4-E2B-it.litertlm"
        ),
        LlmModel(
            id = "gemma-4-E2B-gpu",
            name = "Gemma 4 E2B (GPU)",
            supportedBackends = listOf("GPU"),
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-gpu.litertlm",
            sizeBytes = 2_008_000_000L,
            fileName = "gemma-4-E2B-it-gpu.litertlm"
        ),
        LlmModel(
            id = "gemma-4-E2B-npu",
            name = "Gemma 4 E2B (NPU QCS8275)",
            supportedBackends = listOf("NPU"),
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_Qualcomm_QCS8275.litertlm",
            sizeBytes = 2_722_000_000L,
            fileName = "gemma-4-E2B-it_Qualcomm_QCS8275.litertlm"
        ),
        LlmModel(
            id = "gemma-4-E2B-npu-jit",
            name = "Gemma 4 E2B (NPU JIT)",
            supportedBackends = listOf("NPU"),
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_NPU_Uncompiled.litertlm",
            sizeBytes = 2_615_000_000L,
            fileName = "gemma-4-E2B-it_NPU_Uncompiled.litertlm"
        ),
        LlmModel(
            id = "qwen3.5-4b-int4",
            name = "Qwen 3.5 4B (Розумна, INT4)",
            supportedBackends = listOf("CPU", "GPU"),
            url = "https://huggingface.co/litert-community/Qwen3.5-4B/resolve/main/Qwen3.5-4B_mixed_int4.litertlm",
            sizeBytes = 2_570_000_000L,
            fileName = "Qwen3.5-4B_mixed_int4.litertlm"
        ),
        LlmModel(
            id = "qwen3.5-4b-int8",
            name = "Qwen 3.5 4B (Максимальна, INT8)",
            supportedBackends = listOf("CPU", "GPU"),
            url = "https://huggingface.co/litert-community/Qwen3.5-4B/resolve/main/Qwen3.5-4B_int8.litertlm",
            sizeBytes = 4_100_000_000L,
            fileName = "Qwen3.5-4B_int8.litertlm"
        ),
        LlmModel(
            id = "qwen3-0.6b",
            name = "Qwen3 0.6B (Крихітна)",
            supportedBackends = listOf("CPU", "GPU", "NPU*"),
            url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            sizeBytes = 614_000_000L,
            fileName = "Qwen3-0.6B.litertlm"
        ),
                        LlmModel(
            id = "qwen2.5-1.5b",
            name = "Qwen2.5 1.5B Instruct",
            supportedBackends = listOf("CPU", "GPU", "NPU"),
            url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            sizeBytes = 1_600_000_000L,
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
        ),
        LlmModel(
            id = "qwen3-1.7b",
            name = "Qwen3 1.7B (Українська)",
            supportedBackends = listOf("CPU", "GPU", "NPU"),
            url = "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/main/Qwen3_1.7B.litertlm",
            sizeBytes = 1_700_000_000L,
            fileName = "Qwen3_1.7B.litertlm"
        ),        LlmModel(
            id = "smollm2-1.7b",
            name = "SmolLM2 1.7B (Швидка)",
            url = "https://huggingface.co/litert-community/SmolLM2-1.7B-Instruct/resolve/main/SmolLM2-1_7B-Instruct_dynamic_wi8_afp32.litertlm",
            sizeBytes = 1_700_000_000L,
            fileName = "SmolLM2-1_7B-Instruct_dynamic_wi8_afp32.litertlm"
        ),

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
