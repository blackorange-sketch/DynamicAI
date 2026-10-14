package com.dynamicai.launcher.util

object Calculator {

    sealed class Result {
        data class success(val value: Double) : Result()
        data class failure(val msg: String) : Result()
    }

    fun calculate(input: String): Result {
        return try {
            var expr = input.lowercase()
                .replace("плюс", "+")
                .replace("мінус", "-")
                .replace("помножити на", "*")
                .replace("помножити", "*")
                .replace("поділити на", "/")
                .replace("поділити", "/")
                .replace("×", "*")
                .replace("÷", "/")
                .replace(" ", "")
                .replace(",", ".")

            val parser = ExpressionParser(expr)
            val result = parser.parse()
            Result.success(result)
        } catch (t: Throwable) {
            Result.failure(t.message ?: "помилка обчислення")
        }
    }

    private class ExpressionParser(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            val result = parseExpression()
            if (pos < s.length) throw IllegalArgumentException("Зайвий символ: ${s[pos]}")
            return result
        }

        private fun parseExpression(): Double {
            var result = parseTerm()
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos++]
                val term = parseTerm()
                result = if (op == '+') result + term else result - term
            }
            return result
        }

        private fun parseTerm(): Double {
            var result = parseFactor()
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
                val op = s[pos++]
                val factor = parseFactor()
                result = if (op == '*') result * factor else {
                    if (factor == 0.0) throw ArithmeticException("Ділення на нуль")
                    result / factor
                }
            }
            return result
        }

        private fun parseFactor(): Double {
            if (pos < s.length && s[pos] == '(') {
                pos++
                val result = parseExpression()
                if (pos >= s.length || s[pos] != ')') throw IllegalArgumentException("Немає )")
                pos++
                return result
            }

            val start = pos
            if (pos < s.length && (s[pos] == '-' || s[pos] == '+')) pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException("Очікувалось число")
            return s.substring(start, pos).toDouble()
        }
    }
}
