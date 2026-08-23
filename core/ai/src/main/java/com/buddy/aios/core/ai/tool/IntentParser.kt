package com.buddy.aios.core.ai.tool

import com.buddy.aios.core.common.logging.AppLogger
import com.buddy.aios.core.common.time.NaturalLanguageTimeParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Parses [BuddyTool] directives from raw AI response text.
 *
 * The AI is instructed via system prompt to append tool calls in the format:
 * [BUDDY_ACTION:{"tool":"TASK","action":"CREATE","title":"...","dueTimestamp":null}]
 *
 * This parser:
 * 1. Detects whether a directive is present.
 * 2. Extracts and parses the JSON payload.
 * 3. Returns a validated [BuddyTool] or null (no directive / invalid format).
 * 4. Returns the cleaned response text (directive stripped from user-visible output).
 */
object IntentParser {

    private const val TAG = "IntentParser"
    private val DIRECTIVE_REGEX = Regex("""\[BUDDY_ACTION:(\{.*?\})\]""", RegexOption.DOT_MATCHES_ALL)
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    data class ParseResult(
        val cleanedText: String,
        val tool: BuddyTool?,
    )

    /**
     * Parses [rawText] for a BUDDY_ACTION directive.
     * Always returns [ParseResult] — never throws.
     */
    fun parse(rawText: String): ParseResult {
        val match = DIRECTIVE_REGEX.find(rawText) ?: return ParseResult(
            cleanedText = rawText.trim(),
            tool = null,
        )

        val jsonStr = match.groupValues[1]
        val cleanedText = rawText.replace(match.value, "").trim()

        val tool = try {
            parseToolJson(jsonStr)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to parse BUDDY_ACTION JSON: $jsonStr", e)
            null
        }

        return ParseResult(cleanedText = cleanedText, tool = tool)
    }

    private fun parseToolJson(jsonStr: String): BuddyTool? {
        val obj = jsonParser.parseToJsonElement(jsonStr) as? JsonObject ?: return null

        val toolType = obj["tool"]?.jsonPrimitive?.content?.uppercase() ?: return null
        val action = obj["action"]?.jsonPrimitive?.content?.uppercase() ?: return null

        return when (toolType) {
            "TASK" -> when (action) {
                "CREATE" -> {
                    val title = obj["title"]?.jsonPrimitive?.content?.trim() ?: ""
                    if (title.isBlank()) return null
                    val desc = obj["description"]?.jsonPrimitive?.content ?: ""
                    val rawDue = obj["dueTimestamp"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0L }
                    val now = System.currentTimeMillis()
                    val due = when {
                        rawDue == null -> NaturalLanguageTimeParser.parse(title, now).timestamp
                        rawDue < 100_000_000_000L -> {
                            val ms = rawDue * 1000L
                            if (ms <= now - 60_000L) {
                                NaturalLanguageTimeParser.parse(title, now).timestamp ?: ms
                            } else {
                                ms
                            }
                        }
                        rawDue <= now - 60_000L -> {
                            NaturalLanguageTimeParser.parse(title, now).timestamp ?: rawDue
                        }
                        else -> rawDue
                    }
                    BuddyTool.CreateTask(
                        title = title,
                        description = desc,
                        dueTimestamp = due,
                    )
                }
                "COMPLETE" -> {
                    val title = obj["title"]?.jsonPrimitive?.content?.trim() ?: ""
                    if (title.isBlank()) return null
                    BuddyTool.CompleteTask(title = title)
                }
                "DELETE" -> {
                    val title = obj["title"]?.jsonPrimitive?.content?.trim() ?: ""
                    if (title.isBlank()) return null
                    BuddyTool.DeleteTask(title = title)
                }
                else -> null
            }
            "MEMORY" -> when (action) {
                "SAVE" -> {
                    val content = obj["content"]?.jsonPrimitive?.content?.trim() ?: ""
                    if (content.isBlank()) return null
                    val importance = obj["importance"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0.7f
                    BuddyTool.SaveMemory(
                        content = content,
                        importance = importance.coerceIn(0.0f, 1.0f),
                    )
                }
                "DELETE" -> {
                    val content = obj["content"]?.jsonPrimitive?.content?.trim() ?: ""
                    if (content.isBlank()) return null
                    BuddyTool.DeleteMemory(content = content)
                }
                else -> null
            }
            "MORNING_WISH" -> when (action) {
                "SET", "CONFIGURE", "CREATE" -> {
                    val hour = obj["hour"]?.jsonPrimitive?.intOrNull ?: 6
                    val minute = obj["minute"]?.jsonPrimitive?.intOrNull ?: 0
                    val enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
                    BuddyTool.ConfigureMorningWish(
                        hour = hour.coerceIn(0, 23),
                        minute = minute.coerceIn(0, 59),
                        isEnabled = enabled
                    )
                }
                else -> null
            }
            else -> null
        }
    }
}
