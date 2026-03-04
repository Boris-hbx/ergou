package com.ergou.app.data.tool.tools

import com.ergou.app.data.repository.MemoryRepository
import com.ergou.app.data.tool.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class SearchMemoryTool(private val memoryRepository: MemoryRepository) : Tool {
    override val name = "search_memory"
    override val description = "搜索记忆，当用户问'我之前说过什么'或需要回忆某件事时使用"
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("keyword") {
                put("type", "string")
                put("description", "搜索关键词")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("keyword")) })
    }

    override suspend fun execute(arguments: Map<String, String>): String {
        val keyword = arguments["keyword"] ?: return "缺少关键词"
        val results = memoryRepository.searchMemories(keyword)
        if (results.isEmpty()) return "没找到关于「$keyword」的记忆"

        return results.take(5).joinToString("\n") { memory ->
            "[${memory.category}] ${memory.content}"
        }
    }
}
