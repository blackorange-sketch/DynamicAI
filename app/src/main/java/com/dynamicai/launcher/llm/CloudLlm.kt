package com.dynamicai.launcher.llm

import com.dynamicai.launcher.data.LauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Хмарні моделі зі стрімінгом: Google Gemini (основна) і Groq (запасна, формат OpenAI).
 * Назовні йде лише те, що передає викликач (питання, коротка історія, публічні уривки з вебу).
 */
object CloudLlm {
    class CloudException(message: String, val code: Int = 0) : Exception(message)

    /** Яка модель відповіла востаннє («Gemini» / «Groq»). */
    @Volatile var lastProvider: String = ""
        private set

    fun hasKey(s: LauncherSettings): Boolean = s.geminiKey.isNotBlank() || s.groqKey.isNotBlank()

    /**
     * Генерує відповідь, віддаючи токени по мірі надходження. Спершу Gemini, при помилці до першого токена — Groq.
     * Повертає повний текст або кидає CloudException.
     */
    suspend fun generate(s: LauncherSettings, system: String, user: String, onToken: (String) -> Unit): String {
        var lastError: Exception? = null
        var emitted = false
        val track: (String) -> Unit = { emitted = true; onToken(it) }
        if (s.geminiKey.isNotBlank()) {
            try {
                val r = gemini(s.geminiKey.trim(), s.geminiModel.ifBlank { "gemini-flash-latest" }, system, user, track)
                lastProvider = "Gemini"
                return r
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
                // Якщо частину відповіді вже озвучено — не перемикаємось, щоб не дублювати
                if (emitted) throw e
            }
        }
        if (s.groqKey.isNotBlank()) {
            try {
                val r = groq(s.groqKey.trim(), s.groqModel.ifBlank { "llama-3.3-70b-versatile" }, system, user, track)
                lastProvider = "Groq"
                return r
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
            }
        }
        throw lastError as? CloudException ?: CloudException(lastError?.message ?: "Ключ API не задано")
    }

    private fun errorText(conn: HttpURLConnection, code: Int): String {
        val body = try { (conn.errorStream ?: conn.inputStream).bufferedReader().use { it.readText() } } catch (_: Exception) { "" }
        val msg = try {
            val j = JSONObject(body)
            j.optJSONObject("error")?.optString("message").orEmpty().ifBlank { j.optString("message") }
        } catch (_: Exception) { "" }
        val hint = when (code) {
            400, 401, 403 -> " (перевірте ключ API)"
            429 -> " (вичерпано ліміт запитів)"
            else -> ""
        }
        return "HTTP $code$hint ${msg.take(160)}".trim()
    }

    private suspend fun sse(conn: HttpURLConnection, onData: (String) -> Unit) {
        val code = conn.responseCode
        if (code !in 200..299) throw CloudException(errorText(conn, code), code)
        conn.inputStream.bufferedReader().use { r ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = r.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue
                onData(data)
            }
        }
    }

    private fun open(url: String, headers: Map<String, String>, body: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 8_000
        conn.readTimeout = 25_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return conn
    }

    private suspend fun gemini(key: String, model: String, system: String, user: String, onToken: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
                .put("generationConfig", JSONObject().put("temperature", 0.6).put("maxOutputTokens", 1400))
                .toString()
            val conn = open(
                "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse",
                mapOf("x-goog-api-key" to key), body
            )
            val acc = StringBuilder()
            try {
                sse(conn) { data ->
                    val parts = try {
                        JSONObject(data).optJSONArray("candidates")?.optJSONObject(0)
                            ?.optJSONObject("content")?.optJSONArray("parts")
                    } catch (_: Exception) { null } ?: return@sse
                    for (i in 0 until parts.length()) {
                        val p = parts.optJSONObject(i) ?: continue
                        if (p.optBoolean("thought", false)) continue   // службові «роздуми» моделі не показуємо
                        val t = p.optString("text")
                        if (t.isNotEmpty()) { acc.append(t); onToken(t) }
                    }
                }
            } finally { conn.disconnect() }
            if (acc.isBlank()) throw CloudException("Gemini повернув порожню відповідь")
            acc.toString()
        }

    private suspend fun groq(key: String, model: String, system: String, user: String, onToken: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", model)
                .put("stream", true)
                .put("temperature", 0.6)
                .put("max_tokens", 1400)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)))
                .toString()
            val conn = open("https://api.groq.com/openai/v1/chat/completions", mapOf("Authorization" to "Bearer $key"), body)
            val acc = StringBuilder()
            try {
                sse(conn) { data ->
                    val t = try {
                        JSONObject(data).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
                    } catch (_: Exception) { "" }
                    if (t.isNotEmpty()) { acc.append(t); onToken(t) }
                }
            } finally { conn.disconnect() }
            if (acc.isBlank()) throw CloudException("Groq повернув порожню відповідь")
            acc.toString()
        }
}
