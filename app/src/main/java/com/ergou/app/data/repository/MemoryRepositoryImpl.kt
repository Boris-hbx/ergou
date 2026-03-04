package com.ergou.app.data.repository

import com.ergou.app.data.local.dao.MemoryDao
import com.ergou.app.data.local.dao.PersonDao
import com.ergou.app.data.local.entity.MemoryEntity
import com.ergou.app.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import kotlin.math.ln
import kotlin.math.max

class MemoryRepositoryImpl(
    private val memoryDao: MemoryDao,
    private val personDao: PersonDao
) : MemoryRepository {

    companion object {
        // 时间衰减半衰期（7天，单位毫秒）
        private const val HALF_LIFE_MS = 7 * 24 * 60 * 60 * 1000L
    }

    // === 记忆 CRUD ===

    override fun getAllMemories(): Flow<List<MemoryEntity>> = memoryDao.getAllMemories()

    override fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>> =
        memoryDao.getByCategory(category)

    override suspend fun saveMemory(category: String, content: String, importance: Int, sessionId: Long): Long {
        val now = System.currentTimeMillis()
        val id = memoryDao.insert(
            MemoryEntity(
                category = category,
                content = content,
                importance = importance.coerceIn(1, 5),
                sourceSessionId = sessionId,
                createdAt = now,
                lastAccessedAt = now,
                accessCount = 1
            )
        )
        Timber.d("保存记忆: [$category] $content (重要度:$importance)")
        return id
    }

    override suspend fun updateMemory(memory: MemoryEntity) = memoryDao.update(memory)

    override suspend fun deleteMemory(id: Long) = memoryDao.deleteById(id)

    override suspend fun deleteAllMemories() = memoryDao.deleteAll()

    // === 记忆召回 ===

    override suspend fun recallMemories(limit: Int): List<MemoryEntity> {
        val allMemories = memoryDao.recallTopMemories(limit * 2) // 取多一些做二次排序
        val now = System.currentTimeMillis()

        // 按记忆强度二次排序
        return allMemories
            .sortedByDescending { getMemoryStrengthInternal(it, now) }
            .take(limit)
            .also { memories ->
                // 标记这些记忆被访问
                memories.forEach { memoryDao.markAccessed(it.id, now) }
            }
    }

    override suspend fun searchMemories(keyword: String): List<MemoryEntity> =
        memoryDao.searchByKeyword(keyword)

    // === 记忆强度模型 ===
    // strength = importance × repetition_factor × emotion_weight × time_decay

    override suspend fun getMemoryStrength(memory: MemoryEntity): Float =
        getMemoryStrengthInternal(memory, System.currentTimeMillis())

    private fun getMemoryStrengthInternal(memory: MemoryEntity, now: Long): Float {
        val importance = memory.importance.toFloat()
        val repetitionFactor = 1.0f + ln(max(memory.repeatCount.toFloat(), 1.0f))
        val emotionWeight = memory.emotionWeight
        val timeDecay = calculateTimeDecay(memory.lastAccessedAt, now)

        return importance * repetitionFactor * emotionWeight * timeDecay
    }

    /**
     * 指数时间衰减：半衰期7天
     * 刚访问过 → ~1.0，7天前 → ~0.5，14天前 → ~0.25
     */
    private fun calculateTimeDecay(lastAccessedAt: Long, now: Long): Float {
        if (lastAccessedAt == 0L) return 0.1f
        val elapsed = (now - lastAccessedAt).toFloat()
        return Math.pow(0.5, (elapsed / HALF_LIFE_MS).toDouble()).toFloat()
            .coerceIn(0.01f, 1.0f)
    }

    // === 上下文构建 ===

    override suspend fun buildMemoryContext(): String {
        val memories = recallMemories(20)
        if (memories.isEmpty()) return ""

        val sb = StringBuilder()
        val grouped = memories.groupBy { it.category }

        grouped["fact"]?.let { facts ->
            sb.appendLine("你记得的事实：")
            facts.forEach { sb.appendLine("- ${it.content}") }
        }
        grouped["habit"]?.let { habits ->
            sb.appendLine("你观察到的习惯：")
            habits.forEach { sb.appendLine("- ${it.content}") }
        }
        grouped["personality"]?.let { traits ->
            sb.appendLine("你了解的偏好：")
            traits.forEach { sb.appendLine("- ${it.content}") }
        }
        grouped["intent"]?.let { intents ->
            sb.appendLine("用户的计划/意图：")
            intents.forEach { sb.appendLine("- ${it.content}") }
        }

        return sb.toString().trimEnd()
    }

    // === 人物 CRUD ===

    override fun getAllPeople(): Flow<List<PersonEntity>> = personDao.getAllPeople()

    override suspend fun savePerson(
        name: String, relationship: String, nickname: String, attitude: String, notes: String
    ): Long {
        val now = System.currentTimeMillis()
        return personDao.insert(
            PersonEntity(
                name = name,
                relationship = relationship,
                nickname = nickname,
                attitude = attitude,
                notes = notes,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun updatePerson(person: PersonEntity) = personDao.update(person)

    override suspend fun deletePerson(id: Long) = personDao.deleteById(id)

    override suspend fun buildPeopleContext(): String {
        val people = personDao.getRecentPeople(10)
        if (people.isEmpty()) return ""

        val sb = StringBuilder("你认识的人：\n")
        people.forEach { p ->
            sb.append("- ${p.name}")
            if (p.relationship.isNotBlank()) sb.append("（${p.relationship}）")
            if (p.nickname.isNotBlank()) sb.append("，你叫ta「${p.nickname}」")
            if (p.attitude.isNotBlank()) sb.append("，${p.attitude}")
            if (p.notes.isNotBlank()) sb.append("。备注：${p.notes}")
            sb.appendLine()
        }
        return sb.toString().trimEnd()
    }
}
