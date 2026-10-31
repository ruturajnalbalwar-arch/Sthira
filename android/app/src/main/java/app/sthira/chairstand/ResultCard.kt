package app.sthira.chairstand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sthira.R
import app.sthira.engine.ChairStandSummary
import app.sthira.engine.norms.CDC_CITATION
import app.sthira.engine.norms.NormSex
import app.sthira.engine.norms.Verdict
import app.sthira.ui.common.Banner
import app.sthira.ui.common.BannerKind
import app.sthira.ui.common.BannerText
import app.sthira.ui.common.Body
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.CardShape
import app.sthira.ui.common.Heading
import app.sthira.ui.common.Muted
import app.sthira.ui.common.Neu
import app.sthira.ui.common.NeuDepth
import app.sthira.ui.common.NeuSurface
import app.sthira.ui.common.asString
import app.sthira.ui.theme.DisplaySize
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

/**
 * The finished run, in reading order: the big number, then the CDC comparison sentence, then the
 * attempts that did not count, then the per-attempt chips and the reference. A raised surface: result
 * numbers are content. "Run again" lives in the bottom action bar.
 */
@Composable
fun ResultCard(result: ResultView, durationS: Double, normsNote: String?) {
    NeuSurface(Modifier.fillMaxWidth(), shape = Neu.CardShape) {
        Column(Modifier.fillMaxWidth().padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Heading(stringResource(R.string.cs_result_title), size = 20)
            // No early return here: returning out of the inline Column lambda corrupts the composer's
            // group stack with this Compose compiler (crash "Stack.pop Index -1" when the run finishes).
            val summary = result.summary
            if (summary == null) {
                Body(result.noCount?.asString() ?: stringResource(R.string.cs_no_count_before_go), size = 18)
            } else {
                Score(summary, durationS)
                result.verdict?.let { Comparison(it, result) }
                Rejected(summary)
                RepChips(summary.events)
                // The citation and the note come from the engine and norms.json and are not translated.
                Muted(stringResource(R.string.cs_result_reference, CDC_CITATION, normsNote ?: stringResource(R.string.cs_result_us_reference)))
            }
        }
    }
}

@Composable
private fun Score(summary: ChairStandSummary, durationS: Double) {
    val size = with(LocalDensity.current) { DisplaySize.result.toSp() }
    Column(Modifier.semantics(mergeDescendants = true) { }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${summary.full}", style = SthiraType.numeric.copy(fontSize = size, lineHeight = size), color = Sthira.colors.text)
            Text(
                stringResource(R.string.cs_stands_word_spaced, pluralStringResource(R.plurals.cs_result_full_stands, summary.full)),
                style = SthiraType.title, color = Sthira.colors.text, modifier = Modifier.padding(bottom = 14.dp),
            )
        }
        if (summary.windowComplete) {
            Muted(stringResource(R.string.cs_result_in_seconds, durationS.toInt()))
        } else {
            Text(stringResource(R.string.cs_result_stopped_early), style = SthiraType.titleSmall, color = Sthira.colors.warn)
        }
    }
}

@Composable
private fun Rejected(summary: ChairStandSummary) {
    val terms = listOf(
        stringResource(R.string.cs_result_half) to summary.half,
        stringResource(R.string.cs_rep_arms) to summary.armAssisted,
        stringResource(R.string.cs_rep_unclear) to summary.uncertain,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(
            stringResource(R.string.cs_result_not_counted), style = SthiraType.titleSmall, color = Sthira.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
            terms.forEach { (term, n) ->
                val spoken = stringResource(R.string.cs_result_term_count, term, n)
                NeuSurface(
                    Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = spoken },
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
private fun Comparison(verdict: Verdict, result: ResultView) {
    when (verdict) {
        is Verdict.NotCompared -> Banner(BannerKind.Warn) {
            BannerText(stringResource(R.string.cs_result_no_comparison))
            // The engine's reasons are only a fallback (debug previews build a ResultView by hand).
            val reasons = result.notComparedReasons.map { it.asString() }.ifEmpty { verdict.reasons }
            BulletList(reasons)
        }
        is Verdict.Compared -> Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            val words = stringResource(if (verdict.belowAverage) R.string.cs_result_below else R.string.cs_result_at_or_above)
            Text(words, style = SthiraType.title, color = Sthira.colors.text)
            val row = result.norm as? NormChoice.Row
            if (row == null) {
                Body(verdict.sentence)
            } else {
                val e = row.entry
                val id = if (e.sex == NormSex.MALE) R.string.cs_result_sentence_men else R.string.cs_result_sentence_women
                Body(stringResource(id, e.ageMin, e.ageMax, verdict.cutoff, verdict.full))
                row.note?.let { Muted(it.asString()) }
            }
        }
    }
}
