package app.sthira

import android.util.Log
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun str(id: Int, vararg args: Any): String = targetContext.getString(id, *args)

const val CAMERA_LOG = "SthiraCameraTest"
val LIVE_PILL = Regex("""^(\d+) fps · (GPU|CPU) · (person in view|no person)$""")
const val CAMERA_START_MS = 15_000L
const val STAGE_WAIT_MS = 9_000L

val STAND get() = str(R.string.cs_stage_stand)
val SIT get() = str(R.string.cs_stage_sit)
val NOT_COUNTING get() = str(R.string.cs_counting_off)
val START get() = str(R.string.cs_start)
val PERSON_LOST get() = str(R.string.cs_pill_person_lost)
val STEP_INTO_VIEW get() = str(R.string.cs_step_into_view)

/** Fills the setup form and continues to the camera screen. */
fun AppRule.startSession(code: String = "P07", band: String = "70-74", sex: String = "Female") {
    openCard("30-second chair stand")
    type(str(R.string.cs_code_label), code)
    chip(band).tap()
    chip(sex).tap()
    eventually { button(str(R.string.cs_continue)).assertIsEnabled() }
    tapButton(str(R.string.cs_continue))
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
        rule.assertScreen(str(R.string.cs_title))
    }

    @Test
    fun cameraGoesLive_withFpsAndDelegate_andLensSwitchWorks() {
        rule.onNode(hasText("P07 · 70-74 · Female")).assertExists()
        val pill = rule.waitLive()
        rule.button(START).assertIsEnabled()
        rule.onNode(hasText(PERSON_LOST)).assertDoesNotExist() // only shown while a run is active
        rule.shot("e-02-camera-live-back")
        // The lens switch is a round button on the camera view, found by its TalkBack description.
        val toFront = hasContentDescription(str(R.string.cs_switch_to_front)) and hasClickAction()
        val toBack = hasContentDescription(str(R.string.cs_switch_to_back)) and hasClickAction()
        rule.waitFor(toFront)
        rule.onNode(toFront).tap()
        rule.waitFor(toBack)
        Log.i(CAMERA_LOG, "front pill: ${rule.waitLive()}")
        rule.shot("e-03-camera-live-front")
        rule.onNode(toBack).tap()
        rule.waitFor(toFront)
        rule.waitLive()
        assertTrue(pill, LIVE_PILL.matches(pill))
    }

    @Test
    fun fullRun_calibrationCountdowns_untunedTestPhase_stop_runAgain() {
        rule.waitLive()
        rule.tapButton(START)
        // With nobody in front of the camera, calibration waits and says so.
        if (rule.exists(hasText(STEP_INTO_VIEW))) Log.i(CAMERA_LOG, "waiting for a person")
        rule.waitFor(hasText(STAND), STAGE_WAIT_MS)
        // The lens switch is hidden while a run is active.
        rule.onNode(hasContentDescription(str(R.string.cs_switch_to_front))).assertDoesNotExist()
        rule.button(str(R.string.cs_redo_calibration)).assertExists()
        rule.button(str(R.string.cs_cancel_test)).assertExists()
        rule.onNode(hasText(START)).assertDoesNotExist()
        rule.button(str(R.string.cs_edit_session)).assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Disabled))
        val pill = rule.onNode(hasTextMatching(LIVE_PILL)).text()
        if (pill.endsWith("no person")) rule.onNode(hasText(PERSON_LOST)).assertExists()
        Log.i(CAMERA_LOG, "calibration pill: $pill")
        rule.shot("e-04-calibration-stand")

        rule.waitFor(hasText(SIT), STAGE_WAIT_MS)
        rule.shot("e-05-calibration-sit")

        rule.waitFor(hasText(NOT_COUNTING), STAGE_WAIT_MS)
        rule.button(str(R.string.cs_stop)).assertExists()
        rule.button(str(R.string.cs_cancel_test)).assertExists()
        rule.onNode(hasText(str(R.string.cs_redo_calibration))).assertDoesNotExist()
        assertEquals(0, rule.onAllNodes(noCountShown()).fetchSemanticsNodes().size)
        rule.shot("e-06-test-untuned")

        rule.tapButton(str(R.string.cs_stop))
        rule.waitFor(hasText(str(R.string.cs_stage_done)))
        rule.onNode(hasText(str(R.string.cs_no_count_not_tuned))).assertExists()
        assertEquals(0, rule.onAllNodes(noCountShown()).fetchSemanticsNodes().size)
        rule.onNode(hasText(str(R.string.cs_issues_title))).assertDoesNotExist()
        rule.shot("e-07-done-untuned")

        // The bottom bar is rebuilt on the ViewModel's background coroutine: retry, and dump the screen if it never shows.
        try {
            rule.eventually(5_000) { rule.button(str(R.string.cs_run_again)).assertExists() }
        } catch (e: AssertionError) {
            rule.onRoot().printToLog("SEMANTICS")
            throw e
        }
        rule.tapButton(str(R.string.cs_run_again))
        rule.waitFor(hasText(START))
        rule.onNode(hasText(str(R.string.cs_no_count_not_tuned))).assertDoesNotExist()
    }

    @Test
    fun redoCalibrationRestartsTheStandCountdown_fromBothCalibrationSteps_thenCancel() {
        rule.waitLive()
        rule.tapButton(START)
        rule.waitFor(hasText(STAND), STAGE_WAIT_MS)
        rule.waitFor(hasContentDescription("3 seconds"), STAGE_WAIT_MS)
        rule.tapButton(str(R.string.cs_redo_calibration))
        rule.waitFor(hasContentDescription("5 seconds"), 2_000)
        rule.onNode(hasText(STAND)).assertExists()

        rule.waitFor(hasText(SIT), STAGE_WAIT_MS)
        rule.tapButton(str(R.string.cs_redo_calibration))
        rule.waitFor(hasText(STAND), 3_000)
        rule.waitGone(hasText(SIT), 3_000)

        rule.tapButton(str(R.string.cs_cancel_test))
        rule.waitFor(hasText(START))
        rule.onNode(hasText(STAND)).assertDoesNotExist()
        rule.onNode(hasText(str(R.string.cs_stage_done))).assertDoesNotExist()
    }

    @Test
    fun cancelDuringTestPhase_returnsToReady() {
        rule.waitLive()
        rule.tapButton(START)
        rule.waitFor(hasText(NOT_COUNTING), STAGE_WAIT_MS * 2)
        rule.tapButton(str(R.string.cs_cancel_test))
        rule.waitFor(hasText(START))
        rule.onNode(hasText(str(R.string.cs_stage_done))).assertDoesNotExist()
    }

    @Test
    fun systemBackDuringRunCancels_thenSecondBackLeaves() {
        rule.waitLive()
        rule.tapButton(START)
        rule.waitFor(hasText(STAND), STAGE_WAIT_MS)
        rule.systemBack()
        rule.waitFor(hasText(START))
        rule.assertScreen(str(R.string.cs_title))
        rule.systemBack()
        rule.assertHome()
    }

    @Test
    fun editSessionReturnsToTheFilledForm() {
        rule.waitLive()
        rule.tapButton(str(R.string.cs_edit_session))
        // The ViewModel handles this on a background coroutine that waitForIdle() does not track.
        rule.eventually { rule.field(str(R.string.cs_code_label)).assert(hasText("P07")) }
        rule.button(str(R.string.cs_continue)).assertIsEnabled()
        rule.tapButton(str(R.string.cs_continue))
        rule.waitLive()
        rule.tapBackArrow()
        rule.assertHome()
    }
}
