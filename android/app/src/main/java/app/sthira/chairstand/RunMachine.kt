package app.sthira.chairstand

import app.sthira.engine.CounterResult
import app.sthira.engine.PoseFrame
import app.sthira.engine.VideoGeometry
import app.sthira.pose.FpsMeter
import app.sthira.pose.FrameClock
import app.sthira.pose.PoseMapper
import app.sthira.pose.PoseSample

/**
 * State of one chair-stand run, from camera frames and clock ticks. Run time t = 0 is the first
 * frame after Start in which a person is detected, so the standing calibration span begins exactly on
 * a frame that has the person in it (an empty room is never calibrated).
 */
data class RunState(
    val clock: FrameClock = FrameClock(),
    val originNs: Long? = null,
    val startRequested: Boolean = false,
    val phase: PhaseState = PhaseMachine.INITIAL,
    val live: LiveCountState = LiveCount.INITIAL,
    val fps: FpsMeter = FpsMeter(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val personDetected: Boolean = false,
    val issues: Set<CaptureIssue> = emptySet(),
    /** Run time of the last tick or frame (seconds). */
    val nowT: Double = 0.0,
) {
    val isRunning: Boolean get() = startRequested || phase.stage.isActive
    val geometry: VideoGeometry get() = VideoGeometry(imageWidth.toDouble(), imageHeight.toDouble())
}

/** Pure transitions for a run. Times in nanoseconds are on the elapsedRealtime clock. */
class RunMachine(private val config: PhaseConfig, private val counter: CounterResult) {

    fun requestStart(s: RunState): RunState =
        if (s.phase.stage == Stage.Idle && !s.startRequested) s.copy(startRequested = true, issues = emptySet()) else s

    fun onFrame(s: RunState, sample: PoseSample, nowNs: Long): RunState {
        val mapped = s.clock.map(sample.captureNs, nowNs)
        val realtimeNs = mapped.realtimeNs ?: return s.copy(clock = mapped.clock)
        val landmarks = PoseMapper.toLandmarks(sample.person, unmirror = sample.mirrored)
        val seen = s.copy(
            clock = mapped.clock,
            fps = s.fps.add(realtimeNs / NANOS),
            imageWidth = sample.imageWidth,
            imageHeight = sample.imageHeight,
            personDetected = landmarks.isNotEmpty(),
        )
        // After Start, calibration begins only on a frame where the landmarker found a person.
        val started = if (seen.startRequested && seen.personDetected) begin(seen, realtimeNs) else seen
        val origin = started.originNs
        if (origin == null || !started.phase.stage.isActive) return started
        val t = (realtimeNs - origin) / NANOS
        val ticked = apply(started, PhaseEvent.Tick(t)).copy(nowT = t)
        if (!ticked.phase.stage.isActive) return ticked
        return ticked.copy(live = LiveCount.onFrame(ticked.live, PoseFrame(t, landmarks), counter))
    }

    fun onTick(s: RunState, nowNs: Long): RunState {
        val t = runTime(s, nowNs) ?: return s
        return apply(s, PhaseEvent.Tick(t)).copy(nowT = maxOf(s.nowT, t))
    }

    fun stop(s: RunState, nowNs: Long): RunState = timed(s, nowNs) { PhaseEvent.Stop(it) }

    fun redoCalibration(s: RunState, nowNs: Long): RunState = timed(s, nowNs) { PhaseEvent.RedoCalibration(it) }

    /** Ends an active run early (camera lost, app hidden) and records why. */
    fun interrupt(s: RunState, nowNs: Long, issue: CaptureIssue): RunState {
        if (s.startRequested && s.originNs == null) return s.copy(startRequested = false, issues = s.issues + issue)
        if (!s.phase.stage.isActive) return s
        return timed(s.copy(issues = s.issues + issue), nowNs) { PhaseEvent.Interrupt(it) }
    }

    /** Back to idle, keeping the camera clock and live stats. */
    fun cancel(s: RunState): RunState = idle(s)

    fun reset(s: RunState): RunState = idle(s)

    private fun idle(s: RunState): RunState = RunState(
        clock = s.clock, fps = s.fps, imageWidth = s.imageWidth, imageHeight = s.imageHeight, personDetected = s.personDetected,
    )

    private fun begin(s: RunState, realtimeNs: Long): RunState =
        apply(s.copy(startRequested = false, originNs = realtimeNs, nowT = 0.0), PhaseEvent.Start(0.0))

    private fun runTime(s: RunState, nowNs: Long): Double? {
        val origin = s.originNs ?: return null
        return maxOf(0.0, (nowNs - origin) / NANOS)
    }

    private fun timed(s: RunState, nowNs: Long, event: (Double) -> PhaseEvent): RunState {
        val t = maxOf(runTime(s, nowNs) ?: return s, s.nowT)
        return apply(s, event(t)).copy(nowT = t)
    }

    private fun apply(s: RunState, event: PhaseEvent): RunState {
        val next = PhaseMachine.reduce(s.phase, event, config)
        if (next == s.phase) return s
        return s.copy(phase = next, live = LiveCount.onPhase(s.live, next, s.geometry, counter))
    }

    private companion object {
        const val NANOS = 1_000_000_000.0
    }
}
