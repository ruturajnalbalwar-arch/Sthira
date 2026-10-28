package app.sthira.engine

import app.sthira.engine.Fixtures.Rep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behaviour the golden file cannot express (NaN inputs, bad timestamps), mirroring chairStand.edge.test.ts. */
class EdgeCaseTest {
    private val fps = 30.0
    private val sessionS = 40.0
    private val oneRep = listOf(Rep(2.0), Rep(6.0))

    private fun patchKeys(frames: List<PoseFrame>, from: Double, to: Double, keys: List<Int>, patch: (Landmark) -> Landmark) =
        frames.map { f ->
            if (f.t < from || f.t > to) f else f.copy(landmarks = f.landmarks.mapIndexed { i, lm -> if (i in keys) patch(lm) else lm })
        }

    private fun kinds(frames: List<PoseFrame>) = summarize(Fixtures.run(frames), Fixtures.ctx()).events.map { it.kind }

    @Test
    fun `NaN visibility counts as low visibility and makes the rep uncertain`() {
        val frames = patchKeys(Fixtures.session(fps, sessionS, oneRep), 2.0, 4.5, KEY_LANDMARKS) { it.copy(visibility = Double.NaN) }
        assertEquals(listOf(RepKind.UNCERTAIN, RepKind.FULL), kinds(frames))
    }

    @Test
    fun `a NaN coordinate never poisons the smoothed head signal`() {
        val frames = patchKeys(Fixtures.session(fps, sessionS, oneRep), 2.5, 3.5, listOf(NOSE)) { it.copy(x = Double.NaN) }
        val state = Fixtures.run(frames)
        assertTrue(state.smoothedHeadY?.isFinite() == true)
        assertEquals(listOf(RepKind.UNCERTAIN, RepKind.FULL), kinds(frames))
    }

    @Test
    fun `frames with no landmarks inside a rep make it uncertain`() {
        val frames = Fixtures.session(fps, sessionS, oneRep).map { if (it.t in 2.5..3.5) it.copy(landmarks = emptyList()) else it }
        assertEquals(RepKind.UNCERTAIN, kinds(frames).first())
    }

    @Test
    fun `non-increasing and non-finite timestamps are dropped and counted`() {
        val frames = Fixtures.session(fps, 5.0, emptyList())
        val bad = frames.take(10) + frames[3] + frames[10].copy(t = Double.NaN) + frames[10].copy(t = Double.POSITIVE_INFINITY) + frames.drop(10)
        val summary = summarize(Fixtures.run(bad), Fixtures.ctx())
        assertEquals(3, summary.droppedFrames)
        assertEquals(fps, summary.effectiveFps, 1e-6)
    }

    @Test
    fun `a gap longer than maxGapS is counted and adds unseen time to the open rep`() {
        val ctx = Fixtures.ctx()
        val frames = Fixtures.session(fps, sessionS, listOf(Rep(2.0, holdS = 2.0)))
        val rising = frames.takeWhile { it.t < 2.5 }.fold(initialState()) { s, f -> step(s, f, ctx) }
        assertEquals(Phase.RISING, rising.phase)
        val gapS = Fixtures.TEST_PARAMS.maxGapS * 2
        val after = step(rising, frames[100].copy(t = (rising.lastT ?: 0.0) + gapS), ctx)
        assertEquals(1, after.gaps)
        assertEquals((rising.rep?.repS ?: Double.NaN) + gapS, after.rep?.repS ?: Double.NaN, 1e-9)
        assertEquals((rising.rep?.lowVisibilityS ?: Double.NaN) + gapS, after.rep?.lowVisibilityS ?: Double.NaN, 1e-9)
    }

    @Test
    fun `the window is anchored to testStartT and progress is exposed for the UI`() {
        val state = Fixtures.run(Fixtures.session(fps, 45.0, listOf(Rep(2.0), Rep(10.0), Rep(36.0))), start = initialState(5.0))
        val summary = summarize(state, Fixtures.ctx())
        assertEquals(1, summary.events.size)
        assertTrue(summary.events[0].startT > 10)
        assertTrue(state.progress != null && state.lastT != null)
    }

    @Test
    fun `finalize is a no-op without an open rep and the early-stop credit is withheld`() {
        val ctx = Fixtures.ctx()
        val state = Fixtures.run(Fixtures.session(fps, 10.0, oneRep))
        assertSame(state, finalize(state, ctx))
        val stopped = summarize(Fixtures.run(Fixtures.session(fps, 12.0, listOf(Rep(10.5, peak = 0.6, riseS = 0.5, holdS = 5.0)))), ctx)
        assertFalse(stopped.windowComplete)
        assertTrue(stopped.events.isEmpty())
    }

    @Test
    fun `makeContext rejects non-finite params and bad calibration`() {
        val params = Fixtures.TEST_PARAMS.copy(maxGapS = Double.NaN)
        val result = makeContext(Fixtures.GEOMETRY, Calibration(100.0, 200.0, 0.0), params)
        assertEquals(
            ContextResult.Failed(
                listOf(
                    "calibration: seated head must be below standing head (range > 0)",
                    "calibration: body scale must be positive",
                    "params: not finite: maxGapS",
                ),
            ),
            result,
        )
    }

    @Test
    fun `coveringFrames adds the nearest frame on each side`() {
        val frames = listOf(1.0, 2.0, 3.0).map { Fixtures.makeFrame(it, Fixtures.SEATED_Y) }
        assertEquals(listOf(1.0, 2.0, 3.0), coveringFrames(frames, 0.0, 9.0).map { it.t })
        assertEquals(listOf(1.0, 2.0, 3.0), coveringFrames(frames, 1.5, 2.5).map { it.t })
        assertEquals(listOf(2.0, 3.0), coveringFrames(frames, 2.0, 2.5).map { it.t })
        assertEquals(emptyList<PoseFrame>(), coveringFrames(emptyList(), 0.0, 1.0))
    }

    @Test
    fun `JS hypot matches V8 special cases`() {
        assertEquals(5.0, jsHypot(3.0, -4.0), 0.0)
        assertEquals(Double.POSITIVE_INFINITY, jsHypot(Double.NaN, Double.NEGATIVE_INFINITY), 0.0)
        assertTrue(jsHypot(Double.NaN, 1.0).isNaN())
        assertEquals(0.0, jsHypot(0.0, -0.0), 0.0)
    }
}
