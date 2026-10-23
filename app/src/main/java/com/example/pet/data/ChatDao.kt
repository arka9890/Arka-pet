package com.example.pet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ChatDao {
    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Query("SELECT * FROM chat_messages WHERE petId = :petId ORDER BY timestamp ASC, id ASC")
    suspend fun getByPet(petId: String): List<ChatMessage>

    @Query("DELETE FROM chat_messages WHERE petId = :petId")
    suspend fun clear(petId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}