package com.buddy.aios.core.analytics.activity

import com.buddy.aios.core.common.logging.AppLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class SleepConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

enum class SleepConsistency {
    CONSISTENT,
    FAIRLY_CONSISTENT,
    IRREGULAR,
    INSUFFICIENT_DATA,
}

enum class OvernightActivity {
    LOW,
    MODERATE,
    HIGH,
    UNKNOWN,
}

enum class MorningReadiness {
    READY,
    NORMAL,
    TAKE_IT_EASY,
    UNKNOWN,
}

data class EstimatedSleepEstimate(
    val hasSufficientData: Boolean,
    val sleepStartTimestamp: Long = 0L,
    val sleepEndTimestamp: Long = 0L,
    val durationMinutes: Int = 0,
    val confidence: SleepConfidence = SleepConfidence.LOW,
    val consistency: SleepConsistency = SleepConsistency.INSUFFICIENT_DATA,
    val overnightActivity: OvernightActivity = OvernightActivity.UNKNOWN,
    val morningReadiness: MorningReadiness = MorningReadiness.UNKNOWN,
    val formattedDuration: String = "",
    val formattedWindow: String = "",
    val comparisonToTargetText: String = "",
)

/**
 * Non-medical sleep and morning readiness estimator.
 *
 * Rules:
 * - NEVER claims medical certainty or diagnoses sleep quality.
 * - Uses language: "estimated sleep", "overnight inactivity", "morning readiness".
 * - Returns [hasSufficientData] = false when confidence is LOW (avoids fake numbers).
 * - All computations are 100% LOCAL_ONLY.
 */
@Singleton
class SleepActivityInference @Inject constructor() {

    companion object {
        private const val TAG = "SleepActivityInference"
        private const val MIN_SLEEP_DURATION_MS = 4 * 3600 * 1000L  // 4 hours
        private const val MAX_SLEEP_DURATION_MS = 14 * 3600 * 1000L // 14 hours
    }

    fun inferSleepEstimate(
        snapshot: DeviceActivitySnapshot,
        targetSleepHours: Int = 8,
        historicalDurationsMinutes: List<Int> = emptyList(),
    ): EstimatedSleepEstimate {
        val inactivityMs = snapshot.inactivityDurationMs
        AppLogger.d(TAG, "Inferring sleep estimate: inactivityMs=$inactivityMs (${inactivityMs / 3600000f} hrs)")

        // 1. Validation: check if inactivity window falls within plausible sleep bounds (4-14 hrs)
        if (inactivityMs < MIN_SLEEP_DURATION_MS || inactivityMs > MAX_SLEEP_DURATION_MS) {
            return EstimatedSleepEstimate(
                hasSufficientData = false,
                confidence = SleepConfidence.LOW,
                consistency = SleepConsistency.INSUFFICIENT_DATA,
                overnightActivity = OvernightActivity.UNKNOWN,
                morningReadiness = MorningReadiness.UNKNOWN,
                comparisonToTargetText = "AIOS couldn't confidently estimate your sleep duration last night.",
            )
        }

        val sleepStart = snapshot.lastScreenOffTime
        val sleepEnd = snapshot.lastScreenOnTime
        val durationMins = (inactivityMs / 60000).toInt()
        val durationHours = durationMins / 60f

        // 2. Confidence Assessment based on timing
        val timeFormat = SimpleDateFormat("h:mm a", Locale.ENGLISH)
        val formattedStart = timeFormat.format(Date(sleepStart))
        val formattedEnd = timeFormat.format(Date(sleepEnd))
        val formattedWindow = "$formattedStart → $formattedEnd"

        val hoursInt = durationMins / 60
        val minsInt = durationMins % 60
        val formattedDuration = if (minsInt == 0) "${hoursInt}h" else "${hoursInt}h ${minsInt}m"

        val confidence = when {
            durationHours in 6.0..10.0 && snapshot.isCharging -> SleepConfidence.HIGH
            durationHours in 5.0..11.0 -> SleepConfidence.MEDIUM
            else -> SleepConfidence.LOW
        }

        // 3. Overnight Activity Level
        val overnightActivity = when {
            snapshot.isCharging && confidence == SleepConfidence.HIGH -> OvernightActivity.LOW
            durationHours >= 6.5f -> OvernightActivity.LOW
            else -> OvernightActivity.MODERATE
        }

        // 4. Sleep Consistency from history
        val consistency = when {
            historicalDurationsMinutes.size < 3 -> SleepConsistency.INSUFFICIENT_DATA
            historicalDurationsMinutes.all { Math.abs(it - durationMins) < 45 } -> SleepConsistency.CONSISTENT
            historicalDurationsMinutes.all { Math.abs(it - durationMins) < 90 } -> SleepConsistency.FAIRLY_CONSISTENT
            else -> SleepConsistency.IRREGULAR
        }

        // 5. Non-Medical Morning Readiness
        val morningReadiness = when {
            durationHours >= (targetSleepHours - 0.75f) && overnightActivity == OvernightActivity.LOW -> MorningReadiness.READY
            durationHours >= (targetSleepHours - 1.5f) -> MorningReadiness.NORMAL
            else -> MorningReadiness.TAKE_IT_EASY
        }

        // 6. Comparison to Target Text
        val targetText = when {
            durationHours >= (targetSleepHours - 0.5f) ->
                "Your estimated sleep was $formattedDuration, close to your $targetSleepHours-hour personal target."
            durationHours < (targetSleepHours - 0.5f) ->
                "Your estimated sleep was $formattedDuration, which is below your $targetSleepHours-hour personal target."
            else ->
                "Your estimated sleep was $formattedDuration."
        }

        return EstimatedSleepEstimate(
            hasSufficientData = true,
            sleepStartTimestamp = sleepStart,
            sleepEndTimestamp = sleepEnd,
            durationMinutes = durationMins,
            confidence = confidence,
            consistency = consistency,
            overnightActivity = overnightActivity,
            morningReadiness = morningReadiness,
            formattedDuration = formattedDuration,
            formattedWindow = formattedWindow,
            comparisonToTargetText = targetText,
        )
    }
}
