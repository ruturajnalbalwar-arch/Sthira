package app.sthira.engine

import app.sthira.engine.json.Sink
import app.sthira.engine.json.asObject
import app.sthira.engine.json.childPath
import app.sthira.engine.json.parseJsonText
import app.sthira.engine.json.readEnum
import app.sthira.engine.json.readNumber
import app.sthira.engine.json.readString
import kotlinx.serialization.json.JsonElement

/** One params.json entry (mirror of ParamEntrySchema). [script] is null when the key is absent. */
data class ParamEntry(
    val value: Double,
    val paramClass: ParamClass,
    val unit: String,
    val source: String,
    val script: String?,
    val date: String,
)

/** Structural result of reading params.json: entries in file order, or the structural issues. */
internal sealed interface ParsedParamsFile {
    data class Ok(val entries: Map<String, ParamEntry>) : ParsedParamsFile
    data class Bad(val errors: List<String>) : ParsedParamsFile
}

private val CLASS_WIRES = ParamClass.entries.map { it.wire }

private fun readEntry(element: JsonElement?, path: String, sink: Sink): ParamEntry? {
    val obj = asObject(element, path, sink) ?: return null
    val before = sink.size
    val value = readNumber(obj, "value", path, sink)
    val cls = readEnum(obj, "class", path, sink, CLASS_WIRES)?.let(ParamClass::fromWire)
    val unit = readString(obj, "unit", path, sink)
    val source = readString(obj, "source", path, sink)
    val script = readString(obj, "script", path, sink, optional = true)
    val date = readString(obj, "date", path, sink)
    if (sink.size != before || value == null || cls == null || unit == null || source == null || date == null) return null
    return ParamEntry(value, cls, unit, source, script, date)
}

/** ParamsFileSchema: { version, generatedAt, params: record<string, entry> }. Unknown top-level keys are ignored. */
internal fun parseParamsFile(element: JsonElement?): ParsedParamsFile {
    val sink = mutableListOf<String>()
    val root = asObject(element, "", sink) ?: return ParsedParamsFile.Bad(sink.toList())
    readString(root, "version", "", sink)
    readString(root, "generatedAt", "", sink)
    val params = asObject(root["params"], "params", sink)
    val entries = params?.entries?.mapNotNull { (name, value) ->
        readEntry(value, childPath("params", name), sink)?.let { name to it }
    }?.toMap()
    if (sink.isNotEmpty() || entries == null) return ParsedParamsFile.Bad(sink.toList())
    return ParsedParamsFile.Ok(entries)
}

internal fun parseParamsText(json: String): ParsedParamsFile {
    val (element, syntaxError) = parseJsonText(json)
    return if (syntaxError != null) ParsedParamsFile.Bad(listOf(syntaxError)) else parseParamsFile(element)
}
