package com.ergou.app.data.remote.api

import com.ergou.app.data.remote.dto.ChatRequest
import com.ergou.app.data.remote.dto.ChatResponse
import kotlinx.coroutines.flow.Flow

/**
 * LLM服务接口 - 可插拔设计，支持后续切换模型
 */
interface LLMService {
    suspend fun chat(request: ChatRequest): ChatResponse
    fun chatStream(request: ChatRequest): Flow<String>
}
