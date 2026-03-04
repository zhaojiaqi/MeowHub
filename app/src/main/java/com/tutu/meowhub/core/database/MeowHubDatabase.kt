package com.tutu.meowhub.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LocalSkillEntity::class, SkillTagEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MeowHubDatabase : RoomDatabase() {

    abstract fun skillDao(): SkillDao

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
