package com.buddy.aios.core.common.coroutines

import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [DispatcherProvider] backed by real Kotlin [Dispatchers].
 */
@Singleton
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val main       = Dispatchers.Main
    override val io         = Dispatchers.IO
    override val default    = Dispatchers.Default
    override val unconfined = Dispatchers.Unconfined
}
