package app.sthira.engine

/** Rep tracking and the SEATED -> RISING -> STANDING phase machine (mirror of chairStand.ts). */

internal fun freshRep(startT: Double): RepTracker =
    RepTracker(startT = startT, repS = 0.0, lowVisibilityS = 0.0, armsViolationS = 0.0, standingT = null)

internal fun trackRep(rep: RepTracker, frame: PoseFrame, dtS: Double, ctx: StepContext): RepTracker {
    val lowVisibility = keyVisibility(frame) < ctx.params.visibilityMin
    val ratio = armsCrossedRatio(frame, ctx.geometry)
    val uncrossed = !lowVisibility && ratio > ctx.params.armsCrossedMaxRatio
    return rep.copy(
        repS = rep.repS + dtS,
        lowVisibilityS = rep.lowVisibilityS + (if (lowVisibility) dtS else 0.0),
        armsViolationS = rep.armsViolationS + (if (uncrossed) dtS else 0.0),
    )
}

private fun classify(rep: RepTracker, params: ChairStandParams): RepKind {
    val lowFraction = if (rep.repS > 0) rep.lowVisibilityS / rep.repS else 0.0
    if (lowFraction > params.uncertainFrameMaxFraction) return RepKind.UNCERTAIN
    if (rep.armsViolationS >= params.armsViolationMinS) return RepKind.ARM_ASSISTED
    return if (rep.standingT == null) RepKind.HALF else RepKind.FULL
}

internal fun finishRep(state: ChairStandState, rep: RepTracker, endT: Double, ctx: StepContext): ChairStandState {
    val event = RepEvent(kind = classify(rep, ctx.params), startT = rep.startT, standingT = rep.standingT, endT = endT)
    return state.copy(phase = Phase.SEATED, rep = null, reachSince = null, seatSince = null, events = state.events + event)
}

private fun checkSeatReturn(state: ChairStandState, p: Double, t: Double, ctx: StepContext): ChairStandState {
    val rep = state.rep ?: return state
    if (p > ctx.params.seatFraction) return state.copy(seatSince = null)
    val since = state.seatSince ?: t
    if (t - since >= ctx.params.minSitDwellS) return finishRep(state, rep, t, ctx)
    return state.copy(seatSince = since)
}

private fun checkReach(state: ChairStandState, p: Double, t: Double, ctx: StepContext): ChairStandState {
    val rep = state.rep ?: return state
    if (p < ctx.params.reachFraction) return state.copy(reachSince = null)
    val since = state.reachSince ?: t
    if (t - since < ctx.params.minStandDwellS) return state.copy(reachSince = since)
    return state.copy(phase = Phase.STANDING, reachSince = null, seatSince = null, rep = rep.copy(standingT = t))
}

internal fun advancePhase(state: ChairStandState, frame: PoseFrame, p: Double, ctx: StepContext): ChairStandState =
    when (state.phase) {
        Phase.SEATED ->
            if (p <= ctx.params.leaveSeatFraction) {
                state
            } else {
                state.copy(phase = Phase.RISING, rep = trackRep(freshRep(frame.t), frame, 0.0, ctx))
            }
        Phase.RISING -> {
            val reached = checkReach(state, p, frame.t, ctx)
            if (reached.phase == Phase.RISING) checkSeatReturn(reached, p, frame.t, ctx) else reached
        }
        Phase.STANDING -> checkSeatReturn(state, p, frame.t, ctx)
    }
