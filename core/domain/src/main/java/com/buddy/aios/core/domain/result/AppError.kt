package com.buddy.aios.core.domain.result

import java.io.IOException

/**
 * Sealed hierarchy of all application errors.
 * Typed errors allow the UI layer to show precise, actionable messages.
 */
sealed class AppError {

    /** Network/HTTP errors from Retrofit/OkHttp */
    data class NetworkError(
        val code: Int,
        val message: String,
    ) : AppError()

    /** Device has no internet connectivity */
    object OfflineError : AppError()

    /** AI engine errors */
    data class AIError(val type: AIErrorType) : AppError()

    /** Firebase Auth / JWT errors */
    data class AuthError(val reason: AuthFailReason) : AppError()

    /** Room / DataStore persistence errors */
    data class StorageError(val cause: Throwable) : AppError()

    /** Security / encryption errors */
    data class SecurityError(val message: String) : AppError()

    /** Anything not covered above */
    data class UnknownError(val cause: Throwable? = null) : AppError()

    companion object {
        fun fromException(e: Exception): AppError = when (e) {
            is IOException -> OfflineError
            else           -> UnknownError(e)
        }
    }
}

enum class AIErrorType {
    MODEL_NOT_LOADED,
    MODEL_NOT_FOUND,
    AUTHENTICATION_ERROR,
    CONTEXT_OVERFLOW,
    INFERENCE_FAILED,
    CLOUD_QUOTA_EXCEEDED,
    CONTENT_FILTERED,
    TIMEOUT,
}

enum class AuthFailReason {
    INVALID_CREDENTIALS,
    TOKEN_EXPIRED,
    SESSION_REVOKED,
    BIOMETRIC_FAILED,
    ACCOUNT_DISABLED,
}
