package com.buddy.aios.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/**
 * Throws [OfflineException] before the request is dispatched if no network is available.
 * This provides a fast-fail for offline scenarios without waiting for a connection timeout.
 */
class ConnectivityInterceptor @Inject constructor(
    private val connectivityChecker: ConnectivityChecker,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!connectivityChecker.isConnected()) {
            throw OfflineException("No internet connection")
        }
        return chain.proceed(chain.request())
    }
}

class OfflineException(message: String) : IOException(message)

/** Abstraction over network connectivity checks — injectable for testing. */
interface ConnectivityChecker {
    fun isConnected(): Boolean
}
