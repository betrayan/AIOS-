package com.buddy.aios.core.ai.voice

import com.buddy.aios.core.domain.entity.BuddyMode
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VoiceCommand {
    data object StopListening : VoiceCommand
    data class SetVoiceMode(val enabled: Boolean) : VoiceCommand
    data class SetBuddyModeCommand(val mode: BuddyMode) : VoiceCommand
    data object MorningWishCommand : VoiceCommand
    data object AcknowledgeMorningWishCommand : VoiceCommand

    sealed interface RecordingCommand : VoiceCommand {
        data object Start : RecordingCommand
        data object Stop : RecordingCommand
        data object Save : RecordingCommand
        data object Delete : RecordingCommand
        data object Enable : RecordingCommand
    }
    sealed interface SummaryCommand : VoiceCommand {
        data object ShortSummary : SummaryCommand
        data object ExplainSimply : SummaryCommand
        data object BeginnerExplanation : SummaryCommand
        data object DetailedExplanation : SummaryCommand
    }
}

/**
 * Recognizes and parses spoken voice commands for system settings, continuous mode,
 * BuddyMode, voice recording, and summary depth.
 */
@Singleton
class VoiceCommandParser @Inject constructor() {

    fun parse(userSpeech: String): VoiceCommand? {
        val lower = userSpeech.lowercase().trim()

        return when {
            // 1. Voice Stop Commands
            lower.contains("stop listening") || lower.contains("stop voice mode") ||
            lower.contains("turn off microphone") || lower.contains("disable microphone") ->
                VoiceCommand.StopListening

            // 2. Continuous Voice Mode Commands
            lower.contains("turn on voice mode") || lower.contains("enable continuous voice") ||
            lower.contains("start continuous voice") ->
                VoiceCommand.SetVoiceMode(enabled = true)

            lower.contains("turn off voice mode") || lower.contains("disable continuous voice") ->
                VoiceCommand.SetVoiceMode(enabled = false)

            // 3. Buddy Mode Voice Commands
            lower.contains("switch to active mode") || lower.contains("set buddy mode to active") ||
            lower.contains("enable active mode") ->
                VoiceCommand.SetBuddyModeCommand(BuddyMode.ACTIVE)

            lower.contains("enable quiet mode") || lower.contains("switch to quiet mode") ||
            lower.contains("set buddy mode to quiet") ->
                VoiceCommand.SetBuddyModeCommand(BuddyMode.QUIET)

            lower.contains("go silent") || lower.contains("enable silent mode") ||
            lower.contains("switch to silent mode") || lower.contains("set buddy mode to silent") ->
                VoiceCommand.SetBuddyModeCommand(BuddyMode.SILENT)

            lower.contains("turn buddy mode off") || lower.contains("turn off buddy mode") ->
                VoiceCommand.SetBuddyModeCommand(BuddyMode.OFF)

            // 4. Voice Recording Commands
            lower.contains("start recording") || lower.contains("record this") || lower.contains("start voice recording") ->
                VoiceCommand.RecordingCommand.Start

            lower.contains("stop recording") || lower.contains("stop voice recording") ->
                VoiceCommand.RecordingCommand.Stop

            lower.contains("save this recording") || lower.contains("save recording") ->
                VoiceCommand.RecordingCommand.Save

            lower.contains("delete this recording") || lower.contains("delete recording") || lower.contains("discard recording") ->
                VoiceCommand.RecordingCommand.Delete

            lower.contains("enable voice recording") || lower.contains("turn on voice recording") ->
                VoiceCommand.RecordingCommand.Enable

            // 5. Summary / Explanation Commands
            lower.contains("summarize that") || lower.contains("give me the short version") || lower.contains("tell me the important part") ->
                VoiceCommand.SummaryCommand.ShortSummary

            lower.contains("explain it simply") || lower.contains("simplify that") ->
                VoiceCommand.SummaryCommand.ExplainSimply

            lower.contains("explain this like i'm a beginner") || lower.contains("explain like i'm 5") || lower.contains("for a beginner") ->
                VoiceCommand.SummaryCommand.BeginnerExplanation

            lower.contains("explain in detail") || lower.contains("explain it in detail") || lower.contains("detailed explanation") ->
                VoiceCommand.SummaryCommand.DetailedExplanation

            // 6. Morning Wish Commands
            lower.contains("morning wish") || lower.contains("aios, morning wish") ||
            lower.contains("give me my morning wish") || lower.contains("good morning aios") ||
            lower.contains("start morning briefing") ->
                VoiceCommand.MorningWishCommand

            // 7. Natural Morning Wish Acknowledgements
            lower == "good morning" || lower == "morning" || lower == "thank you" ||
            lower == "okay" || lower == "i'm awake" || lower == "yes" ||
            lower == "got it" || lower == "stop" || lower == "that's enough" ->
                VoiceCommand.AcknowledgeMorningWishCommand

            else -> null
        }
    }
}
