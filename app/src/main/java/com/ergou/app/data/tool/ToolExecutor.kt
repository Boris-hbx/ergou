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
     * 工具调用阶段为非流式，最终回复为流式
     */
    fun chatWithTools(messages: List<Message>): Flow<String> = flow {
        val toolDefinitions = toolRegistry.getAllDefinitions()
        val conversationMessages = messages.toMutableList()
        var round = 0

        while (round < MAX_ROUNDS) {
            round++

            // 非流式请求（需要解析 tool_calls）
            val request = ChatRequest(
                messages = conversationMessages,
                tools = if (toolDefinitions.isNotEmpty()) toolDefinitions else null,
                stream = false
            )

            val response = llmService.chat(request)
            val choice = response.choices.firstOrNull() ?: break
            val message = choice.message ?: break

            // 检查是否有工具调用
            val toolCalls = message.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                // 没有工具调用，这是最终回复
                val content = message.content ?: ""
                // 流式输出最终回复（模拟逐字效果）
                val chunks = content.chunked(3) // 每3个字符一块
                for (chunk in chunks) {
                    emit(chunk)
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
