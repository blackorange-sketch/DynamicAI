package com.dynamicai.launcher.command

object FuzzyMatcher {

    private val cyr2lat = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'ґ' to "g",
        'д' to "d", 'е' to "e", 'є' to "ye", 'ж' to "zh", 'з' to "z",
        'и' to "i", 'і' to "i", 'ї' to "yi", 'й' to "y", 'к' to "k",
        'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p",
        'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f",
        'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ь' to "", 'ю' to "yu", 'я' to "ya", 'ы' to "y", 'э' to "e",
        'ё' to "e", 'ъ' to ""
    )

    private val synonyms = mapOf(
        "тг" to "telegram", "телега" to "telegram", "телеграм" to "telegram",
        "вотсап" to "whatsapp", "ватсап" to "whatsapp", "втс" to "whatsapp",
        "вк" to "vk", "вконтакте" to "vk",
        "ютуб" to "youtube", "ютьюб" to "youtube",
        "ютуб мьюзик" to "youtube music",
        "інст" to "instagram", "инста" to "instagram", "инстаграм" to "instagram",
        "фб" to "facebook", "фейсбук" to "facebook",
        "твіттер" to "twitter",
        "вайбер" to "viber",
        "нетфлікс" to "netflix",
        "спотифай" to "spotify",
        "гугл" to "google", "ґуґл" to "google",
        "калькулятор" to "calculator",
        "камера" to "camera",
        "настройки" to "settings", "налаштування" to "settings"
    )

    fun transliterate(input: String): String {
        val sb = StringBuilder()
        for (c in input.lowercase()) sb.append(cyr2lat[c] ?: c)
        return sb.toString()
    }

    fun normalize(input: String): String {
        val s = input.lowercase().trim()
            .replace(Regex("\\s+"), " ")   // колапс множинних пробілів
        return synonyms[s] ?: s
    }

    /**
     * Головний метод — порівнює label застосунку з запитом.
     */
    fun score(label: String, query: String): Int {
        val q0 = query.lowercase().trim()
        if (q0.isEmpty()) return 0

        val l = label.lowercase().trim().replace(Regex("\\s+"), " ")
        val q = normalize(q0)
        val lt = transliterate(l).replace(Regex("\\s+"), " ")
        val qt = transliterate(q0).replace(Regex("\\s+"), " ")

        // 1. EXACT MATCH — максимальний пріоритет
        if (l == q) return 200
        if (lt == qt) return 200

        // 2. Всі слова запиту присутні в назві (як слова)
        val queryWords = q.split(" ").filter { it.isNotBlank() }
        val labelWords = l.split(" ").filter { it.isNotBlank() }

        if (queryWords.isNotEmpty() && queryWords.all { qw -> 
                labelWords.any { lw -> lw == qw || lw.startsWith(qw) || qw.startsWith(lw) } 
            }) {
            // Бонус за точний порядок слів
            if (l.startsWith(q)) return 190
            return 150
        }

        // 3. Те саме з транслітерацією
        val queryWordsT = qt.split(" ").filter { it.isNotBlank() }
        val labelWordsT = lt.split(" ").filter { it.isNotBlank() }
        if (queryWordsT.isNotEmpty() && queryWordsT.all { qw -> 
                labelWordsT.any { lw -> lw == qw || lw.startsWith(qw) || qw.startsWith(lw) } 
            }) {
            if (lt.startsWith(qt)) return 190
            return 150
        }

        // 4. Стандартні матчі
        return maxOf(
            raw(l, q),
            raw(l, q0),
            raw(lt, q),
            raw(lt, qt),
            fuzzy(l, q),
            fuzzy(lt, q),
            fuzzy(l, qt),
            fuzzy(lt, qt)
        )
    }

    private fun raw(a: String, b: String): Int = when {
        a == b -> 100
        a.startsWith(b) -> 85
        b.startsWith(a) && a.length >= 3 -> 80
        a.contains(b) -> 70
        b.contains(a) && a.length >= 4 -> 60
        else -> 0
    }

    private fun fuzzy(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        val dist = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length)
        val sim = 1.0 - dist.toDouble() / maxLen
        return when {
            sim > 0.85 -> 65
            sim > 0.7 -> 50
            sim > 0.55 -> 35
            else -> 0
        }
    }

    fun levenshtein(a: String, b: String): Int {
        val m = a.length; val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        val prev = IntArray(n + 1) { it }
        val curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            for (k in 0..n) prev[k] = curr[k]
        }
        return prev[n]
    }
}
