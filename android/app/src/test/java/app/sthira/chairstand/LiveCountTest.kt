package app.sthira.chairstand

import app.sthira.engine.CounterResult
import app.sthira.engine.Landmark
import app.sthira.engine.PoseFrame
import app.sthira.engine.VideoGeometry
import app.sthira.engine.createCounter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveCountTest {
    private val config = PhaseConfig(calibrationStandS = 5.0, calibrationSitS = 5.0, testDurationS = 30.0)
    private val geometry = VideoGeometry(480.0, 640.0)
    private val untuned = CounterResult.Untuned(listOf("reachFraction"))
    private val nobody = { t: Double -> PoseFrame(t, emptyList()) }

    /** A tuned test fixture from the shared golden file (not the app's params.json). */
    private fun tunedCounter(): CounterResult {
        val path = requireNotNull(System.getProperty("sthira.golden")) { "sthira.golden not set" }
        val root = Json.parseToJsonElement(File(path).readText()).jsonObject
        val complete = root.getValue("loader").jsonArray.first { it.jsonObject.getValue("name").jsonPrimitive.content == "complete" }
        return createCounter(complete.jsonObject.getValue("json"))
    }

    private class Run(val counter: CounterResult, val config: PhaseConfig, val geometry: VideoGeometry) {
        var phase = PhaseMachine.INITIAL
        var live = LiveCount.INITIAL
        fun event(e: PhaseEvent) {
            phase = PhaseMachine.reduce(phase, e, config)
            live = LiveCount.onPhase(live, phase, geometry, counter)
        }
        fun frame(f: PoseFrame) {
            event(PhaseEvent.Tick(f.t))
            live = LiveCount.onFrame(live, f, counter)
        }
    }

    private fun framesUntil(endT: Double, run: Run, make: (Double) -> PoseFrame) {
        val last = Math.round(endT * 10).toInt()
        for (i in 0..last) run.frame(make(i / 10.0))
    }

    @Test
    fun `an untuned counter never counts but the flow still runs`() {
        val run = Run(untuned, config, geometry)
        run.event(PhaseEvent.Start(0.0))
        framesUntil(12.0, run, nobody)
        assertEquals(CountSetup.NotTuned, run.live.setup)
        assertEquals(null, LiveCount.liveSummary(run.live))
    }

    @Test
    fun `calibration frames are collected across stand and sit`() {
        val run = Run(untuned, config, geometry)
        run.event(PhaseEvent.Start(0.0))
        framesUntil(9.0, run, nobody)
        assertEquals(Stage.CalibrationSit, run.live.stage)
        assertEquals(91, run.live.calibrationFrames.size)
    }

    @Test
    fun `a tuned counter calibrates at the first frame after Go and reports reasons verbatim`() {
        val counter = tunedCounter()
        assertTrue(counter is CounterResult.Ready)
        val run = Run(counter, config, geometry)
        run.event(PhaseEvent.Start(0.0))
        framesUntil(10.05, run, nobody)
        val failed = run.live.setup as CountSetup.CalibrationFailed
        assertEquals(listOf("Move so your whole body is visible"), failed.reasons)
    }

    @Test
    fun `redo calibration clears collected frames`() {
        val run = Run(untuned, config, geometry)
        run.event(PhaseEvent.Start(0.0))
        framesUntil(3.0, run, nobody)
        run.event(PhaseEvent.RedoCalibration(3.05))
        assertEquals(0, run.live.calibrationFrames.size)
        assertEquals(Stage.CalibrationStand, run.live.stage)
    }

    @Test
    fun `stopping before Go leaves the count waiting`() {
        val run = Run(untuned, config, geometry)
        run.event(PhaseEvent.Start(0.0))
        run.frame(PoseFrame(0.0, List(33) { Landmark(0.5, 0.5, 0.0, 1.0) }))
        run.event(PhaseEvent.Cancel)
        assertEquals(LiveCount.INITIAL, run.live)
    }
}
