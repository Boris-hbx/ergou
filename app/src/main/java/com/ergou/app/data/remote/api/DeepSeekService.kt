package com.ergou.app.data.remote.api

import com.ergou.app.data.remote.dto.ChatRequest
import com.ergou.app.data.remote.dto.ChatResponse
import com.ergou.app.util.ApiKeyProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import timber.log.Timber

class DeepSeekService(
    private val httpClient: HttpClient,
    private val apiKeyProvider: ApiKeyProvider
) : LLMService {

    companion object {
        private const val BASE_URL = "https://api.deepseek.com"
        private const val CHAT_ENDPOINT = "$BASE_URL/chat/completions"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun getApiKey(): String = apiKeyProvider.apiKey.first()

    override suspend fun chat(request: ChatRequest): ChatResponse {
        val key = getApiKey()
        Timber.d("发送聊天请求: ${request.messages.lastOrNull()?.content?.take(50)}...")
        return httpClient.post(CHAT_ENDPOINT) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $key")
            setBody(request)
        }.body()
    }

    override fun chatStream(request: ChatRequest): Flow<String> = flow {
        val key = getApiKey()
        val streamRequest = request.copy(stream = true)
        val response = httpClient.post(CHAT_ENDPOINT) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $key")
            setBody(streamRequest)
        }

        val channel = response.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (!line.startsWith("data: ")) continue
            val data = line.removePrefix("data: ").trim()
            if (data == "[DONE]") break

            try {
                val chunk = json.decodeFromString<ChatResponse>(data)
                val content = chunk.choices.firstOrNull()?.delta?.content
                if (content != null) {
                    emit(content)
                }
            } catch (e: Exception) {
                Timber.w(e, "解析SSE数据失败: $data")
            }
        }
    }
}
