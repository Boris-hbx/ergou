package com.ergou.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ergou.app.data.local.entity.MessageEntity
import com.ergou.app.data.local.entity.SessionEntity
import com.ergou.app.data.repository.ChatRepository
import com.ergou.app.data.repository.ChatRepositoryImpl
import com.ergou.app.util.ApiKeyProvider
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
    val isApiKeySet: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val apiKeyProvider: ApiKeyProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    init {
        // 检查API Key
        viewModelScope.launch {
            val key = apiKeyProvider.apiKey.first()
            _uiState.value = _uiState.value.copy(isApiKeySet = key.isNotBlank())
        }

        // 加载会话列表
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
                _uiState.value = _uiState.value.copy(
                    currentSessionId = null,
                    messages = emptyList()
                )
            }
        }
    }

    private suspend fun switchToSession(sessionId: Long) {
        _uiState.value = _uiState.value.copy(currentSessionId = sessionId)
        chatRepository.getMessages(sessionId).collect { messages ->
            _uiState.value = _uiState.value.copy(messages = messages)
        }
    }

    fun onSendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isSending) return

        viewModelScope.launch {
            // 如果没有当前会话，自动创建
            val sessionId = _uiState.value.currentSessionId ?: run {
                val id = chatRepository.createSession()
                _uiState.value = _uiState.value.copy(currentSessionId = id)
                // 开始监听该会话的消息
                launch {
                    chatRepository.getMessages(id).collect { messages ->
                        _uiState.value = _uiState.value.copy(messages = messages)
                    }
                }
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
                val messageCount = _uiState.value.messages.size
                if (messageCount <= 1) {
                    (chatRepository as? ChatRepositoryImpl)?.generateTitle(sessionId, text)
                }

                // 流式获取AI回复
                val responseBuilder = StringBuilder()
                chatRepository.sendMessage(sessionId, text).collect { chunk ->
                    responseBuilder.append(chunk)
                    _uiState.value = _uiState.value.copy(
                        streamingContent = responseBuilder.toString()
                    )
                }

                // 保存AI回复
                val fullResponse = responseBuilder.toString()
                chatRepository.saveMessage(sessionId, "assistant", fullResponse)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    streamingContent = ""
                )

                Timber.d("收到回复: ${fullResponse.take(50)}...")
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

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
