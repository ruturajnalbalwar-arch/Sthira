package app.sthira.engine

import app.sthira.engine.norms.AgeRange
import app.sthira.engine.norms.ChairStandNorms
import app.sthira.engine.norms.NormLookup
import app.sthira.engine.norms.NormSex
import app.sthira.engine.norms.NormsParseResult
import app.sthira.engine.norms.lookupChairStandNorm
import app.sthira.engine.norms.parseAgeBand
import app.sthira.engine.norms.parseChairStandNorms
import app.sthira.engine.timers.BALANCE_START
import app.sthira.engine.timers.BalanceEvent
import app.sthira.engine.timers.BalanceState
import app.sthira.engine.timers.BalanceVerdict
import app.sthira.engine.timers.CurrentStage
import app.sthira.engine.timers.StageResult
import app.sthira.engine.timers.TimerNorms
import app.sthira.engine.timers.TimerNormsResult
import app.sthira.engine.timers.TugVerdict
import app.sthira.engine.timers.balanceReducer
import app.sthira.engine.timers.balanceVerdict
import app.sthira.engine.timers.readTimerNorms
import app.sthira.engine.timers.tugVerdict
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Mirrors web/src/lib/count/norms.test.ts and web/src/lib/timers/timers.test.ts against the real norms.json. */
class NormsAndTimersTest {
    private val normsText: String by lazy {
        File(requireNotNull(System.getProperty("sthira.norms")) { "system property sthira.norms is not set" }).readText()
    }
    private val chairNorms: ChairStandNorms by lazy {
        when (val r = parseChairStandNorms(normsText)) {
            is NormsParseResult.Ok -> r.norms
            is NormsParseResult.Failed -> error(r.errors.joinToString("; "))
        }
    }
    private val timer: TimerNorms by lazy {
        when (val r = readTimerNorms(normsText)) {
            is TimerNormsResult.Ok -> r.norms
            is TimerNormsResult.Failed -> error(r.errors.joinToString("; "))
        }
    }
    private val hold get() = timer.balance.stageDurationS

    private fun run(events: List<BalanceEvent>): BalanceState =
        events.fold(BALANCE_START) { s, e -> balanceReducer(s, e, timer.balance) }

    private fun cutoff(sex: String, ageMin: Int) = chairNorms.entries.first { it.sex.wire == sex && it.ageMin == ageMin }.belowAverageIfLessThan

    @Test
    fun `chair-stand norms keep the CDC table and note`() {
        assertTrue(chairNorms.note.contains("Not validated for Indian older adults"))
        assertTrue(chairNorms.entries.all { it.source.url.startsWith("https://www.cdc.gov/") })
        val bad = parseChairStandNorms("""{"note":"x","chairStand30s":{"entries":[{"sex":"any"}]}}""")
        assertTrue(bad is NormsParseResult.Failed && bad.errors.isNotEmpty())
    }

    @Test
    fun `age bands parse like the TypeScript`() {
        assertEquals(AgeRange(70.0, 74.0), parseAgeBand("70-74"))
        assertEquals(AgeRange(80.0, null), parseAgeBand("80+"))
        assertNull(parseAgeBand("seventy"))
    }

    @Test
    fun `lookup returns the exact 5-year band cut-off read from norms json`() {
        val female = lookupChairStandNorm(chairNorms, "70-74", NormSex.FEMALE)
        val male = lookupChairStandNorm(chairNorms, "60-64", NormSex.MALE)
        assertEquals(cutoff("female", 70), (female as NormLookup.Available).entry.belowAverageIfLessThan)
        assertEquals(cutoff("male", 60), (male as NormLookup.Available).entry.belowAverageIfLessThan)
    }

    @Test
    fun `lookup is not available for unrecorded sex, under 60, 80+ and unknown bands`() {
        val notRecorded = lookupChairStandNorm(chairNorms, "70-74", NormSex.NOT_RECORDED) as NormLookup.NotAvailable
        assertTrue(notRecorded.reason.contains("sex was not recorded"))
        val young = lookupChairStandNorm(chairNorms, "45-59", NormSex.FEMALE) as NormLookup.NotAvailable
        assertTrue(young.reason.contains("no value for age 45-59"))
        val old = lookupChairStandNorm(chairNorms, "80+", NormSex.MALE) as NormLookup.NotAvailable
        assertTrue(old.reason.contains("80-84, 85-89, 90-94"))
        assertTrue(lookupChairStandNorm(chairNorms, "old", NormSex.MALE) is NormLookup.NotAvailable)
    }

    @Test
    fun `timer norms read the CDC balance and TUG values`() {
        assertEquals(listOf(1, 2, 3, 4), timer.balance.stages.map { it.stage })
        assertEquals(3, timer.balance.riskStage)
        assertTrue(timer.tug.atRiskIfAtLeastS > 0)
        assertTrue(timer.tug.source.url.startsWith("https://www.cdc.gov/"))
        val root = Json.parseToJsonElement(normsText).jsonObject
        assertTrue(readTimerNorms(JsonObject(mapOf("tug" to root.getValue("tug")))) is TimerNormsResult.Failed)
    }

    @Test
    fun `balance moves on after a full hold and stops when lost`() {
        val held = run(listOf(BalanceEvent.StartStage(0.0), BalanceEvent.Tick(hold)))
        assertEquals(BalanceState(listOf(StageResult(1, hold, true)), CurrentStage(1, null), false), held)
        val lost = run(listOf(BalanceEvent.StartStage(0.0), BalanceEvent.Tick(hold), BalanceEvent.StartStage(20.0), BalanceEvent.Lost(20 + hold / 2)))
        assertEquals(StageResult(2, hold / 2, false), lost.results[1])
        assertTrue(lost.finished && lost.current == null)
    }

    @Test
    fun `balance clamps late ticks, finishes after the last stage, ignores stray events`() {
        assertEquals(hold, run(listOf(BalanceEvent.StartStage(0.0), BalanceEvent.Tick(hold * 3))).results[0].heldS, 0.0)
        val all = run(timer.balance.stages.indices.flatMap { listOf(BalanceEvent.StartStage(it * 100.0), BalanceEvent.Tick(it * 100.0 + hold)) })
        assertEquals(timer.balance.stages.size, all.results.size)
        assertTrue(all.finished)
        assertEquals(BALANCE_START, run(listOf(BalanceEvent.Lost(1.0))))
        assertEquals(BALANCE_START, run(listOf(BalanceEvent.Tick(1.0))))
        val started = run(listOf(BalanceEvent.StartStage(0.0)))
        assertSame(started, balanceReducer(started, BalanceEvent.StartStage(1.0), timer.balance))
        assertEquals(CurrentStage(0, null), run(listOf(BalanceEvent.StartStage(0.0), BalanceEvent.CancelStage)).current)
        assertEquals(BALANCE_START, run(listOf(BalanceEvent.StartStage(0.0), BalanceEvent.Reset)))
    }

    @Test
    fun `balance verdict compares the tandem stage with the CDC statement`() {
        assertEquals(BalanceVerdict.NotReached, balanceVerdict(BALANCE_START, timer.balance))
        val below = run(
            listOf(
                BalanceEvent.StartStage(0.0), BalanceEvent.Tick(hold), BalanceEvent.StartStage(20.0),
                BalanceEvent.Tick(20 + hold), BalanceEvent.StartStage(40.0), BalanceEvent.Lost(41.0),
            ),
        )
        assertEquals(BalanceVerdict.Below(1.0), balanceVerdict(below, timer.balance))
        val meets = run((0..2).flatMap { listOf(BalanceEvent.StartStage(it * 20.0), BalanceEvent.Tick(it * 20.0 + hold)) })
        assertEquals(BalanceVerdict.Meets(hold), balanceVerdict(meets, timer.balance))
        val stopped = run(listOf(BalanceEvent.StartStage(0.0), BalanceEvent.Lost(2.0)))
        assertEquals(BalanceVerdict.StoppedBefore(1), balanceVerdict(stopped, timer.balance))
    }

    @Test
    fun `tug verdict compares with the CDC reference`() {
        val ref = timer.tug.atRiskIfAtLeastS
        assertEquals(TugVerdict.AT_OR_ABOVE, tugVerdict(ref, timer.tug))
        assertEquals(TugVerdict.BELOW, tugVerdict(ref / 2, timer.tug))
    }
}
