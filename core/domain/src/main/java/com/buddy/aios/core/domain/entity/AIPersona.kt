package com.buddy.aios.core.domain.entity

/**
 * Domain entity representing an AI persona configuration.
 * Personas define the system prompt, tone, and capabilities of the AI companion.
 */
data class AIPersona(
    val id: String,
    val name: String,
    val displayName: String,
    val systemPrompt: String,
    val tone: PersonaTone,
    val capabilities: Set<AICapability>,
    val avatarKey: String,               // key used to resolve avatar asset
    val isDefault: Boolean = false,
)

enum class PersonaTone {
    FRIENDLY,
    PROFESSIONAL,
    PLAYFUL,
    CALM,
    DIRECT,
}

enum class AICapability {
    MEMORY,              // Can store and recall long-term memories
    CODE_ASSISTANCE,     // Can help write/debug code
    WEB_SEARCH,          // Can trigger web search (future)
    VOICE,               // Can respond via TTS
    IMAGE_UNDERSTANDING, // Can process images (multimodal)
}
