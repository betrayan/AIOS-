package com.buddy.aios.core.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Abstraction over Kotlin coroutine dispatchers.
 * Injected into every class that uses coroutines so tests can substitute
 * [UnconfinedTestDispatcher] without touching production code.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}
