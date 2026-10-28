package app.sthira.chairstand

import app.sthira.engine.CounterResult
import app.sthira.pose.PoseSample
import app.sthira.pose.RawLandmark
import app.sthira.record.CaptureIssue
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
        assertEquals(Stage.CalibrationStand, out.state.phase.stage)
        assertEquals(0.0, out.frame?.t ?: -1.0, 0.0)
        assertEquals(33, out.frame?.landmarks?.size)
    }

    @Test
    fun `frames before Start are not recorded but still update live stats`() {
        val out = machine.onFrame(RunState(), sample(0, who = null), nowNs = 0)
        assertNull(out.frame)
        assertFalse(out.state.personDetected)
        assertEquals(640, out.state.imageHeight)
    }

    @Test
    fun `ticks advance stand, sit, test and auto-stop at the protocol length`() {
        var s = machine.requestStart(RunState())
        s = machine.onFrame(s, sample(0), nowNs = 0).state
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
        s = machine.onFrame(s, sample(0), nowNs = 0).state
        val again = machine.onFrame(s, sample(0), nowNs = 10 * ms)
        assertNull(again.frame)
    }

    @Test
    fun `an interrupt ends the run and keeps the issue`() {
        var s = machine.requestStart(RunState())
        s = machine.onFrame(s, sample(0), nowNs = 0).state
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
        s = machine.onFrame(s, sample(0), nowNs = 0).state
        s = machine.onTick(s, 12_000 * ms)
        s = machine.stop(s, 15_000 * ms)
        assertEquals(Stage.Done, s.phase.stage)
        assertEquals(15.0, s.phase.endT ?: 0.0, 1e-9)
    }
}
