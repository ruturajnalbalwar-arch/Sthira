package app.sthira.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Replays every case of shared/golden/chair-stand.golden.json (generated from the TypeScript engine)
 * and requires the Kotlin engine to produce the same output: exact for counts, booleans, strings,
 * kinds and nulls; doubles within 1e-9 relative.
 */
class GoldenParityTest {
    private val golden: JsonObject by lazy {
        val path = requireNotNull(System.getProperty("sthira.golden")) { "system property sthira.golden is not set" }
        Json.parseToJsonElement(File(path).readText()).jsonObject
    }

    private fun cases(key: String) = golden.getValue(key).jsonArray.map { it.jsonObject }

    private fun name(case: JsonObject) = case.getValue("name").jsonPrimitive.content

    private fun check(section: String, case: JsonObject, expected: JsonElement, actual: JsonElement): String? {
        val diffs = GoldenJson.diff(expected, actual)
        return if (diffs.isEmpty()) null else "$section/${name(case)}:\n  " + diffs.joinToString("\n  ")
    }

    private fun assertAll(section: String, results: List<String?>) {
        val failures = results.filterNotNull()
        if (failures.isNotEmpty()) fail("${failures.size} $section case(s) differ from the golden file:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `golden file has the expected schema and covers every section`() {
        assertEquals("sthira.engine-golden.v1", golden.getValue("schema").jsonPrimitive.content)
        listOf("loader", "counter", "calibration", "sessions").forEach { assertTrue("$it is empty", cases(it).isNotEmpty()) }
    }

    @Test
    fun `loader cases match`() = assertAll("loader", cases("loader").map { case ->
        check("loader", case, case.getValue("expected"), GoldenJson.encode(loadParams(case.getValue("json"))))
    })

    @Test
    fun `loader accepts the same cases as JSON text`() = assertAll("loader-text", cases("loader").map { case ->
        val text = case.getValue("json").toString()
        check("loader-text", case, case.getValue("expected"), GoldenJson.encode(loadParams(text)))
    })

    @Test
    fun `counter cases match`() = assertAll("counter", cases("counter").map { case ->
        // Counter cases carry only the name; their input is the loader case of the same name.
        val actual = GoldenJson.status(createCounter(loaderJson(name(case))))
        check("counter", case, case.getValue("expectedStatus"), JsonPrimitive(actual))
    })

    private fun loaderJson(caseName: String): String =
        cases("loader").first { name(it) == caseName }.getValue("json").toString()

    @Test
    fun `calibration cases match`() {
        val geometry = GoldenJson.geometry(golden.getValue("geometry"))
        assertAll("calibration", cases("calibration").map { case ->
            val result = calibrate(
                GoldenJson.frames(case.getValue("sit")),
                GoldenJson.frames(case.getValue("stand")),
                geometry,
                GoldenJson.params(case.getValue("params")),
            )
            check("calibration", case, case.getValue("expected"), GoldenJson.encode(result))
        })
    }

    @Test
    fun `session cases match`() {
        val geometry = GoldenJson.geometry(golden.getValue("geometry"))
        assertAll("session", cases("sessions").map { case -> check("session", case, case.getValue("expected"), replay(case, geometry)) })
    }

    private fun replay(case: JsonObject, geometry: VideoGeometry): JsonElement {
        val params = GoldenJson.params(case.getValue("params"))
        val calib = GoldenJson.calibration(case.getValue("calib"))
        val ctx = when (val result = makeContext(geometry, calib, params)) {
            is ContextResult.Ok -> result.ctx
            is ContextResult.Failed -> return JsonPrimitive("context failed: ${result.errors}")
        }
        val start = case["testStartT"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.double
        val final = GoldenJson.frames(case.getValue("frames")).fold(initialState(start)) { s, f -> step(s, f, ctx) }
        return GoldenJson.encode(summarize(final, ctx))
    }
}
