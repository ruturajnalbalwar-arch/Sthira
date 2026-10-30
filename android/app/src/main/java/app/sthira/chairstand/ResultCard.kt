package app.sthira.chairstand

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import app.sthira.ui.common.Neu
import app.sthira.ui.common.NeuDepth
import app.sthira.ui.common.NeuSurface
import app.sthira.ui.theme.DisplaySize
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.SthiraType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sthira.engine.ChairStandSummary
import app.sthira.engine.norms.CDC_CITATION
import app.sthira.engine.norms.Verdict
import app.sthira.record.SavedRecording
import app.sthira.ui.common.ActionButton
import app.sthira.ui.common.Banner
import app.sthira.ui.common.BannerKind
import app.sthira.ui.common.BannerText
import app.sthira.ui.common.Body
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.CardShape
import app.sthira.ui.common.Heading
import app.sthira.ui.common.Muted
import app.sthira.ui.common.Tone
import app.sthira.ui.theme.Sthira

/**
 * The finished run, in reading order: the big number, then the CDC comparison sentence, then the
 * attempts that did not count, then the per-attempt chips and the reference. A raised surface: result
 * numbers are content. "Run again" lives in the bottom action bar.
 */
@Composable
fun ResultCard(result: ResultView, durationS: Double, normsNote: String?) {
    NeuSurface(Modifier.fillMaxWidth(), shape = Neu.CardShape) {
        Column(Modifier.fillMaxWidth().padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Heading("Result", size = 20)
            // No early return here: returning out of the inline Column lambda corrupts the composer's
            // group stack with this Compose compiler (crash "Stack.pop Index -1" when the run finishes).
            val summary = result.summary
            if (summary == null) {
                Body(result.noCount ?: ChairStandResults.NO_COUNT_BEFORE_GO, size = 18)
            } else {
                Score(summary, durationS)
                result.verdict?.let { Comparison(it) }
                Rejected(summary)
                RepChips(summary.events)
                Muted(
                    "Reference: $CDC_CITATION. ${normsNote ?: "US reference values."} For the physiotherapist to " +
                        "interpret; Sthira does not diagnose or predict falls.",
                )
            }
        }
    }
}

@Composable
private fun Score(summary: ChairStandSummary, durationS: Double) {
    val word = if (summary.full == 1) "stand" else "stands"
    val size = with(LocalDensity.current) { DisplaySize.result.toSp() }
    Column(Modifier.semantics(mergeDescendants = true) { }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${summary.full}", style = SthiraType.numeric.copy(fontSize = size, lineHeight = size), color = Sthira.colors.text)
            Text(" full $word", style = SthiraType.title, color = Sthira.colors.text, modifier = Modifier.padding(bottom = 14.dp))
        }
        if (summary.windowComplete) {
            Muted("in ${durationS.toInt()} s")
        } else {
            Text("Test stopped early: not a CDC score", style = SthiraType.titleSmall, color = Sthira.colors.warn)
        }
    }
}

@Composable
private fun Rejected(summary: ChairStandSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text("Not counted", style = SthiraType.titleSmall, color = Sthira.colors.text, modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
            listOf("Half stands" to summary.half, "Arms used" to summary.armAssisted, "Unclear" to summary.uncertain).forEach { (term, n) ->
                NeuSurface(
                    Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "$term: $n" },
                    shape = CardShape, depth = NeuDepth.Inset, elevation = 3.dp,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("$n", style = SthiraType.title, color = Sthira.colors.text)
                        Text(term, style = SthiraType.label, color = Sthira.colors.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Comparison(verdict: Verdict) {
    when (verdict) {
        is Verdict.NotCompared -> Banner(BannerKind.Warn) {
            BannerText("No CDC comparison for this run")
            BulletList(verdict.reasons)
        }
        is Verdict.Compared -> Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            val words = if (verdict.belowAverage) "Below the CDC average for this age and sex" else "At or above the CDC average for this age and sex"
            Text(words, style = SthiraType.title, color = Sthira.colors.text)
            Body(verdict.sentence)
        }
    }
}

/** The optional landmark file for this run. */
@Composable
fun RecordingCard(recording: RecordingView, onExport: (String) -> Unit, onDelete: (String) -> Unit) {
    when (recording) {
        RecordingView.Off -> Unit
        RecordingView.Saving -> Banner(BannerKind.Plain) { BannerText("Saving the landmark recording...") }
        is RecordingView.Invalid -> Banner(BannerKind.Danger) {
            BannerText("The recording failed validation and was not saved")
            BulletList(recording.errors, mono = true)
        }
        is RecordingView.Failed -> Banner(BannerKind.Danger) { BannerText(recording.message) }
        is RecordingView.Saved -> Banner(BannerKind.Plain) {
            BannerText("Landmark recording saved inside the app", color = Sthira.colors.text)
            Muted("It stays private to Sthira on this phone until you export it.")
            SavedRow(recording.recording, onExport, onDelete)
        }
    }
}

@Composable
fun SavedRecordingsList(items: List<SavedRecording>, onExport: (String) -> Unit, onDelete: (String) -> Unit) {
    if (items.isEmpty()) return
    Banner(BannerKind.Plain) {
        BannerText("Recordings kept on this phone (${items.size})", color = Sthira.colors.text)
        Muted("Export each one to your study folder, then delete it here.")
        items.forEach { SavedRow(it, onExport, onDelete) }
    }
}

@Composable
private fun SavedRow(item: SavedRecording, onExport: (String) -> Unit, onDelete: (String) -> Unit) {
    var confirm by remember(item.fileName) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(item.fileName, style = SthiraType.label, color = Sthira.colors.text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Export", { onExport(item.fileName) }, Modifier.weight(1f))
            if (confirm) {
                ActionButton("Yes, delete", { onDelete(item.fileName) }, Modifier.weight(1f), tone = Tone.Danger)
            } else {
                ActionButton("Delete", { confirm = true }, Modifier.weight(1f), tone = Tone.Secondary)
            }
        }
    }
}

/** Opens the system "save as" picker (SAF): no storage permission, and only where the user chooses. */
@Composable
fun rememberExporter(onPicked: (fileName: String, uri: android.net.Uri) -> Unit): (String) -> Unit {
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val name = pending
        pending = null
        if (uri != null && name != null) onPicked(name, uri)
    }
    return { fileName ->
        pending = fileName
        launcher.launch(fileName)
    }
}
