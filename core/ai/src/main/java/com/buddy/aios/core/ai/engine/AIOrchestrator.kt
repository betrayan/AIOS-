package com.buddy.aios.core.ai.engine

import com.buddy.aios.core.ai.context.ContextManager
import com.buddy.aios.core.ai.memory.MemoryExtractor
import com.buddy.aios.core.ai.policy.AIPolicy
import com.buddy.aios.core.ai.policy.AIRoutingContext
import com.buddy.aios.core.ai.provider.AIProvider
import com.buddy.aios.core.ai.tool.IntentParser
import com.buddy.aios.core.ai.tool.ToolExecutor
import com.buddy.aios.core.ai.tool.ToolResult
import com.buddy.aios.core.common.coroutines.DispatcherProvider
import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.domain.entity.BuddyMode
import com.buddy.aios.core.domain.entity.PrivacyLevel
import com.buddy.aios.core.domain.repository.IBuddyModeRepository
import com.buddy.aios.core.domain.repository.IMemoryRepository
import com.buddy.aios.core.domain.repository.ITaskRepository
import com.buddy.aios.core.domain.repository.IUserRepository
import com.buddy.aios.core.domain.result.AIErrorType
import com.buddy.aios.core.domain.result.AppError
import com.buddy.aios.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.buddy.aios.core.ai.summary.SummaryValidator
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AIOrchestrator @Inject constructor(
    private val policy: AIPolicy,
    private val contextManager: ContextManager,
    private val memoryExtractor: MemoryExtractor,
    private val toolExecutor: ToolExecutor,
    private val summaryValidator: SummaryValidator,
    @Named("local") private val localProvider: AIProvider,
    @Named("cloud") private val cloudProvider: AIProvider,
    private val buddyModeRepository: IBuddyModeRepository,
    private val memoryRepository: IMemoryRepository,
    private val taskRepository: ITaskRepository,
    private val userRepository: IUserRepository,
    private val dispatchers: DispatcherProvider,
    private val intelligenceEngine: com.buddy.aios.core.ai.brain.PersonalIntelligenceEngine,
) : AIEngine {

    companion object {
        private const val TAG = "AIOrchestrator"
    }

    override fun complete(prompt: AIPrompt): Flow<Result<AIChunk>> = flow {
        val collector = this
        val buddyMode = withContext(dispatchers.io) {
            buddyModeRepository.getBuddyMode()
        }

        if (!policy.canExecuteTextAI(buddyMode)) {
            AppLogger.d(TAG, "AI blocked: BuddyMode=$buddyMode")
            collector.emit(Result.Error(AppError.AIError(AIErrorType.INFERENCE_FAILED)))
            return@flow
        }

        val userProfile = withContext(dispatchers.io) {
            (userRepository.getUserProfile() as? Result.Success)?.value
        }
        val privacyLevel = userProfile?.privacyLevel ?: PrivacyLevel.LOCAL_ONLY

        val relevantMemories = withContext(dispatchers.io) {
            val searchResult = memoryRepository.searchMemories(prompt.userMessage.take(100))
            (searchResult as? Result.Success)?.value
                ?.sortedByDescending { it.importance }
                ?.take(6)
                ?: emptyList()
        }

        val activeTasks = withContext(dispatchers.io) {
            val taskResult = taskRepository.getUpcomingTasks()
            (taskResult as? Result.Success)?.value?.take(5) ?: emptyList()
        }

        // 1. Evaluate Stage 8 Personal Intelligence Decision
        val snapshot = intelligenceEngine.buildSnapshot(
            userProfile = userProfile,
            buddyMode = buddyMode,
            activeTasks = activeTasks,
            memories = relevantMemories,
        )

        val decision = intelligenceEngine.processQuery(prompt.userMessage, snapshot)
        if (decision.primaryTextResponse.isNotBlank() && decision.actionType != com.buddy.aios.core.ai.brain.ActionType.EXECUTE_TOOL) {
            val voiceSummary = summaryValidator.validateAndSanitize(decision.primaryTextResponse, decision.voiceTextResponse ?: "")
            val aiResponse = com.buddy.aios.core.ai.summary.AIResponse(
                fullResponse = decision.primaryTextResponse,
                displayContent = decision.primaryTextResponse,
                voiceSummary = voiceSummary,
                intent = "PERSONAL_BRAIN",
            )
            collector.emit(Result.Success(AIChunk(text = aiResponse.displayContent, isComplete = true)))
            return@flow
        }

        val enrichedContext = contextManager.buildEnrichedContext(
            allMessages = prompt.conversationHistory,
            userProfile = userProfile,
            relevantMemories = relevantMemories,
            activeTasks = activeTasks,
            buddyMode = buddyMode,
            userMessage = prompt.userMessage,
        )

        val enrichedPrompt = prompt.copy(
            systemInstruction = enrichedContext.systemInstruction,
            conversationHistory = enrichedContext.conversationHistory,
        )

        val routingContext = AIRoutingContext(
            buddyMode = buddyMode,
            privacyLevel = privacyLevel,
            isNetworkAvailable = isNetworkAvailable(),
            isOnDeviceModelLoaded = localProvider.isAvailable(),
        )

        val useCloud = policy.shouldUseCloud(routingContext)
        val (primaryProvider, fallbackProvider) = if (useCloud) {
            cloudProvider to localProvider
        } else {
            localProvider to cloudProvider
        }

        AppLogger.d(TAG, "Provider selected: ${primaryProvider.name} (cloud=$useCloud, privacy=$privacyLevel)")

        var fullResponseText = ""

        val providerAvailable = primaryProvider.isAvailable()
        if (!providerAvailable) {
            AppLogger.w(TAG, "${primaryProvider.name} unavailable — checking fallback")
        }

        val activeProvider = when {
            providerAvailable -> primaryProvider
            !useCloud && privacyLevel != PrivacyLevel.LOCAL_ONLY && fallbackProvider.isAvailable() -> {
                AppLogger.w(TAG, "Local unavailable. Falling back to ${fallbackProvider.name}")
                fallbackProvider
            }
            useCloud && fallbackProvider.isAvailable() -> {
                AppLogger.w(TAG, "Cloud unavailable. Falling back to ${fallbackProvider.name}")
                fallbackProvider
            }
            else -> null
        }

        if (activeProvider == null) {
            val noProviderMsg = buildNoProviderMessage(privacyLevel, buddyMode)
            collector.emit(Result.Success(AIChunk(text = noProviderMsg, isComplete = true)))
            return@flow
        }

        try {
            activeProvider.generate(enrichedPrompt).collect { chunkResult ->
                when (chunkResult) {
                    is Result.Success -> {
                        val chunk = chunkResult.value
                        fullResponseText += chunk.text

                        if (chunk.isComplete) {
                            val parseResult = IntentParser.parse(fullResponseText)
                            val finalText = parseResult.cleanedText
                            val voiceSummary = summaryValidator.validateAndSanitize(finalText, parseResult.cleanedText)

                            val tool = parseResult.tool
                            var executedToolResult: ToolResult? = null
                            if (tool != null) {
                                AppLogger.d(TAG, "Executing tool: $tool")
                                executedToolResult = withContext(dispatchers.io) {
                                    toolExecutor.execute(tool)
                                }
                                if (executedToolResult is ToolResult.Failure) {
                                    AppLogger.w(TAG, "Tool execution failed: ${(executedToolResult as ToolResult.Failure).reason}")
                                }
                            }

                            collector.emit(Result.Success(AIChunk(
                                text = finalText,
                                isComplete = true,
                                totalTokensUsed = chunk.totalTokensUsed,
                                toolExecuted = tool,
                                toolResult = executedToolResult,
                            )))

                            withContext(dispatchers.io) {
                                memoryExtractor.maybeExtract(
                                    userMessage = prompt.userMessage,
                                    buddyMode = buddyMode,
                                    privacyLevel = privacyLevel,
                                    conversationId = prompt.conversationId,
                                )
                            }
                        } else {
                            collector.emit(Result.Success(chunk))
                        }
                    }
                    is Result.Error -> {
                        AppLogger.e(TAG, "Provider error: ${chunkResult.error}")
                        collector.emit(Result.Error(chunkResult.error))
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Unhandled exception in provider generation", e)
            collector.emit(Result.Error(AppError.UnknownError(e)))
        }
    }.flowOn(dispatchers.default)

    override suspend fun summarize(messages: List<com.buddy.aios.core.domain.entity.Message>): Result<String> {
        return Result.Success("Conversation with ${messages.size} messages.")
    }

    override suspend fun isAvailable(): Boolean {
        return localProvider.isAvailable() || cloudProvider.isAvailable()
    }

    override suspend fun release() { }

    private fun isNetworkAvailable(): Boolean {
        // OkHttp handles actual network reachability and throws IOException on offline state.
        // We avoid process ping calls because Android app sandbox blocks ICMP raw sockets.
        return true
    }

    private fun buildNoProviderMessage(privacyLevel: PrivacyLevel, buddyMode: BuddyMode): String {
        return when {
            privacyLevel == PrivacyLevel.LOCAL_ONLY ->
                "I'm currently set to local-only mode, but the on-device AI model isn't loaded yet. " +
                "You can enable Cloud AI in Settings > Privacy to use Gemini, or wait for the local model to become available."
            buddyMode == BuddyMode.SILENT ->
                "I can't process that right now — I'm in silent mode and the local AI isn't available."
            else ->
                "I'm having trouble thinking right now — both local and cloud AI are unavailable. " +
                "Please check your connection or try again shortly."
        }
    }
}
