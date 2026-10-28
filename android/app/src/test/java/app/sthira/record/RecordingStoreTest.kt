package app.sthira.record

import app.sthira.chairstand.PhaseSpan
import app.sthira.chairstand.RecordingMeta
import app.sthira.chairstand.Stage
import app.sthira.engine.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val meta = RecordingMeta("P07", "70-74", "saree", "side", 90.0, 250.0, "room", "")
    private val capture = CaptureInfo("2026-10-28T10:00:00.000Z", 480, 640, emptyList())
    private val phases = listOf(PhaseSpan(Stage.Test, 0.0, 1.0))

    @Test
    fun `saves, lists and deletes a private recording`() {
        val store = RecordingStore(tmp.root)
        val saved = store.save(meta, capture, phases, listOf(PoseFrame(0.0, emptyList()))) as SaveResult.Saved
        assertEquals(listOf(saved.recording.fileName), store.list().map { it.fileName })
        assertTrue(store.delete(saved.recording.fileName))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `refuses invalid frames and paths outside the folder`() {
        val store = RecordingStore(tmp.root)
        val bad = listOf(PoseFrame(1.0, emptyList()), PoseFrame(0.5, emptyList()))
        assertTrue(store.save(meta, capture, phases, bad) is SaveResult.Invalid)
        assertFalse(store.delete("../secrets.json"))
    }
}
