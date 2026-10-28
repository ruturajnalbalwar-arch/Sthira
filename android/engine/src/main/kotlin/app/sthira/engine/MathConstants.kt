package app.sthira.engine

import kotlin.math.abs

/** Named math constants and helpers (mirror of mathConstants.ts). */
internal const val HALF = 0.5
internal const val TAU = Math.PI * 2

internal fun midpoint(a: Double, b: Double): Double = a + (b - a) * HALF

/**
 * JavaScript's Math.hypot for two values, with V8's exact algorithm (scale by the max, Kahan sum,
 * sqrt, rescale), so distances match the TypeScript engine bit for bit. java.lang.Math.hypot uses a
 * different algorithm and can differ in the last bit.
 */
internal fun jsHypot(a: Double, b: Double): Double {
    if (a.isNaN() || b.isNaN()) {
        return if (a.isInfinite() || b.isInfinite()) Double.POSITIVE_INFINITY else Double.NaN
    }
    val absValues = doubleArrayOf(abs(a), abs(b))
    val max = maxOf(absValues[0], absValues[1])
    if (max == Double.POSITIVE_INFINITY) return Double.POSITIVE_INFINITY
    if (max == 0.0) return 0.0
    val (sum, _) = absValues.fold(0.0 to 0.0) { (sum, compensation), value ->
        val n = value / max
        val summand = n * n - compensation
        val preliminary = sum + summand
        preliminary to ((preliminary - sum) - summand)
    }
    return StrictMath.sqrt(sum) * max
}
