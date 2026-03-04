package com.ergou.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ergou.app.data.repository.MemoryRepository
import com.ergou.app.util.ApiKeyProvider
import com.ergou.app.util.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKeyMasked: String = "",
    val hasApiKey: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val memoryCount: Int = 0,
    val peopleCount: Int = 0
)

class SettingsViewModel(
    private val apiKeyProvider: ApiKeyProvider,
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            apiKeyProvider.apiKey.collect { key ->
                _uiState.value = _uiState.value.copy(
                    hasApiKey = key.isNotBlank(),
                    apiKeyMasked = if (key.length > 8) "sk-***${key.takeLast(4)}" else ""
                )
            }
        }

        viewModelScope.launch {
            apiKeyProvider.themeMode.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }

        viewModelScope.launch {
            memoryRepository.getAllMemories().collect { memories ->
                _uiState.value = _uiState.value.copy(memoryCount = memories.size)
            }
        }

        viewModelScope.launch {
            memoryRepository.getAllPeople().collect { people ->
                _uiState.value = _uiState.value.copy(peopleCount = people.size)
            }
        }
    }

    fun onSaveApiKey(key: String) {
        viewModelScope.launch {
            apiKeyProvider.saveApiKey(key)
        }
    }

    fun onThemeModeChanged(mode: ThemeMode) {
        viewModelScope.launch {
            apiKeyProvider.saveThemeMode(mode)
        }
    }
}
