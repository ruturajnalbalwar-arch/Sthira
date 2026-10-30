package app.sthira

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import app.sthira.engine.timers.TimerNormsResult
import app.sthira.engine.timers.readTimerNorms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val HOLD_WAIT_MS = 14_000L
private const val REF = "10.0 s"

@LargeTest
@RunWith(AndroidJUnit4::class)
class BalanceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val norms by lazy { ((readTimerNorms(asset("norms.json")) as TimerNormsResult.Ok).norms).balance }

    @Before
    fun open() {
        rule.keepScreenOn()
        rule.openCard("4-stage balance")
        rule.assertScreen("4-stage balance")
        assertEquals(10.0, norms.stageDurationS, 0.0)
        assertEquals(3, norms.riskStage)
    }

    /** "held", "lost position", "current", "not started" or "not run" for stages 1-4. */
    private fun assertStepper(vararg spoken: String) {
        norms.stages.forEachIndexed { i, s ->
            rule.onNode(hasContentDescription("Stage ${s.stage} of 4, ${s.name}: ${spoken[i]}")).assertExists()
        }
    }

    private fun hold(stage: Int) {
        rule.tapButton("Start stage $stage")
        rule.button("Lost position").assertExists()
        rule.button("Not ready, restart stage").assertExists()
        val next = if (stage < 4) hasText("Start stage ${stage + 1}") else hasText("Start over")
        rule.waitFor(next, HOLD_WAIT_MS)
        rule.onNode(hasContentDescription("Stage $stage: 10.0 s, Held")).assertExists()
    }

    private fun loseAt(stage: Int) {
        rule.tapButton("Start stage $stage")
        Thread.sleep(1_500)
        rule.tapButton("Lost position")
        rule.waitFor(hasText("Start over"))
        rule.onNode(hasContentDescription("Stage $stage: ", substring = true) and hasContentDescription("Lost position", substring = true))
            .assertExists()
    }

    @Test
    fun initialState() {
        assertStepper("current", "not started", "not started", "not started")
        rule.onNode(hasText("Stage 1: feet side-by-side")).assertExists()
        rule.onNode(hasText("“Stand with your feet side-by-side.”")).assertExists()
        rule.onNode(hasContentDescription("Hold for 10.0 s")).assertExists()
        rule.onNode(hasText("Start over")).assertDoesNotExist()
        rule.onNode(hasText("Stage results")).assertDoesNotExist()
    }

    @Test
    fun holdAllFourStages_meetsTheReference_thenStartOver() {
        hold(1)
        assertStepper("held", "current", "not started", "not started")
        rule.button("Start over").assertExists() // secondary action once a stage has a result
        hold(2)
        hold(3)
        assertStepper("held", "held", "held", "current")
        hold(4)
        assertStepper("held", "held", "held", "held")
        rule.onNode(hasText("Tandem stand held 10.0 s: meets the CDC reference of $REF")).assertExists()
        rule.tapButton("Start over")
        assertStepper("current", "not started", "not started", "not started")
        rule.onNode(hasText("Stage results")).assertDoesNotExist()
        rule.onNode(hasText("meets the CDC reference", substring = true)).assertDoesNotExist()
    }

    @Test
    fun lostAtStage1_stoppedBefore() {
        loseAt(1)
        assertStepper("lost position", "not run", "not run", "not run")
        rule.onNode(hasText("Stopped at stage 1, before the tandem stand: below the CDC reference of $REF in tandem")).assertExists()
        rule.tapButton("Start over")
        assertStepper("current", "not started", "not started", "not started")
    }

    @Test
    fun lostAtStage2_stoppedBefore() {
        hold(1)
        loseAt(2)
        assertStepper("held", "lost position", "not run", "not run")
        rule.onNode(hasText("Stopped at stage 2, before the tandem stand: below the CDC reference of $REF in tandem")).assertExists()
    }

    @Test
    fun lostAtStage3_belowTheReference() {
        hold(1)
        hold(2)
        loseAt(3)
        assertStepper("held", "held", "lost position", "not run")
        val title = rule.onNode(hasTextMatching(Regex("""^Tandem stand held \d+\.\d s: below the CDC reference of 10\.0 s$"""))).text()
        val held = title.removePrefix("Tandem stand held ").substringBefore(" s").toDouble()
        assertTrue("held $held", held in 1.0..9.9)
    }

    @Test
    fun lostAtStage4_stillMeetsTheReference() {
        hold(1)
        hold(2)
        hold(3)
        loseAt(4)
        assertStepper("held", "held", "held", "lost position")
        rule.onNode(hasText("Tandem stand held 10.0 s: meets the CDC reference of $REF")).assertExists()
    }

    @Test
    fun notReadyRestartsTheStage_andStartOverMidTest() {
        rule.tapButton("Start stage 1")
        Thread.sleep(1_500)
        rule.tapButton("Not ready, restart stage")
        rule.button("Start stage 1").assertExists()
        rule.onNode(hasText("Lost position")).assertDoesNotExist()
        rule.onNode(hasText("Stage results")).assertDoesNotExist()
        assertStepper("current", "not started", "not started", "not started")

        hold(1)
        rule.tapButton("Start over")
        assertStepper("current", "not started", "not started", "not started")
        rule.button("Start stage 1").assertExists()
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

    private val result = Regex("""^(\d+\.\d) s: (under|at or above) the CDC reference of 12\.0 s$""")

    @Before
    fun open() {
        rule.keepScreenOn()
        rule.openCard("Timed Up and Go")
        rule.assertScreen("Timed Up and Go")
        val tug = (readTimerNorms(asset("norms.json")) as TimerNormsResult.Ok).norms.tug
        assertEquals(12.0, tug.atRiskIfAtLeastS, 0.0)
    }

    private fun runFor(ms: Long): Pair<Double, String> {
        rule.tapButton(if (rule.exists(hasText("Go"))) "Go" else "Run again: Go")
        rule.button("Sat back down: stop").assertExists()
        Thread.sleep(ms)
        rule.tapButton("Sat back down: stop")
        rule.waitFor(hasTextMatching(result))
        val m = requireNotNull(result.matchEntire(rule.onNode(hasTextMatching(result)).text()))
        return m.groupValues[1].toDouble() to m.groupValues[2]
    }

    @Test
    fun quickRun_isUnderTheReference_andRunAgainRestarts() {
        val (s, word) = runFor(800)
        assertEquals("under", word)
        assertTrue("$s", s < 12.0)
        rule.onNode(hasText("A walking aid was used.")).assertDoesNotExist()
        rule.tapButton("Run again: Go")
        rule.button("Sat back down: stop").assertExists()
        rule.waitGone(hasTextMatching(result))
        rule.tapButton("Sat back down: stop")
        rule.waitFor(hasTextMatching(result))
    }

    @LargeTest
    @Test
    fun runOfTwelveSecondsOrMore_isAtOrAbove() {
        val (s, word) = runFor(12_300)
        assertEquals("at or above", word)
        assertTrue("$s", s >= 12.0)
    }

    @Test
    fun walkingAidCheckbox_isShownInTheResult() {
        rule.check("Used a walking aid").assertIsOff()
        rule.check("Used a walking aid").tap()
        rule.waitForIdle()
        rule.check("Used a walking aid").assertIsOn()
        runFor(500)
        rule.onNode(hasText("A walking aid was used.")).assertExists()
        rule.check("Used a walking aid").tap()
        rule.waitForIdle()
        rule.onNode(hasText("A walking aid was used.")).assertDoesNotExist()
    }

    @Test
    fun systemBackReturnsHome() {
        rule.systemBack()
        rule.assertHome()
    }
}
