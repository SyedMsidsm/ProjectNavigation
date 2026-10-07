package `in`.mite.rain

import android.content.ComponentName
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.GLES20
import androidx.test.platform.app.InstrumentationRegistry
import `in`.mite.rain.ar.ArPreviewActivity
import `in`.mite.rain.ar.ArPreviewRenderer
import `in`.mite.rain.core.ArWalkingPath
import org.junit.Assert.*
import org.junit.Test

/** Verifies real GPU shader compatibility without recording or reading camera images. */
class ArPhoneSmokeTest {
    @Test fun cameraAndArrowShadersCompileOnPhoneGpu() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertNotEquals(EGL14.EGL_NO_DISPLAY, display)
        val version = IntArray(2)
        assertTrue(EGL14.eglInitialize(display, version, 0, version, 1))
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        assertTrue(EGL14.eglChooseConfig(display, intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0))
        assertTrue(count[0] > 0)
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0],
            intArrayOf(EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE), 0)
        try {
            assertNotEquals(EGL14.EGL_NO_CONTEXT, context)
            assertNotEquals(EGL14.EGL_NO_SURFACE, surface)
            assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))
            val failures = mutableListOf<String>()
            val renderer = ArPreviewRenderer(ArWalkingPath(listOf(10.0), listOf(0.0)), { 0 }, { }) { text, _ -> failures += text }
            renderer.onSurfaceCreated(null, null)
            renderer.onSurfaceChanged(null, 16, 16)
            renderer.onDrawFrame(null)
            assertTrue("AR graphics setup reported a failure: $failures", failures.isEmpty())
            assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    @Test fun arRemainsOptionalAndActivityIsInternal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        assertEquals("optional", app.metaData.getString("com.google.ar.core"))
        val activity = context.packageManager.getActivityInfo(ComponentName(context, ArPreviewActivity::class.java), 0)
        assertFalse(activity.exported)
        val main = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals(android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK, main.launchMode)
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.INTERNET))
    }
}
