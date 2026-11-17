package com.example.obd.protocol

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class TorqueCsvPid(
    val name: String,
    val shortName: String,
    val modeAndPid: String,
    val equation: String,
    val minValue: Float,
    val maxValue: Float,
    val unit: String,
    val header: String
)

/**
 * Evaluates Torque Pro style OBD2 PID equations using variables A..H (response payload bytes)
 * and functions SIGNED(x), ABS(x), MIN(x,y), MAX(x,y), INT(x), ROUND(x), SQRT(x), BIT(val,bit).
 */
object TorqueEquationEvaluator {

    fun normalizeModeAndPid(raw: String): String {
        return raw.uppercase().replace(Regex("[^0-9A-F]"), "")
    }

    fun normalizeHeader(raw: String): String {
        return raw.uppercase().replace(Regex("[^0-9A-F]"), "")
    }

    fun validateEquation(equation: String, isEnglish: Boolean = false): Result<Unit> {
        return runCatching {
            val trimmed = equation.trim()
            require(trimmed.isNotEmpty()) {
                if (isEnglish) "Equation cannot be empty" else "Формула не может быть пустой"
            }
            val dummyBytes = listOf(0x1A, 0xF8, 0x10, 0x20, 0x30, 0x40, 0x50, 0x60)
            val parser = Parser(trimmed, dummyBytes, isEnglish)
            parser.parseExpression()
            parser.expectEnd()
        }
    }

    fun evaluate(equation: String, bytes: List<Int>): Float? {
        return runCatching {
            val trimmed = equation.trim()
            if (trimmed.isEmpty()) return null
            val parser = Parser(trimmed, bytes)
            val result = parser.parseExpression()
            parser.expectEnd()
            if (result.isNaN() || result.isInfinite()) null else result.toFloat()
        }.getOrNull()
    }

    fun expectedResponseHeader(modeAndPid: String): String? {
        val clean = normalizeModeAndPid(modeAndPid)
        if (clean.length < 4) return null
        val modeByte = clean.substring(0, 2).toIntOrNull(16) ?: return null
        val respMode = (modeByte + 0x40) and 0xFF
        val pidRest = clean.substring(2)
        return String.format("%02X%s", respMode, pidRest)
    }

    fun parseCustomPidResponse(modeAndPid: String, equation: String, rawResponse: String): Float? {
        if (Elm327Parser.isErrorResponse(rawResponse)) return null
        val expectedHeader = expectedResponseHeader(modeAndPid) ?: return null
        val bytes = Elm327Parser.extractIsoTpPayload(expectedHeader, rawResponse) ?: return null
        return evaluate(equation, bytes)
    }

    fun parseHexSampleBytes(sampleHex: String): List<Int> {
        val clean = sampleHex.uppercase().replace(Regex("[^0-9A-F]"), "")
        val bytes = mutableListOf<Int>()
        var i = 0
        while (i + 1 < clean.length) {
            clean.substring(i, i + 2).toIntOrNull(16)?.let { bytes.add(it) }
            i += 2
        }
        return bytes
    }

    /**
     * Parses standard Torque Pro CSV extended PID definitions:
     * Name,ShortName,ModeAndPID,Equation,Min Value,Max Value,Units,Header
     */
    fun parseTorqueCsv(csvText: String): List<TorqueCsvPid> {
        val result = mutableListOf<TorqueCsvPid>()
        val lines = csvText.replace("\r\n", "\n").replace("\r", "\n").lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val cols = splitCsvRow(line)
            if (cols.size < 4) continue

            val firstLower = cols[0].trim().lowercase()
            if (firstLower == "name" || firstLower == "название") continue

            val name = cols[0].trim()
            val shortName = cols.getOrNull(1)?.trim().orEmpty().ifBlank { name.take(12) }
            val modeAndPid = normalizeModeAndPid(cols.getOrNull(2).orEmpty())
            val equation = cols.getOrNull(3)?.trim().orEmpty()
            val minVal = cols.getOrNull(4)?.trim()?.toFloatOrNull() ?: 0f
            val maxVal = cols.getOrNull(5)?.trim()?.toFloatOrNull() ?: 100f
            val unit = cols.getOrNull(6)?.trim().orEmpty()
            val header = normalizeHeader(cols.getOrNull(7).orEmpty())

            if (name.isBlank() || modeAndPid.length < 4 || validateEquation(equation).isFailure) {
                continue
            }
            result.add(
                TorqueCsvPid(
                    name = name,
                    shortName = shortName,
                    modeAndPid = modeAndPid,
                    equation = equation,
                    minValue = minVal,
                    maxValue = if (maxVal > minVal) maxVal else minVal + 100f,
                    unit = unit,
                    header = header
                )
            )
        }
        return result
    }

    private fun splitCsvRow(line: String): List<String> {
        val delimiter = if (line.count { it == ';' } > line.count { it == ',' }) ';' else ','
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    private class Parser(
        private val input: String,
        private val bytes: List<Int>,
        private val isEnglish: Boolean = false
    ) {
        private var pos = 0

        fun expectEnd() {
            skipWhitespace()
            if (pos < input.length) {
                throw IllegalArgumentException(
                    if (isEnglish) "Unexpected trailing character '${input[pos]}' at position $pos"
                    else "Лишний символ '${input[pos]}' в позиции $pos"
                )
            }
        }

        fun parseExpression(): Double {
            var left = parseTerm()
            while (true) {
                skipWhitespace()
                left = when {
                    match('+') -> left + parseTerm()
                    match('-') -> left - parseTerm()
                    else -> return left
                }
            }
        }

        private fun parseTerm(): Double {
            var left = parseFactor()
            while (true) {
                skipWhitespace()
                left = when {
                    match('*') -> left * parseFactor()
                    match('/') -> {
                        val right = parseFactor()
                        if (right == 0.0) Double.NaN else left / right
                    }
                    match('%') -> {
                        val right = parseFactor()
                        if (right == 0.0) Double.NaN else left % right
                    }
                    else -> return left
                }
            }
        }

        private fun parseFactor(): Double {
            skipWhitespace()
            if (match('+')) return parseFactor()
            if (match('-')) return -parseFactor()

            if (match('(')) {
                val value = parseExpression()
                skipWhitespace()
                if (!match(')')) {
                    throw IllegalArgumentException(
                        if (isEnglish) "Expected closing parenthesis ')'"
                        else "Ожидалась закрывающая скобка ')'"
                    )
                }
                return value
            }

            if (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
                return parseNumber()
            }

            if (pos < input.length && input[pos].isLetter()) {
                val ident = parseIdentifier().uppercase()
                skipWhitespace()
                if (match('(')) {
                    val args = mutableListOf<Double>()
                    skipWhitespace()
                    if (!match(')')) {
                        do {
                            args.add(parseExpression())
                            skipWhitespace()
                        } while (match(','))
                        if (!match(')')) {
                            throw IllegalArgumentException(
                                if (isEnglish) "Expected ')' after $ident arguments"
                                else "Ожидалась ')' после аргументов $ident"
                            )
                        }
                    }
                    return evalFunction(ident, args)
                }
                return resolveVariable(ident)
            }

            throw IllegalArgumentException(
                if (isEnglish) "Unexpected character in equation (position $pos)"
                else "Неожиданный символ в формуле (позиция $pos)"
            )
        }

        private fun parseNumber(): Double {
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
                pos++
            }
            return input.substring(start, pos).toDoubleOrNull()
                ?: throw IllegalArgumentException(
                    if (isEnglish) "Invalid number" else "Некорректное число"
                )
        }

        private fun parseIdentifier(): String {
            val start = pos
            while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) {
                pos++
            }
            return input.substring(start, pos)
        }

        private fun resolveVariable(name: String): Double {
            if (name.length == 1 && name[0] in 'A'..'P') {
                val idx = name[0] - 'A'
                return (bytes.getOrNull(idx) ?: 0).toDouble()
            }
            throw IllegalArgumentException(
                if (isEnglish) "Unknown variable '$name' (allowed A..P)"
                else "Неизвестная переменная '$name' (доступны A..P)"
            )
        }

        private fun evalFunction(name: String, args: List<Double>): Double {
            return when (name) {
                "SIGNED" -> {
                    val v = args.firstOrNull()?.toInt() ?: 0
                    if (v >= 128) (v - 256).toDouble() else v.toDouble()
                }
                "ABS" -> abs(args.firstOrNull() ?: 0.0)
                "MIN" -> {
                    if (args.size < 2) {
                        throw IllegalArgumentException(
                            if (isEnglish) "MIN requires 2 arguments" else "MIN требует 2 аргумента"
                        )
                    }
                    min(args[0], args[1])
                }
                "MAX" -> {
                    if (args.size < 2) {
                        throw IllegalArgumentException(
                            if (isEnglish) "MAX requires 2 arguments" else "MAX требует 2 аргумента"
                        )
                    }
                    max(args[0], args[1])
                }
                "INT" -> (args.firstOrNull() ?: 0.0).toInt().toDouble()
                "ROUND" -> (args.firstOrNull() ?: 0.0).roundToInt().toDouble()
                "SQRT" -> {
                    val v = args.firstOrNull() ?: 0.0
                    if (v < 0.0) Double.NaN else sqrt(v)
                }
                "BIT" -> {
                    if (args.size < 2) {
                        throw IllegalArgumentException(
                            if (isEnglish) "BIT requires 2 arguments (byte, bit)"
                            else "BIT требует 2 аргумента (байт, бит)"
                        )
                    }
                    val byteVal = args[0].toInt()
                    val bitPos = args[1].toInt().coerceIn(0, 31)
                    if (((byteVal shr bitPos) and 1) == 1) 1.0 else 0.0
                }
                else -> throw IllegalArgumentException(
                    if (isEnglish) "Unknown function '$name'" else "Неизвестная функция '$name'"
                )
            }
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }

        private fun match(ch: Char): Boolean {
            if (pos < input.length && input[pos] == ch) {
                pos++
                return true
            }
            return false
        }
    }
}
