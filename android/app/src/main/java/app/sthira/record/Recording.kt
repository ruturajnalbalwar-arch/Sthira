package app.sthira.record

import app.sthira.chairstand.PhaseSpan
import app.sthira.chairstand.RecordingMeta
import app.sthira.engine.PoseFrame
import kotlinx.serialization.json.JsonPrimitive
import java.io.Writer

/** Problems during capture, stored in capture.issues (same names as the web schema). */
enum class CaptureIssue(val wire: String, val text: String) {
    AppHidden("tab-hidden", "The app was in the background during capture, so frames are missing: redo this trial."),
    DetectionFailed("detection-failed", "Pose tracking stopped during capture: redo this trial."),
    CameraEnded("camera-ended", "The camera stopped during capture: redo this trial."),
    FrameLimit("frame-limit", "The recording reached the largest size a file may hold and was stopped at the limit."),
}

data class CaptureInfo(
    val startedAt: String,
    val videoWidth: Int,
    val videoHeight: Int,
    val issues: List<CaptureIssue>,
) {
    val model: String get() = RecordingFormat.MODEL
    val tasksVisionVersion: String get() = RecordingFormat.TASKS_VISION_VERSION
}

object RecordingFormat {
    const val SCHEMA = "sthira.recording.v1"
    const val MODEL = "pose_landmarker_full"
    const val TASKS_VISION_VERSION = "1.1.1"
    const val FRAME_CLOCK = "capture-time"
    const val TRIAL = "chair-stand-30s"
    const val LANDMARKS = 33
    /** Ten minutes at 60 fps, the same cap as the web schema (MAX_FRAMES). */
    const val MAX_FRAMES = 60 * 60 * 10
}

/**
 * Writes a sthira.recording.v1 JSON file (web/src/lib/record/schema.ts). Validates first and never
 * writes a file the fitter would reject. Pure apart from the [Writer] it is given.
 */
object RecordingWriter {
    fun validate(phases: List<PhaseSpan>, frames: List<PoseFrame>): List<String> {
        val errors = mutableListOf<String>()
        if (frames.size > RecordingFormat.MAX_FRAMES) errors += "frames: more than ${RecordingFormat.MAX_FRAMES}"
        frames.forEachIndexed { i, f ->
            if (!f.t.isFinite() || f.t < 0) errors += "frames.$i.t: must be a finite number >= 0"
            if (i > 0 && f.t <= frames[i - 1].t) errors += "frames.$i.t: timestamps must strictly increase"
            if (f.landmarks.isNotEmpty() && f.landmarks.size != RecordingFormat.LANDMARKS) errors += "frames.$i.landmarks: need 0 or 33"
            if (f.landmarks.any { !(it.x.isFinite() && it.y.isFinite() && it.z.isFinite() && it.visibility.isFinite()) }) {
                errors += "frames.$i.landmarks: values must be finite"
            }
        }
        phases.forEachIndexed { i, p ->
            if (p.phase.wireName == null) errors += "phases.$i: not a recordable phase"
            if (p.endT < p.startT || p.startT < 0) errors += "phases.$i: a phase cannot end before it starts"
        }
        return errors.take(MAX_LISTED)
    }

    fun write(out: Writer, meta: RecordingMeta, capture: CaptureInfo, phases: List<PhaseSpan>, frames: List<PoseFrame>) {
        out.write("{\"schema\":${str(RecordingFormat.SCHEMA)},\"meta\":")
        writeMeta(out, meta)
        out.write(",\"capture\":")
        writeCapture(out, capture)
        out.write(",\"phases\":[")
        phases.forEachIndexed { i, p ->
            if (i > 0) out.write(",")
            out.write("{\"phase\":${str(p.phase.wireName.orEmpty())},\"startT\":${num(p.startT)},\"endT\":${num(p.endT)}}")
        }
        out.write("],\"frames\":[")
        frames.forEachIndexed { i, f ->
            if (i > 0) out.write(",")
            writeFrame(out, f)
        }
        out.write("]}")
    }

    /** File name base: code, trial, clothing and a compact UTC timestamp. No personal data. */
    fun fileBase(meta: RecordingMeta, startedAtIso: String): String {
        val stamp = startedAtIso.replace(Regex("[-:]"), "").replace(Regex("\\.\\d+Z$"), "Z")
        return listOf(meta.participantCode, RecordingFormat.TRIAL, meta.clothing, stamp).joinToString("_")
    }

    private const val MAX_LISTED = 10

    private fun str(s: String): String = JsonPrimitive(s).toString()
    private fun num(d: Double): String = JsonPrimitive(d).toString()

    private fun writeMeta(out: Writer, m: RecordingMeta) {
        out.write(
            "{\"participantCode\":${str(m.participantCode)},\"ageBand\":${str(m.ageBand)},\"clothing\":${str(m.clothing)}," +
                "\"trial\":${str(RecordingFormat.TRIAL)},\"viewAngle\":${str(m.viewAngle)}," +
                "\"cameraHeightCm\":${num(m.cameraHeightCm)},\"cameraDistanceCm\":${num(m.cameraDistanceCm)}," +
                "\"room\":${str(m.room)},\"notes\":${str(m.notes)},\"consentConfirmed\":true}",
        )
    }

    private fun writeCapture(out: Writer, c: CaptureInfo) {
        val issues = c.issues.distinct().joinToString(",") { str(it.wire) }
        out.write(
            "{\"startedAt\":${str(c.startedAt)},\"videoWidth\":${c.videoWidth},\"videoHeight\":${c.videoHeight}," +
                "\"model\":${str(c.model)},\"tasksVisionVersion\":${str(c.tasksVisionVersion)}," +
                "\"frameClock\":${str(RecordingFormat.FRAME_CLOCK)},\"issues\":[$issues]}",
        )
    }

    private fun writeFrame(out: Writer, f: PoseFrame) {
        out.write("{\"t\":${num(f.t)},\"landmarks\":[")
        f.landmarks.forEachIndexed { i, l ->
            if (i > 0) out.write(",")
            out.write("{\"x\":${num(l.x)},\"y\":${num(l.y)},\"z\":${num(l.z)},\"visibility\":${num(l.visibility)}}")
        }
        out.write("]}")
    }
}
