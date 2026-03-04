package com.ergou.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ergou.app.data.local.dao.MemoryDao
import com.ergou.app.data.local.dao.MessageDao
import com.ergou.app.data.local.dao.PersonDao
import com.ergou.app.data.local.dao.ReminderDao
import com.ergou.app.data.local.dao.SessionDao
import com.ergou.app.data.local.entity.MemoryEntity
import com.ergou.app.data.local.entity.MessageEntity
import com.ergou.app.data.local.entity.PersonEntity
import com.ergou.app.data.local.entity.ReminderEntity
import com.ergou.app.data.local.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        PersonEntity::class,
        ReminderEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ErgouDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun personDao(): PersonDao
    abstract fun reminderDao(): ReminderDao
}
