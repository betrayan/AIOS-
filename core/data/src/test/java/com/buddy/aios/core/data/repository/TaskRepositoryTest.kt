package com.buddy.aios.core.data.repository

import com.buddy.aios.core.common.coroutines.DispatcherProvider
import com.buddy.aios.core.database.dao.TaskDao
import com.buddy.aios.core.database.entity.TaskEntity
import com.buddy.aios.core.domain.entity.Task
import com.buddy.aios.core.domain.entity.TaskPriority
import com.buddy.aios.core.domain.repository.IReminderScheduler
import com.buddy.aios.core.domain.result.Result
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    private val reminderScheduler: IReminderScheduler = mockk(relaxed = true)
    private val dispatchers: DispatcherProvider = mockk()
    private lateinit var repository: TaskRepositoryImpl

    @BeforeEach
    fun setUp() {
        every { dispatchers.io } returns Dispatchers.Unconfined
        every { dispatchers.main } returns Dispatchers.Unconfined
        every { dispatchers.default } returns Dispatchers.Unconfined
        repository = TaskRepositoryImpl(taskDao, reminderScheduler, dispatchers)
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
    fun `saveTask inserts into DB and schedules OS reminder if timestamp present`() = runTest {
        val futureTime = System.currentTimeMillis() + 3600_000L
        val task = Task(
            id = "task-100",
            title = "Study Java at 9 PM",
            createdAt = System.currentTimeMillis(),
            dueDate = futureTime,
            reminderTime = futureTime,
        )

        val result = repository.saveTask(task)

        assertTrue(result is Result.Success)
        coVerify { taskDao.insert(any()) }
        verify { reminderScheduler.schedule(task) }
    }

    @Test
    fun `completeTask delegates to taskDao markCompleted and cancels OS alarm`() = runTest {
        val entity = TaskEntity(
            id = "task-1",
            title = "Study Java",
            createdAt = 1000L,
            notificationId = 12345,
        )
        coEvery { taskDao.getById("task-1") } returns entity
        coEvery { taskDao.markCompleted("task-1", true) } returns Unit

        val result = repository.completeTask("task-1", true)

        assertTrue(result is Result.Success)
        coVerify { taskDao.markCompleted("task-1", true) }
        verify { reminderScheduler.cancel("task-1", 12345) }
    }

    @Test
    fun `deleteTask deletes from DB and cancels OS alarm`() = runTest {
        val entity = TaskEntity(
            id = "task-2",
            title = "Drink water",
            createdAt = 1000L,
            notificationId = 67890,
        )
        coEvery { taskDao.getById("task-2") } returns entity
        coEvery { taskDao.delete("task-2") } returns Unit

        val result = repository.deleteTask("task-2")

        assertTrue(result is Result.Success)
        coVerify { taskDao.delete("task-2") }
        verify { reminderScheduler.cancel("task-2", 67890) }
    }
}
