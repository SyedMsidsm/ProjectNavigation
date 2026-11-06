package `in`.mite.rain

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import `in`.mite.rain.core.*
import `in`.mite.rain.platform.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TrailIntegrationTest {
    @Test fun oppositeRoomLabelsSurviveSavingAndReloading() {
        val recorder = TrailRecorder(.7,"Entrance")
        repeat(5) { recorder.step() }
        recorder.markBeside("Lab",emptyList(),"left")
        recorder.markBeside("Office",emptyList(),"right")
        val graph = FloorPackStore.parse(TrailLibrary.pack("Corridor",recorder.finish()).json).floor.graph
        val lab = graph.nodes.values.single { it.roomName == "Lab" }
        val office = graph.nodes.values.single { it.roomName == "Office" }
        assertEquals("left",lab.corridorSide)
        assertEquals("label_bearing=270.0",lab.notes)
        assertEquals("right",office.corridorSide)
        assertEquals(0.0,RoutePlanner(graph).plan(lab.id,office.id)!!.totalLengthM,.001)
    }
    private fun trail():Graph {
        val recorder=TrailRecorder(.7,"Lobby")
        repeat(5){recorder.step()};recorder.mark("Fees, office",listOf("Payments"))
        recorder.changeFloor(1,"lift");repeat(4){recorder.step()};recorder.mark("Library")
        return recorder.finish()
    }
    @Test fun recordedPackPreservesFloorsAliasesAndTransitions() {
        val pack=TrailLibrary.pack("Test building",trail())
        val loaded=FloorPackStore.parse(pack.json)
        assertEquals("recorded_trail",loaded.floor.source)
        assertEquals(setOf(0,1),loaded.floor.graph.nodes.values.map{it.floor}.toSet())
        assertEquals("Fees, office",DestinationSearch.matches("payments",loaded.floor.graph.nodes.values).single().roomName)
        assertEquals("lift",loaded.floor.graph.edges.single{it.transition.isNotBlank()}.transition)
        assertEquals(0.0,loaded.floor.graph.edges.single{it.transition.isNotBlank()}.lengthM,.001)
    }
    @Test fun libraryPersistsRenamesAndDeletesWithoutTouchingOtherMaps() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.cacheDir,"trail-test-${System.nanoTime()}").apply{mkdirs()}
        val isolated=object:ContextWrapper(base){override fun getFilesDir()=root}
        try{
            val library=TrailLibrary(isolated)
            val first=library.save("First",trail());val second=library.save("Second",trail())
            library.rename(first,"Renamed")
            assertEquals(2,TrailLibrary(isolated).list().size)
            assertEquals("Renamed",TrailLibrary(isolated).read(first).floor.name)
            library.delete(first)
            assertEquals(second,library.list().single().id)
        }finally{root.deleteRecursively()}
    }
    @Test fun rejectsPathTraversalInMapId() {
        val library=TrailLibrary(InstrumentationRegistry.getInstrumentation().targetContext)
        try{library.read("../../active-floor");fail("Accepted an unsafe map ID")}catch(_:IllegalArgumentException){}
    }
    @Test fun legacyMapStillDefaultsToGroundFloor() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val graph=c.assets.open("ground-floor.json").use{FloorPackStore.parse(FloorPackStore.readLimited(it)).floor.graph}
        assertTrue(graph.nodes.values.all{it.floor==0})
        assertTrue(graph.edges.all{it.transition.isBlank()})
    }
}
