package com.ergou.app.data.repository

import com.ergou.app.data.local.entity.MemoryEntity
import com.ergou.app.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    // 记忆 CRUD
    fun getAllMemories(): Flow<List<MemoryEntity>>
    fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>>
    suspend fun saveMemory(category: String, content: String, importance: Int = 3, sessionId: Long = 0): Long
    suspend fun updateMemory(memory: MemoryEntity)
    suspend fun deleteMemory(id: Long)
    suspend fun deleteAllMemories()

    // 记忆召回
    suspend fun recallMemories(limit: Int = 20): List<MemoryEntity>
    suspend fun searchMemories(keyword: String): List<MemoryEntity>

    // 记忆强度计算
    suspend fun getMemoryStrength(memory: MemoryEntity): Float

    // 构建记忆上下文（注入prompt用）
    suspend fun buildMemoryContext(): String

    // 人物 CRUD
    fun getAllPeople(): Flow<List<PersonEntity>>
    suspend fun savePerson(name: String, relationship: String, nickname: String = "", attitude: String = "", notes: String = ""): Long
    suspend fun updatePerson(person: PersonEntity)
    suspend fun deletePerson(id: Long)

    // 构建人物上下文（注入prompt用）
    suspend fun buildPeopleContext(): String
}
