package app.sthira.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sthira.ui.theme.Sthira

@Composable
fun FieldError(error: String?) {
    if (error != null) Text(error, color = Sthira.colors.danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

/** A labelled group of large, single-choice buttons (TalkBack reads them as radio buttons). */
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
    val c = Sthira.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, color = c.text, fontSize = 16.sp)
        if (help != null) Muted(help, size = 14)
        FlowRow(
            Modifier.selectableGroup().semantics { if (error != null) error(error) },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    shape = CardShape,
                    color = if (isSelected) c.primary else c.surface,
                    contentColor = if (isSelected) c.onPrimary else c.text,
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (error != null) c.danger else c.borderStrong),
                    modifier = Modifier
                        .heightIn(min = MinTarget)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
                ) {
                    Row(Modifier.heightIn(min = MinTarget).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text(option), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        FieldError(error)
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            isError = error != null,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
            supportingText = help?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth().semantics { if (error != null) error(error) },
        )
        FieldError(error)
    }
}

@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit, error: String? = null) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTarget)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
                .semantics { if (error != null) error(error) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text(text, Modifier.padding(start = 8.dp), color = Sthira.colors.text, fontSize = 16.sp)
        }
        FieldError(error)
    }
}
