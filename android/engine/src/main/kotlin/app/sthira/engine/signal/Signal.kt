package app.sthira.engine.signal

import app.sthira.engine.HALF
import app.sthira.engine.TAU
import app.sthira.engine.midpoint

/** Mirror of signal/smoothing.ts and signal/stats.ts, same operation order as the TypeScript. */

/** One exponential low-pass step; alpha = 1 - exp(-dt/tau) is exact for irregular dt. */
internal fun smoothStep(previous: Double, value: Double, dtS: Double, cutoffHz: Double): Double {
    require(cutoffHz > 0) { "cutoffHz must be > 0" }
    if (!(dtS > 0)) return previous
    val tau = 1 / (TAU * cutoffHz)
    // StrictMath.exp is fdlibm, the same algorithm V8 uses for Math.exp.
    val alpha = 1 - StrictMath.exp(-dtS / tau)
    return previous + alpha * (value - previous)
}

internal fun mean(values: List<Double>): Double {
    if (values.isEmpty()) return Double.NaN
    return values.fold(0.0) { sum, v -> sum + v } / values.size
}

internal fun median(values: List<Double>): Double {
    if (values.isEmpty()) return Double.NaN
    val sorted = values.sortedWith { a, b -> a.compareTo(b) }
    val half = sorted.size * HALF
    val mid = Math.floor(half).toInt()
    val isEven = half == Math.floor(half)
    return if (isEven) midpoint(sorted[mid - 1], sorted[mid]) else sorted[mid]
}

/** Population standard deviation. */
internal fun std(values: List<Double>): Double {
    val m = mean(values)
    return StrictMath.sqrt(mean(values.map { (it - m) * (it - m) }))
}

/** Frames per second from a frame count and the time they span. */
internal fun rateFromCount(count: Int, spanS: Double): Double =
    if (count > 1 && spanS > 0) (count - 1) / spanS else 0.0
