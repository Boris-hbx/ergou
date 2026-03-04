package com.ergou.app.data.repository

import com.ergou.app.data.local.entity.MessageEntity
import com.ergou.app.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    // 会话管理
    fun getAllSessions(): Flow<List<SessionEntity>>
    suspend fun createSession(title: String = "新对话"): Long
    suspend fun deleteSession(id: Long)
    suspend fun updateSessionTitle(id: Long, title: String)

    // 消息管理
    fun getMessages(sessionId: Long): Flow<List<MessageEntity>>
    suspend fun getMessagesOnce(sessionId: Long): List<MessageEntity>
    suspend fun saveMessage(sessionId: Long, role: String, content: String): Long

    // AI对话
    fun sendMessage(sessionId: Long, userContent: String): Flow<String>
}
