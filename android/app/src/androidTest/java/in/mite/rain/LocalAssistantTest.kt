package `in`.mite.rain

import androidx.test.platform.app.InstrumentationRegistry
import `in`.mite.rain.core.Node
import `in`.mite.rain.core.NodeType
import `in`.mite.rain.platform.LocalDestinationAssistant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real inference on a labelled synthetic catalog; not a campus accuracy benchmark. */
class LocalAssistantTest {
    @Test fun preparedModelCanSuggestAnExistingLibrary() = runBlocking {
        val assistant=LocalDestinationAssistant(InstrumentationRegistry.getInstrumentation().targetContext)
        assumeTrue("Local model must be prepared separately",assistant.available())
        val catalog=listOf(Node("LIBRARY",NodeType.JUNCTION,"Library"),
            Node("FEES",NodeType.JUNCTION,"Fees counter"),Node("LAB",NodeType.JUNCTION,"Computer laboratory"))
        val answer=assistant.suggest("Where can I borrow a book?",catalog)
        assertNotNull("Model did not return a valid place ID",answer)
        assertEquals("LIBRARY",answer!!.id)
    }
    @Test fun preparedModelUnderstandsWashroomRequest() = runBlocking {
        val assistant=LocalDestinationAssistant(InstrumentationRegistry.getInstrumentation().targetContext)
        assumeTrue(assistant.available())
        val catalog=listOf(Node("WC",NodeType.JUNCTION,"Washroom"),
            Node("LIB",NodeType.JUNCTION,"Library"),Node("OFFICE",NodeType.JUNCTION,"Office"))
        assertEquals("WC", assistant.suggest("I need to wash my hands",catalog)?.id)
    }
    @Test fun preparedModelRejectsUnavailableDestination() = runBlocking {
        val assistant=LocalDestinationAssistant(InstrumentationRegistry.getInstrumentation().targetContext)
        assumeTrue(assistant.available())
        val catalog=listOf(Node("LIB",NodeType.JUNCTION,"Library"),Node("OFFICE",NodeType.JUNCTION,"Office"))
        assertNull(assistant.suggest("Take me to the swimming pool",catalog))
    }
}
