package com.auak.agent.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.auak.agent.core.model.ChatMessageEntity
import com.auak.agent.core.model.ChatSessionEntity

@Database(
    entities = [
        LocalSkillEntity::class,
        SkillTagEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class auak agentyDatabase : RoomDatabase() {

    abstract fun skillDao(): SkillDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: auak agentyDatabase? = null

        fun getInstance(context: Context): auak agentyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    auak agentyDatabase::class.java,
                    "auak agenty.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
