package com.ergou.app.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ergou.app.data.local.entity.MemoryEntity
import com.ergou.app.data.local.entity.PersonEntity
import com.ergou.app.data.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MemoryUiState(
    val memories: List<MemoryEntity> = emptyList(),
    val people: List<PersonEntity> = emptyList(),
    val selectedTab: Int = 0  // 0=记忆, 1=人物
)

class MemoryViewModel(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState

    init {
        viewModelScope.launch {
            memoryRepository.getAllMemories().collect { memories ->
                _uiState.value = _uiState.value.copy(memories = memories)
            }
        }
        viewModelScope.launch {
            memoryRepository.getAllPeople().collect { people ->
                _uiState.value = _uiState.value.copy(people = people)
            }
        }
    }

    fun onTabSelected(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    fun onDeleteMemory(id: Long) {
        viewModelScope.launch { memoryRepository.deleteMemory(id) }
    }

    fun onDeletePerson(id: Long) {
        viewModelScope.launch { memoryRepository.deletePerson(id) }
    }

    fun onClearAllMemories() {
        viewModelScope.launch { memoryRepository.deleteAllMemories() }
    }
}
