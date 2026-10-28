package app.sthira.engine.timers

import app.sthira.engine.json.NumberRule
import app.sthira.engine.json.Sink
import app.sthira.engine.json.asArray
import app.sthira.engine.json.asObject
import app.sthira.engine.json.childPath
import app.sthira.engine.json.parseJsonText
import app.sthira.engine.json.readInt
import app.sthira.engine.json.readNumber
import app.sthira.engine.json.readString
import app.sthira.engine.json.readUrl
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Mirror of web/src/lib/timers/norms.ts: CDC STEADI balance and TUG reference values; nothing is defaulted. */
data class CdcSource(val url: String, val title: String)

data class BalanceStage(val stage: Int, val name: String, val cdcText: String)

data class BalanceNorms(
    val stages: List<BalanceStage>,
    val stageDurationS: Double,
    val progressionRule: String,
    val allowedMovement: String,
    val riskStage: Int,
    val riskHoldLessThanS: Double,
    val source: CdcSource,
)

data class TugNorms(
    val atRiskIfAtLeastS: Double,
    val walkDistanceMeters: Double,
    val timingRule: String,
    val footwearAndAids: String,
    val source: CdcSource,
)

data class TimerNorms(val balance: BalanceNorms, val tug: TugNorms)

sealed interface TimerNormsResult {
    data class Ok(val norms: TimerNorms) : TimerNormsResult
    data class Failed(val errors: List<String>) : TimerNormsResult
}

private fun readSource(obj: JsonObject?, path: String, sink: Sink): CdcSource? {
    val here = childPath(path, "source")
    val source = obj?.let { asObject(it["source"], here, sink) } ?: return null
    val url = readUrl(source, "url", here, sink)
    val title = readString(source, "title", here, sink)
    return if (url != null && title != null) CdcSource(url, title) else null
}

/** Reads `key` as an array of exactly one object (zod .length(1)). */
private fun singleEntry(parent: JsonObject?, key: String, path: String, sink: Sink): Pair<JsonObject?, String> {
    val here = childPath(path, key)
    val array = parent?.let { asArray(it[key], here, sink) } ?: return null to here
    if (array.size != 1) {
        sink.add("$here: array must contain exactly 1 element")
        return null to here
    }
    val entryPath = childPath(here, "0")
    return asObject(array[0], entryPath, sink) to entryPath
}

private fun readStages(balance: JsonObject?, sink: Sink): List<BalanceStage>? {
    val path = "balance4Stage.stages"
    val array = balance?.let { asArray(it["stages"], path, sink, minSize = 1) } ?: return null
    val stages = array.mapIndexedNotNull { i, element ->
        val here = childPath(path, i.toString())
        val obj = asObject(element, here, sink) ?: return@mapIndexedNotNull null
        val stage = readInt(obj, "stage", here, sink)
        val name = readString(obj, "name", here, sink)
        val cdcText = readString(obj, "cdcText", here, sink)
        if (stage != null && name != null && cdcText != null) BalanceStage(stage, name, cdcText) else null
    }
    return if (stages.size == array.size) stages else null
}

private fun readBalance(root: JsonObject, sink: Sink): BalanceNorms? {
    val balance = asObject(root["balance4Stage"], "balance4Stage", sink)
    val stages = readStages(balance, sink)
    val protocolPath = "balance4Stage.protocol"
    val protocol = balance?.let { asObject(it["protocol"], protocolPath, sink) }
    val duration = protocol?.let { readNumber(it, "stageDurationSeconds", protocolPath, sink, NumberRule.POSITIVE) }
    val progression = protocol?.let { readString(it, "progressionRule", protocolPath, sink) }
    val movement = protocol?.let { readString(it, "allowedMovement", protocolPath, sink) }
    val source = readSource(protocol, protocolPath, sink)
    val (risk, riskPath) = singleEntry(balance, "entries", "balance4Stage", sink)
    val riskStage = risk?.let { readInt(it, "stage", riskPath, sink) }
    val riskHold = risk?.let { readNumber(it, "increasedRiskIfHoldLessThan", riskPath, sink, NumberRule.POSITIVE) }
    readSource(risk, riskPath, sink)
    if (stages == null || duration == null || progression == null || movement == null || source == null) return null
    if (riskStage == null || riskHold == null) return null
    return BalanceNorms(stages, duration, progression, movement, riskStage, riskHold, source)
}

private fun readTug(root: JsonObject, sink: Sink): TugNorms? {
    val tug = asObject(root["tug"], "tug", sink)
    val (entry, entryPath) = singleEntry(tug, "entries", "tug", sink)
    val atRisk = entry?.let { readNumber(it, "atRiskIfAtLeast", entryPath, sink, NumberRule.POSITIVE) }
    readSource(entry, entryPath, sink)
    val protocolPath = "tug.protocol"
    val protocol = tug?.let { asObject(it["protocol"], protocolPath, sink) }
    val walk = protocol?.let { readNumber(it, "walkDistanceMeters", protocolPath, sink, NumberRule.POSITIVE) }
    val timing = protocol?.let { readString(it, "timingRule", protocolPath, sink) }
    val footwear = protocol?.let { readString(it, "footwearAndAids", protocolPath, sink) }
    val source = readSource(protocol, protocolPath, sink)
    if (atRisk == null || walk == null || timing == null || footwear == null || source == null) return null
    return TugNorms(atRisk, walk, timing, footwear, source)
}

/** Reads the CDC STEADI balance and TUG reference values from norms.json. Never throws. */
fun readTimerNorms(element: JsonElement): TimerNormsResult {
    val sink = mutableListOf<String>()
    val root = asObject(element, "", sink) ?: return TimerNormsResult.Failed(sink.toList())
    val balance = readBalance(root, sink)
    val tug = readTug(root, sink)
    if (sink.isNotEmpty() || balance == null || tug == null) return TimerNormsResult.Failed(sink.toList())
    return TimerNormsResult.Ok(TimerNorms(balance, tug))
}

fun readTimerNorms(json: String): TimerNormsResult {
    val (element, syntaxError) = parseJsonText(json)
    if (element == null) return TimerNormsResult.Failed(listOfNotNull(syntaxError))
    return readTimerNorms(element)
}
