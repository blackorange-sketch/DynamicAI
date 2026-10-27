package com.dynamicai.launcher.morning

object UkrainianSpeechUtils {

    fun numberToWords(n: Int): String {
        if (n < 0) return "мінус " + numberToWords(-n)
        if (n == 0) return "нуль"
        if (n == 100) return "сто"

        val ones = listOf("", "один", "два", "три", "чотири", "п'ять", "шість", "сім", "вісім", "дев'ять")
        val teens = listOf(
            "десять", "одинадцять", "дванадцять", "тринадцять", "чотирнадцять",
            "п'ятнадцять", "шістнадцять", "сімнадцять", "вісімнадцять", "дев'ятнадцять"
        )
        val tens = listOf(
            "", "", "двадцять", "тридцять", "сорок", "п'ятдесят", "шістдесят",
            "сімдесят", "вісімдесят", "дев'яносто"
        )

        if (n < 10) return ones[n]
        if (n < 20) return teens[n - 10]
        if (n < 100) {
            val t = n / 10
            val o = n % 10
            return if (o == 0) tens[t] else tens[t] + " " + ones[o]
        }
        val h = n / 100
        val rest = n % 100
        val hundreds = when (h) {
            1 -> "сто"
            2 -> "двісті"
            3 -> "триста"
            4 -> "чотириста"
            5 -> "п'ятсот"
            6 -> "шістсот"
            7 -> "сімсот"
            8 -> "вісімсот"
            9 -> "дев'ятсот"
            else -> ""
        }
        return if (rest == 0) hundreds else hundreds + " " + numberToWords(rest)
    }

    // Дата у називному відмінку: "двадцять сьоме жовтня"
    fun dayToWords(day: Int): String {
        val ordinals = mapOf(
            1 to "перше", 2 to "друге", 3 to "третє", 4 to "четверте",
            5 to "п'яте", 6 to "шосте", 7 to "сьоме", 8 to "восьме",
            9 to "дев'яте", 10 to "десяте", 11 to "одинадцяте",
            12 to "дванадцяте", 13 to "тринадцяте", 14 to "чотирнадцяте",
            15 to "п'ятнадцяте", 16 to "шістнадцяте", 17 to "сімнадцяте",
            18 to "вісімнадцяте", 19 to "дев'ятнадцяте", 20 to "двадцяте",
            21 to "двадцять перше", 22 to "двадцять друге", 23 to "двадцять третє",
            24 to "двадцять четверте", 25 to "двадцять п'яте", 26 to "двадцять шосте",
            27 to "двадцять сьоме", 28 to "двадцять восьме", 29 to "двадцять дев'яте",
            30 to "тридцяте", 31 to "тридцять перше"
        )
        return ordinals[day] ?: numberToWords(day)
    }

    private fun degreeForm(n: Int): String {
        val lastTwo = n % 100
        val lastDigit = n % 10
        return when {
            lastTwo in 11..14 -> "градусів"
            lastDigit == 1 -> "градус"
            lastDigit in 2..4 -> "градуси"
            else -> "градусів"
        }
    }

    fun temperatureToWords(temp: Int): String {
        if (temp == 0) return "нуль градусів"
        val absTemp = kotlin.math.abs(temp)
        val word = numberToWords(absTemp)
        val deg = degreeForm(absTemp)
        return if (temp < 0) "мінус $word $deg" else "плюс $word $deg"
    }

    private fun percentForm(n: Int): String {
        val lastTwo = n % 100
        val lastDigit = n % 10
        return when {
            lastTwo in 11..14 -> "відсотків"
            lastDigit == 1 -> "відсоток"
            lastDigit in 2..4 -> "відсотки"
            else -> "відсотків"
        }
    }

    fun batteryToWords(percent: Int): String {
        val safe = percent.coerceIn(0, 100)
        return "${numberToWords(safe)} ${percentForm(safe)}"
    }
}
