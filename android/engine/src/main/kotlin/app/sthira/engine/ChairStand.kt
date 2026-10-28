package app.sthira.engine

import app.sthira.engine.signal.rateFromCount
import app.sthira.engine.signal.smoothStep

/** The pure chair-stand reducer and its summary (mirror of chairStand.ts). */

/** A person is in view: update the smoothed head signal and advance the state machine. */
private fun stepPresent(
    state: ChairStandState,
    frame: PoseFrame,
    dtRawS: Double,
    dtS: Double,
    ctx: StepContext,
): ChairStandState {
    val raw = headY(frame, ctx.geometry)
    val previous = state.smoothedHeadY
    val smoothed = if (previous == null) raw else smoothStep(previous, raw, dtRawS, ctx.params.smoothingCutoffHz)
    val calib = ctx.calib
    val progress = (calib.seatedHeadY - smoothed) / (calib.seatedHeadY - calib.standingHeadY)
    val rep = state.rep?.let { trackRep(it, frame, dtS, ctx) }
    return advancePhase(state.copy(smoothedHeadY = smoothed, progress = progress, rep = rep), frame, progress, ctx)
}

/** Unseen time inside an open rep counts as low visibility, so a camera stall makes the rep less certain. */
private fun addUnseen(rep: RepTracker?, dtS: Double): RepTracker? =
    rep?.copy(repS = rep.repS + dtS, lowVisibilityS = rep.lowVisibilityS + dtS)

/** No usable person: time advances and an open rep abstains (low visibility); the head signal is untouched. */
private fun stepAbsent(state: ChairStandState, dtS: Double): ChairStandState =
    state.copy(rep = addUnseen(state.rep, dtS), reachSince = null, seatSince = null)

private fun dropFrame(state: ChairStandState): ChairStandState = state.copy(droppedFrames = state.droppedFrames + 1)

/**
 * Pure reducer. Frames with a non-finite or non-increasing t are dropped (and counted).
 * A gap longer than maxGapS resets dwell timers and counts as unseen time for an open rep.
 */
fun step(state: ChairStandState, frame: PoseFrame, ctx: StepContext): ChairStandState {
    val t = frame.t
    if (!t.isFinite()) return dropFrame(state)
    val lastT = state.lastT
    if (lastT != null && t <= lastT) return dropFrame(state)
    val start = state.windowStartT
    if (start != null && t < start) return state
    val windowStartT = start ?: t
    if (t - windowStartT > ctx.params.chairStandDurationS) return state

    val dtRawS = if (lastT == null) 0.0 else t - lastT
    val isGap = dtRawS > ctx.params.maxGapS
    val base = state.copy(
        windowStartT = windowStartT,
        firstT = state.firstT ?: t,
        lastT = t,
        frameCount = state.frameCount + 1,
        gaps = state.gaps + (if (isGap) 1 else 0),
        reachSince = if (isGap) null else state.reachSince,
        seatSince = if (isGap) null else state.seatSince,
        rep = if (isGap) addUnseen(state.rep, dtRawS) else state.rep,
    )
    val dtS = if (isGap) 0.0 else dtRawS
    return if (isUsableFrame(frame)) stepPresent(base, frame, dtRawS, dtS, ctx) else stepAbsent(base, dtS)
}

/**
 * Close the open rep at the last frame (time-up). CDC: a stand more than halfway up at 30 s counts;
 * "halfway" is the fitted timeUpCountFraction, and the credit only applies when the window really ran out.
 * A rep below that fraction is cut off by time, not a half-stand, so it is dropped.
 */
fun finalize(state: ChairStandState, ctx: StepContext): ChairStandState {
    val rep = state.rep ?: return state
    val lastT = state.lastT ?: return state
    val timeUpCredit = isWindowComplete(state, ctx) && (state.progress ?: 0.0) >= ctx.params.timeUpCountFraction
    val reached = rep.standingT != null || timeUpCredit
    if (!reached) return state.copy(phase = Phase.SEATED, rep = null, reachSince = null, seatSince = null)
    return finishRep(state, rep.copy(standingT = rep.standingT ?: lastT), lastT, ctx)
}

/** The last frame lies within one maxGapS of the end of the timed window. */
fun isWindowComplete(state: ChairStandState, ctx: StepContext): Boolean {
    val start = state.windowStartT ?: return false
    val lastT = state.lastT ?: return false
    return lastT - start >= ctx.params.chairStandDurationS - ctx.params.maxGapS
}

fun summarize(state: ChairStandState, ctx: StepContext): ChairStandSummary {
    val closed = finalize(state, ctx)
    val events = closed.events
    val count = { kind: RepKind -> events.count { it.kind == kind } }
    val firstT = closed.firstT
    val lastT = closed.lastT
    val fps = if (firstT == null || lastT == null) 0.0 else rateFromCount(closed.frameCount, lastT - firstT)
    val uncertain = count(RepKind.UNCERTAIN)
    return ChairStandSummary(
        full = count(RepKind.FULL),
        half = count(RepKind.HALF),
        armAssisted = count(RepKind.ARM_ASSISTED),
        uncertain = uncertain,
        events = events,
        effectiveFps = fps,
        lowFps = fps < ctx.params.minFps,
        unreliable = events.isNotEmpty() &&
            uncertain.toDouble() / events.size > ctx.params.unreliableUncertainFraction,
        droppedFrames = closed.droppedFrames,
        gaps = closed.gaps,
        windowComplete = isWindowComplete(state, ctx),
    )
}
