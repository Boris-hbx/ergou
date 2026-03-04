package com.ergou.app.data.repository

import com.ergou.app.data.local.dao.MessageDao
import com.ergou.app.data.local.dao.SessionDao
import com.ergou.app.data.local.entity.MessageEntity
import com.ergou.app.data.local.entity.SessionEntity
import com.ergou.app.data.remote.ErgouPrompt
import com.ergou.app.data.remote.api.LLMService
import com.ergou.app.data.remote.dto.ChatRequest
import com.ergou.app.data.remote.dto.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

class ChatRepositoryImpl(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val llmService: LLMService
) : ChatRepository {

    companion object {
        private const val MAX_HISTORY_MESSAGES = 20
    }

    override fun getAllSessions(): Flow<List<SessionEntity>> = sessionDao.getAllSessions()

    override suspend fun createSession(title: String): Long {
        val now = System.currentTimeMillis()
        return sessionDao.insert(SessionEntity(title = title, createdAt = now, updatedAt = now))
    }

    override suspend fun deleteSession(id: Long) {
        sessionDao.deleteById(id)
    }

    override suspend fun updateSessionTitle(id: Long, title: String) {
        sessionDao.updateTitle(id, title, System.currentTimeMillis())
    }

    override fun getMessages(sessionId: Long): Flow<List<MessageEntity>> =
        messageDao.getMessagesBySession(sessionId)

    override suspend fun getMessagesOnce(sessionId: Long): List<MessageEntity> =
        messageDao.getMessagesBySessionOnce(sessionId)

    override suspend fun saveMessage(sessionId: Long, role: String, content: String): Long {
        val messageId = messageDao.insert(
            MessageEntity(
                sessionId = sessionId,
                role = role,
                content = content,
                createdAt = System.currentTimeMillis()
            )
        )
        sessionDao.incrementMessageCount(sessionId, System.currentTimeMillis())
        return messageId
    }

    override fun sendMessage(sessionId: Long, userContent: String): Flow<String> = flow {
        // 构建消息历史
        val history = messageDao.getRecentMessages(sessionId, MAX_HISTORY_MESSAGES)
            .reversed()
            .map { Message(role = it.role, content = it.content) }

        val messages = buildList {
            // System prompt 由 ViewModel 层构建（含动态上下文）
            // 这里用无上下文版本作为 fallback
            add(Message(role = "system", content = ErgouPrompt.buildSystemPrompt()))
            addAll(history)
            add(Message(role = "user", content = userContent))
        }

        val request = ChatRequest(messages = messages, stream = true)

        llmService.chatStream(request).collect { chunk ->
            emit(chunk)
        }
    }

    /**
     * 带动态上下文的消息发送
     */
    fun sendMessageWithContext(
        sessionId: Long,
        userContent: String,
        peopleContext: String,
        memoryContext: String
    ): Flow<String> = flow {
        val history = messageDao.getRecentMessages(sessionId, MAX_HISTORY_MESSAGES)
            .reversed()
            .map { Message(role = it.role, content = it.content) }

        val systemPrompt = ErgouPrompt.buildSystemPrompt(
            peopleContext = peopleContext,
            memoryContext = memoryContext
        )

        val messages = buildList {
            add(Message(role = "system", content = systemPrompt))
            addAll(history)
            add(Message(role = "user", content = userContent))
        }

        val request = ChatRequest(messages = messages, stream = true)

        llmService.chatStream(request).collect { chunk ->
            emit(chunk)
        }
    }

    suspend fun generateTitle(sessionId: Long, firstMessage: String) {
        val title = if (firstMessage.length <= 20) {
            firstMessage
        } else {
            firstMessage.take(18) + "..."
        }
        sessionDao.updateTitle(sessionId, title, System.currentTimeMillis())
    }
}
