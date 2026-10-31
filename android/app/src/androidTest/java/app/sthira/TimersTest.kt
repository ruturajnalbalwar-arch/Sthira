package app.sthira

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import app.sthira.engine.timers.TimerNormsResult
import app.sthira.engine.timers.readTimerNorms
import app.sthira.timers.StepStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val HOLD_WAIT_MS = 14_000L
private const val PLACEHOLDER = "\u0000"

/** Every label comes from the app's own strings, so these tests follow the device language. */
private fun str(@StringRes id: Int, vararg args: Any): String = targetContext.getString(id, *args)
private fun secs(value: Double): String = str(R.string.timer_seconds_value, value)

/** A regex for string [id] with its first argument as a capture group and the other [rest] arguments filled in. */
private fun patternFor(@StringRes id: Int, vararg rest: Any): Regex {
    val (before, after) = str(id, PLACEHOLDER, *rest).split(PLACEHOLDER)
    return Regex("^" + Regex.escape(before) + "(.+)" + Regex.escape(after) + "$")
}

/** The seconds value whose formatted text is [text], searched in tenths up to [maxS]. */
private fun parseSecs(text: String, maxS: Double): Double? =
    (0..(maxS * 10).toInt()).map { it / 10.0 }.firstOrNull { secs(it) == text }

@LargeTest
@RunWith(AndroidJUnit4::class)
class BalanceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val norms by lazy { ((readTimerNorms(asset("norms.json")) as TimerNormsResult.Ok).norms).balance }
    private val ref by lazy { secs(norms.riskHoldLessThanS) }
    private val stageNames = listOf(
        R.string.timer_stage_name_1, R.string.timer_stage_name_2, R.string.timer_stage_name_3, R.string.timer_stage_name_4,
    )
    private val fresh = arrayOf(StepStatus.Current, StepStatus.Upcoming, StepStatus.Upcoming, StepStatus.Upcoming)
    private val startOver get() = str(R.string.timer_start_over)
    private val lost get() = str(R.string.timer_lost_position)
    private val restart get() = str(R.string.timer_restart_stage)
    private val results get() = str(R.string.timer_stage_results)
    private fun startStage(stage: Int) = str(R.string.timer_start_stage, stage)

    @Before
    fun open() {
        rule.keepScreenOn()
        rule.openCard(str(R.string.timer_balance_title))
        rule.assertScreen(str(R.string.timer_balance_title))
        assertEquals(10.0, norms.stageDurationS, 0.0)
        assertEquals(3, norms.riskStage)
    }

    /** The spoken state of each stepper dot, stages 1-4. */
    private fun assertStepper(vararg states: StepStatus) {
        norms.stages.forEachIndexed { i, s ->
            val cd = str(R.string.timer_stepper_cd, s.stage, norms.stages.size, str(stageNames[i]), str(states[i].spoken))
            rule.onNode(hasContentDescription(cd)).assertExists()
        }
    }

    private fun hold(stage: Int) {
        rule.tapButton(startStage(stage))
        rule.button(lost).assertExists()
        rule.button(restart).assertExists()
        val next = if (stage < norms.stages.size) hasText(startStage(stage + 1)) else hasText(startOver)
        rule.waitFor(next, HOLD_WAIT_MS)
        val row = str(R.string.timer_stage_result_cd, stage, secs(norms.stageDurationS), str(R.string.timer_outcome_held))
        rule.onNode(hasContentDescription(row)).assertExists()
    }

    private fun loseAt(stage: Int) {
        rule.tapButton(startStage(stage))
        Thread.sleep(1_500)
        rule.tapButton(lost)
        rule.waitFor(hasText(startOver))
        val row = Regex(
            "^" + Regex.escape(str(R.string.timer_stage_result_cd, stage, PLACEHOLDER, str(R.string.timer_outcome_lost)))
                .replace(PLACEHOLDER, "\\E.+\\Q") + "$",
        )
        assertTrue(rule.exists(hasDescriptionMatching(row)))
    }

    @Test
    fun initialState() {
        assertStepper(*fresh)
        rule.onNode(hasText(str(R.string.timer_stage_heading, 1, str(R.string.timer_stage_name_1)))).assertExists()
        rule.onNode(hasText(str(R.string.timer_stage_quote, str(R.string.timer_stage_cdc_1)))).assertExists()
        rule.onNode(hasContentDescription(str(R.string.timer_hold_for, secs(norms.stageDurationS)))).assertExists()
        rule.onNode(hasText(startOver)).assertDoesNotExist()
        rule.onNode(hasText(results)).assertDoesNotExist()
    }

    @Test
    fun holdAllFourStages_meetsTheReference_thenStartOver() {
        hold(1)
        assertStepper(StepStatus.Held, StepStatus.Current, StepStatus.Upcoming, StepStatus.Upcoming)
        rule.button(startOver).assertExists() // secondary action once a stage has a result
        hold(2)
        hold(3)
        assertStepper(StepStatus.Held, StepStatus.Held, StepStatus.Held, StepStatus.Current)
        hold(4)
        assertStepper(StepStatus.Held, StepStatus.Held, StepStatus.Held, StepStatus.Held)
        val meets = str(R.string.timer_balance_meets, secs(norms.stageDurationS), ref)
        rule.onNode(hasText(meets)).assertExists()
        rule.tapButton(startOver)
        assertStepper(*fresh)
        rule.onNode(hasText(results)).assertDoesNotExist()
        rule.onNode(hasText(meets)).assertDoesNotExist()
    }

    @Test
    fun lostAtStage1_stoppedBefore() {
        loseAt(1)
        assertStepper(StepStatus.Lost, StepStatus.Skipped, StepStatus.Skipped, StepStatus.Skipped)
        rule.onNode(hasText(str(R.string.timer_balance_stopped_before, 1, ref))).assertExists()
        rule.tapButton(startOver)
        assertStepper(*fresh)
    }

    @Test
    fun lostAtStage2_stoppedBefore() {
        hold(1)
        loseAt(2)
        assertStepper(StepStatus.Held, StepStatus.Lost, StepStatus.Skipped, StepStatus.Skipped)
        rule.onNode(hasText(str(R.string.timer_balance_stopped_before, 2, ref))).assertExists()
    }

    @Test
    fun lostAtStage3_belowTheReference() {
        hold(1)
        hold(2)
        loseAt(3)
        assertStepper(StepStatus.Held, StepStatus.Held, StepStatus.Lost, StepStatus.Skipped)
        val pattern = patternFor(R.string.timer_balance_below, ref)
        val heldText = requireNotNull(pattern.matchEntire(rule.onNode(hasTextMatching(pattern)).text())).groupValues[1]
        val held = parseSecs(heldText, norms.riskHoldLessThanS)
        assertNotNull("held $heldText", held)
        assertTrue("held $held", requireNotNull(held) in 1.0..9.9)
    }

    @Test
    fun lostAtStage4_stillMeetsTheReference() {
        hold(1)
        hold(2)
        hold(3)
        loseAt(4)
        assertStepper(StepStatus.Held, StepStatus.Held, StepStatus.Held, StepStatus.Lost)
        rule.onNode(hasText(str(R.string.timer_balance_meets, secs(norms.stageDurationS), ref))).assertExists()
    }

    @Test
    fun notReadyRestartsTheStage_andStartOverMidTest() {
        rule.tapButton(startStage(1))
        Thread.sleep(1_500)
        rule.tapButton(restart)
        rule.button(startStage(1)).assertExists()
        rule.onNode(hasText(lost)).assertDoesNotExist()
        rule.onNode(hasText(results)).assertDoesNotExist()
        assertStepper(*fresh)

        hold(1)
        rule.tapButton(startOver)
        assertStepper(*fresh)
        rule.button(startStage(1)).assertExists()
    }

    @Test
    fun systemBackReturnsHome() {
        rule.systemBack()
        rule.assertHome()
    }
}

@RunWith(AndroidJUnit4::class)
class TugTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val tug by lazy { (readTimerNorms(asset("norms.json")) as TimerNormsResult.Ok).norms.tug }
    private val ref by lazy { secs(tug.atRiskIfAtLeastS) }
    private val under by lazy { patternFor(R.string.timer_tug_under, ref) }
    private val atOrAbove by lazy { patternFor(R.string.timer_tug_at_or_above, ref) }
    private val go get() = str(R.string.timer_tug_go)
    private val stop get() = str(R.string.timer_tug_stop)
    private val runAgain get() = str(R.string.timer_tug_run_again)
    private val usedAid get() = str(R.string.timer_used_aid)
    private val aidWasUsed get() = str(R.string.timer_aid_was_used)
    private fun anyResult() = hasTextMatching(under) or hasTextMatching(atOrAbove)

    @Before
    fun open() {
        rule.keepScreenOn()
        rule.openCard(str(R.string.timer_tug_title))
        rule.assertScreen(str(R.string.timer_tug_title))
        assertEquals(12.0, tug.atRiskIfAtLeastS, 0.0)
    }

    /** Runs the timer for [ms] and returns the measured seconds and whether the result was "under". */
    private fun runFor(ms: Long): Pair<Double, Boolean> {
        rule.tapButton(if (rule.exists(hasText(go))) go else runAgain)
        rule.button(stop).assertExists()
        Thread.sleep(ms)
        rule.tapButton(stop)
        rule.waitFor(anyResult())
        val isUnder = rule.exists(hasTextMatching(under))
        val pattern = if (isUnder) under else atOrAbove
        val text = requireNotNull(pattern.matchEntire(rule.onNode(hasTextMatching(pattern)).text())).groupValues[1]
        val seconds = requireNotNull(parseSecs(text, ms / 1000.0 + 60.0)) { "unreadable time $text" }
        return seconds to isUnder
    }

    @Test
    fun quickRun_isUnderTheReference_andRunAgainRestarts() {
        val (s, isUnder) = runFor(800)
        assertTrue("under", isUnder)
        assertTrue("$s", s < 12.0)
        rule.onNode(hasText(aidWasUsed)).assertDoesNotExist()
        rule.tapButton(runAgain)
        rule.button(stop).assertExists()
        rule.waitGone(anyResult())
        rule.tapButton(stop)
        rule.waitFor(anyResult())
    }

    @LargeTest
    @Test
    fun runOfTwelveSecondsOrMore_isAtOrAbove() {
        val (s, isUnder) = runFor(12_300)
        assertTrue("at or above", !isUnder)
        assertTrue("$s", s >= 12.0)
    }

    @Test
    fun walkingAidCheckbox_isShownInTheResult() {
        rule.check(usedAid).assertIsOff()
        rule.check(usedAid).tap()
        rule.waitForIdle()
        rule.check(usedAid).assertIsOn()
        runFor(500)
        rule.onNode(hasText(aidWasUsed)).assertExists()
        rule.check(usedAid).tap()
        rule.waitForIdle()
        rule.onNode(hasText(aidWasUsed)).assertDoesNotExist()
    }

    @Test
    fun systemBackReturnsHome() {
        rule.systemBack()
        rule.assertHome()
    }
}
