package app.sthira.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseMathTest {
    private val person = List(33) { RawLandmark(0.2f, 0.4f, -0.1f, 0.8f) }

    @Test
    fun `maps 33 landmarks and unmirrors the front camera`() {
        val plain = PoseMapper.toLandmarks(person, unmirror = false)
        assertEquals(33, plain.size)
        assertEquals(0.2, plain[0].x, 1e-6)
        val unmirrored = PoseMapper.toLandmarks(person, unmirror = true)
        assertEquals(0.8, unmirrored[0].x, 1e-6)
        assertEquals(0.4, unmirrored[0].y, 1e-6)
    }

    @Test
    fun `missing visibility becomes 0 and bad input means no person`() {
        val noVis = PoseMapper.toLandmarks(person.map { it.copy(visibility = null) }, false)
        assertEquals(0.0, noVis[5].visibility, 0.0)
        assertTrue(PoseMapper.toLandmarks(null, false).isEmpty())
        assertTrue(PoseMapper.toLandmarks(person.take(10), false).isEmpty())
        assertTrue(PoseMapper.toLandmarks(person.mapIndexed { i, l -> if (i == 3) l.copy(x = Float.NaN) else l }, false).isEmpty())
    }

    @Test
    fun `frame clock keeps capture spacing and drops non-increasing frames`() {
        val first = FrameClock().map(captureNs = 1_000, nowRealtimeNs = 50_000)
        assertEquals(50_000L, first.realtimeNs)
        val second = first.clock.map(captureNs = 34_000, nowRealtimeNs = 99_999)
        assertEquals(83_000L, second.realtimeNs)
        val repeat = second.clock.map(captureNs = 34_000, nowRealtimeNs = 120_000)
        assertNull(repeat.realtimeNs)
    }

    @Test
    fun `fps meter averages over the last second`() {
        val meter = (0..30).fold(FpsMeter()) { m, i -> m.add(i / 30.0) }
        assertEquals(30.0, meter.fps ?: 0.0, 0.5)
        assertNull(FpsMeter().add(0.0).fps)
    }

    @Test
    fun `fill center crops the longer side and centres the image`() {
        // 480x640 image on a 1080x1080 view: scale to width, crop top and bottom.
        val t = FillCenterTransform.of(480, 640, 1080f, 1080f) ?: error("no transform")
        assertEquals(2.25f, t.scale, 1e-4f)
        assertEquals(540f, t.x(0.5f), 1e-3f)
        assertEquals(540f, t.y(0.5f), 1e-3f)
        assertEquals(-180f, t.y(0f), 1e-3f)
        assertNull(FillCenterTransform.of(0, 640, 10f, 10f))
    }
}
