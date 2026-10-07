package `in`.mite.rain.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.google.ar.core.*
import `in`.mite.rain.core.ArWalkingPath
import `in`.mite.rain.core.ArWalkingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Continuous camera-pose route tracking with a rolling anchored arrow window. */
internal class ArPreviewRenderer(
    private val path: ArWalkingPath, private val rotation: () -> Int,
    private val progress: (ArWalkingState) -> Unit,
    private val status: (String, Boolean) -> Unit,
) : GLSurfaceView.Renderer {
    @Volatile var session: Session? = null
    private val alignRequested = AtomicBoolean(false)
    private var anchor: Anchor? = null
    private var originX = 0f; private var originZ = 0f
    private var aligned = false
    private var lastProgressNs = 0L
    private var width = 1; private var height = 1
    private var texture = 0; private var background = 0; private var arrowProgram = 0
    private var graphicsReady = false
    private var lastStatus = ""
    private val quad = floats(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f)
    private val uv = floats(0f,0f, 1f,0f, 0f,1f, 1f,1f)
    // Triangle head and rectangular stem, in metres, pointing toward local -Z.
    private val arrow = floats(-.22f, .025f,0f, .22f,.025f,0f, 0f,.025f,-.38f,
        -.07f,.025f,0f, -.07f,.025f,.25f, .07f,.025f,0f,
        .07f,.025f,0f, -.07f,.025f,.25f, .07f,.025f,.25f)
    private val projection = FloatArray(16); private val view = FloatArray(16)
    private val model = FloatArray(16); private val pv = FloatArray(16); private val mvp = FloatArray(16)
    fun requestAlignment() { alignRequested.set(true) }
    // Called only after GLSurfaceView.onPause has joined the renderer thread.
    fun clearAlignment() {
        if (aligned) path.invalidate("AR interrupted. Return to the map and confirm your location.")
        anchor?.detach(); anchor = null; alignRequested.set(false)
        if (aligned) progress(path.snapshot())
    }
    fun snapshot(): ArWalkingState = path.snapshot()
    private fun report(text: String, ready: Boolean) {
        val key = "$ready:$text"
        if (lastStatus != key) { lastStatus = key; status(text, ready) }
    }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        graphicsReady = false
        try {
        val textures = IntArray(1); glGenTextures(1, textures, 0); texture = textures[0]
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        background = program("attribute vec2 p; attribute vec2 uv; varying vec2 t; void main(){gl_Position=vec4(p,0.,1.);t=uv;}",
            "#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES camera; varying vec2 t; void main(){gl_FragColor=texture2D(camera,t);}")
        arrowProgram = program("attribute vec3 p; uniform mat4 mvp; void main(){gl_Position=mvp*vec4(p,1.);}",
            "precision mediump float; void main(){gl_FragColor=vec4(0.35,1.0,0.67,1.0);}")
        clearAlignment()
        graphicsReady = true
        } catch (_: Exception) {
            report("AR graphics unavailable. Return to the map to continue navigation.", false)
        }
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width; this.height = height; glViewport(0, 0, width, height)
    }
    override fun onDrawFrame(gl: GL10?) {
        glClearColor(.04f,.08f,.07f,1f); glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        if (!graphicsReady) return
        val ar = session ?: return
        try {
            ar.setDisplayGeometry(rotation(), width, height)
            ar.setCameraTextureName(texture)
            val frame = ar.update()
            if (frame.timestamp != 0L) drawBackground(frame)
            val camera = frame.camera
            if (camera.trackingState != TrackingState.TRACKING) {
                clearAlignment()
                report(if (aligned) "Tracking lost. Return to the map and confirm your location. Arrows hidden." else "Move slowly in good light to start tracking.", false)
                return
            }
            path.snapshot().failure?.let { report(it, false); return }
            val hit = frame.hitTest(width * .5f, height * .5f).firstOrNull {
                val plane = it.trackable as? Plane
                plane?.type == Plane.Type.HORIZONTAL_UPWARD_FACING && plane.isPoseInPolygon(it.hitPose) &&
                    camera.pose.ty() - it.hitPose.ty() in .35f..2.2f
            }
            if (anchor?.trackingState != null && anchor?.trackingState != TrackingState.TRACKING) {
                clearAlignment()
                report("Floor anchor lost. Return to the map and confirm your location.", false)
                return
            }
            if (alignRequested.getAndSet(false) && !aligned) {
                val back = camera.pose.zAxis
                val horizontal = sqrt(back[0]*back[0] + back[2]*back[2])
                if (hit == null || horizontal < .25f) {
                    report("Aim diagonally down at a clear floor while facing the corridor, then align again.", hit != null)
                    return
                }
                val yaw = atan2(back[0], back[2])
                val pose = Pose(floatArrayOf(camera.pose.tx(), hit.hitPose.ty(), camera.pose.tz()),
                    floatArrayOf(0f, sin(yaw/2), 0f, cos(yaw/2)))
                anchor?.detach(); anchor = ar.createAnchor(pose)
                aligned = true
            }
            val current = anchor
            if (current == null) {
                report(if (hit == null) "Move the phone slowly to find the floor. Aim the crosshair at it."
                    else "Floor found. Confirm it is the floor, face the corridor and tap Align arrows.", hit != null)
                return
            }
            val local = current.pose.inverse().compose(camera.pose)
            val state = path.update((local.tx() + originX).toDouble(), (local.tz() + originZ).toDouble(), frame.timestamp)
            if (frame.timestamp - lastProgressNs >= 250_000_000L || state.failure != null) {
                lastProgressNs = frame.timestamp; progress(state)
            }
            state.failure?.let { report(it, false); return }
            if (state.arrived) { report("Estimated arrival. Return to the map and confirm the destination sign.", false); return }
            report(if (state.offPath) "Possible route deviation. Arrows hidden."
                else "Walking AR active · follow the corridor and watch your surroundings.", false)
            val arrows = path.arrows()
            // Rebase near the camera without changing the route transform. This does not remove drift.
            if (sqrt(local.tx() * local.tx() + local.tz() * local.tz()) > 4f) {
                val replacement = ar.createAnchor(current.pose.compose(Pose.makeTranslation(local.tx(), 0f, local.tz())))
                originX += local.tx(); originZ += local.tz()
                anchor = replacement; current.detach()
                return
            }
            camera.getProjectionMatrix(projection, 0, .1f, 30f)
            camera.getViewMatrix(view, 0); Matrix.multiplyMM(pv, 0, projection, 0, view, 0)
            glEnable(GL_DEPTH_TEST); glUseProgram(arrowProgram)
            val position = glGetAttribLocation(arrowProgram, "p")
            glEnableVertexAttribArray(position); arrow.position(0)
            glVertexAttribPointer(position, 3, GL_FLOAT, false, 0, arrow)
            for (point in arrows) {
                current.pose.toMatrix(model, 0)
                Matrix.translateM(model, 0, point.x - originX, 0f, point.z - originZ)
                Matrix.rotateM(model, 0, -point.clockwiseDegrees, 0f, 1f, 0f)
                Matrix.multiplyMM(mvp, 0, pv, 0, model, 0)
                glUniformMatrix4fv(glGetUniformLocation(arrowProgram, "mvp"), 1, false, mvp, 0)
                glDrawArrays(GL_TRIANGLES, 0, 9)
            }
            glDisableVertexAttribArray(position)
        } catch (_: Exception) {
            clearAlignment()
            report("AR tracking unavailable. Return to the map and reopen AR to retry.", false)
        }
    }
    private fun drawBackground(frame: Frame) {
        quad.position(0); uv.position(0)
        frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad,
            Coordinates2d.TEXTURE_NORMALIZED, uv)
        glDisable(GL_DEPTH_TEST); glUseProgram(background)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        glUniform1i(glGetUniformLocation(background, "camera"), 0)
        val p = glGetAttribLocation(background, "p"); val t = glGetAttribLocation(background, "uv")
        quad.position(0); uv.position(0)
        glEnableVertexAttribArray(p); glEnableVertexAttribArray(t)
        glVertexAttribPointer(p, 2, GL_FLOAT, false, 0, quad)
        glVertexAttribPointer(t, 2, GL_FLOAT, false, 0, uv)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glDisableVertexAttribArray(p); glDisableVertexAttribArray(t)
    }
    private fun program(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = glCreateShader(type); glShaderSource(shader, source); glCompileShader(shader)
            val ok = IntArray(1); glGetShaderiv(shader, GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "AR shader compilation failed" }
            return shader
        }
        val v = compile(GL_VERTEX_SHADER, vertex); val f = compile(GL_FRAGMENT_SHADER, fragment)
        val result = glCreateProgram(); glAttachShader(result, v); glAttachShader(result, f); glLinkProgram(result)
        val ok = IntArray(1); glGetProgramiv(result, GL_LINK_STATUS, ok, 0)
        glDeleteShader(v); glDeleteShader(f); check(ok[0] != 0) { "AR shader linking failed" }
        return result
    }
    companion object {
        private fun floats(vararg values: Float): FloatBuffer = ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
    }
}
