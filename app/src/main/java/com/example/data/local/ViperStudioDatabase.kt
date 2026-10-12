package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.RecordingDao
import com.example.data.local.dao.SceneDao
import com.example.data.local.dao.StreamProfileDao
import com.example.data.local.entity.RecordingEntity
import com.example.data.local.entity.SceneEntity
import com.example.data.local.entity.StreamProfileEntity

@Database(
    entities = [SceneEntity::class, RecordingEntity::class, StreamProfileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ViperStudioDatabase : RoomDatabase() {
    abstract fun sceneDao(): SceneDao
    abstract fun recordingDao(): RecordingDao
    abstract fun streamProfileDao(): StreamProfileDao

    companion object {
        @Volatile
        private var INSTANCE: ViperStudioDatabase? = null

        fun getDatabase(context: Context): ViperStudioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ViperStudioDatabase::class.java,
                    "viper_studio.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
