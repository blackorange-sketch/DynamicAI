package com.dynamicai.launcher.command

object DeepLinks {

    fun telegram(target: String): String {
        val clean = target.trim().removePrefix("@")
        return when {
            clean.startsWith("+") -> "tg://resolve?phone=${clean.removePrefix("+")}"
            clean.matches(Regex("\\d+")) -> "tg://resolve?phone=$clean"
            else -> "tg://resolve?domain=$clean"
        }
    }

    fun whatsapp(target: String): String {
        val digits = target.filter { it.isDigit() }
        return if (digits.isNotEmpty()) "https://wa.me/$digits"
        else "https://wa.me/"
    }

    fun instagram(target: String): String {
        val clean = target.trim().removePrefix("@")
        return "instagram://user?username=$clean"
    }

    fun viber(target: String): String {
        val digits = target.filter { it.isDigit() || it == '+' }
        return "viber://chat?number=${digits.removePrefix("+")}"
    }

    fun signal(target: String): String {
        val clean = target.trim().removePrefix("@")
        return if (clean.startsWith("+") || clean.matches(Regex("\\d+")))
            "sgnl://signal.me/#p/+${clean.removePrefix("+").removePrefix("+")}"
        else "sgnl://signal.me/#p/$clean"
    }

    fun youtube(query: String): String =
        "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8")

    fun maps(query: String): String =
        "geo:0,0?q=" + java.net.URLEncoder.encode(query, "UTF-8")

    fun spotify(query: String): String =
        "spotify:search:" + java.net.URLEncoder.encode(query, "UTF-8")
}
