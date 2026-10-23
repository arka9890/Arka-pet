package com.example.pet.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ChatMessage::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}