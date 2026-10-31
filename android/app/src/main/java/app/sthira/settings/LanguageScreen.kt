package app.sthira.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.sthira.R
import app.sthira.ui.common.Muted
import app.sthira.ui.common.Neu
import app.sthira.ui.common.NeuDepth
import app.sthira.ui.common.NeuSurface
import app.sthira.ui.common.ScreenScaffold
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

/**
 * Pick the app language. Every language is written in its own script so a reader finds theirs
 * whatever the app is showing now. [onPick] gets the tag, or null for "follow the phone".
 */
@Composable
fun LanguageScreen(current: String?, onPick: (String?) -> Unit, onBack: () -> Unit) {
    ScreenScaffold(stringResource(R.string.common_language_title), onBack) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            LanguageRow(
                name = stringResource(R.string.common_language_phone),
                help = stringResource(R.string.common_language_phone_help),
                selected = current == null,
            ) { onPick(null) }
            AppLocales.LANGUAGES.forEach { lang ->
                LanguageRow(stringResource(lang.nativeName), help = null, selected = current == lang.tag) { onPick(lang.tag) }
            }
        }
    }
}

@Composable
private fun LanguageRow(name: String, help: String?, selected: Boolean, onClick: () -> Unit) {
    val c = Sthira.colors
    val selectedLabel = stringResource(R.string.common_selected)
    // Pressed in, outlined and checked when chosen: never the shadow alone.
    NeuSurface(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { if (selected) stateDescription = selectedLabel },
        shape = Neu.ButtonShape,
        depth = if (selected) NeuDepth.Inset else NeuDepth.Raised,
        elevation = 4.dp,
        rim = if (selected) c.primary else null,
        rimWidth = 2.dp,
    ) {
        Row(
            Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = Space.m, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = SthiraType.title, color = c.text)
                if (help != null) Muted(help)
            }
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = c.primary, modifier = Modifier.size(28.dp))
        }
    }
}
