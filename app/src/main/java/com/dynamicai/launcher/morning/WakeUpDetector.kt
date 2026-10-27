package com.dynamicai.launcher.morning

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.util.Calendar

class WakeUpDetector(private val context: Context) {
    companion object {
        private const val TAG = "WakeUpDetector"
        private const val PREFS = "wakeup_detector"
        private const val KEY_LAST_UNLOCK = "last_unlock"
        private const val KEY_LAST_WAKE = "last_wake"
        private const val KEY_WAKE_HISTORY = "wake_history"
        private const val LONG_SLEEP_THRESHOLD = 5 * 3600 * 1000L // 5 годин
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun onUserPresent() {
        val now = System.currentTimeMillis()
        val lastUnlock = prefs.getLong(KEY_LAST_UNLOCK, 0)
        val lastWake = prefs.getLong(KEY_LAST_WAKE, 0)

        // Перевіряємо, чи це "ранок" (5+ годин сну)
        val isNewMorning = (now - lastUnlock > LONG_SLEEP_THRESHOLD)
        // Перевіряємо, чи не було вже брифінгу сьогодні
        val daysSinceWake = (now - lastWake) / (24 * 3600 * 1000L)
        val alreadyBriefedToday = daysSinceWake < 1 && lastWake > 0

        Log.d(TAG, "Unlock: isNewMorning=$isNewMorning, alreadyBriefedToday=$alreadyBriefedToday")

        if (isNewMorning && !alreadyBriefedToday) {
            Log.d(TAG, "Triggering morning briefing!")
            prefs.edit().putLong(KEY_LAST_WAKE, now).apply()
            recordWakeTime(now)
            MorningBriefingService.start(context)
        }

        prefs.edit().putLong(KEY_LAST_UNLOCK, now).apply()
    }

    private fun recordWakeTime(timeMs: Long) {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        val raw = prefs.getString(KEY_WAKE_HISTORY, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        
        val entry = org.json.JSONObject().apply {
            put("hour", hour)
            put("minute", minute)
            put("dayOfWeek", dayOfWeek)
            put("timestamp", timeMs)
        }
        arr.put(entry)

        // Зберігаємо лише останні 30 записів
        val trimmed = if (arr.length() > 30) {
            JSONArray().apply {
                for (i in (arr.length() - 30) until arr.length()) put(arr.get(i))
            }
        } else arr

        prefs.edit().putString(KEY_WAKE_HISTORY, trimmed.toString()).apply()
    }

    /**
     * Повертає середню годину пробудження (0-23) або -1, якщо історії мало.
     */
    fun getAverageWakeHour(): Int {
        val raw = prefs.getString(KEY_WAKE_HISTORY, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { return -1 }
        if (arr.length() < 3) return -1

        var totalMinutes = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            totalMinutes += o.getInt("hour") * 60 + o.getInt("minute")
        }
        val avgMinutes = totalMinutes / arr.length()
        return avgMinutes / 60
    }

    fun getAverageWakeMinute(): Int {
        val raw = prefs.getString(KEY_WAKE_HISTORY, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { return -1 }
        if (arr.length() < 3) return -1

        var totalMinutes = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            totalMinutes += o.getInt("hour") * 60 + o.getInt("minute")
        }
        return totalMinutes / arr.length() % 60
    }
}
