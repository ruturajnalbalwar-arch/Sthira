package app.sthira.chairstand

import app.sthira.R
import app.sthira.engine.norms.NormSex
import app.sthira.pose.CameraStatus
import app.sthira.pose.PoseDelegate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The three-step flow and the spoken instructions, from the screen state alone. */
class ChairStandFlowTest {
    private val session = Session(AgeBand.A70_74, NormSex.FEMALE)
    private val camera = ChairStandUiState(session = session, camera = CameraStatus.Live(PoseDelegate.GPU), stats = LiveStats(24.0, true))

    @Test
    fun `no answers yet is step 1, the camera is step 2, a finished run is step 3`() {
        assertEquals(FlowStep.Person, ChairStandUiState().step)
        assertEquals(FlowStep.Camera, camera.step)
        listOf(Stage.CalibrationStand, Stage.CalibrationSit, Stage.Test).forEach {
            assertEquals("$it", FlowStep.Camera, camera.copy(stage = it).step)
        }
        assertEquals(FlowStep.Result, camera.copy(stage = Stage.Done).step)
        assertEquals(listOf(1, 2, 3), FlowStep.entries.map { it.number })
    }

    @Test
    fun `each stage speaks the instruction shown on screen`() {
        assertEquals(R.string.cs_stage_stand, spokenCue(camera.copy(stage = Stage.CalibrationStand)))
        assertEquals(R.string.cs_stage_sit, spokenCue(camera.copy(stage = Stage.CalibrationSit)))
        assertEquals(R.string.cs_stage_go, spokenCue(camera.copy(stage = Stage.Test)))
        assertEquals(R.string.cs_stage_done, spokenCue(camera.copy(stage = Stage.Done)))
    }

    @Test
    fun `waiting for a person after Start says step into view`() {
        val waiting = camera.copy(startRequested = true, stats = LiveStats(24.0, false))
        assertEquals(R.string.cs_step_into_view, spokenCue(waiting))
    }

    @Test
    fun `nothing is said before Start or once a person is found but calibration has not begun`() {
        assertNull(spokenCue(camera))
        assertNull(spokenCue(camera.copy(startRequested = true)))
        assertNull(spokenCue(ChairStandUiState()))
    }
}
