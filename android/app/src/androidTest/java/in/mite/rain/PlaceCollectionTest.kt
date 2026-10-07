package `in`.mite.rain

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import `in`.mite.rain.platform.*
import `in`.mite.rain.core.VisualMatching
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

class PlaceCollectionTest {
    private val base get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun bitmap():Bitmap = Bitmap.createBitmap(224,224,Bitmap.Config.ARGB_8888).also {
        val canvas=Canvas(it);canvas.drawColor(Color.WHITE)
        canvas.drawRect(20f,30f,100f,190f,Paint().apply { color=Color.BLUE })
        canvas.drawText("TEST",105f,110f,Paint().apply { color=Color.BLACK;textSize=24f })
    }
    @Test fun actualBundledNeuralModelProducesStableFiniteFeaturesAndPreservesInput() {
        val bitmap=bitmap()
        PlaceEncoder(base).use { encoder ->
            val a=encoder.encode(bitmap);val b=encoder.encode(bitmap)
            assertTrue(a.size>=16);assertTrue(a.all { it.isFinite() })
            assertEquals(1.0,VisualMatching.cosine(a,b),1e-5)
            assertFalse(bitmap.isRecycled)
        }
        bitmap.recycle()
    }
    @Test fun datasetPreservesSplitsFreezesReferencesAndExportsOnlyExplicitCaptures() {
        val root=File(base.cacheDir,"place-tests-${UUID.randomUUID()}").apply { mkdirs() }
        val context=object:ContextWrapper(base) { override fun getFilesDir()=root }
        val floor=base.assets.open("ground-floor.json").use { FloorPackStore.parse(FloorPackStore.readLimited(it)).floor }
        val store=PlaceDataset(context,floor)
        val bitmap=bitmap();val vector=listOf(1.0,0.0)
        val evidence=VisualMatching.evidence(floor.graph.nodes.values,"",vector,emptyList())
        try {
            store.save(bitmap,vector,"A001","reference","REF",evidence,1)
            store.save(bitmap,vector,"A002","reference","REF",evidence,2)
            assertTrue(runCatching { store.save(bitmap,vector,"A001","train","REF",evidence,3) }.isFailure)
            store.save(bitmap,vector,"A001","train","TRAIN",evidence,3)
            assertTrue(runCatching { store.save(bitmap,vector,"A003","reference","REF",evidence,4) }.isFailure)
            assertTrue(runCatching { store.save(bitmap,vector,"A001","test","TEST",evidence,3) }.isFailure)
            assertEquals(2,store.references().size)
            assertEquals(0,PlaceDataset(context,floor,"another-study").references().size)
            val output=ByteArrayOutputStream();store.export(output)
            val names=mutableListOf<String>()
            ZipInputStream(output.toByteArray().inputStream()).use { zip ->
                while(true) { val entry=zip.nextEntry ?: break;names+=entry.name;zip.closeEntry() }
            }
            assertEquals(4,names.size);assertTrue("dataset.json" in names)
            assertEquals(3,PlaceDataset(context,floor).read().getJSONArray("captures").length())
        } finally { bitmap.recycle();root.deleteRecursively() }
    }
}
