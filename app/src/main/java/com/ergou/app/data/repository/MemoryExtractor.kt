package com.ergou.app.data.repository

import com.ergou.app.data.remote.api.LLMService
import com.ergou.app.data.remote.dto.ChatRequest
import com.ergou.app.data.remote.dto.Message
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * 自动记忆提取器 — 每轮对话后在后台分析，提取值得记住的信息
 */
class MemoryExtractor(
    private val llmService: LLMService,
    private val memoryRepository: MemoryRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val EXTRACTION_PROMPT = """
你是一个信息提取助手。分析以下对话，提取用户透露的重要个人信息。

规则：
- 只提取用户明确说出的事实，不要推测
- 忽略闲聊、问候、泛泛而谈的内容
- 如果没有值得记住的信息，返回空数组
- 每条记忆要简洁（20字以内）

返回严格JSON格式（不要加任何其他文字）：
{"memories":[{"category":"fact/habit/personality/intent","content":"简要描述","importance":1-5}],"people":[{"name":"姓名","relationship":"关系","notes":"备注"}]}

category说明：
- fact: 客观事实（生日、过敏、住址等）
- habit: 行为习惯（作息、饮食偏好等）
- personality: 性格偏好（喜欢的东西、讨厌的东西等）
- intent: 计划意图（要做的事、目标等）
""".trimIndent()
    }

    /**
     * 分析一轮对话，提取并保存记忆。后台调用，不阻塞UI。
     */
    suspend fun extractFromConversation(
        userMessage: String,
        assistantReply: String,
        sessionId: Long
    ) {
        // 太短的对话不分析
        if (userMessage.length < 5) return

        try {
            val messages = listOf(
                Message(role = "system", content = EXTRACTION_PROMPT),
                Message(
                    role = "user",
                    content = "用户说：$userMessage\n助手回复：$assistantReply"
                )
            )

            val request = ChatRequest(
                messages = messages,
                stream = false,
                temperature = 0.1,  // 低温度，确保稳定输出
                maxTokens = 512
            )

            val response = llmService.chat(request)
            val content = response.choices.firstOrNull()?.message?.content ?: return

            parseAndSave(content, sessionId)
        } catch (e: Exception) {
            Timber.w(e, "自动记忆提取失败（不影响正常使用）")
        }
    }

    private suspend fun parseAndSave(responseText: String, sessionId: Long) {
        // 提取JSON部分（LLM可能在前后加了多余文字）
        val jsonStr = responseText
            .substringAfter("{", "")
            .substringBeforeLast("}", "")
            .let { "{$it}" }

        val result = try {
            json.decodeFromString<ExtractionResult>(jsonStr)
        } catch (e: Exception) {
            Timber.w("记忆提取JSON解析失败: ${responseText.take(100)}")
            return
        }

        var savedCount = 0

        // 保存记忆
        result.memories.forEach { memory ->
            if (memory.content.isNotBlank() &&
                memory.category in listOf("fact", "habit", "personality", "intent")
            ) {
                memoryRepository.saveMemory(
                    category = memory.category,
                    content = memory.content,
                    importance = memory.importance.coerceIn(1, 5),
                    sessionId = sessionId
                )
                savedCount++
            }
        }

        // 保存人物
        result.people.forEach { person ->
            if (person.name.isNotBlank()) {
                memoryRepository.savePerson(
                    name = person.name,
                    relationship = person.relationship,
                    notes = person.notes
                )
                savedCount++
            }
        }

        if (savedCount > 0) {
            Timber.d("自动提取了 $savedCount 条记忆")
        }
    }

    @Serializable
    data class ExtractionResult(
        val memories: List<ExtractedMemory> = emptyList(),
        val people: List<ExtractedPerson> = emptyList()
    )

    @Serializable
    data class ExtractedMemory(
        val category: String = "",
        val content: String = "",
        val importance: Int = 3
    )

    @Serializable
    data class ExtractedPerson(
        val name: String = "",
        val relationship: String = "",
        val notes: String = ""
    )
}
