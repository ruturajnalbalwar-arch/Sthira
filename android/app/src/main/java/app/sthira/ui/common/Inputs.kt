package app.sthira.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import app.sthira.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

/** Tag on the check mark of the selected choice chip, for the on-device tests. */
const val CHOICE_CHECK_TAG = "choice-check"

@Composable
fun FieldError(error: String?) {
    if (error != null) Text(error, color = Sthira.colors.danger, style = SthiraType.label)
}

/**
 * A labelled group of single-choice chips (TalkBack reads them as radio buttons). The selected
 * chip shows a check mark, so the state never depends on colour alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceGroup(
    label: String,
    options: List<T>,
    selected: T?,
    text: (T) -> String,
    onSelect: (T) -> Unit,
    error: String? = null,
    help: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(label, style = SthiraType.titleSmall, color = Sthira.colors.text)
        if (help != null) Muted(help)
        FlowRow(
            Modifier.selectableGroup().semantics { if (error != null) error(error) },
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            options.forEach { option -> ChoiceChip(text(option), option == selected, error != null) { onSelect(option) } }
        }
        FieldError(error)
    }
}

@Composable
private fun ChoiceChip(label: String, isSelected: Boolean, hasError: Boolean, onClick: () -> Unit) {
    val c = Sthira.colors
    // Outlined when free; filled with a check mark when selected, so the state is never colour alone.
    NeuSurface(
        modifier = Modifier
            .heightIn(min = MainTarget)
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
        Row(
            Modifier.heightIn(min = MainTarget).padding(horizontal = 16.dp).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(22.dp).testTag(CHOICE_CHECK_TAG))
            Text(label, style = SthiraType.titleSmall)
        }
    }
}

/** A text field on the card colour with a strong outline, help or the error below it. */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String? = null,
    help: String? = null,
    number: Boolean = false,
    singleLine: Boolean = true,
) {
    val c = Sthira.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        NeuSurface(shape = Neu.FieldShape, depth = NeuDepth.Flat, fill = c.surface) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label) },
                isError = error != null,
                singleLine = singleLine,
                textStyle = SthiraType.body,
                shape = Neu.FieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent, focusedContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    unfocusedBorderColor = c.borderStrong, focusedBorderColor = c.primary,
                    unfocusedLabelColor = c.muted, focusedLabelColor = c.primary,
                    errorBorderColor = c.danger, errorLabelColor = c.danger,
                    focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = c.primary,
                ),
                modifier = Modifier.fillMaxWidth().semantics { if (error != null) error(error) },
            )
        }
        if (error != null) FieldError(error)
        else if (help != null) Text(help, style = SthiraType.label, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit, error: String? = null) {
    val c = Sthira.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MainTarget)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
                .semantics { if (error != null) error(error) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // An empty outlined box when off; a filled box with a check mark when on.
            NeuSurface(
                Modifier.size(32.dp), shape = RoundedCornerShape(8.dp),
                fill = if (checked) c.primary else null,
                rim = if (checked) null else c.borderStrong, rimWidth = Neu.StrongEdgeWidth,
            ) {
                if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = c.onPrimary, modifier = Modifier.align(Alignment.Center).size(24.dp))
            }
            Text(text, Modifier.padding(start = 12.dp), color = c.text, style = SthiraType.body)
        }
        FieldError(error)
    }
}

/**
 * An on/off setting as one big row: the label (and [help]) on the left, the word On or Off and a
 * switch on the right. The whole row is the touch target, and TalkBack reads it as a switch.
 */
@Composable
fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, help: String? = null) {
    val c = Sthira.colors
    NeuSurface(
        modifier.fillMaxWidth().heightIn(min = BigTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        shape = Neu.ButtonShape,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = 12.dp).align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(text, style = SthiraType.titleSmall, color = c.text)
                if (help != null) Muted(help)
            }
            Text(
                stringResource(if (checked) R.string.common_on else R.string.common_off),
                style = SthiraType.titleSmall, color = c.text, modifier = Modifier.clearAndSetSemantics { },
            )
            Switch(
                checked = checked, onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary, checkedBorderColor = c.primary,
                    uncheckedThumbColor = c.borderStrong, uncheckedTrackColor = c.surface, uncheckedBorderColor = c.borderStrong,
                ),
            )
        }
    }
}
