package com.buddy.aios.core.database.di

import android.content.Context
import androidx.room.Room
import com.buddy.aios.core.database.BuddyDatabase
import com.buddy.aios.core.database.dao.ConversationDao
import com.buddy.aios.core.database.dao.MemoryDao
import com.buddy.aios.core.database.dao.MessageDao
import com.buddy.aios.core.database.dao.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideBuddyDatabase(
        @ApplicationContext context: Context,
    ): BuddyDatabase = Room.databaseBuilder(
        context,
        BuddyDatabase::class.java,
        BuddyDatabase.DATABASE_NAME,
    )
        .fallbackToDestructiveMigrationFrom() // Only for dev — remove before production release
        .build()

    @Provides
    fun provideConversationDao(db: BuddyDatabase): ConversationDao = db.conversationDao()

    @Provides
    fun provideMessageDao(db: BuddyDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideMemoryDao(db: BuddyDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun provideTaskDao(db: BuddyDatabase): TaskDao = db.taskDao()
}
