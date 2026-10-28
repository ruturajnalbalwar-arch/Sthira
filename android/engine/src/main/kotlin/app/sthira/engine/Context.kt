package app.sthira.engine

/** Context accepted by [step]. Only [makeContext] can produce one (mirror of context.ts). */
class StepContext internal constructor(
    val geometry: VideoGeometry,
    val calib: Calibration,
    val params: ChairStandParams,
)

sealed interface ContextResult {
    data class Ok(val ctx: StepContext) : ContextResult
    data class Failed(val errors: List<String>) : ContextResult
}

private fun isPositive(value: Double): Boolean = value.isFinite() && value > 0

private fun contextErrors(geometry: VideoGeometry, calib: Calibration, params: ChairStandParams): List<String> =
    buildList {
        if (!isPositive(geometry.width) || !isPositive(geometry.height)) {
            add("geometry: width and height must be positive")
        }
        val finite = listOf(calib.seatedHeadY, calib.standingHeadY, calib.bodyScalePx).all { it.isFinite() }
        if (!finite) add("calibration: values must be finite")
        if (finite && !(calib.seatedHeadY > calib.standingHeadY)) {
            add("calibration: seated head must be below standing head (range > 0)")
        }
        if (!isPositive(calib.bodyScalePx)) add("calibration: body scale must be positive")
        val badParams = ParamName.entries.filter { !params[it].isFinite() }
        if (badParams.isNotEmpty()) {
            add("params: not finite: ${badParams.joinToString(", ") { it.key }}")
        } else {
            addAll(orderingErrors(params).map { "params: $it" })
        }
    }

/** Validate geometry, calibration and params before the reducer may run. */
fun makeContext(geometry: VideoGeometry, calib: Calibration, params: ChairStandParams): ContextResult {
    val errors = contextErrors(geometry, calib, params)
    if (errors.isNotEmpty()) return ContextResult.Failed(errors)
    return ContextResult.Ok(StepContext(geometry, calib, params))
}
