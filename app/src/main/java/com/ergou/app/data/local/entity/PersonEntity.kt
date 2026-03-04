package com.ergou.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 人际关系表
 */
@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",
    val relationship: String = "",      // 妻子、同事、朋友、老板...
    val nickname: String = "",          // 二狗称呼ta的方式
    val attitude: String = "",          // 用温暖语气、保持礼貌距离...
    val notes: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
