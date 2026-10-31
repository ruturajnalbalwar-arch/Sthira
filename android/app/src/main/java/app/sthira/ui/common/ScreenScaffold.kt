package app.sthira.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sthira.R
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

private val MaxContentWidth = 720.dp
private val TopBarHeight = 64.dp

/**
 * Every screen: the flat soft backdrop, a soft top app bar (back arrow, title, text zoom) on inner screens, an
 * optional floating soft action bar that keeps the primary action reachable, and scrolling
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
        NeuBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Sthira.colors.text,
            topBar = { if (onBack != null) NeuTopBar(title, onBack) },
            bottomBar = { if (bottomBar != null) NeuActionBar(bottomBar) },
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

/**
 * The inner-screen bar: back arrow, the title (up to two lines, so a 200% zoom still shows most of
 * it; TalkBack reads it whole) and the A-/A+ text zoom on the right.
 */
@Composable
private fun NeuTopBar(title: String, onBack: () -> Unit) {
    val c = Sthira.colors
    NeuBar(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = TopBarHeight).padding(horizontal = Space.xs, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = c.text)
            }
            Text(
                title, style = SthiraType.title, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = Space.xs).semantics {
                    heading()
                    contentDescription = title
                },
            )
            TextZoomControl(Modifier.padding(end = Space.xs))
        }
    }
}

/** Soft sheet anchored to the bottom edge for the screen's main action(s); the nav-bar inset sits inside it. */
@Composable
private fun NeuActionBar(content: @Composable ColumnScope.() -> Unit) {
    NeuSurface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        elevation = 8.dp,
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
