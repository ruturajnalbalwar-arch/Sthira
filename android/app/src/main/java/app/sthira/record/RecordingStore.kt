package app.sthira.record

import android.content.ContentResolver
import android.net.Uri
import app.sthira.chairstand.PhaseSpan
import app.sthira.chairstand.RecordingMeta
import app.sthira.engine.PoseFrame
import java.io.File
import java.io.IOException

/** A recording kept in app-private storage until the user exports or deletes it. */
data class SavedRecording(val fileName: String, val sizeBytes: Long)

sealed interface SaveResult {
    data class Saved(val recording: SavedRecording) : SaveResult
    data class Invalid(val errors: List<String>) : SaveResult
    data class Failed(val message: String) : SaveResult
}

/**
 * Landmark recordings live only in the app's private files directory (not backed up: the manifest
 * disables backup). Nothing goes to shared storage or the gallery; export happens only through a
 * document the user picks with the system file picker (SAF), so no storage permission is needed.
 */
class RecordingStore(filesDir: File) {
    private val dir = File(filesDir, DIR)

    fun list(): List<SavedRecording> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { SavedRecording(it.name, it.length()) }
            .orEmpty()

    fun save(meta: RecordingMeta, capture: CaptureInfo, phases: List<PhaseSpan>, frames: List<PoseFrame>): SaveResult {
        val errors = RecordingWriter.validate(phases, frames)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        return try {
            if (!dir.exists() && !dir.mkdirs()) return SaveResult.Failed("Could not create the private recordings folder.")
            val file = File(dir, RecordingWriter.fileBase(meta, capture.startedAt) + SUFFIX)
            val partial = File(dir, file.name + ".part")
            partial.bufferedWriter().use { RecordingWriter.write(it, meta, capture, phases, frames) }
            if (!partial.renameTo(file)) {
                partial.delete()
                return SaveResult.Failed("Could not finish writing the recording file.")
            }
            SaveResult.Saved(SavedRecording(file.name, file.length()))
        } catch (e: IOException) {
            SaveResult.Failed("Could not write the recording (the phone may be out of space).")
        }
    }

    /** Copies a private recording into the document the user chose. */
    fun export(fileName: String, resolver: ContentResolver, target: Uri): Boolean {
        val file = resolve(fileName) ?: return false
        return try {
            val out = resolver.openOutputStream(target, "w") ?: return false
            out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
            true
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    fun delete(fileName: String): Boolean = resolve(fileName)?.delete() ?: false

    /** Only plain names inside the private folder; never a path from outside. */
    private fun resolve(fileName: String): File? {
        if (fileName.contains('/') || fileName.contains('\\') || !fileName.endsWith(SUFFIX)) return null
        return File(dir, fileName).takeIf { it.isFile }
    }

    private companion object {
        const val DIR = "recordings"
        const val SUFFIX = ".json"
    }
}
