package app.sthira.engine.norms

import app.sthira.engine.json.Sink
import app.sthira.engine.json.asArray
import app.sthira.engine.json.asObject
import app.sthira.engine.json.childPath
import app.sthira.engine.json.parseJsonText
import app.sthira.engine.json.readEnum
import app.sthira.engine.json.readInt
import app.sthira.engine.json.readString
import kotlinx.serialization.json.JsonElement

/** Mirror of web/src/lib/count/norms.ts: the CDC 30-second chair-stand table from norms.json. */
enum class NormSex(val wire: String, val label: String) {
    FEMALE("female", "Female"),
    MALE("male", "Male"),
    NOT_RECORDED("not-recorded", "Not recorded"),
}

data class NormSource(val url: String, val title: String)

data class ChairStandNormEntry(
    val sex: NormSex,
    val ageMin: Int,
    val ageMax: Int,
    val belowAverageIfLessThan: Int,
    val source: NormSource,
)

data class ChairStandNorms(val note: String, val entries: List<ChairStandNormEntry>)

sealed interface NormsParseResult {
    data class Ok(val norms: ChairStandNorms) : NormsParseResult
    data class Failed(val errors: List<String>) : NormsParseResult
}

private val ENTRY_SEXES = listOf(NormSex.FEMALE, NormSex.MALE)

private fun readSource(element: JsonElement?, path: String, sink: Sink): NormSource? {
    val obj = asObject(element, path, sink) ?: return null
    val url = readString(obj, "url", path, sink)
    val title = readString(obj, "title", path, sink)
    return if (url != null && title != null) NormSource(url, title) else null
}

private fun readEntry(element: JsonElement?, path: String, sink: Sink): ChairStandNormEntry? {
    val obj = asObject(element, path, sink) ?: return null
    val sex = readEnum(obj, "sex", path, sink, ENTRY_SEXES.map { it.wire })
        ?.let { wire -> ENTRY_SEXES.firstOrNull { it.wire == wire } }
    val ageMin = readInt(obj, "ageMin", path, sink)
    val ageMax = readInt(obj, "ageMax", path, sink)
    val cutoff = readInt(obj, "belowAverageIfLessThan", path, sink, nonNegative = true)
    val source = readSource(obj["source"], childPath(path, "source"), sink)
    if (sex == null || ageMin == null || ageMax == null || cutoff == null || source == null) return null
    return ChairStandNormEntry(sex, ageMin, ageMax, cutoff, source)
}

/** Validates norms.json (external data) and keeps only the chair-stand table. Never throws. */
fun parseChairStandNorms(element: JsonElement): NormsParseResult {
    val sink = mutableListOf<String>()
    val root = asObject(element, "", sink) ?: return NormsParseResult.Failed(sink.toList())
    val note = readString(root, "note", "", sink)
    val table = asObject(root["chairStand30s"], "chairStand30s", sink)
    val path = "chairStand30s.entries"
    val array = table?.let { asArray(it["entries"], path, sink, minSize = 1) }
    val entries = array?.mapIndexedNotNull { i, e -> readEntry(e, childPath(path, i.toString()), sink) }
    if (sink.isNotEmpty() || note == null || entries == null) return NormsParseResult.Failed(sink.toList())
    return NormsParseResult.Ok(ChairStandNorms(note, entries))
}

fun parseChairStandNorms(json: String): NormsParseResult {
    val (element, syntaxError) = parseJsonText(json)
    if (element == null) return NormsParseResult.Failed(listOfNotNull(syntaxError))
    return parseChairStandNorms(element)
}

sealed interface NormLookup {
    data class Available(val entry: ChairStandNormEntry) : NormLookup
    data class NotAvailable(val reason: String) : NormLookup
}

/** max is null for an open-ended band such as "80+". */
data class AgeRange(val min: Double, val max: Double?)

private val CLOSED_BAND = Regex("""^(\d+)-(\d+)$""")
private val OPEN_BAND = Regex("""^(\d+)\+$""")

/** Reads "60-64" or "80+" (the session form's age bands). Null for anything else. */
fun parseAgeBand(band: String): AgeRange? {
    CLOSED_BAND.matchEntire(band)?.let { m ->
        return AgeRange(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())
    }
    val open = OPEN_BAND.matchEntire(band) ?: return null
    return AgeRange(open.groupValues[1].toDouble(), null)
}

private fun within(entry: ChairStandNormEntry, range: AgeRange): Boolean =
    entry.ageMin >= range.min && (range.max == null || entry.ageMax <= range.max)

private fun bandText(entry: ChairStandNormEntry): String = "${entry.ageMin}-${entry.ageMax}"

/**
 * The CDC cut-off for this age band and sex. Only an exact CDC 5-year band matches: a band that
 * spans several CDC rows (e.g. 80+) has more than one cut-off, so no single value is picked.
 */
fun lookupChairStandNorm(norms: ChairStandNorms, ageBand: String, sex: NormSex): NormLookup {
    if (sex == NormSex.NOT_RECORDED) {
        return NormLookup.NotAvailable("The CDC table gives separate values for women and men, and sex was not recorded.")
    }
    val range = parseAgeBand(ageBand)
        ?: return NormLookup.NotAvailable("The age band \"$ageBand\" is not one the CDC table uses.")
    val matches = norms.entries.filter { it.sex == sex && within(it, range) }
    if (matches.isEmpty()) return NormLookup.NotAvailable("The CDC table has no value for age $ageBand.")
    val exact = matches.firstOrNull { it.ageMin.toDouble() == range.min && it.ageMax.toDouble() == range.max }
    if (exact != null) return NormLookup.Available(exact)
    return NormLookup.NotAvailable(
        "Age $ageBand covers several CDC age bands (${matches.joinToString(", ", transform = ::bandText)}) " +
            "with their own values; no single value applies.",
    )
}
