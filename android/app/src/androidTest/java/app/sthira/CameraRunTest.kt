package app.sthira

import android.util.Log
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import app.sthira.chairstand.ChairStandResults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

const val CAMERA_LOG = "SthiraCameraTest"
val LIVE_PILL = Regex("""^(\d+) fps · (GPU|CPU) · (person in view|no person)$""")
const val CAMERA_START_MS = 15_000L
const val STAGE_WAIT_MS = 9_000L

const val STAND = "Stand up tall and keep still"
const val SIT = "Sit down and keep still"
const val NOT_COUNTING = "Counting is off: the counter is not tuned yet."
const val START = "Start calibration and test"
const val PERSON_LOST = "Cannot see the person: whole body, head to feet, in view"

/** Fills the setup form and continues to the camera screen. */
fun AppRule.startSession(code: String = "P07", band: String = "70-74", sex: String = "Female") {
    openCard("30-second chair stand")
    type("Participant code", code)
    chip(band).tap()
    chip(sex).tap()
    eventually { button("Continue to camera").assertIsEnabled() }
    tapButton("Continue to camera")
}

/** Waits for the status pill to report a live camera with a frame rate above zero; returns its text. */
fun AppRule.waitLive(): String {
    waitUntil(CAMERA_START_MS) {
        onAllNodes(hasTextMatching(LIVE_PILL)).fetchSemanticsNodes().any { node ->
            val text = node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
            (LIVE_PILL.matchEntire(text)?.groupValues?.get(1)?.toInt() ?: 0) > 0
        }
    }
    return onNode(hasTextMatching(LIVE_PILL)).text().also { Log.i(CAMERA_LOG, "pill: $it") }
}

private fun noCountShown(): SemanticsMatcher =
    hasTextMatching(Regex("""^ ?(full )?stands?$""")) or hasContentDescription("full stand", substring = true)

@LargeTest
@RunWith(AndroidJUnit4::class)
class CameraRunTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        assumeTrue("CAMERA must be granted (adb shell pm grant app.sthira android.permission.CAMERA)", cameraGranted())
        rule.keepScreenOn()
        rule.startSession()
        rule.assertScreen("30-second chair stand")
    }

    @Test
    fun cameraGoesLive_withFpsAndDelegate_andLensSwitchWorks() {
        rule.onNode(hasText("P07 · 70-74 · Female")).assertExists()
        val pill = rule.waitLive()
        rule.button(START).assertIsEnabled()
        rule.onNode(hasText(PERSON_LOST)).assertDoesNotExist() // only shown while a run is active
        rule.shot("e-02-camera-live-back")
        if (rule.exists(hasText("Switch to front camera"))) {
            rule.tapButton("Switch to front camera")
            rule.waitFor(hasText("Switch to back camera"))
            Log.i(CAMERA_LOG, "front pill: ${rule.waitLive()}")
            rule.shot("e-03-camera-live-front")
            rule.tapButton("Switch to back camera")
            rule.waitFor(hasText("Switch to front camera"))
            rule.waitLive()
        }
        assertTrue(pill, LIVE_PILL.matches(pill))
    }

    @Test
    fun fullRun_calibrationCountdowns_untunedTestPhase_stop_runAgain() {
        rule.waitLive()
        rule.tapButton(START)
        rule.waitFor(hasText(STAND), STAGE_WAIT_MS)
        rule.button("Redo calibration").assertExists()
        rule.button("Cancel test").assertExists()
        rule.onNode(hasText(START)).assertDoesNotExist()
        rule.button("Edit session").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Disabled))
        val pill = rule.onNode(hasTextMatching(LIVE_PILL)).text()
        if (pill.endsWith("no person")) rule.onNode(hasText(PERSON_LOST)).assertExists()
        Log.i(CAMERA_LOG, "calibration pill: $pill")
        rule.shot("e-04-calibration-stand")

        rule.waitFor(hasText(SIT), STAGE_WAIT_MS)
        rule.shot("e-05-calibration-sit")

        rule.waitFor(hasText(NOT_COUNTING), STAGE_WAIT_MS)
        rule.button("Stop").assertExists()
        rule.button("Cancel test").assertExists()
        rule.onNode(hasText("Redo calibration")).assertDoesNotExist()
        assertEquals(0, rule.onAllNodes(noCountShown()).fetchSemanticsNodes().size)
        rule.shot("e-06-test-untuned")

        rule.tapButton("Stop")
        rule.waitFor(hasText("Stop. Test finished"))
        rule.onNode(hasText(ChairStandResults.NO_COUNT_NOT_TUNED)).assertExists()
        assertEquals(0, rule.onAllNodes(noCountShown()).fetchSemanticsNodes().size)
        rule.onNode(hasText("This run had capture problems")).assertDoesNotExist()
        rule.shot("e-07-done-untuned")

        rule.tapButton("Run again")
        rule.waitFor(hasText(START))
        rule.onNode(hasText(ChairStandResults.NO_COUNT_NOT_TUNED)).assertDoesNotExist()
    }

    @Test
    fun redoCalibrationRestartsTheStandCountdown_fromBothCalibrationSteps_thenCancel() {
        rule.waitLive()
        rule.tapButton(START)
        rule.waitFor(hasText(STAND), STAGE_WAIT_MS)
        rule.waitFor(hasContentDescription("3 seconds"), STAGE_WAIT_MS)
        rule.tapButton("Redo calibration")
        rule.waitFor(hasContentDescription("5 seconds"), 2_000)
        rule.onNode(hasText(STAND)).assertExists()

        rule.waitFor(hasText(SIT), STAGE_WAIT_MS)
        rule.tapButton("Redo calibration")
        rule.waitFor(hasText(STAND), 3_000)
        rule.waitGone(hasText(SIT), 3_000)

        rule.tapButton("Cancel test")
        rule.waitFor(hasText(START))
        rule.onNode(hasText(STAND)).assertDoesNotExist()
        rule.onNode(hasText("Stop. Test finished")).assertDoesNotExist()
    }

    @Test
    fun cancelDuringTestPhase_returnsToReady() {
        rule.waitLive()
        rule.tapButton(START)
        rule.waitFor(hasText(NOT_COUNTING), STAGE_WAIT_MS * 2)
        rule.tapButton("Cancel test")
        rule.waitFor(hasText(START))
        rule.onNode(hasText("Stop. Test finished")).assertDoesNotExist()
    }

    @Test
    fun systemBackDuringRunCancels_thenSecondBackLeaves() {
        rule.waitLive()
        rule.tapButton(START)
        rule.waitFor(hasText(STAND), STAGE_WAIT_MS)
        rule.systemBack()
        rule.waitFor(hasText(START))
        rule.assertScreen("30-second chair stand")
        rule.systemBack()
        rule.assertHome()
    }

    @Test
    fun editSessionReturnsToTheFilledForm() {
        rule.waitLive()
        rule.tapButton("Edit session")
        rule.field("Participant code").assert(hasText("P07"))
        rule.button("Continue to camera").assertIsEnabled()
        rule.tapButton("Continue to camera")
        rule.waitLive()
        rule.tapBackArrow()
        rule.assertHome()
    }
}
