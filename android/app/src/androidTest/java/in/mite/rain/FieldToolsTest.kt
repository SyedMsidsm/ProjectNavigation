package `in`.mite.rain

import androidx.test.platform.app.InstrumentationRegistry
import `in`.mite.rain.platform.FloorPackStore
import `in`.mite.rain.platform.UserSettings
import `in`.mite.rain.core.CalibrationResult
import `in`.mite.rain.core.PromptMode
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FieldToolsTest {
    private val context get() = object : android.content.ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getFilesDir() = java.io.File(cacheDir, "field-tools-test").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("field_test_$name", mode)
    }
    private fun sample() = JSONObject().put("schema_version", 1).put("name", "Imported sample")
        .put("data_source", "synthetic_floor")
        .put("rooms_csv", context.assets.open("rooms.csv").bufferedReader().use { it.readText() })
        .put("edges_csv", context.assets.open("edges.csv").bufferedReader().use { it.readText() })

    @Test fun settingsAndCalibrationSurviveNewRepositoryInstance() {
        val settings = UserSettings(context)
        settings.save(80.0, .8, true, true, PromptMode.NEVER)
        val reloaded = UserSettings(context)
        assertEquals(80.0, reloaded.lambda, .0001)
        assertTrue(reloaded.voice); assertTrue(reloaded.haptics)
        assertEquals(PromptMode.NEVER, reloaded.promptMode)
        settings.saveCalibration(CalibrationResult(14.0, 20, .7), "automated synthetic test")
        assertEquals(.7, UserSettings(context).stepLength, .0001)
        // Do not leave fabricated calibration or enabled speech in the installed demo.
        context.getSharedPreferences("rain_settings", 0).edit().clear().commit()
    }
    @Test fun importedFloorIsValidatedPersistedAndCanRestoreDemo() = runBlocking {
        val store = FloorPackStore(context)
        val pack = FloorPackStore.parse(sample().toString())
        try {
            store.activate(pack)
            assertEquals("Imported sample", FloorPackStore(context).load().name)
            assertEquals(15, store.load().graph.nodes.size)
            val invalid = sample().put("edges_csv", "broken data").toString()
            assertTrue(runCatching { FloorPackStore.parse(invalid) }.isFailure)
            assertEquals(pack.floor.digest, store.load().digest)
        } finally { store.restoreDemo() }
        assertEquals("Demo floor / Level 01", store.load().name)
    }
    @Test fun oversizedAndUnreachableFloorPacksAreRejected() {
        assertTrue(runCatching { FloorPackStore.readLimited(java.io.ByteArrayInputStream(ByteArray(FloorPackStore.MAX_BYTES + 1))) }.isFailure)
        val json = sample()
        json.put("edges_csv", json.getString("edges_csv").replace(",true,", ",false,"))
        assertTrue(runCatching { FloorPackStore.parse(json.toString()) }.isFailure)
    }
}
