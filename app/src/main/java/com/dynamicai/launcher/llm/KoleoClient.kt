package com.dynamicai.launcher.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class KoleoClient {
    companion object {
        private const val TAG = "KoleoClient"
        private const val BASE_URL = "https://api.koleo.pl/v2"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class Station(val id: Int, val name: String)
    data class Connection(
        val departure: String,
        val arrival: String,
        val duration: String,
        val price: String?
    )

    suspend fun searchStation(query: String): Station? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/main/livesearch?q=${URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder().url(url).header("User-Agent", "DynamicAI/1.0").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONArray(body)
                if (json.length() == 0) return@withContext null
                val obj = json.getJSONObject(0)
                Station(obj.getInt("id"), obj.getString("name"))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "searchStation error: ${t.message}", t)
            null
        }
    }

    suspend fun findConnections(fromQuery: String, toQuery: String): List<Connection> = withContext(Dispatchers.IO) {
        try {
            val fromStation = searchStation(fromQuery)
            val toStation = searchStation(toQuery)
            if (fromStation == null || toStation == null) {
                Log.e(TAG, "Stations not found: from=$fromQuery, to=$toQuery")
                return@withContext emptyList()
            }

            val bodyJson = JSONObject().apply {
                put("departure_after", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                put("start_id", fromStation.id)
                put("end_id", toStation.id)
                put("only_direct", false)
            }

            val request = Request.Builder()
                .url("$BASE_URL/main/eol_connections/search")
                .header("User-Agent", "DynamicAI/1.0")
                .post(bodyJson.toString().toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONArray(body)
                val result = mutableListOf<Connection>()
                for (i in 0 until minOf(json.length(), 3)) {
                    val obj = json.getJSONObject(i)
                    // Парсимо різні можливі назви полів
                    val dep = obj.optString("departure", obj.optString("departure_time", ""))
                    val arr = obj.optString("arrival", obj.optString("arrival_time", ""))
                    val dur = obj.optString("duration", "")
                    val price = obj.optString("price", null)
                    if (dep.isNotBlank() && arr.isNotBlank()) {
                        result.add(Connection(dep, arr, dur, price))
                    }
                }
                result
            }
        } catch (t: Throwable) {
            Log.e(TAG, "findConnections error: ${t.message}", t)
            emptyList()
        }
    }
}
