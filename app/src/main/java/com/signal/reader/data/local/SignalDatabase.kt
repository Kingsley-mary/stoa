package com.signal.reader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.signal.reader.data.local.dao.ArticleDao
import com.signal.reader.data.local.dao.SourceDao
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.data.local.entity.SourceEntity

@Database(
    entities = [
        SourceEntity::class,
        ArticleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SignalDatabase : RoomDatabase() {

    abstract fun sourceDao(): SourceDao
    abstract fun articleDao(): ArticleDao

    companion object {
        @Volatile
        private var INSTANCE: SignalDatabase? = null

        fun getInstance(context: Context): SignalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SignalDatabase::class.java,
                    "signal_reader.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
