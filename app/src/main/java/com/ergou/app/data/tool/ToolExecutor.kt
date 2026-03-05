package com.ergou.app.data.tool

import com.ergou.app.data.remote.api.LLMService
import com.ergou.app.data.remote.dto.ChatRequest
import com.ergou.app.data.remote.dto.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/**
 * Tool Use 循环引擎
 * LLM 请求工具 → 执行工具 → 结果回注 → LLM 继续，最多5轮
 */
class ToolExecutor(
    private val llmService: LLMService,
    private val toolRegistry: ToolRegistry
) {
    companion object {
        private const val MAX_ROUNDS = 5
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 带工具调用的对话 — 返回最终文本回复的流
     * 工具调用阶段为非流式，最终回复为流式（真正的SSE）
     */
    fun chatWithTools(messages: List<Message>): Flow<String> = flow {
        val toolDefinitions = toolRegistry.getAllDefinitions()
        val conversationMessages = messages.toMutableList()
        var round = 0

        while (round < MAX_ROUNDS) {
            round++
            Timber.d("Tool Use 第${round}轮开始，消息数: ${conversationMessages.size}")

            // 非流式请求（需要解析 tool_calls）
            val request = ChatRequest(
                messages = conversationMessages,
                tools = if (toolDefinitions.isNotEmpty()) toolDefinitions else null,
                stream = false
            )

            val response = try {
                llmService.chat(request)
            } catch (e: Exception) {
                Timber.e(e, "LLM API 调用失败")
                // 降级：不带工具重试一次，用流式
                Timber.d("降级为无工具流式请求")
                val fallbackRequest = ChatRequest(
                    messages = conversationMessages,
                    stream = true
                )
                llmService.chatStream(fallbackRequest).collect { emit(it) }
                return@flow
            }

            val choice = response.choices.firstOrNull()
            val message = choice?.message

            if (message == null) {
                // API 返回了但没有有效内容，降级为无工具流式请求
                Timber.w("API 返回空 choices/message，降级为无工具流式请求")
                val fallbackRequest = ChatRequest(
                    messages = conversationMessages,
                    stream = true
                )
                llmService.chatStream(fallbackRequest).collect { emit(it) }
                return@flow
            }

            // 检查是否有工具调用
            val toolCalls = message.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                // 没有工具调用 — 这是最终回复，用流式重新请求获得更好的体验
                val content = message.content
                if (!content.isNullOrBlank()) {
                    // 已经拿到完整回复，分块输出
                    val chunks = content.chunked(3)
                    for (chunk in chunks) {
                        emit(chunk)
                    }
                } else {
                    emit("（二狗想说什么但忘了，再问一次？）")
                }
                return@flow
            }

            // 有工具调用：加入 assistant 的 tool_calls 消息
            conversationMessages.add(
                Message(
                    role = "assistant",
                    content = message.content,
                    toolCalls = toolCalls
                )
            )

            // 执行每个工具调用
            for (toolCall in toolCalls) {
                val toolName = toolCall.function.name
                val argsString = toolCall.function.arguments

                val args = try {
                    val jsonObj = json.decodeFromString<JsonObject>(argsString)
                    jsonObj.mapValues { it.value.jsonPrimitive.content }
                } catch (e: Exception) {
                    Timber.w(e, "解析工具参数失败: $argsString")
                    emptyMap()
                }

                val result = toolRegistry.executeTool(toolName, args)

                // 加入 tool result 消息
                conversationMessages.add(
                    Message(
                        role = "tool",
                        content = result,
                        toolCallId = toolCall.id
                    )
                )
            }

            Timber.d("Tool Use 第${round}轮完成，工具: ${toolCalls.map { it.function.name }}")
        }

        if (round >= MAX_ROUNDS) {
            emit("（工具调用轮次已达上限）")
        }
    }
}
