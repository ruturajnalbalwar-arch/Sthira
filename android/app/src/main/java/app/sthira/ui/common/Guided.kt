package app.sthira.ui.common

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sthira.R
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

/** Tag on the check mark of the selected tile, for the on-device tests. */
const val TILE_CHECK_TAG = "tile-check"

private val TileMinHeight = 80.dp

/**
 * A big single-choice grid: [columns] tiles per row (2 by default), large text, an optional icon on
 * each. Free tiles are outlined; the selected one is filled with a check mark, so the state never
 * depends on colour alone. TalkBack reads the tiles as radio buttons. Rows grow with long labels.
 */
@Composable
fun <T> ChoiceTileGrid(
    options: List<T>,
    selected: T?,
    text: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ((T) -> ImageVector?)? = null,
    columns: Int = 2,
    error: String? = null,
) {
    val perRow = columns.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s)) {
        if (label != null) {
            Text(label, style = SthiraType.titleSmall, color = Sthira.colors.text, modifier = Modifier.semantics { heading() })
        }
        Column(
            Modifier.selectableGroup().semantics { if (error != null) error(error) },
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            options.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    row.forEach { option ->
                        ChoiceTile(
                            text(option), icon?.invoke(option), option == selected, error != null,
                            Modifier.weight(1f),
                        ) { onSelect(option) }
                    }
                    // Keeps the last row's tiles the same width as the others.
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        FieldError(error)
    }
}

@Composable
private fun ChoiceTile(
    label: String, icon: ImageVector?, isSelected: Boolean, hasError: Boolean, modifier: Modifier, onClick: () -> Unit,
) {
    val c = Sthira.colors
    NeuSurface(
        modifier.heightIn(min = TileMinHeight)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        shape = Neu.ButtonShape,
        fill = if (isSelected) c.primary else null,
        rim = when {
            hasError -> c.danger
            isSelected -> null
            else -> c.borderStrong
        },
        rimWidth = Neu.StrongEdgeWidth,
        contentColor = if (isSelected) c.onPrimary else c.text,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp).align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(label, style = SthiraType.title, textAlign = TextAlign.Center)
        }
        if (isSelected) {
            Icon(
                Icons.Filled.Check, contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).testTag(TILE_CHECK_TAG),
            )
        }
    }
}

/**
 * "Step 2 of 3" with a row of dots: done and current steps are filled, the current one is wider,
 * later ones are outlined. [current] counts from 1. TalkBack reads only the words.
 */
@Composable
fun StepIndicator(current: Int, total: Int, modifier: Modifier = Modifier) {
    val c = Sthira.colors
    val count = total.coerceAtLeast(1)
    val step = current.coerceIn(1, count)
    val words = stringResource(R.string.common_step_of, step, count)
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = words },
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text(words, style = SthiraType.titleSmall, color = c.text, modifier = Modifier.clearAndSetSemantics { })
        Row(
            Modifier.clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (1..count).forEach { i ->
                val dot = Modifier.height(DOT).width(if (i == step) DOT_CURRENT else DOT)
                Box(
                    if (i <= step) dot.background(c.primary, CircleShape)
                    else dot.border(Neu.StrongEdgeWidth, c.borderStrong, CircleShape),
                )
            }
        }
    }
}

private val DOT = 14.dp
private val DOT_CURRENT = 32.dp
