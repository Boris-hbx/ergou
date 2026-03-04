package com.ergou.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String = "",
    val triggerAt: Long = 0,        // 触发时间戳
    val isCompleted: Boolean = false,
    val createdAt: Long = 0
)
