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
    // Raised when free, pressed in when selected. The selection is never the shadow alone: a check
    // mark, a primary outline and primary text come with it.
    NeuSurface(
        modifier = Modifier
            .heightIn(min = MinTarget)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        shape = Neu.ButtonShape,
        depth = if (isSelected) NeuDepth.Inset else NeuDepth.Raised,
        elevation = 4.dp,
        rim = when {
            hasError -> c.danger
            isSelected -> c.primary
            else -> null
        },
        contentColor = if (isSelected) c.primary else c.text,
    ) {
        Row(
            Modifier.heightIn(min = MinTarget).padding(horizontal = 14.dp).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp).testTag(CHOICE_CHECK_TAG))
            Text(label, style = SthiraType.label.copy(fontSize = SthiraType.body.fontSize))
        }
    }
}

/** A text field set into the surface: a pressed-in well with a 3:1 outline, help or the error below it. */
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
        NeuSurface(shape = Neu.FieldShape, depth = NeuDepth.Inset, elevation = 4.dp) {
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
                    unfocusedBorderColor = c.muted, focusedBorderColor = c.primary,
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
                .heightIn(min = MinTarget)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
                .semantics { if (error != null) error(error) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Raised box when off (with a 3:1 outline so it is findable), pressed in with a check when on.
            NeuSurface(
                Modifier.size(28.dp), shape = RoundedCornerShape(8.dp), elevation = 3.dp,
                depth = if (checked) NeuDepth.Inset else NeuDepth.Raised,
                rim = if (checked) c.primary else c.muted, rimWidth = if (checked) 2.dp else 1.dp,
            ) {
                if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = c.primary, modifier = Modifier.align(Alignment.Center).size(20.dp))
            }
            Text(text, Modifier.padding(start = 12.dp), color = c.text, style = SthiraType.body)
        }
        FieldError(error)
    }
}
