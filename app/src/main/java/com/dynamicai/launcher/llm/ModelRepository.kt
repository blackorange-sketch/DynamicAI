package com.dynamicai.launcher.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Log

data class LlmModel(
    val id: String,
    val name: String,
    val url: String,
    val sizeBytes: Long,
    val fileName: String,
    val supportedBackends: List<String> = listOf("CPU", "GPU"),
    val engine: String = "litert"  // "litert" | "mnn" | "gguf"
)

object ModelRepository {
    /** Папка GGUF-моделей (і завантажених, і імпортованих) у filesDir. */
    const val GGUF_DIR = "gguf_models"

    private fun gguf(id: String, name: String, url: String, size: Long) = LlmModel(
        id = "gguf-$id", name = name, url = url, sizeBytes = size,
        fileName = "$GGUF_DIR/${url.substringAfterLast('/')}",
        supportedBackends = listOf("CPU"), engine = "gguf"
    )

    /** Готові GGUF для llama.cpp (Q4_K_M — найкращий баланс якості й швидкості на телефоні). */
    private val GGUF_MODELS = listOf(
        gguf("qwen3-1.7b", "Qwen3 1.7B Q4_K_M",
            "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf", 1_110_000_000L),
        gguf("qwen3-4b-2507", "Qwen3 4B Instruct 2507 Q4_K_M",
            "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf", 2_500_000_000L),
        gguf("qwen2.5-1.5b", "Qwen2.5 1.5B Instruct Q4_K_M",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf", 1_120_000_000L),
        gguf("gemma3-1b", "Gemma 3 1B Q4_K_M",
            "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf", 806_000_000L),
        gguf("llama3.2-3b", "Llama 3.2 3B Instruct Q4_K_M",
            "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf", 2_020_000_000L),
    )

    /** Власні .gguf, імпортовані користувачем (оновлюється через [refreshImported]). */
    @Volatile private var imported: List<LlmModel> = emptyList()

    /** Перечитує gguf_models/: файли, яких немає серед готових, стають «імпортованими» моделями. */
    fun refreshImported(context: Context) {
        val known = GGUF_MODELS.map { it.fileName }.toSet()
        val dir = File(context.filesDir, GGUF_DIR)
        imported = dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) && "$GGUF_DIR/${it.name}" !in known }
            .sortedBy { it.name.lowercase() }
            .map { f ->
                LlmModel(
                    id = "gguf-file-${f.name}", name = f.name.removeSuffix(".gguf"), url = "",
                    sizeBytes = f.length(), fileName = "$GGUF_DIR/${f.name}",
                    supportedBackends = listOf("CPU"), engine = "gguf"
                )
            }
    }

    val AVAILABLE_MODELS: List<LlmModel> get() = BASE_MODELS + GGUF_MODELS + imported

    private val BASE_MODELS = listOf(
        LlmModel(
            id = "gemma-4-E2B-cpu",
            name = "Gemma 4 E2B",
            supportedBackends = listOf("CPU", "GPU"),
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            sizeBytes = 1_800_000_000L,
            fileName = "gemma-4-E2B-it.litertlm"
        ),
        LlmModel(
            id = "gemma-4-E2B-gpu",
            name = "Gemma 4 E2B (GPU-збірка, експериментальна)",
            supportedBackends = listOf("GPU"),
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-gpu.litertlm",
            sizeBytes = 2_008_000_000L,
            fileName = "gemma-4-E2B-it-gpu.litertlm"
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
            name = "Qwen3 1.7B",
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
        // ===== MNN Models =====
        LlmModel(
            id = "mnn-qwen3.5-0.8b",
            name = "MNN Qwen3.5 0.8B (Крихітна)",
            url = "https://huggingface.co/taobao-mnn/Qwen3.5-0.8B-MNN/resolve/main/llm.mnn.weight",
            sizeBytes = 800_000_000L,
            fileName = "qwen3.5-0.8b",
            engine = "mnn",
            supportedBackends = listOf("CPU", "GPU")
        ),
        LlmModel(
            id = "mnn-qwen3.5-2b",
            name = "MNN Qwen3.5 2B (Швидка)",
            url = "https://huggingface.co/taobao-mnn/Qwen3.5-2B-MNN/resolve/main/llm.mnn.weight",
            sizeBytes = 2_000_000_000L,
            fileName = "qwen3.5-2b",
            engine = "mnn",
            supportedBackends = listOf("CPU", "GPU")
        ),
        LlmModel(
            id = "mnn-qwen3.5-4b",
            name = "MNN Qwen3.5 4B (Розумна)",
            url = "https://huggingface.co/taobao-mnn/Qwen3.5-4B-MNN/resolve/main/llm.mnn.weight",
            sizeBytes = 4_000_000_000L,
            fileName = "qwen3.5-4b",
            engine = "mnn",
            supportedBackends = listOf("CPU", "GPU")
        ),
        LlmModel(
            id = "mnn-gemma-4-e2b",
            name = "MNN Gemma 4 E2B",
            url = "https://huggingface.co/taobao-mnn/gemma-4-E2B-it-MNN/resolve/main/llm.mnn.weight",
            // Сума файлів, які реально качає додаток: llm.mnn.weight 1.44 + ple_embeddings 1.47 + audio 0.59 + visual 0.23 ГБ
            sizeBytes = 3_730_000_000L,
            fileName = "gemma-4-e2b",
            engine = "mnn",
            supportedBackends = listOf("CPU", "GPU")
        ),
        LlmModel(
            id = "mnn-gemma-4-e4b",
            name = "MNN Gemma 4 E4B",
            url = "https://huggingface.co/taobao-mnn/gemma-4-E4B-it-MNN/resolve/main/llm.mnn.weight",
            sizeBytes = 4_000_000_000L,
            fileName = "gemma-4-e4b",
            engine = "mnn",
            supportedBackends = listOf("CPU", "GPU")
        ),
        LlmModel(
            id = "mnn-lfm2.5-1.2b",
            name = "MNN LFM2.5 1.2B",
            url = "https://huggingface.co/taobao-mnn/LFM2.5-1.2B-Instruct-MNN/resolve/main/llm.mnn.weight",
            sizeBytes = 1_200_000_000L,
            fileName = "lfm2.5-1.2b",
            engine = "mnn",
            supportedBackends = listOf("CPU", "GPU")
        ),
        LlmModel(
            id = "mnn-qwen3-0.6b",
            name = "MNN Qwen3 0.6B (Крихітна)",
            url = "https://huggingface.co/taobao-mnn/Qwen3-0.6B-MNN/resolve/main/llm.mnn.weight",
            sizeBytes = 400_000_000L,
            fileName = "qwen3-0.6b",
            engine = "mnn",
            supportedBackends = listOf("CPU", "GPU")
        ),
        LlmModel(
            id = "mnn-qwen3-1.7b",
            name = "MNN Qwen3 1.7B (Швидка)",
            url = "https://huggingface.co/taobao-mnn/Qwen3-1.7B-MNN/resolve/main/llm.mnn.weight",
            sizeBytes = 1_700_000_000L,
            fileName = "qwen3-1.7b",
            engine = "mnn",
            supportedBackends = listOf("CPU", "GPU")
        ),
        LlmModel(
            id = "mnn-qwen2.5-1.5b",
            name = "MNN Qwen 2.5 1.5B",
            url = "https://huggingface.co/taobao-mnn/Qwen2.5-1.5B-Instruct-MNN/resolve/main/llm.mnn.weight",
            sizeBytes = 1_500_000_000L,
            fileName = "qwen2.5-1.5b",
            engine = "mnn",
            supportedBackends = listOf("CPU", "GPU")
        )
                    )

    fun getDownloadedModels(context: Context): List<LlmModel> {
        refreshImported(context)
        return AVAILABLE_MODELS.filter { model ->
            if (model.id.startsWith("mnn-")) {
                val dir = File(File(context.filesDir, "mnn_models"), model.fileName)
                val hasDir = dir.isDirectory
                val hasLlm = File(dir, "llm.mnn").exists()
                val hasWeight = File(dir, "llm.mnn.weight").exists()
                val hasTokenizer = File(dir, "tokenizer.txt").exists() || File(dir, "tokenizer.mtok").exists()
                // Gemma 4 без цих файлів падає нативно — вважаємо її незавантаженою (UI запропонує докачати)
                val gemmaComplete = !model.fileName.contains("gemma", ignoreCase = true) ||
                    listOf("ple_embeddings_int4.bin", "audio.mnn.weight", "visual.mnn.weight").all { File(dir, it).exists() }
                Log.d("MNN_SCAN", "Model ${model.id} (${model.fileName}): dir=$hasDir, llm=$hasLlm, weight=$hasWeight, tok=$hasTokenizer, complete=$gemmaComplete")
                hasDir && hasLlm && hasWeight && hasTokenizer && gemmaComplete
            } else {
                File(context.filesDir, model.fileName).exists()
            }
        }
    }

    fun getModelFile(context: Context, model: LlmModel): File {
        return if (model.id.startsWith("mnn-")) {
            File(File(context.filesDir, "mnn_models"), model.fileName)
        } else {
            File(context.filesDir, model.fileName)
        }
    }

    suspend fun deleteModel(context: Context, model: LlmModel): Boolean = withContext(Dispatchers.IO) {
        val file = getModelFile(context, model)
        if (!file.exists()) return@withContext false
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }
}
