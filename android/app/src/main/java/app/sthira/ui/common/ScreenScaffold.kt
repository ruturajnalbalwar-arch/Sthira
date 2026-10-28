package app.sthira.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sthira.ui.theme.Sthira

private val MaxContentWidth = 720.dp

/** Scrolling page with a Back link and a heading; content is capped in width for tablets. */
@Composable
fun ScreenScaffold(title: String, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Sthira.colors.bg)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (onBack != null) LinkButton("← Back", onBack)
            Heading(title, size = 28)
            content()
        }
    }
}
