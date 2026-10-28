package app.sthira.chairstand

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun ResultCard(result: ResultView, durationS: Double, normsNote: String?, onRunAgain: () -> Unit) {
    Banner(BannerKind.Success) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading("Result", size = 22)
            val summary = result.summary
            if (summary == null) {
                Body(result.noCount ?: ChairStandResults.NO_COUNT_BEFORE_GO, size = 18, bold = true)
            } else {
                Score(summary, durationS)
                RepChips(summary.events)
                Rejected(summary)
                result.verdict?.let { Comparison(it) }
                Muted(
                    "Reference: $CDC_CITATION. ${normsNote ?: "US reference values."} Shown for the physiotherapist to " +
                        "interpret; Sthira does not diagnose or predict falls.",
                )
            }
            ActionButton("Run again", onRunAgain, tone = Tone.Secondary)
        }
    }
}

@Composable
private fun Score(summary: ChairStandSummary, durationS: Double) {
    val word = if (summary.full == 1) "stand" else "stands"
    val tail = if (summary.windowComplete) "in ${durationS.toInt()} s" else ", test stopped early: not a CDC score"
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics(mergeDescendants = true) { }) {
        Text("${summary.full}", fontSize = 72.sp, lineHeight = 76.sp, fontWeight = FontWeight.Black, color = Sthira.colors.text)
        Text(" full $word $tail", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Sthira.colors.text, modifier = Modifier.padding(bottom = 12.dp))
    }
}

@Composable
private fun Rejected(summary: ChairStandSummary) {
    Text("Not counted", fontWeight = FontWeight.Bold, color = Sthira.colors.text, fontSize = 18.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("Half stands" to summary.half, "Arms used" to summary.armAssisted, "Unclear on camera" to summary.uncertain).forEach { (term, n) ->
            Surface(
                color = Sthira.colors.bg, shape = CardShape,
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "$term: $n" },
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(term, fontSize = 13.sp, color = Sthira.colors.muted)
                    Text("$n", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Sthira.colors.text)
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
        is Verdict.Compared -> Banner(BannerKind.Plain) {
            BannerText("Next to the CDC reference", color = Sthira.colors.text)
            val words = if (verdict.belowAverage) "Below the CDC average for this age and sex" else "At or above the CDC average for this age and sex"
            Body(words, size = 18, bold = true)
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
        Text(item.fileName, fontSize = 14.sp, color = Sthira.colors.text, fontWeight = FontWeight.SemiBold)
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
