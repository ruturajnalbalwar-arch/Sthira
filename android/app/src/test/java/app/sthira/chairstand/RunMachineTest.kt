package app.sthira.chairstand

import app.sthira.engine.CounterResult
import app.sthira.pose.PoseSample
import app.sthira.pose.RawLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunMachineTest {
    private val config = PhaseConfig(calibrationStandS = 5.0, calibrationSitS = 5.0, testDurationS = 30.0)
    private val machine = RunMachine(config, CounterResult.Untuned(listOf("reachFraction")))
    private val person = List(33) { RawLandmark(0.3f, 0.5f, 0f, 0.9f) }
    private val ms = 1_000_000L

    private fun sample(captureMs: Long, who: List<RawLandmark>? = person) =
        PoseSample(captureMs * ms, who, 480, 640, mirrored = false)

    @Test
    fun `the first frame after Start is run time zero and opens standing calibration`() {
        val requested = machine.requestStart(RunState())
        assertTrue(requested.isRunning)
        val out = machine.onFrame(requested, sample(1_000), nowNs = 5_000 * ms)
        assertEquals(Stage.CalibrationStand, out.phase.stage)
        assertEquals(0.0, out.nowT, 0.0)
        assertEquals(listOf(0.0), out.live.calibrationFrames.map { it.t })
        assertEquals(33, out.live.calibrationFrames.single().landmarks.size)
    }

    @Test
    fun `after Start, calibration waits for a frame with a person and tracks live meanwhile`() {
        var s = machine.requestStart(RunState())
        s = machine.onFrame(s, sample(0, who = null), nowNs = 0)
        assertEquals(Stage.Idle, s.phase.stage)
        assertTrue(s.startRequested)
        assertFalse(s.personDetected)
        assertNull(s.originNs)
        // Ticks do not start the countdown while nobody is in view.
        s = machine.onTick(s, 8_000 * ms)
        assertEquals(Stage.Idle, s.phase.stage)
        s = machine.onFrame(s, sample(9_000), nowNs = 9_000 * ms)
        assertEquals(Stage.CalibrationStand, s.phase.stage)
        assertFalse(s.startRequested)
        assertEquals(9_000 * ms, s.originNs)
        s = machine.onTick(s, 15_000 * ms)
        assertEquals(Stage.CalibrationSit, s.phase.stage)
    }

    @Test
    fun `a person leaving after calibration began does not stop the run`() {
        var s = machine.requestStart(RunState())
        s = machine.onFrame(s, sample(0), nowNs = 0)
        s = machine.onFrame(s, sample(100, who = null), nowNs = 100 * ms)
        assertEquals(Stage.CalibrationStand, s.phase.stage)
        assertFalse(s.personDetected)
    }

    @Test
    fun `an interrupt while waiting for a person cancels the request`() {
        val waiting = machine.onFrame(machine.requestStart(RunState()), sample(0, who = null), nowNs = 0)
        val s = machine.interrupt(waiting, 1_000 * ms, CaptureIssue.AppHidden)
        assertFalse(s.isRunning)
        assertEquals(setOf(CaptureIssue.AppHidden), s.issues)
    }

    @Test
    fun `frames before Start only update live stats`() {
        val out = machine.onFrame(RunState(), sample(0, who = null), nowNs = 0)
        assertEquals(Stage.Idle, out.phase.stage)
        assertTrue(out.live.calibrationFrames.isEmpty())
        assertFalse(out.personDetected)
        assertEquals(640, out.imageHeight)
    }

    @Test
    fun `ticks advance stand, sit, test and auto-stop at the protocol length`() {
        var s = machine.requestStart(RunState())
        s = machine.onFrame(s, sample(0), nowNs = 0)
        s = machine.onTick(s, 6_000 * ms)
        assertEquals(Stage.CalibrationSit, s.phase.stage)
        s = machine.onTick(s, 11_000 * ms)
        assertEquals(Stage.Test, s.phase.stage)
        s = machine.onTick(s, 41_000 * ms)
        assertEquals(Stage.Done, s.phase.stage)
        assertEquals(40.0, s.phase.endT ?: 0.0, 1e-9)
        assertEquals(CountSetup.NotTuned, s.live.setup)
    }

    @Test
    fun `repeated capture timestamps are dropped`() {
        var s = machine.requestStart(RunState())
        s = machine.onFrame(s, sample(0), nowNs = 0)
        val again = machine.onFrame(s, sample(0), nowNs = 10 * ms)
        assertEquals(1, again.live.calibrationFrames.size)
        assertEquals(s.fps, again.fps)
    }

    @Test
    fun `an interrupt ends the run and keeps the issue`() {
        var s = machine.requestStart(RunState())
        s = machine.onFrame(s, sample(0), nowNs = 0)
        s = machine.interrupt(s, 2_000 * ms, CaptureIssue.CameraEnded)
        assertEquals(Stage.Done, s.phase.stage)
        assertEquals(setOf(CaptureIssue.CameraEnded), s.issues)
        val idle = machine.reset(s)
        assertEquals(Stage.Idle, idle.phase.stage)
        assertTrue(idle.issues.isEmpty())
    }

    @Test
    fun `stop during the test ends it early`() {
        var s = machine.requestStart(RunState())
        s = machine.onFrame(s, sample(0), nowNs = 0)
        s = machine.onTick(s, 12_000 * ms)
        s = machine.stop(s, 15_000 * ms)
        assertEquals(Stage.Done, s.phase.stage)
        assertEquals(15.0, s.phase.endT ?: 0.0, 1e-9)
    }
}
