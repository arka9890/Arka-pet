package com.example.pet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val petId: String,
    val role: String,
    val text: String,
    val timestamp: Long,
)