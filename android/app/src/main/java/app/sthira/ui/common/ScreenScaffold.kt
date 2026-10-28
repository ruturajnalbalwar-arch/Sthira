package app.sthira.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

private val MaxContentWidth = 720.dp

/**
 * Every screen: the teal backdrop, a glass top app bar (back arrow + title) on inner screens, an
 * optional floating glass action bar that keeps the primary action reachable, and scrolling
 * content that passes under both bars.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    fullBleed: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        GlassBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Sthira.colors.text,
            topBar = { if (onBack != null) GlassTopBar(title, onBack) },
            bottomBar = { if (bottomBar != null) GlassActionBar(bottomBar) },
        ) { padding ->
            val dir = LocalLayoutDirection.current
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                    PaddingValues(
                        start = padding.calculateStartPadding(dir), end = padding.calculateEndPadding(dir),
                        top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Space.m,
                    ),
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                fullBleed?.invoke()
                Column(
                    Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(horizontal = Space.m, vertical = Space.m),
                    verticalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    if (onBack == null) Text(title, style = SthiraType.headline, modifier = Modifier.semantics { heading() })
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassTopBar(title: String, onBack: () -> Unit) {
    GlassBar(Modifier.fillMaxWidth()) {
        TopAppBar(
            title = {
                // The M3 bar is a fixed 64dp, so at large font scales the title is one line with an ellipsis;
                // TalkBack still reads the whole title.
                Text(
                    title, style = SthiraType.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics {
                        heading()
                        contentDescription = title
                    },
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
            windowInsets = WindowInsets.statusBars,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent,
                titleContentColor = Sthira.colors.text, navigationIconContentColor = Sthira.colors.text,
            ),
        )
    }
}

/** Glass sheet anchored to the bottom edge for the screen's main action(s); the nav-bar inset sits inside it. */
@Composable
private fun GlassActionBar(content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        elevation = 8.dp,
        dense = true,
    ) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(horizontal = Space.m, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Space.s),
                content = content,
            )
        }
    }
}
