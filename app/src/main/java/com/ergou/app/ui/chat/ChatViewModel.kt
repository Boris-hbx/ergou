package com.ergou.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ergou.app.data.local.entity.MessageEntity
import com.ergou.app.data.local.entity.SessionEntity
import com.ergou.app.data.remote.ErgouPrompt
import com.ergou.app.data.remote.dto.Message
import com.ergou.app.data.repository.ChatRepository
import com.ergou.app.data.repository.ChatRepositoryImpl
import com.ergou.app.data.repository.MemoryExtractor
import com.ergou.app.data.repository.MemoryRepository
import com.ergou.app.data.tool.ToolExecutor
import com.ergou.app.util.ApiKeyProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

data class ChatUiState(
    val sessions: List<SessionEntity> = emptyList(),
    val currentSessionId: Long? = null,
    val messages: List<MessageEntity> = emptyList(),
    val streamingContent: String = "",
    val inputText: String = "",
    val isApiKeySet: Boolean? = null,  // null=加载中, false=未设置, true=已设置
    val isSending: Boolean = false,
    val error: String? = null
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val toolExecutor: ToolExecutor,
    private val apiKeyProvider: ApiKeyProvider,
    private val memoryExtractor: MemoryExtractor
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private var messageCollectionJob: Job? = null

    // 记忆指令正则
    private val memoryPattern = Regex("""\[SAVE_MEMORY:(\w+):(.+?)]""")
    private val personPattern = Regex("""\[SAVE_PERSON:(.+?):(.+?):(.+?)]""")

    init {
        viewModelScope.launch {
            val key = apiKeyProvider.apiKey.first()
            _uiState.value = _uiState.value.copy(isApiKeySet = key.isNotBlank())
        }

        viewModelScope.launch {
            chatRepository.getAllSessions().collect { sessions ->
                _uiState.value = _uiState.value.copy(sessions = sessions)
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun onSaveApiKey(key: String) {
        viewModelScope.launch {
            apiKeyProvider.saveApiKey(key)
            _uiState.value = _uiState.value.copy(isApiKeySet = key.isNotBlank())
        }
    }

    fun onNewSession() {
        viewModelScope.launch {
            val sessionId = chatRepository.createSession()
            switchToSession(sessionId)
        }
    }

    fun onSwitchSession(sessionId: Long) {
        viewModelScope.launch {
            switchToSession(sessionId)
        }
    }

    fun onDeleteSession(sessionId: Long) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            if (_uiState.value.currentSessionId == sessionId) {
                messageCollectionJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    currentSessionId = null,
                    messages = emptyList()
                )
            }
        }
    }

    private fun switchToSession(sessionId: Long) {
        messageCollectionJob?.cancel()
        _uiState.value = _uiState.value.copy(currentSessionId = sessionId)
        messageCollectionJob = viewModelScope.launch {
            chatRepository.getMessages(sessionId).collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
            }
        }
    }

    fun onSendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isSending) return

        viewModelScope.launch {
            val sessionId = _uiState.value.currentSessionId ?: run {
                val id = chatRepository.createSession()
                switchToSession(id)
                id
            }

            _uiState.value = _uiState.value.copy(
                inputText = "",
                isSending = true,
                error = null,
                streamingContent = ""
            )

            try {
                // 保存用户消息
                chatRepository.saveMessage(sessionId, "user", text)

                // 第一条消息时自动设置标题
                if (_uiState.value.messages.size <= 1) {
                    (chatRepository as? ChatRepositoryImpl)?.generateTitle(sessionId, text)
                }

                // 构建动态上下文
                val peopleContext = memoryRepository.buildPeopleContext()
                val memoryContext = memoryRepository.buildMemoryContext()

                // 构建消息列表（system + 最近历史，历史已包含刚保存的用户消息）
                val systemPrompt = ErgouPrompt.buildSystemPrompt(
                    peopleContext = peopleContext,
                    memoryContext = memoryContext
                )

                val history = chatRepository.getMessagesOnce(sessionId)
                    .takeLast(MAX_HISTORY_MESSAGES)
                    .map { Message(role = it.role, content = it.content) }

                val messages = buildList {
                    add(Message(role = "system", content = systemPrompt))
                    addAll(history)
                }

                // 通过 ToolExecutor 发送（支持工具调用循环）
                val responseBuilder = StringBuilder()

                toolExecutor.chatWithTools(messages).collect { chunk ->
                    responseBuilder.append(chunk)
                    _uiState.value = _uiState.value.copy(
                        streamingContent = responseBuilder.toString()
                    )
                }

                val fullResponse = responseBuilder.toString()

                if (fullResponse.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        streamingContent = "",
                        error = "二狗没回复，检查一下网络或 API Key"
                    )
                    return@launch
                }

                // 解析并执行记忆指令（文本标记作为备用机制）
                processMemoryCommands(fullResponse, sessionId)

                // 保存AI回复（去掉指令标记）
                val cleanResponse = cleanMemoryCommands(fullResponse)
                chatRepository.saveMessage(sessionId, "assistant", cleanResponse)

                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    streamingContent = ""
                )

                // 后台自动提取记忆（不阻塞UI）
                viewModelScope.launch {
                    memoryExtractor.extractFromConversation(text, cleanResponse, sessionId)
                }

                Timber.d("收到回复: ${cleanResponse.take(50)}...")
            } catch (e: Exception) {
                Timber.e(e, "发送消息失败")
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    streamingContent = "",
                    error = "发送失败: ${e.message}"
                )
            }
        }
    }

    companion object {
        private const val MAX_HISTORY_MESSAGES = 20
    }

    /**
     * 解析AI回复中的记忆指令并执行
     */
    private suspend fun processMemoryCommands(response: String, sessionId: Long) {
        // 解析记忆保存指令
        memoryPattern.findAll(response).forEach { match ->
            val category = match.groupValues[1]
            val content = match.groupValues[2]
            if (category in listOf("fact", "habit", "personality", "intent") && content.isNotBlank()) {
                memoryRepository.saveMemory(category, content, importance = 3, sessionId = sessionId)
                Timber.d("二狗记住了: [$category] $content")
            }
        }

        // 解析人物保存指令
        personPattern.findAll(response).forEach { match ->
            val name = match.groupValues[1]
            val relationship = match.groupValues[2]
            val notes = match.groupValues[3]
            if (name.isNotBlank()) {
                memoryRepository.savePerson(name, relationship, notes = notes)
                Timber.d("二狗认识了: $name ($relationship)")
            }
        }
    }

    /**
     * 清除回复中的指令标记，返回干净文本
     */
    private fun cleanMemoryCommands(response: String): String {
        return response
            .replace(memoryPattern, "")
            .replace(personPattern, "")
            .trim()
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
