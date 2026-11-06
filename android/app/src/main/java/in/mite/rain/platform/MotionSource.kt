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
import `in`.mite.rain.core.ReferenceTurnPipeline
import `in`.mite.rain.core.ReferenceStepDetector
import `in`.mite.rain.core.WalkEvent
import `in`.mite.rain.core.WalkingTurnWindow
import `in`.mite.rain.core.WalkingCadence
import kotlin.math.*

/** Foreground relative motion only; yaw is never presented as an indoor compass. */
class MotionSource(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    var status: String = "Sensors idle"
        private set
    private var stepCallback: () -> Unit = {}
    private var turnCallback: (Double) -> Unit = {}
    private var headingCallback: (Double) -> Unit = {}
    private val walkingTurns = WalkingTurnWindow()
    private var guiding = false
    private var allowSingleHeading = false
    private var quietSteps = false
    private var handlingUntil = 0L
    private var headingTimestamp = 0L
    private var turns = ReferenceTurnPipeline()
    private var peaks = ReferenceStepDetector()
    private var acceleration: Double? = null
    private var verticalAcceleration = 0.0
    private var horizontalAcceleration = 0.0
    private var usingGravitySensor = false
    private var startNs = 0L
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var gravityVector = doubleArrayOf(0.0, 0.0, 9.81)
    private var gravityReady = false
    private var cadence = WalkingCadence()
    private var useFallback = false
    private var lastHardwareMs = 0L
    private var active = false
    // Retain the last five minutes independently of Android's rotating log buffer.
    // Buffered in memory while walking; written only when motion stops.
    private val diagnosticRows = java.util.ArrayDeque<String>()
    private fun diagnostic(row: String) {
        if (!`in`.mite.rain.BuildConfig.DEBUG) return
        if (diagnosticRows.size >= 32000) diagnosticRows.removeFirst()
        diagnosticRows.addLast(row)
    }
    private val sourcePreferences = appContext.getSharedPreferences("rain_motion", Context.MODE_PRIVATE)
    var preferAccelerometer: Boolean
        get() = sourcePreferences.getBoolean("accelerometer", false)
        set(value) { sourcePreferences.edit().putBoolean("accelerometer", value).apply() }

    fun start(onStep: () -> Unit, onTurn: (Double) -> Unit, onHeadingDelta: (Double) -> Unit = {}, recordingTrail: Boolean = false, navigating: Boolean = false) {
        stepCallback = onStep
        turnCallback = onTurn
        headingCallback = onHeadingDelta
        if (active) return
        guiding = navigating
        walkingTurns.reset()
        allowSingleHeading = recordingTrail
        quietSteps = recordingTrail || navigating
        peaks = ReferenceStepDetector(detectQuietSteps = quietSteps)
        turns = ReferenceTurnPipeline(confirmationSteps = if (recordingTrail || navigating) 1 else 2)
        handlingUntil = 0L
        startNs = android.os.SystemClock.elapsedRealtimeNanos()
        lastHardwareMs = startNs / 1_000_000
        diagnostic("start,${startNs / 1_000_000}")
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val rotation = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        // Match the reference's native adapter: keep raw acceleration and gyro running
        // at 50 Hz, zero batching, explicit main handler, including in hardware-step mode.
        val accelerationActive = register(accelerometer)
        val rotationActive = register(rotation)
        usingGravitySensor = register(manager.getDefaultSensor(Sensor.TYPE_GRAVITY))
        val activityAllowed = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val step = if (activityAllowed && !preferAccelerometer) manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) else null
        val hardwareActive = register(step)
        useFallback = !hardwareActive
        val sourceActive = hardwareActive || (accelerationActive && rotationActive)
        active = sourceActive
        status = (if (!sourceActive) "No step sensor" else if (useFallback) "Motion steps · reference detector" else "Step detector active") +
            (if (rotationActive) " · gravity-based turns" else " · turns unavailable")
    }
    private fun register(sensor: Sensor?): Boolean = sensor != null && runCatching {
        manager.registerListener(this, sensor, 20_000, 0, mainHandler)
    }.getOrDefault(false)
    fun stop() {
        manager.unregisterListener(this)
        if (active && `in`.mite.rain.BuildConfig.DEBUG) {
            diagnostic("stop,${android.os.SystemClock.elapsedRealtime()}")
            runCatching { java.io.File(appContext.filesDir, "last-motion-diagnostic.csv")
                .writeText(diagnosticRows.joinToString("\n")) }
        }
        active = false
        stepCallback = {}; turnCallback = {}; headingCallback = {}; headingTimestamp = 0L
        turns = ReferenceTurnPipeline(); peaks = ReferenceStepDetector()
        gravityReady = false; acceleration = null
        cadence = WalkingCadence()
        status = "Sensors idle"
    }
    fun flushPendingSteps() { dispatch(turns.reset()) }
    override fun onSensorChanged(event: SensorEvent) {
        if (!active || event.timestamp < startNs) return
        val ms = event.timestamp / 1_000_000
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> if (!useFallback && ms >= handlingUntil) {
                if(ms <= lastHardwareMs) return
                lastHardwareMs = ms
                android.util.Log.d("RainMotion", "hardware step at $ms")
                acceptStep(ms)
            }
            Sensor.TYPE_GRAVITY -> {
                val vector = DoubleArray(3) { event.values[it].toDouble() }
                if (vector.all { it.isFinite() } && vector.any { it != 0.0 }) {
                    gravityVector = vector; gravityReady = true
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val vector = DoubleArray(3) { event.values[it].toDouble() }
                if (!vector.all { it.isFinite() }) return
                acceleration = sqrt(vector.sumOf { it * it })
                if (gravityReady) {
                    val g = sqrt(gravityVector.sumOf { it*it })
                    if(g > 0) {
                        val projected = (0..2).sumOf { vector[it]*gravityVector[it] } / g
                        verticalAcceleration = projected-g
                        horizontalAcceleration = sqrt((vector.sumOf { it*it }-projected*projected).coerceAtLeast(0.0))
                    }
                }
                if (!usingGravitySensor) {
                    gravityVector = if (!gravityReady) vector else DoubleArray(3) { .8 * gravityVector[it] + .2 * vector[it] }
                    gravityReady = true
                }
                diagnostic("accel,$ms,$acceleration,$verticalAcceleration,$horizontalAcceleration")
                // Each acceleration sample keeps its own timestamp. Gyroscope delivery
                // must not duplicate or skip the peaks used for walking distance.
                if (gravityReady && ms >= handlingUntil) {
                    peaks.sample(ms, acceleration!!)?.let { peak ->
                        val steps = cadence.peak(peak)
                        diagnostic("peak,$peak,${steps.size}")
                        if (!useFallback) {
                            // Switch once per session only after sustained software cadence
                            // and hardware silence. Never replay already credited hardware steps.
                            if (steps.isEmpty() || peak-lastHardwareMs < 4500) return@let
                            useFallback=true
                            status="Motion steps · hardware detector silent"
                            diagnostic("fallback,$peak")
                        }
                        if (allowSingleHeading && steps.isEmpty()) dispatch(turns.confirmRecordingHeading(peak))
                        steps.forEach(::acceptStep)
                    }
                }
            }
            Sensor.TYPE_GYROSCOPE -> if (gravityReady && acceleration != null) {
                val magnitude = sqrt(gravityVector.sumOf { it * it })
                if (magnitude > 0) {
                    val rate = -(0..2).sumOf { event.values[it] * gravityVector[it] } / magnitude
                    val speedSquared = (0..2).sumOf { event.values[it].toDouble().let { w -> w*w } }
                    val tiltRate = Math.toDegrees(sqrt((speedSquared - rate*rate).coerceAtLeast(0.0)))
                    if (tiltRate > 210.0) {
                        // Rapid pitch/roll commonly occurs when picking up or stowing the phone.
                        // A normal upright change of walking direction rotates about gravity.
                        if (ms >= handlingUntil) {
                            dispatch(turns.reset())
                            peaks = ReferenceStepDetector(detectQuietSteps = quietSteps)
                            cadence = WalkingCadence()
                        }
                        handlingUntil = ms + 350
                    }
                    if (ms < handlingUntil) { headingTimestamp = event.timestamp; return }
                    val degrees = Math.toDegrees(rate)
                    val elapsed = event.timestamp - headingTimestamp
                    headingTimestamp = event.timestamp
                    // Display-only yaw: no turn threshold and no synthetic walking events.
                    if (elapsed in 1..200_000_000 && degrees.isFinite())
                        headingCallback(degrees * elapsed / 1_000_000_000.0)
                    diagnostic("motion,$ms,$acceleration,$degrees,$verticalAcceleration,$horizontalAcceleration,$tiltRate")
                    if (guiding) walkingTurns.rotate(degrees,ms)
                    if (!guiding) dispatch(turns.rotation(degrees, ms))
                }
            }
        }
    }
    private fun acceptStep(time: Long) {
        diagnostic("accepted_step,$time")
        if (guiding) { walkingTurns.atStep(time)?.let(turnCallback); stepCallback() }
        else dispatch(turns.step(time))
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    private fun dispatch(events: List<WalkEvent>) { events.forEach { when (it) { WalkEvent.Step -> stepCallback(); is WalkEvent.Turn -> turnCallback(it.degrees) } } }
}
