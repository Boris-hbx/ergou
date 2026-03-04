package com.ergou.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 长期记忆表
 * 分类：fact(事实) / habit(习惯) / personality(性格偏好) / intent(意图计划)
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String = "",          // fact | habit | personality | intent
    val content: String = "",
    val importance: Int = 3,            // 1-5 重要度
    val repeatCount: Int = 1,           // 被提及次数
    val emotionWeight: Float = 1.0f,    // 情感权重 0.5-2.0
    val sourceSessionId: Long = 0,      // 来源会话
    val createdAt: Long = 0,
    val lastAccessedAt: Long = 0,
    val accessCount: Int = 0
)
