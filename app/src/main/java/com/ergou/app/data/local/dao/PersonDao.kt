package com.ergou.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ergou.app.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Update
    suspend fun update(person: PersonEntity)

    @Query("SELECT * FROM people ORDER BY updatedAt DESC")
    fun getAllPeople(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getById(id: Long): PersonEntity?

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM people WHERE name LIKE '%' || :name || '%'")
    suspend fun searchByName(name: String): List<PersonEntity>

    @Query("SELECT * FROM people ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentPeople(limit: Int): List<PersonEntity>
}
