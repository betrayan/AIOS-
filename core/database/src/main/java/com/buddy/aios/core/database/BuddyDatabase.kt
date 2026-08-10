package com.buddy.aios.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
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
 *
 * MIGRATION POLICY:
 * - Always write explicit migrations. NEVER use fallbackToDestructiveMigration in production.
 * - Test each migration against a pre-migration schema snapshot in :core:database tests.
 *
 * ENCRYPTION:
 * - Message content and memory summaries are encrypted at the MAPPER level, not here.
 * - The DB file itself is NOT SQLCipher-encrypted (adds 20-30% overhead) unless required.
 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        TaskEntity::class,
    ],
    version = 1,
    exportSchema = true,   // Schema exported to json for migration testing
)
abstract class BuddyDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun taskDao(): TaskDao

    companion object {
        const val DATABASE_NAME = "buddy_ai_os.db"
    }
}
