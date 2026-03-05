package com.ergou.app.data.tool.tools

import com.ergou.app.data.tool.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 知识查询工具 — 二狗坦诚自己不确定时使用
 * 目前基于内置知识回答，后续可接入真正的搜索API
 */
class WebSearchTool : Tool {
    override val name = "knowledge_query"
    override val description = "当你不确定某个知识点或需要提供事实信息时使用。如果你不确定答案，调用这个工具表明需要查询。"
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "要查询的问题")
            }
        }
        put("required", buildJsonArray {
            add(JsonPrimitive("query"))
        })
    }

    override suspend fun execute(arguments: Map<String, String>): String {
        val query = arguments["query"] ?: return "缺少查询内容"
        val now = SimpleDateFormat("yyyy年M月d日", Locale.CHINESE).format(Date())
        return "当前日期: $now。关于「$query」的查询：请根据你已有的知识回答。如果不确定，坦诚告知用户。"
    }
}
