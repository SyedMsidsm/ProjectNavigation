package `in`.mite.rain.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import `in`.mite.rain.core.TurnDetector
import kotlin.math.*

/** Foreground relative motion only; yaw is never presented as an indoor compass. */
class MotionSource(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    var status: String = "Sensors idle"
        private set
    private var stepCallback: () -> Unit = {}
    private var turnCallback: (Double) -> Unit = {}
    private var lastPeak = 0L
    private var gravity = 9.81
    private var armed = true
    private var turns = TurnDetector()
    private var useFallback = false

    fun start(onStep: () -> Unit, onTurn: (Double) -> Unit) {
        stop()
        stepCallback = onStep
        turnCallback = onTurn
        val activityAllowed = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val step = if (activityAllowed) manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) else null
        useFallback = step == null
        val source = step ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sourceActive = source != null && manager.registerListener(this, source, SensorManager.SENSOR_DELAY_GAME)
        val rotation = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val rotationActive = rotation != null && manager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_GAME)
        status = (if (!sourceActive) "No step sensor" else if (useFallback) "Accelerometer steps (uncalibrated)" else "Step detector active") +
            (if (rotationActive) " · relative turns" else " · turns unavailable")
    }
    fun stop() {
        manager.unregisterListener(this)
        stepCallback = {}; turnCallback = {}
        turns = TurnDetector()
        lastPeak = 0; gravity = 9.81; armed = true
        status = "Sensors idle"
    }
    override fun onSensorChanged(event: SensorEvent) {
        val ms = event.timestamp / 1_000_000
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> stepCallback()
            Sensor.TYPE_ACCELEROMETER -> if (useFallback) {
                val magnitude = sqrt(event.values.sumOf { it.toDouble() * it })
                gravity = .9 * gravity + .1 * magnitude
                val signal = magnitude - gravity
                if (signal < .4) armed = true
                if (armed && signal > 1.2 && ms - lastPeak > 300) {
                    lastPeak = ms; armed = false; stepCallback()
                }
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                val rotation = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                val orientation = SensorManager.getOrientation(rotation, FloatArray(3))
                val yaw = Math.toDegrees(orientation[0].toDouble())
                turns.update(yaw, ms)?.let(turnCallback)
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
