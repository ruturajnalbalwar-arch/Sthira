package app.sthira.engine.timers

/** Mirror of web/src/lib/timers/tug.ts. */
enum class TugVerdict(val wire: String) { BELOW("below"), AT_OR_ABOVE("at-or-above") }

/** Compares a TUG time with the single CDC reference value for older adults. */
fun tugVerdict(seconds: Double, norms: TugNorms): TugVerdict =
    if (seconds >= norms.atRiskIfAtLeastS) TugVerdict.AT_OR_ABOVE else TugVerdict.BELOW
