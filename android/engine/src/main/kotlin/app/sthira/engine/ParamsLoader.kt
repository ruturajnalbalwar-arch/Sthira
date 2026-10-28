package app.sthira.engine

import kotlinx.serialization.json.JsonElement

/** Mirror of params/loader.ts. */
sealed interface LoadResult {
    data class Ready(val params: ChairStandParams) : LoadResult
    data class Untuned(val missing: List<String>) : LoadResult
    data class Invalid(val errors: List<String>) : LoadResult
}

private const val FRACTION_UNIT = "fraction"

private fun entryErrors(name: ParamName, entry: ParamEntry): List<String> = buildList {
    val key = name.key
    if (entry.paramClass != name.requiredClass) {
        add("$key: class must be ${name.requiredClass.wire}, got ${entry.paramClass.wire}")
    }
    if (entry.unit != name.unit) add("$key: unit must be ${name.unit}, got ${entry.unit}")
    if (entry.value < 0) add("$key: value must be >= 0")
    if (name.unit == FRACTION_UNIT && entry.value > 1) add("$key: a fraction must be <= 1")
    if (entry.paramClass == ParamClass.FITTED && entry.script == null) {
        add("$key: fitted values must name the script that produced them")
    }
}

/** Cross-parameter consistency rules; also applied by makeContext to hand-built params. */
fun orderingErrors(v: ChairStandParams): List<String> {
    val rules = listOf(
        (v.seatFraction > v.leaveSeatFraction) to "seatFraction must be <= leaveSeatFraction",
        (v.leaveSeatFraction >= v.reachFraction) to "leaveSeatFraction must be below reachFraction",
        (v.seatFraction >= v.reachFraction) to "seatFraction must be below reachFraction",
        (v.reachFraction <= 0) to "reachFraction must be > 0",
        (v.uncertainFrameMaxFraction >= 1) to "uncertainFrameMaxFraction must be < 1",
        (v.unreliableUncertainFraction >= 1) to "unreliableUncertainFraction must be < 1",
        (v.timeUpCountFraction < v.seatFraction || v.timeUpCountFraction > 1) to
            "timeUpCountFraction must be between seatFraction and 1",
        (v.smoothingCutoffHz <= 0) to "smoothingCutoffHz must be > 0",
        (v.maxGapS <= 0) to "maxGapS must be > 0",
        (v.minFps <= 0) to "minFps must be > 0",
        (v.maxGapS * v.minFps <= 1) to "maxGapS must be longer than one frame at minFps",
    )
    return rules.filter { it.first }.map { it.second }
}

private fun loadParsed(parsed: ParsedParamsFile): LoadResult {
    val entries = when (parsed) {
        is ParsedParamsFile.Bad -> return LoadResult.Invalid(parsed.errors)
        is ParsedParamsFile.Ok -> parsed.entries
    }
    val errors = entries.flatMap { (key, entry) ->
        ParamName.fromKey(key)?.let { entryErrors(it, entry) } ?: emptyList()
    }
    if (errors.isNotEmpty()) return LoadResult.Invalid(errors)

    val missing = ParamName.entries.filter { !entries.containsKey(it.key) }.map { it.key }
    if (missing.isNotEmpty()) return LoadResult.Untuned(missing)

    val values = ChairStandParams.fromMap(ParamName.entries.associateWith { entries.getValue(it.key).value })
        ?: return LoadResult.Untuned(missing)
    val ordering = orderingErrors(values)
    if (ordering.isNotEmpty()) return LoadResult.Invalid(ordering)
    return LoadResult.Ready(values)
}

/** Validate a params.json text. Never throws on bad input. */
fun loadParams(json: String): LoadResult = loadParsed(parseParamsText(json))

/** Validate a parsed params.json. Never throws on bad input. */
fun loadParams(element: JsonElement): LoadResult = loadParsed(parseParamsFile(element))
