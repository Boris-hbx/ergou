package com.ergou.app.data.tool.tools

import com.ergou.app.data.repository.MemoryRepository
import com.ergou.app.data.tool.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class SaveMemoryTool(private val memoryRepository: MemoryRepository) : Tool {
    override val name = "save_memory"
    override val description = "保存一条记忆。当用户要求记住某件事，或者你发现了重要信息时使用"
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("category") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("fact"))
                    add(JsonPrimitive("habit"))
                    add(JsonPrimitive("personality"))
                    add(JsonPrimitive("intent"))
                }
                put("description", "记忆分类：fact事实、habit习惯、personality偏好、intent计划意图")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "要记住的内容")
            }
            putJsonObject("importance") {
                put("type", "integer")
                put("description", "重要度1-5，5最重要")
            }
        }
        put("required", buildJsonArray {
            add(JsonPrimitive("category"))
            add(JsonPrimitive("content"))
        })
    }

    override suspend fun execute(arguments: Map<String, String>): String {
        val category = arguments["category"] ?: return "缺少分类"
        val content = arguments["content"] ?: return "缺少内容"
        val importance = arguments["importance"]?.toIntOrNull() ?: 3

        memoryRepository.saveMemory(category, content, importance)
        return "记住了：$content"
    }
}
