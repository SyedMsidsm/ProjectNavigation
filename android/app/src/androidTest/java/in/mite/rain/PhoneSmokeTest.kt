package `in`.mite.rain

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import `in`.mite.rain.core.RoomNameMatcher
import `in`.mite.rain.core.RoutePlanner
import `in`.mite.rain.core.PlannerKind
import `in`.mite.rain.platform.GraphRepository
import `in`.mite.rain.platform.RiskModelLoader
import `in`.mite.rain.platform.SessionLogger
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Real-device integration checks; generated labels do not measure real-sign accuracy. */
class PhoneSmokeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun bundledOcrRecognizesThreeGeneratedSignsWithoutInternetPermission() = runBlocking {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.INTERNET))
        val matcher = RoomNameMatcher(GraphRepository(context).load().nodes.values)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            for ((text, node) in listOf("ROOM 201" to "N01", "CSE LAB 204" to "N04", "LIBRARY" to "N05")) {
                val bitmap = Bitmap.createBitmap(1400, 420, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK; textSize = 150f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    canvas.drawText(text, 60f, 250f, paint)
                    val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS)
                    val match = matcher.match(result.text)
                    assertTrue("Generated sign did not meet conservative acceptance threshold", match.accepted)
                    assertEquals(node, match.best?.nodeId)
                } finally { bitmap.recycle() }
            }
        } finally { recognizer.close() }
    }

    @Test fun roomAssetsRiskModelAndPhoneSensorsAreAvailable() = runBlocking {
        val graph = GraphRepository(context).load()
        assertEquals(15, graph.nodes.size)
        val planner = RoutePlanner(graph, RiskModelLoader.load(context))
        assertEquals(35.0, planner.plan("ENT", "N04", PlannerKind.SHORTEST)!!.totalLengthM, 0.001)
        assertEquals(40.0, planner.plan("ENT", "N04", PlannerKind.RISK)!!.totalLengthM, 0.001)
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        assertNotNull("Test phone lacks a hardware step detector", sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR))
        assertNotNull("Test phone lacks relative rotation sensor", sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR))
    }

    @Test fun sessionWriterPreservesOrderNullableOutcomeAndProvenance() {
        val logger = SessionLogger(context)
        logger.start(mapOf("participant_code" to "AUTOMATED_SMOKE", "device_model" to android.os.Build.MODEL,
            "planner" to "risk", "lambda" to 40.0, "start_node" to "ENT", "dest_node" to "N04",
            "app_version" to BuildConfig.VERSION_NAME, "demo_mode" to true, "data_source" to "synthetic_floor"))
        val id = logger.sessionId!!
        logger.event("arrival", mapOf("node_id" to "N04", "correct" to null, "ground_truth_verified" to false,
            "total_time_s" to 0.0, "distance_walked_m" to 0.0))
        logger.finish(true)
        logger.close()
        val file = File(context.filesDir, "sessions/session_$id.jsonl")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        var lines = emptyList<String>()
        while (System.nanoTime() < deadline) {
            lines = if (file.exists()) file.readLines().filter(String::isNotBlank) else emptyList()
            if (lines.size == 3 && runCatching { JSONObject(lines.last()).getString("event") == "session_end" }.getOrDefault(false)) break
            Thread.sleep(25)
        }
        assertEquals(3, lines.size)
        val records = lines.map(::JSONObject)
        assertEquals(listOf("session_start", "arrival", "session_end"), records.map { it.getString("event") })
        assertTrue(records[1].isNull("correct"))
        assertFalse(records[1].getBoolean("ground_truth_verified"))
        assertTrue(records.all { it.getString("schema_version") == "0.2" && it.getBoolean("demo_mode") })
        assertTrue(records.all { it.getString("session_id") == id })
    }
}
