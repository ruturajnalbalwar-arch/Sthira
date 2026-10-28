package app.sthira.record

import app.sthira.chairstand.PhaseSpan
import app.sthira.chairstand.RecordingMeta
import app.sthira.chairstand.Stage
import app.sthira.engine.Landmark
import app.sthira.engine.PoseFrame
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class RecordingWriterTest {
    private val meta = RecordingMeta("P07", "70-74", "saree", "45-degree", 90.0, 250.0, "living \"room\"", "")
    private val capture = CaptureInfo("2026-10-28T10:00:00.123Z", 480, 640, listOf(CaptureIssue.FrameLimit))
    private val person = List(33) { Landmark(0.5, 0.25, -0.1, 0.9) }
    private val frames = listOf(PoseFrame(0.0, person), PoseFrame(0.04, emptyList()))
    private val phases = listOf(PhaseSpan(Stage.CalibrationStand, 0.0, 5.0), PhaseSpan(Stage.CalibrationSit, 5.0, 10.0))

    @Test
    fun `writes a parseable v1 file with the web field names`() {
        val out = StringWriter()
        RecordingWriter.write(out, meta, capture, phases, frames)
        val root = Json.parseToJsonElement(out.toString()).jsonObject
        assertEquals("sthira.recording.v1", root.getValue("schema").jsonPrimitive.content)
        val cap = root.getValue("capture").jsonObject
        assertEquals("capture-time", cap.getValue("frameClock").jsonPrimitive.content)
        assertEquals(setOf("startedAt", "videoWidth", "videoHeight", "model", "tasksVisionVersion", "frameClock", "issues"), cap.keys)
        assertEquals("frame-limit", cap.getValue("issues").jsonArray.single().jsonPrimitive.content)
        assertEquals("living \"room\"", root.getValue("meta").jsonObject.getValue("room").jsonPrimitive.content)
        assertEquals("calibration-stand", root.getValue("phases").jsonArray[0].jsonObject.getValue("phase").jsonPrimitive.content)
        val written = root.getValue("frames").jsonArray
        assertEquals(33, written[0].jsonObject.getValue("landmarks").jsonArray.size)
        assertEquals(0, written[1].jsonObject.getValue("landmarks").jsonArray.size)
    }

    @Test
    fun `validation rejects non-increasing timestamps and partial landmark lists`() {
        val bad = listOf(PoseFrame(1.0, person), PoseFrame(1.0, person.take(10)))
        val errors = RecordingWriter.validate(phases, bad)
        assertTrue(errors.any { it.contains("strictly increase") })
        assertTrue(errors.any { it.contains("0 or 33") })
        assertTrue(RecordingWriter.validate(phases, frames).isEmpty())
    }

    @Test
    fun `file name holds only the code, trial, clothing and time`() {
        assertEquals("P07_chair-stand-30s_saree_20261028T100000Z", RecordingWriter.fileBase(meta, capture.startedAt))
    }
}
