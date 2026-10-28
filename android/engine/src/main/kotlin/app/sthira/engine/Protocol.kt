package app.sthira.engine

import kotlinx.serialization.json.JsonElement

/** Protocol constants are available before any parameter is fitted (mirror of params/protocol.ts). */
val PROTOCOL_CONSTANT_NAMES: List<ParamName> =
    listOf(ParamName.CHAIR_STAND_DURATION_S, ParamName.CALIBRATION_SIT_S, ParamName.CALIBRATION_STAND_S)

data class ProtocolConstants(
    val chairStandDurationS: Double,
    val calibrationSitS: Double,
    val calibrationStandS: Double,
)

sealed interface ProtocolResult {
    data class Ok(val constants: ProtocolConstants) : ProtocolResult
    data class Failed(val errors: List<String>) : ProtocolResult
}

private const val SECONDS_UNIT = "s"

private fun protocolError(name: ParamName, entry: ParamEntry?): String? = when {
    entry == null -> "${name.key}: missing"
    entry.paramClass != ParamClass.PROTOCOL_CONSTANT -> "${name.key}: must be a protocol-constant"
    entry.unit != SECONDS_UNIT || entry.value <= 0 -> "${name.key}: must be a positive number of seconds"
    else -> null
}

private fun readProtocol(parsed: ParsedParamsFile): ProtocolResult {
    val entries = when (parsed) {
        is ParsedParamsFile.Bad -> return ProtocolResult.Failed(parsed.errors)
        is ParsedParamsFile.Ok -> parsed.entries
    }
    val errors = PROTOCOL_CONSTANT_NAMES.mapNotNull { protocolError(it, entries[it.key]) }
    if (errors.isNotEmpty()) return ProtocolResult.Failed(errors)
    val value = { name: ParamName -> entries.getValue(name.key).value }
    return ProtocolResult.Ok(
        ProtocolConstants(
            value(ParamName.CHAIR_STAND_DURATION_S),
            value(ParamName.CALIBRATION_SIT_S),
            value(ParamName.CALIBRATION_STAND_S),
        ),
    )
}

/** Read the protocol constants from a params.json text. Never throws. */
fun readProtocolConstants(json: String): ProtocolResult = readProtocol(parseParamsText(json))

/** Read the protocol constants from a parsed params.json. Never throws. */
fun readProtocolConstants(element: JsonElement): ProtocolResult = readProtocol(parseParamsFile(element))
