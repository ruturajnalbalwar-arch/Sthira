package app.sthira.chairstand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PhaseMachineTest {
    private val config = PhaseConfig(calibrationStandS = 5.0, calibrationSitS = 4.0, testDurationS = 30.0)
    private fun run(vararg events: PhaseEvent): PhaseState =
        events.fold(PhaseMachine.INITIAL) { s, e -> PhaseMachine.reduce(s, e, config) }

    @Test
    fun `start calibrates standing first`() {
        val s = run(PhaseEvent.Start(1.0))
        assertEquals(Stage.CalibrationStand, s.stage)
        assertEquals(listOf(PhaseSpan(Stage.CalibrationStand, 1.0, 1.0)), s.phases)
    }

    @Test
    fun `stand then sit then test with boundaries at protocol time`() {
        val s = run(PhaseEvent.Start(0.0), PhaseEvent.Tick(5.3))
        assertEquals(Stage.CalibrationSit, s.stage)
        assertEquals(5.0, s.stageStartT, 0.0)
        val t = PhaseMachine.reduce(s, PhaseEvent.Tick(9.1), config)
        assertEquals(Stage.Test, t.stage)
        assertEquals(
            listOf(
                PhaseSpan(Stage.CalibrationStand, 0.0, 5.0),
                PhaseSpan(Stage.CalibrationSit, 5.0, 9.0),
                PhaseSpan(Stage.Test, 9.0, 9.0),
            ),
            t.phases,
        )
    }

    @Test
    fun `a late tick runs through every elapsed stage and auto-stops the test`() {
        val s = run(PhaseEvent.Start(0.0), PhaseEvent.Tick(100.0))
        assertEquals(Stage.Done, s.stage)
        assertEquals(39.0, s.endT ?: -1.0, 0.0)
        assertEquals(PhaseSpan(Stage.Test, 9.0, 39.0), s.phases.last())
    }

    @Test
    fun `stop only applies during the test`() {
        val calibrating = run(PhaseEvent.Start(0.0))
        assertSame(calibrating, PhaseMachine.reduce(calibrating, PhaseEvent.Stop(1.0), config))
        val stopped = run(PhaseEvent.Start(0.0), PhaseEvent.Tick(10.0), PhaseEvent.Stop(12.0))
        assertEquals(Stage.Done, stopped.stage)
        assertEquals(12.0, stopped.endT ?: -1.0, 0.0)
    }

    @Test
    fun `redo calibration starts over from standing`() {
        val s = run(PhaseEvent.Start(0.0), PhaseEvent.Tick(6.0), PhaseEvent.RedoCalibration(7.0))
        assertEquals(Stage.CalibrationStand, s.stage)
        assertEquals(listOf(PhaseSpan(Stage.CalibrationStand, 7.0, 7.0)), s.phases)
    }

    @Test
    fun `interrupt closes the open phase`() {
        val s = run(PhaseEvent.Start(0.0), PhaseEvent.Interrupt(2.0))
        assertEquals(Stage.Done, s.stage)
        assertEquals(PhaseSpan(Stage.CalibrationStand, 0.0, 2.0), s.phases.single())
    }

    @Test
    fun `cancel and reset return to idle`() {
        assertEquals(PhaseMachine.INITIAL, run(PhaseEvent.Start(0.0), PhaseEvent.Cancel))
        assertEquals(PhaseMachine.INITIAL, run(PhaseEvent.Start(0.0), PhaseEvent.Interrupt(1.0), PhaseEvent.Reset))
    }

    @Test
    fun `countdown shows whole seconds left only in calibration`() {
        val s = run(PhaseEvent.Start(0.0))
        assertEquals(5, PhaseMachine.countdown(s, 0.0, config))
        assertEquals(1, PhaseMachine.countdown(s, 4.2, config))
        val test = run(PhaseEvent.Start(0.0), PhaseEvent.Tick(9.5))
        assertNull(PhaseMachine.countdown(test, 9.5, config))
    }
}
