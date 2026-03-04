package com.ergou.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "新对话",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val messageCount: Int = 0
)
