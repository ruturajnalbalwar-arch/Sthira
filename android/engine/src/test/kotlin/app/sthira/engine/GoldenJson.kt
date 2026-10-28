package app.sthira.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.math.abs
import kotlin.math.max

/** Golden-file decoding, wire encoding of Kotlin results (TypeScript shapes) and a tolerant deep diff. */
internal object GoldenJson {
    private const val TOLERANCE = 1e-9
    private const val VALUES_PER_LANDMARK = 4

    fun frames(element: JsonElement): List<PoseFrame> = element.jsonArray.map { frame ->
        val obj = frame.jsonObject
        val lm = obj.getValue("lm").jsonArray.map { numberOrNaN(it) }
        val landmarks = lm.chunked(VALUES_PER_LANDMARK).map { Landmark(it[0], it[1], it[2], it[3]) }
        PoseFrame(obj.getValue("t").jsonPrimitive.double, landmarks)
    }

    /** JSON.stringify writes NaN as null. */
    private fun numberOrNaN(element: JsonElement): Double =
        if (element is JsonNull) Double.NaN else element.jsonPrimitive.double

    fun params(element: JsonElement): ChairStandParams {
        val obj = element.jsonObject
        val map = ParamName.entries.associateWith { obj.getValue(it.key).jsonPrimitive.double }
        return requireNotNull(ChairStandParams.fromMap(map)) { "golden params incomplete" }
    }

    fun calibration(element: JsonElement): Calibration {
        val obj = element.jsonObject
        val v = { key: String -> obj.getValue(key).jsonPrimitive.double }
        return Calibration(v("seatedHeadY"), v("standingHeadY"), v("bodyScalePx"))
    }

    fun geometry(element: JsonElement): VideoGeometry {
        val obj = element.jsonObject
        return VideoGeometry(obj.getValue("width").jsonPrimitive.double, obj.getValue("height").jsonPrimitive.double)
    }

    private fun strings(values: List<String>) = JsonArray(values.map(::JsonPrimitive))

    fun encode(params: ChairStandParams): JsonObject = JsonObject(params.toMap().mapKeys { it.key.key }.mapValues { JsonPrimitive(it.value) })

    fun encode(result: LoadResult): JsonObject = when (result) {
        is LoadResult.Ready -> buildJsonObject { put("status", "ready"); put("params", encode(result.params)) }
        is LoadResult.Untuned -> buildJsonObject { put("status", "untuned"); put("missing", strings(result.missing)) }
        is LoadResult.Invalid -> buildJsonObject { put("status", "invalid"); put("errors", strings(result.errors)) }
    }

    fun status(result: CounterResult): String = when (result) {
        is CounterResult.Ready -> "ready"
        is CounterResult.Untuned -> "untuned"
        is CounterResult.Invalid -> "invalid"
    }

    fun encode(result: CalibrationResult): JsonObject = when (result) {
        is CalibrationResult.Ok -> buildJsonObject {
            put("ok", true)
            put("calib", buildJsonObject {
                put("seatedHeadY", result.calib.seatedHeadY)
                put("standingHeadY", result.calib.standingHeadY)
                put("bodyScalePx", result.calib.bodyScalePx)
            })
        }
        is CalibrationResult.Failed -> buildJsonObject { put("ok", false); put("reasons", strings(result.reasons)) }
    }

    fun encode(summary: ChairStandSummary): JsonObject = buildJsonObject {
        put("full", summary.full)
        put("half", summary.half)
        put("armAssisted", summary.armAssisted)
        put("uncertain", summary.uncertain)
        putJsonArray("events") {
            summary.events.forEach { e ->
                add(buildJsonObject {
                    put("kind", e.kind.wire)
                    put("startT", e.startT)
                    put("standingT", e.standingT)
                    put("endT", e.endT)
                })
            }
        }
        put("effectiveFps", summary.effectiveFps)
        put("lowFps", summary.lowFps)
        put("unreliable", summary.unreliable)
        put("droppedFrames", summary.droppedFrames)
        put("gaps", summary.gaps)
        put("windowComplete", summary.windowComplete)
    }

    /** Every difference between expected and actual as "path: expected X, got Y" (empty when equal). */
    fun diff(expected: JsonElement, actual: JsonElement, path: String = "$"): List<String> = when {
        expected is JsonObject && actual is JsonObject -> {
            (expected.keys + actual.keys).flatMap { key ->
                val e = expected[key] ?: JsonNull
                val a = actual[key] ?: JsonNull
                if (key !in expected || key !in actual) listOf("$path.$key: expected $e, got $a") else diff(e, a, "$path.$key")
            }
        }
        expected is JsonArray && actual is JsonArray ->
            if (expected.size != actual.size) {
                listOf("$path: expected ${expected.size} items, got ${actual.size}: expected $expected, got $actual")
            } else {
                expected.indices.flatMap { diff(expected[it], actual[it], "$path[$it]") }
            }
        else -> if (primitiveEqual(expected, actual)) emptyList() else listOf("$path: expected $expected, got $actual")
    }

    private fun primitiveEqual(expected: JsonElement, actual: JsonElement): Boolean {
        if (expected is JsonNull || actual is JsonNull) return expected is JsonNull && actual is JsonNull
        val e = expected as? JsonPrimitive ?: return false
        val a = actual as? JsonPrimitive ?: return false
        if (e.isString || a.isString) return e.isString && a.isString && e.content == a.content
        val en = e.content.toDoubleOrNull()
        val an = a.content.toDoubleOrNull()
        if (en == null || an == null) return e.content == a.content
        return abs(en - an) <= TOLERANCE * max(1.0, max(abs(en), abs(an)))
    }
}
