package app.sthira.chairstand

import app.sthira.engine.CounterResult
import app.sthira.engine.ProtocolConstants
import app.sthira.engine.ProtocolResult
import app.sthira.engine.createCounter
import app.sthira.engine.norms.NormsParseResult
import app.sthira.engine.norms.parseChairStandNorms
import app.sthira.engine.readProtocolConstants

/** Everything the chair-stand screen reads from params.json and norms.json. Nothing is defaulted. */
data class ChairStandConfig(
    val protocol: ProtocolConstants,
    val counter: CounterResult,
    val norms: NormsParseResult,
) {
    val phaseConfig: PhaseConfig
        get() = PhaseConfig(protocol.calibrationStandS, protocol.calibrationSitS, protocol.chairStandDurationS)

    fun normChoice(session: Session): NormChoice = NormChooser.choose(norms, session.ageBand, session.sex)

    val normsNote: String?
        get() = (norms as? NormsParseResult.Ok)?.norms?.note
}

sealed interface ConfigLoad {
    data class Ok(val config: ChairStandConfig) : ConfigLoad
    /** The protocol durations are missing or invalid, so the test cannot be timed at all. */
    data class Unavailable(val errors: List<String>) : ConfigLoad
}

object ChairStandConfigLoader {
    fun load(paramsJson: String, normsJson: String): ConfigLoad = when (val protocol = readProtocolConstants(paramsJson)) {
        is ProtocolResult.Failed -> ConfigLoad.Unavailable(protocol.errors)
        is ProtocolResult.Ok -> ConfigLoad.Ok(
            ChairStandConfig(protocol.constants, createCounter(paramsJson), parseChairStandNorms(normsJson)),
        )
    }
}
