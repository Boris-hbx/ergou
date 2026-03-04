package com.ergou.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ergou.app.data.local.dao.MessageDao
import com.ergou.app.data.local.dao.SessionDao
import com.ergou.app.data.local.entity.MessageEntity
import com.ergou.app.data.local.entity.SessionEntity

@Database(
    entities = [SessionEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ErgouDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
}
