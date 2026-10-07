package `in`.mite.rain.platform

import android.content.Context
import `in`.mite.rain.core.CalibrationResult
import `in`.mite.rain.core.PromptMode

class UserSettings(context: Context) {
    private val preferences = context.getSharedPreferences("rain_settings", Context.MODE_PRIVATE)
    val stepLength get() = preferences.getFloat("step_length", .7f).toDouble().takeIf { it.isFinite() && it in .3..1.2 } ?: .7
    val lambda get() = preferences.getFloat("lambda", 40f).toDouble().takeIf { it.isFinite() && it in 0.0..160.0 } ?: 40.0
    val voice get() = preferences.getBoolean("voice", false)
    val haptics get() = preferences.getBoolean("haptics", false)
    val promptMode get() = runCatching { PromptMode.valueOf(preferences.getString("prompt_mode", "COST_SENSITIVE")!!) }.getOrDefault(PromptMode.COST_SENSITIVE)
    val calibrationSummary get() = preferences.getString("calibration_summary", "Not calibrated on a measured walk.")!!
    fun save(lambda: Double, step: Double, voice: Boolean, haptics: Boolean, mode: PromptMode) {
        require(lambda.isFinite() && lambda in 0.0..160.0 && step.isFinite() && step in .3..1.2)
        val editedStep = kotlin.math.abs(step - stepLength) > .0001
        preferences.edit().putFloat("lambda", lambda.toFloat()).putFloat("step_length", step.toFloat())
            .putBoolean("voice", voice).putBoolean("haptics", haptics).putString("prompt_mode", mode.name).apply {
                if (editedStep) putString("calibration_summary", "Step length set manually; repeat a measured walk to verify.")
            }.apply()
    }
    fun saveCalibration(result: CalibrationResult, sensor: String) {
        preferences.edit().putFloat("step_length", result.stepLengthM.toFloat())
            .putString("calibration_summary", "${result.distanceM} m / ${result.steps} detected steps. $sensor")
            .putLong("calibrated_at_ms", System.currentTimeMillis()).apply()
    }
}
