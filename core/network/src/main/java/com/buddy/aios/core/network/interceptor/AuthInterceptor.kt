package com.buddy.aios.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches the Bearer auth token to every API request.
 * Token is retrieved from [TokenProvider] — decoupled from token storage implementation.
 */
class AuthInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.getAccessToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

/** Abstraction over token retrieval — implemented by :core:security. */
interface TokenProvider {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
}
