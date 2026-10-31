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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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
 * Every screen: the flat backdrop, a top bar on inner screens (a big "Back" button and the title),
 * an optional bottom bar that keeps the screen's one main action reachable, and scrolling content
 * that passes under both bars. Text size lives in Settings; a screen that really needs the A-/A+
 * control in its top bar passes [showTextZoom].
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    fullBleed: (@Composable () -> Unit)? = null,
    showTextZoom: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        NeuBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Sthira.colors.text,
            topBar = { if (onBack != null) NeuTopBar(title, onBack, showTextZoom) },
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
 * The inner-screen bar: a big outlined Back button (arrow and the word, so it is clear what it does),
 * then the title (up to two lines, so a 200% zoom still shows most of it; TalkBack reads it whole).
 * The Back word is drawn at a fixed size so the button never squeezes the title out at 200%.
 */
@Composable
private fun NeuTopBar(title: String, onBack: () -> Unit, showTextZoom: Boolean) {
    val c = Sthira.colors
    val back = stringResource(R.string.common_back)
    NeuBar(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = TopBarHeight).padding(horizontal = Space.s, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeuSurface(
                Modifier.heightIn(min = MainTarget).semantics { contentDescription = back },
                shape = Neu.ButtonShape, onClick = onBack, rim = c.text, rimWidth = Neu.StrongEdgeWidth,
            ) {
                Row(
                    Modifier.align(Alignment.Center).padding(start = 10.dp, end = 14.dp).clearAndSetSemantics { },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(26.dp))
                    Text(back, style = SthiraType.label.copy(fontSize = fixedSp(18.dp)), maxLines = 1)
                }
            }
            Text(
                title, style = SthiraType.title, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp).semantics {
                    heading()
                    contentDescription = title
                },
            )
            if (showTextZoom) TextZoomControl(Modifier.padding(end = Space.xs))
        }
    }
}

/** Flat sheet anchored to the bottom edge for the screen's main action; the nav-bar inset sits inside it. */
@Composable
private fun NeuActionBar(content: @Composable ColumnScope.() -> Unit) {
    NeuSurface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
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
