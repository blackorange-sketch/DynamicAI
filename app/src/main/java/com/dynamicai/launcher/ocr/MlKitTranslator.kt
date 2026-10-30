package com.dynamicai.launcher.ocr

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MlKitTranslator(private val context: Context) {

    companion object {
        private const val TAG = "MlKitTranslator"
    }

    private val translators = mutableMapOf<String, Translator>()
    @Volatile var lastUsedAt: Long = 0L
        private set

    private fun langCode(code: String): String = when (code.lowercase()) {
        "en" -> TranslateLanguage.ENGLISH
        "uk" -> TranslateLanguage.UKRAINIAN
        "pl" -> TranslateLanguage.POLISH
        "de" -> TranslateLanguage.GERMAN
        "ru" -> TranslateLanguage.RUSSIAN
        else -> TranslateLanguage.ENGLISH
    }

    /** Завантажити модель для пари мов (потрібен інтернет раз). */
    suspend fun downloadModel(from: String, to: String): Boolean {
        return try {
            val key = "${from}_$to"
            val tr = translators.getOrPut(key) {
                TranslatorOptions.Builder()
                    .setSourceLanguage(langCode(from))
                    .setTargetLanguage(langCode(to))
                    .build()
                    .let { Translation.getClient(it) }
            }
            // Без requireWifi: раніше на мобільних даних модель не завантажувалась і переклад мовчки не працював
            val conditions = DownloadConditions.Builder().build()
            suspendCancellableCoroutine { cont ->
                tr.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener {
                        Log.e(TAG, "Model downloaded: $key")
                        cont.resume(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Download failed: ${e.message}")
                        cont.resume(false)
                    }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "downloadModel failed: ${t.message}", t)
            false
        }
    }

    /** Перекласти один текст. Модель має бути завантажена. */
    suspend fun translate(text: String, from: String, to: String): String? {
        lastUsedAt = System.currentTimeMillis()
        return try {
            val key = "${from}_$to"
            val tr = translators.getOrPut(key) {
                TranslatorOptions.Builder()
                    .setSourceLanguage(langCode(from))
                    .setTargetLanguage(langCode(to))
                    .build()
                    .let { Translation.getClient(it) }
            }
            suspendCancellableCoroutine { cont ->
                tr.translate(text)
                    .addOnSuccessListener { result ->
                        Log.e(TAG, "translate OK: '$text' -> '$result'")
                        cont.resume(result)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "translate FAIL: '$text' err=${e.message}", e)
                        cont.resume(null)
                    }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "translate failed: ${t.message}", t)
            null
        }
    }

    fun close() {
        translators.values.forEach { try { it.close() } catch (_: Throwable) {} }
        translators.clear()
    }
}
