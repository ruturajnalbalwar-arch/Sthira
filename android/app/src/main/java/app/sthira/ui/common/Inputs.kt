package app.sthira.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

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
    val bg by animateColorAsState(if (isSelected) c.primary else c.surface, Sthira.motion(), label = "chipBg")
    val outline = when {
        hasError -> c.danger
        isSelected -> c.primary
        else -> c.muted
    }
    Surface(
        shape = CardShape,
        color = bg,
        contentColor = if (isSelected) c.onPrimary else c.text,
        border = BorderStroke(1.dp, outline),
        modifier = Modifier
            .heightIn(min = MinTarget)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            Modifier.heightIn(min = MinTarget).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = SthiraType.label.copy(fontSize = SthiraType.body.fontSize))
        }
    }
}

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
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        singleLine = singleLine,
        textStyle = SthiraType.body,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
        supportingText = (error ?: help)?.let { { Text(it, style = SthiraType.label) } },
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = c.muted, focusedBorderColor = c.primary,
            unfocusedLabelColor = c.muted, focusedLabelColor = c.primary,
            unfocusedSupportingTextColor = c.muted, focusedSupportingTextColor = c.muted,
            errorBorderColor = c.danger, errorLabelColor = c.danger, errorSupportingTextColor = c.danger,
            focusedTextColor = c.text, unfocusedTextColor = c.text, cursorColor = c.primary,
        ),
        modifier = Modifier.fillMaxWidth().semantics { if (error != null) error(error) },
    )
}

@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit, error: String? = null) {
    val c = Sthira.colors
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTarget)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
                .semantics { if (error != null) error(error) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked, onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = c.primary, uncheckedColor = c.muted, checkmarkColor = c.onPrimary),
            )
            Text(text, Modifier.padding(start = 12.dp), color = c.text, style = SthiraType.body)
        }
        FieldError(error)
    }
}
