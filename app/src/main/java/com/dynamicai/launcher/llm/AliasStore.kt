package com.dynamicai.launcher.llm

import android.content.Context
import org.json.JSONObject

/** Особисті псевдоніми: «мама» → «Олена Іванівна». Зберігаються локально, використовуються при пошуку контактів. */
class AliasStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dynamicai_aliases", Context.MODE_PRIVATE)

    companion object {
        /** Грубий корінь слова: прибирає відмінкове закінчення («мамі», «мами» → «мам»). */
        fun stem(word: String): String {
            val w = word.lowercase().trim()
            return when {
                w.length >= 6 -> w.dropLast(2)
                w.length >= 4 -> w.dropLast(1)
                else -> w
            }
        }
    }

    private fun load(): JSONObject = try { JSONObject(prefs.getString("map", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }

    fun put(alias: String, target: String) {
        val o = load()
        o.put(alias.lowercase().trim(), target.trim())
        prefs.edit().putString("map", o.toString()).apply()
    }

    /** Повертає справжнє ім'я за псевдонімом або саме введене ім'я, якщо псевдоніма немає. */
    fun resolve(name: String): String {
        val o = load()
        val n = name.lowercase().trim()
        if (o.has(n)) return o.getString(n)
        val st = stem(n)
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (stem(k) == st) return o.getString(k)
        }
        return name
    }
}
