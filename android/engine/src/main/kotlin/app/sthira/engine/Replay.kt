package app.sthira.engine

/**
 * Frames covering [startT, endT]: those inside it plus the nearest frame on each side (mirror of
 * coveringFrames in replay.ts, used by the web live counter and the fitter to pick calibration frames).
 * Phase boundaries sit at exact protocol times, which rarely coincide with a frame, so frames strictly
 * inside a 5 s phase span less than 5 s and the calibration duration gate would always fail.
 */
fun coveringFrames(frames: List<PoseFrame>, startT: Double, endT: Double): List<PoseFrame> {
    val before = frames.indexOfLast { it.t <= startT }
    val firstInside = frames.indexOfFirst { it.t >= startT }
    val from = if (before >= 0) before else firstInside
    val after = frames.indexOfFirst { it.t >= endT }
    val to = if (after >= 0) after else frames.size - 1
    // JS slice(from, to + 1) is empty when the end is not past the start.
    return if (from < 0 || to + 1 <= from) emptyList() else frames.subList(from, to + 1).toList()
}
