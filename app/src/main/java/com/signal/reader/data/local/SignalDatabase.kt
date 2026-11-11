package com.signal.reader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.signal.reader.data.local.dao.ArticleDao
import com.signal.reader.data.local.dao.HighlightDao
import com.signal.reader.data.local.dao.SourceDao
import com.signal.reader.data.local.entity.ArticleEntity
import com.signal.reader.data.local.entity.HighlightEntity
import com.signal.reader.data.local.entity.SourceEntity

@Database(
    entities = [
        SourceEntity::class,
        ArticleEntity::class,
        HighlightEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SignalDatabase : RoomDatabase() {

    abstract fun sourceDao(): SourceDao
    abstract fun articleDao(): ArticleDao
    abstract fun highlightDao(): HighlightDao

    companion object {
        @Volatile
        private var INSTANCE: SignalDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sources ADD COLUMN notificationsEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sources ADD COLUMN etag TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sources ADD COLUMN lastModified TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sources ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `highlights` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `articleId` INTEGER NOT NULL,
                        `articleTitle` TEXT NOT NULL,
                        `sourceName` TEXT NOT NULL,
                        `quoteText` TEXT NOT NULL,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_articleId` ON `highlights` (`articleId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_createdAt` ON `highlights` (`createdAt`)")
            }
        }

        fun getInstance(context: Context): SignalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SignalDatabase::class.java,
                    "signal_reader.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
