package com.tutu.meowhub.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tutu.meowhub.core.model.ChatMessageEntity
import com.tutu.meowhub.core.model.ChatSessionEntity

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
abstract class MeowHubDatabase : RoomDatabase() {

    abstract fun skillDao(): SkillDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: MeowHubDatabase? = null

        fun getInstance(context: Context): MeowHubDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MeowHubDatabase::class.java,
                    "meowhub.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
