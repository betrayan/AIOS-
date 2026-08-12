package com.buddy.aios.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.buddy.aios.core.database.dao.ConversationDao
import com.buddy.aios.core.database.dao.MemoryDao
import com.buddy.aios.core.database.dao.MessageDao
import com.buddy.aios.core.database.dao.TaskDao
import com.buddy.aios.core.database.entity.ConversationEntity
import com.buddy.aios.core.database.entity.MemoryEntity
import com.buddy.aios.core.database.entity.MessageEntity
import com.buddy.aios.core.database.entity.TaskEntity

/**
 * The single Room database for Buddy AI OS.
 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        TaskEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class BuddyDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun taskDao(): TaskDao

    companion object {
        const val DATABASE_NAME = "buddy_ai_os.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN is_reminder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN notification_id INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN timezone TEXT NOT NULL DEFAULT 'UTC'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrence_rule TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_status ON tasks(status)")
            }
        }
    }
}
