package com.ergou.app.data.tool.tools

import com.ergou.app.data.remote.api.LLMService
import com.ergou.app.data.remote.dto.ChatRequest
import com.ergou.app.data.remote.dto.Message
import com.ergou.app.data.tool.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class TranslateTool(private val llmService: LLMService) : Tool {
    override val name = "translate"
    override val description = "翻译文本。支持中英日韩等多语言互译。"
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("text") {
                put("type", "string")
                put("description", "要翻译的文本")
            }
            putJsonObject("target_language") {
                put("type", "string")
                put("description", "目标语言，如'中文'、'英文'、'日文'")
            }
        }
        put("required", buildJsonArray {
            add(JsonPrimitive("text"))
            add(JsonPrimitive("target_language"))
        })
    }

    override suspend fun execute(arguments: Map<String, String>): String {
        val text = arguments["text"] ?: return "缺少要翻译的文本"
        val targetLang = arguments["target_language"] ?: return "缺少目标语言"

        return try {
            val request = ChatRequest(
                messages = listOf(
                    Message(
                        role = "system",
                        content = "你是一个翻译器。只输出翻译结果，不要解释。"
                    ),
                    Message(
                        role = "user",
                        content = "将以下文本翻译成${targetLang}：\n$text"
                    )
                ),
                stream = false,
                temperature = 0.1,
                maxTokens = 1024
            )

            val response = llmService.chat(request)
            response.choices.firstOrNull()?.message?.content ?: "翻译失败"
        } catch (e: Exception) {
            "翻译出错: ${e.message}"
        }
    }
}
