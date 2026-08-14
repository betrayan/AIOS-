package com.buddy.aios.core.ai.summary

import com.buddy.aios.core.ai.tool.ToolResult

/**
 * Single Source of Truth response wrapper.
 * Ensures the screen display and voice summary are derived from the exact same inference result,
 * preventing any factual contradictions between voice and screen.
 */
data class AIResponse(
    val fullResponse: String,
    val displayContent: String,
    val voiceSummary: String,
    val intent: String = "GENERAL",
    val confidence: Float = 1.0f,
    val toolExecuted: String? = null,
    val toolResult: ToolResult? = null,
)
