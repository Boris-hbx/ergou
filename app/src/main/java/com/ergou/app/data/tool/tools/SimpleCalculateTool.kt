package com.ergou.app.data.tool.tools

import com.ergou.app.data.tool.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class SimpleCalculateTool : Tool {
    override val name = "simple_calculate"
    override val description = "简单数学计算，支持加减乘除"
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("expression") {
                put("type", "string")
                put("description", "数学表达式，如 '2 + 3 * 4'")
            }
        }
        put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("expression")) })
    }

    override suspend fun execute(arguments: Map<String, String>): String {
        val expr = arguments["expression"] ?: return "缺少表达式"
        return try {
            val result = evaluateExpression(expr)
            "$expr = $result"
        } catch (e: Exception) {
            "计算出错: ${e.message}"
        }
    }

    private fun evaluateExpression(expr: String): Double {
        // 简单安全的表达式求值（只支持+-*/和数字）
        val sanitized = expr.replace(" ", "")
        if (!sanitized.matches(Regex("[0-9+\\-*/.()]*"))) {
            throw IllegalArgumentException("不支持的字符")
        }

        // 用 Kotlin 的脚本引擎太重，手动解析简单表达式
        return parseAddSub(sanitized, intArrayOf(0))
    }

    private fun parseAddSub(expr: String, pos: IntArray): Double {
        var result = parseMulDiv(expr, pos)
        while (pos[0] < expr.length) {
            val op = expr[pos[0]]
            if (op != '+' && op != '-') break
            pos[0]++
            val right = parseMulDiv(expr, pos)
            result = if (op == '+') result + right else result - right
        }
        return result
    }

    private fun parseMulDiv(expr: String, pos: IntArray): Double {
        var result = parseNumber(expr, pos)
        while (pos[0] < expr.length) {
            val op = expr[pos[0]]
            if (op != '*' && op != '/') break
            pos[0]++
            val right = parseNumber(expr, pos)
            result = if (op == '*') result * right else result / right
        }
        return result
    }

    private fun parseNumber(expr: String, pos: IntArray): Double {
        if (pos[0] < expr.length && expr[pos[0]] == '(') {
            pos[0]++ // skip (
            val result = parseAddSub(expr, pos)
            if (pos[0] < expr.length && expr[pos[0]] == ')') pos[0]++ // skip )
            return result
        }
        val start = pos[0]
        while (pos[0] < expr.length && (expr[pos[0]].isDigit() || expr[pos[0]] == '.')) {
            pos[0]++
        }
        return expr.substring(start, pos[0]).toDouble()
    }
}
