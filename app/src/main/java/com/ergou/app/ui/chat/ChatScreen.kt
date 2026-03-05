package com.ergou.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ergou.app.data.local.entity.MessageEntity
import com.ergou.app.data.local.entity.SessionEntity
import com.ergou.app.ui.components.MarkdownText
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit = {},
    viewModel: ChatViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showApiKeyDialog by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 首次启动且没有 API Key 时弹出设置（null=加载中，不弹）
    LaunchedEffect(uiState.isApiKeySet) {
        if (uiState.isApiKeySet == false) {
            showApiKeyDialog = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawer(
                sessions = uiState.sessions,
                currentSessionId = uiState.currentSessionId,
                onNewSession = {
                    viewModel.onNewSession()
                    scope.launch { drawerState.close() }
                },
                onSelectSession = { id ->
                    viewModel.onSwitchSession(id)
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = viewModel::onDeleteSession,
                onApiKeySettings = { showApiKeyDialog = true }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("二狗") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "会话列表")
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    }
                )
            }
        ) { padding ->
            ChatContent(
                uiState = uiState,
                onInputChanged = viewModel::onInputChanged,
                onSend = viewModel::onSendMessage,
                onDismissError = viewModel::dismissError,
                modifier = Modifier.padding(padding)
            )
        }
    }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            onDismiss = { showApiKeyDialog = false },
            onSave = { key ->
                viewModel.onSaveApiKey(key)
                showApiKeyDialog = false
            }
        )
    }
}

@Composable
fun ChatContent(
    uiState: ChatUiState,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // 有新消息或流式内容时滚动到底部
    val totalItems = uiState.messages.size + if (uiState.streamingContent.isNotEmpty()) 1 else 0
    LaunchedEffect(totalItems, uiState.streamingContent) {
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // 消息列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 空状态
            if (uiState.messages.isEmpty() && uiState.streamingContent.isEmpty()) {
                item { WelcomeMessage() }
            }

            // 历史消息
            items(uiState.messages, key = { it.id }) { message ->
                if (message.role != "system") {
                    MessageBubble(
                        content = message.content,
                        isFromUser = message.role == "user"
                    )
                }
            }

            // 流式输出中的消息
            if (uiState.streamingContent.isNotEmpty()) {
                item {
                    // 流式显示时隐藏记忆指令标记
                    val displayContent = uiState.streamingContent
                        .replace(Regex("""\[SAVE_MEMORY:\w+:.+?]"""), "")
                        .replace(Regex("""\[SAVE_PERSON:.+?:.+?:.+?]"""), "")
                        .trim()
                    if (displayContent.isNotEmpty()) {
                        MessageBubble(
                            content = displayContent,
                            isFromUser = false
                        )
                    }
                }
            }

            // 加载中
            if (uiState.isSending && uiState.streamingContent.isEmpty()) {
                item { LoadingIndicator() }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        // 错误提示
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = onDismissError) {
                        Text("关闭")
                    }
                }
            ) {
                Text(error)
            }
        }

        // 输入框 — 用 TextFieldValue 本地管理，避免 IME 中文输入被 StateFlow 打断
        var textFieldValue by remember { mutableStateOf(TextFieldValue()) }

        // ViewModel 清空输入时同步（发送后）
        LaunchedEffect(uiState.inputText) {
            if (uiState.inputText.isEmpty() && textFieldValue.text.isNotEmpty()) {
                textFieldValue = TextFieldValue()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    onInputChanged(newValue.text)
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("说点什么...") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )
            IconButton(
                onClick = onSend,
                enabled = textFieldValue.text.isNotBlank() && !uiState.isSending
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送"
                )
            }
        }
    }
}

@Composable
fun WelcomeMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "二狗",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "有事说事，没事别烦我。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                )
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "  二狗正在想...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MessageBubble(content: String, isFromUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = if (isFromUser)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isFromUser) 16.dp else 4.dp,
                        bottomEnd = if (isFromUser) 4.dp else 16.dp
                    )
                )
                .padding(12.dp)
        ) {
            val textColor = if (isFromUser)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurfaceVariant

            if (isFromUser) {
                Text(text = content, color = textColor)
            } else {
                MarkdownText(text = content, color = textColor)
            }
        }
    }
}

@Composable
fun SessionDrawer(
    sessions: List<SessionEntity>,
    currentSessionId: Long?,
    onNewSession: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onApiKeySettings: () -> Unit = {}
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "会话",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onNewSession) {
                    Icon(Icons.Default.Add, contentDescription = "新对话")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (sessions.isEmpty()) {
                Text(
                    text = "还没有对话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(sessions, key = { it.id }) { session ->
                    SessionItem(
                        session = session,
                        isSelected = session.id == currentSessionId,
                        onClick = { onSelectSession(session.id) },
                        onDelete = { onDeleteSession(session.id) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            TextButton(
                onClick = onApiKeySettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("  API Key 设置")
            }
        }
    }
}

@Composable
fun SessionItem(
    session: SessionEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = session.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ApiKeyDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var keyInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置 API Key") },
        text = {
            Column {
                Text("请输入 DeepSeek API Key")
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = { Text("sk-...") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(keyInput.trim()) },
                enabled = keyInput.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
