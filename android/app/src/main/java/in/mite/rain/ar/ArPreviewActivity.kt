package `in`.mite.rain.ar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import `in`.mite.rain.core.NavigationState
import `in`.mite.rain.core.Route
import `in`.mite.rain.core.ArWalkingPath
import `in`.mite.rain.core.ArWalkingState
import `in`.mite.rain.core.signedTurnDegrees
import `in`.mite.rain.platform.GuidanceFeedback
import java.util.Locale

/** ARCore owns both the camera and motion progress while this activity is foreground. */
class ArPreviewActivity : ComponentActivity() {
    private lateinit var surface: GLSurfaceView
    private lateinit var renderer: ArPreviewRenderer
    private lateinit var message: TextView
    private lateinit var align: Button
    private lateinit var cameraSettings: Button
    private lateinit var guidance: TextView
    private lateinit var feedback: GuidanceFeedback
    private var latest: ArWalkingState? = null
    private var lastCue = ""
    private var restoreNeedsFix = false
    private var returning = false
    private var session: Session? = null
    private var installRequested = false
    private var running = false
    private var permissionRequested = false
    private var permissionInFlight = false
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionInFlight = false
        if (granted) startAr() else showStatus("Camera permission is needed. Retry AR to request access, or enable Camera in App settings if Android no longer prompts.", false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        installRequested = savedInstanceState?.getBoolean("install_requested") ?: false
        permissionRequested = savedInstanceState?.getBoolean("permission_requested") ?: false
        restoreNeedsFix = savedInstanceState?.getBoolean("needs_fix") ?: false
        if (restoreNeedsFix) latest = ArWalkingState(savedInstanceState!!.getInt("arc"), savedInstanceState.getDouble("offset"),
            savedInstanceState.getDouble("along"), savedInstanceState.getDouble("remaining"), false, "AR interrupted. Confirm your location on the map.")
        feedback = GuidanceFeedback(this) { }
        feedback.configure(intent.getBooleanExtra("voice", false), intent.getBooleanExtra("haptics", false))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(18, 40, 34))
            setPadding(24, 12, 24, 12)
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                view.setPadding(24 + bars.left, 12 + bars.top, 24 + bars.right, 12 + bars.bottom)
                insets
            }
        }
        fun label(text: String, size: Float) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(Color.WHITE); setPadding(8, 8, 8, 8)
        }
        root.addView(label("AR walking navigation", 24f))
        root.addView(label(if (intent.getBooleanExtra("synthetic", true)) "SYNTHETIC TEST FLOOR"
            else "USER-PROVIDED FLOOR · distances need field verification", 12f))
        guidance = label(intent.getStringExtra("instruction").orEmpty(), 16f)
        root.addView(guidance)
        root.addView(label("${intent.getStringExtra("floor_name").orEmpty()}\nConfirm your starting location. Face the next corridor and aim diagonally down to align once. Distance and turns then update as you walk. Map dimensions and doorways need field verification.", 14f))
        val lengths = intent.getDoubleArrayExtra("lengths")?.toList().orEmpty()
        val bearings = intent.getDoubleArrayExtra("bearings")?.toList().orEmpty()
        val path = runCatching { ArWalkingPath(lengths, bearings, intent.getDoubleExtra("offset", 0.0)) }.getOrNull()
        if (path == null) { finish(); return }
        message = label("Starting camera…", 15f)
        align = Button(this).apply {
            text = "Confirm start and align corridor"; isEnabled = false
            setOnClickListener { renderer.requestAlignment() }
        }
        renderer = ArPreviewRenderer(path, { window.decorView.display?.rotation ?: 0 }, { state ->
            runOnUiThread { if (!isDestroyed && !returning && !restoreNeedsFix) updateProgress(state, lengths, bearings) }
        }) { text, ready ->
            runOnUiThread { if (!isDestroyed) showStatus(text, ready) }
        }
        surface = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2); preserveEGLContextOnPause = true
            setRenderer(renderer)
        }
        val cameraPanel = FrameLayout(this).apply {
            addView(surface, FrameLayout.LayoutParams(-1, -1))
            addView(TextView(this@ArPreviewActivity).apply {
                text = "+"; textSize = 30f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(70, 70, Gravity.CENTER))
        }
        root.addView(cameraPanel, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(message)
        root.addView(align)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        controls.addView(Button(this).apply {
            text = "Retry AR"
            setOnClickListener {
                pauseAr()
                renderer.session = null
                session?.close(); session = null
                startAr(requestPermission = true)
            }
        })
        controls.addView(Button(this).apply { text = "Return to map"; setOnClickListener { finish() } })
        root.addView(controls)
        cameraSettings = Button(this).apply {
            text = "App settings"
            visibility = android.view.View.GONE
            setOnClickListener {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
        }
        root.addView(cameraSettings)
        root.addView(label("Look up regularly. No obstacle detection. Arrival is estimated; confirm the room sign. No camera frames are saved.", 12f))
        setContentView(root)
        root.requestApplyInsets()
    }

    private fun showStatus(text: String, ready: Boolean) {
        message.text = text; align.isEnabled = ready
        cameraSettings.visibility = if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun updateProgress(state: ArWalkingState, lengths: List<Double>, bearings: List<Double>) {
        latest = state
        val next = bearings.getOrNull(state.arcIndex + 1)
        val turn = next?.let { signedTurnDegrees(bearings[state.arcIndex], it) }
        val action = when {
            turn == null -> "to the destination"
            kotlin.math.abs(turn) >= 150 -> "then turn around"
            kotlin.math.abs(turn) < 30 -> "then continue straight"
            turn > 0 -> "then turn right"
            else -> "then turn left"
        }
        val remaining = (lengths[state.arcIndex] - state.offsetM).coerceAtLeast(0.0)
        val text = state.failure ?: if (state.offPath) "Pause. You may be off the mapped corridor; arrows are hidden."
            else if (state.arrived) "Estimated arrival. Confirm the destination sign."
            else String.format(Locale.US, "Walk %.1f m %s\n%.1f m remaining", remaining, action, state.remainingM)
        guidance.text = text
        val cue = "${state.arcIndex}:${remaining <= 3}:${state.arrived}:${state.failure}:${state.offPath}"
        if (cue != lastCue && running) { lastCue = cue; feedback.announce(text, state.failure != null) }
    }

    override fun finish() {
        returning = true
        if (::surface.isInitialized && running) { surface.onPause(); running = false; session?.pause() }
        if (::renderer.isInitialized && !restoreNeedsFix) latest = renderer.snapshot()
        val state = latest
        if (state != null) setResult(RESULT_OK, Intent()
            .putExtra("token", intent.getStringExtra("token"))
            .putExtra("arc", intent.getIntExtra("base_arc", 0) + state.arcIndex)
            .putExtra("offset", state.offsetM).putExtra("along", state.alongM)
            .putExtra("needs_fix", state.failure != null || state.offPath))
        super.finish()
    }

    override fun onResume() { super.onResume(); startAr() }
    private fun startAr(requestPermission: Boolean = false) {
        if (running || isFinishing) return
        if (restoreNeedsFix) { showStatus("AR interrupted. Return to the map and confirm your location.", false); return }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (!permissionInFlight && (!permissionRequested || requestPermission)) {
                permissionRequested = true; permissionInFlight = true
                cameraPermission.launch(Manifest.permission.CAMERA)
            } else if (!permissionInFlight) {
                showStatus("Camera access is disabled. Tap Retry AR or enable Camera in App settings.", false)
            }
            return
        }
        try {
            if (session == null) {
                if (ArCoreApk.getInstance().requestInstall(this, !installRequested) == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    installRequested = true
                    showStatus("Complete Google Play Services for AR installation, then return here.", false)
                    return
                }
                session = Session(this).also { ar ->
                    ar.configure(Config(ar).apply {
                        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        lightEstimationMode = Config.LightEstimationMode.DISABLED
                    })
                }
            }
            session!!.resume()
            renderer.session = session
            surface.onResume(); running = true
        } catch (error: Exception) {
            val text = when (error) {
                is com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException -> "This device does not support AR. Map navigation is still available."
                is com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException -> "AR installation was declined. Tap Retry AR to try again, or return to the map."
                is com.google.ar.core.exceptions.UnavailableApkTooOldException -> "Update Google Play Services for AR, then tap Retry AR."
                is com.google.ar.core.exceptions.UnavailableSdkTooOldException -> "The installed AR service needs a newer app build. Return to the map."
                is com.google.ar.core.exceptions.CameraNotAvailableException -> "Camera unavailable. Close other camera apps and tap Retry AR."
                else -> "AR could not start. Tap Retry AR or return to the map."
            }
            installRequested = false
            showStatus(text, false)
        }
    }
    private fun pauseAr() {
        if (running) {
            surface.onPause() // Stop frame updates before pausing the session.
            renderer.clearAlignment()
            session?.pause(); running = false
        }
    }
    override fun onPause() {
        pauseAr()
        if (::feedback.isInitialized) feedback.pause()
        super.onPause()
    }
    override fun onDestroy() {
        if (::renderer.isInitialized) { renderer.clearAlignment(); renderer.session = null }
        session?.close(); session = null
        if (::feedback.isInitialized) feedback.close()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("install_requested", installRequested)
        outState.putBoolean("permission_requested", permissionRequested)
        if (::renderer.isInitialized && !restoreNeedsFix) {
            // Save only after the render thread is stopped; world coordinates cannot survive recreation.
            pauseAr(); latest = renderer.snapshot()
        }
        latest?.let {
            outState.putBoolean("needs_fix", true)
            outState.putInt("arc", it.arcIndex); outState.putDouble("offset", it.offsetM)
            outState.putDouble("along", it.alongM); outState.putDouble("remaining", it.remainingM)
        }
        super.onSaveInstanceState(outState)
    }
    companion object {
        fun intent(context: Context, route: Route, state: NavigationState): Intent {
            val arcs = route.arcs.drop(state.currentArcIndex)
            return Intent(context, ArPreviewActivity::class.java)
                .putExtra("lengths", arcs.map { it.lengthM }.toDoubleArray())
                .putExtra("bearings", arcs.map { it.bearingDeg }.toDoubleArray())
                .putExtra("offset", state.offsetM.coerceAtMost(arcs.firstOrNull()?.lengthM ?: 0.0))
                .putExtra("remaining", state.remainingDistanceM)
                .putExtra("instruction", state.instruction)
                .putExtra("base_arc", state.currentArcIndex)
        }
    }
}
