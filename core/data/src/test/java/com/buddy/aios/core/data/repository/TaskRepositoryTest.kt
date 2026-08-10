package com.buddy.aios.core.data.repository

import com.buddy.aios.core.common.coroutines.DispatcherProvider
import com.buddy.aios.core.database.dao.TaskDao
import com.buddy.aios.core.database.entity.TaskEntity
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.TaskPriority
import com.buddy.aios.core.domain.result.Result
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TaskRepositoryTest {

    private val taskDao: TaskDao = mockk(relaxed = true)
    private val dispatchers: DispatcherProvider = mockk()
    private lateinit var repository: TaskRepositoryImpl

    @BeforeEach
    fun setUp() {
        every { dispatchers.io } returns Dispatchers.Unconfined
        every { dispatchers.main } returns Dispatchers.Unconfined
        every { dispatchers.default } returns Dispatchers.Unconfined
        repository = TaskRepositoryImpl(taskDao, dispatchers)
    }

    @Test
    fun `observeActiveTasks maps entity list to domain list`() = runTest {
        val entity = TaskEntity(
            id = "task-1",
            title = "Buy milk",
            description = "Store run",
            isCompleted = false,
            createdAt = 1000L,
            priority = "HIGH",
        )
        every { taskDao.observeActiveTasks() } returns flowOf(listOf(entity))

        val tasks = repository.observeActiveTasks().first()

        assertEquals(1, tasks.size)
        assertEquals("task-1", tasks[0].id)
        assertEquals("Buy milk", tasks[0].title)
        assertEquals(TaskPriority.HIGH, tasks[0].priority)
    }

    @Test
    fun `completeTask delegates to taskDao markCompleted`() = runTest {
        coEvery { taskDao.markCompleted("task-1", true) } returns Unit

        val result = repository.completeTask("task-1", true)

        assertTrue(result is Result.Success)
        coVerify { taskDao.markCompleted("task-1", true) }
    }
}
