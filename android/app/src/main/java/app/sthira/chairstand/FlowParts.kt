package app.sthira.chairstand

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sthira.R
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

/** Test tag on the check mark of a selected tile (the selection never depends on colour alone). */
const val TILE_CHECK_TAG = "cs_tile_check"

private val TileShape = RoundedCornerShape(12.dp)
private val BigTileHeight = 72.dp
private val SmallTileHeight = 56.dp
private val TileBorder = 2.dp
private val DotHeight = 8.dp
private val HeaderMaxWidth = 720.dp
private const val COLUMNS = 2

/**
 * "Step 1 of 3" with three bars and the step's name, at the same place on every step. TalkBack
 * reads it as one heading.
 */
@Composable
internal fun StepHeader(step: FlowStep) {
    val c = Sthira.colors
    val name = stringResource(
        when (step) {
            FlowStep.Person -> R.string.cs_step_person
            FlowStep.Camera -> R.string.cs_step_camera
            FlowStep.Result -> R.string.cs_result_title
        },
    )
    val count = FlowStep.entries.size
    val of = stringResource(R.string.cs_step_of, step.number, count)
    val spoken = stringResource(R.string.cs_details_line, of, name)
    Column(
        Modifier.widthIn(max = HeaderMaxWidth).fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s)
            .clearAndSetSemantics {
                heading()
                contentDescription = spoken
            },
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), modifier = Modifier.fillMaxWidth()) {
            FlowStep.entries.forEach { s ->
                val done = s.number <= step.number
                Box(
                    Modifier.weight(1f).height(DotHeight).clip(TileShape)
                        .background(if (done) c.primary else c.surface)
                        .border(1.dp, if (done) c.primary else c.borderStrong, TileShape),
                )
            }
        }
        Text(of, style = SthiraType.label, color = c.muted)
        Text(name, style = SthiraType.title, color = c.text)
    }
}

/**
 * Big single-choice tiles in a 2-column grid (TalkBack reads them as radio buttons). The selected
 * tile is filled, outlined and shows a check mark, so the state never depends on colour alone.
 */
@Composable
internal fun <T> ChoiceTiles(
    label: String,
    options: List<T>,
    selected: T?,
    text: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    help: String? = null,
    extra: T? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(label, style = SthiraType.titleSmall, color = Sthira.colors.text, modifier = Modifier.semantics { heading() })
        if (help != null) Text(help, style = SthiraType.body, color = Sthira.colors.muted)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            options.chunked(COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { option ->
                        Tile(text(option), option == selected, Modifier.weight(1f), big = true) { onSelect(option) }
                    }
                    // A lone last tile keeps the width of one column.
                    repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            // A smaller, full-width choice under the grid (for example "Prefer not to say").
            if (extra != null) Tile(text(extra), extra == selected, Modifier.fillMaxWidth(), big = false) { onSelect(extra) }
        }
    }
}

@Composable
private fun Tile(label: String, isSelected: Boolean, modifier: Modifier, big: Boolean, onClick: () -> Unit) {
    val c = Sthira.colors
    val content = if (isSelected) c.onPrimary else c.text
    Box(
        modifier
            .heightIn(min = if (big) BigTileHeight else SmallTileHeight)
            .clip(TileShape)
            .background(if (isSelected) c.primary else c.surface)
            .border(TileBorder, if (isSelected) c.primary else c.borderStrong, TileShape)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.s, vertical = Space.s),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isSelected) {
                Icon(
                    Icons.Filled.Check, contentDescription = null, tint = content,
                    modifier = Modifier.size(if (big) 26.dp else 20.dp).testTag(TILE_CHECK_TAG),
                )
            }
            Text(
                label, color = content, textAlign = TextAlign.Center,
                style = if (big) SthiraType.title else SthiraType.label.copy(fontSize = SthiraType.body.fontSize),
            )
        }
    }
}
