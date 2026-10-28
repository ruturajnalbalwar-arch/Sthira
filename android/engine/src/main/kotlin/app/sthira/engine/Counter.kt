package app.sthira.engine

import kotlinx.serialization.json.JsonElement

/** Mirror of counter.ts: the engine only runs on a fully tuned, valid params.json. */
sealed interface CounterResult {
    data class Ready(val params: ChairStandParams) : CounterResult {
        fun makeContext(geometry: VideoGeometry, calib: Calibration): ContextResult =
            app.sthira.engine.makeContext(geometry, calib, params)
    }

    data class Untuned(val missing: List<String>) : CounterResult
    data class Invalid(val errors: List<String>) : CounterResult
}

private fun toCounter(loaded: LoadResult): CounterResult = when (loaded) {
    is LoadResult.Ready -> CounterResult.Ready(loaded.params)
    is LoadResult.Untuned -> CounterResult.Untuned(loaded.missing)
    is LoadResult.Invalid -> CounterResult.Invalid(loaded.errors)
}

fun createCounter(paramsJson: String): CounterResult = toCounter(loadParams(paramsJson))

fun createCounter(paramsJson: JsonElement): CounterResult = toCounter(loadParams(paramsJson))
