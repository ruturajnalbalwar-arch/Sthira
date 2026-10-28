package app.sthira.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The real params.json: protocol constants only, so the counter is untuned until the fitter runs. */
class RepoParamsTest {
    private val json: String by lazy {
        File(requireNotNull(System.getProperty("sthira.params")) { "system property sthira.params is not set" }).readText()
    }

    @Test
    fun `repo params json loads as untuned with the 16 fitted names missing`() {
        val expected = ParamName.entries.filter { it.requiredClass == ParamClass.FITTED }.map { it.key }
        assertEquals(16, expected.size)
        assertEquals(LoadResult.Untuned(expected), loadParams(json))
        assertTrue(createCounter(json) is CounterResult.Untuned)
    }

    @Test
    fun `repo params json yields the protocol constants`() {
        assertEquals(ProtocolResult.Ok(ProtocolConstants(30.0, 5.0, 5.0)), readProtocolConstants(json))
    }

    @Test
    fun `malformed params never throw`() {
        assertTrue(loadParams("{not json") is LoadResult.Invalid)
        assertTrue(loadParams("[]") is LoadResult.Invalid)
        val missingValue = """{"version":"1","generatedAt":"x","params":{"minFps":{"class":"fitted","unit":"fps","source":"s","date":"d"}}}"""
        val result = loadParams(missingValue)
        assertEquals(LoadResult.Invalid(listOf("params.minFps.value: expected number, received undefined")), result)
        assertTrue(readProtocolConstants("""{"version":"1","generatedAt":"x","params":{}}""") is ProtocolResult.Failed)
    }
}
