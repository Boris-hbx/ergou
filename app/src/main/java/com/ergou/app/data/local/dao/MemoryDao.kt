package com.ergou.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ergou.app.data.local.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("SELECT * FROM memories ORDER BY lastAccessedAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY lastAccessedAt DESC")
    fun getByCategory(category: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: Long): MemoryEntity?

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()

    /**
     * 召回记忆：按记忆强度排序（重要度 × 重复系数 × 情感权重 / 时间衰减）
     * 时间衰减用 accessCount 和 lastAccessedAt 近似
     */
    @Query("""
        SELECT * FROM memories
        ORDER BY (importance * (1.0 + repeatCount * 0.2) * emotionWeight * accessCount) DESC
        LIMIT :limit
    """)
    suspend fun recallTopMemories(limit: Int): List<MemoryEntity>

    /**
     * 关键词搜索记忆
     */
    @Query("SELECT * FROM memories WHERE content LIKE '%' || :keyword || '%' ORDER BY lastAccessedAt DESC")
    suspend fun searchByKeyword(keyword: String): List<MemoryEntity>

    /**
     * 更新访问时间和次数（每次被召回时调用）
     */
    @Query("UPDATE memories SET lastAccessedAt = :accessTime, accessCount = accessCount + 1 WHERE id = :id")
    suspend fun markAccessed(id: Long, accessTime: Long)

    /**
     * 增加重复计数（同一事实被再次提及时）
     */
    @Query("UPDATE memories SET repeatCount = repeatCount + 1 WHERE id = :id")
    suspend fun incrementRepeat(id: Long)
}
