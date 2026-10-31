package app.sthira.home

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sthira.R
import app.sthira.settings.LocalAppSettings
import app.sthira.settings.shownLanguageName
import app.sthira.ui.common.AppIcons
import app.sthira.ui.common.LinkButton
import app.sthira.ui.common.MinTarget
import app.sthira.ui.common.Neu
import app.sthira.ui.common.NeuBackdrop
import app.sthira.ui.common.NeuDepth
import app.sthira.ui.common.NeuSurface
import app.sthira.ui.common.TestIcons
import app.sthira.ui.common.asString
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

enum class HomeAction { ChairStand, Balance, Tug, Privacy, Terms, Licences, Language, Settings }

private data class TestCardInfo(
    val action: HomeAction,
    /** Drawn as a tinted mask, so the one picture follows the light or dark theme. */
    val icon: @Composable () -> Painter,
    @StringRes val title: Int,
    @StringRes val measures: Int,
)

private val TESTS = listOf(
    TestCardInfo(
        HomeAction.ChairStand, { painterResource(R.drawable.ic_test_chair_stand) },
        R.string.home_test_chair_title, R.string.home_test_chair_measures,
    ),
    TestCardInfo(
        HomeAction.Balance, { rememberVectorPainter(TestIcons.Balance) },
        R.string.home_test_balance_title, R.string.home_test_balance_measures,
    ),
    TestCardInfo(HomeAction.Tug, { painterResource(R.drawable.ic_test_tug) }, R.string.home_test_tug_title, R.string.home_test_tug_measures),
)

@Composable
fun HomeScreen(onAction: (HomeAction) -> Unit, vm: HomeViewModel = viewModel()) {
    HomeContent(vm.counterBadge, onAction)
}

/**
 * Stateless home, so it can be previewed (including at 200% font scale). Kept to one job, picking a
 * test: a header (brand, then Language and Settings buttons), the three numbered test cards, and a
 * two-line footer. Everything else (theme, text size, voice, the offline note) lives in Settings.
 */
@Composable
fun HomeContent(counterBadge: CounterBadge, onAction: (HomeAction) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        NeuBackdrop()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // At least the screen tall so the footer sits at the bottom. The system bar insets are
                // padding inside the scrolled content, so the footer always scrolls clear of the nav bar.
                Column(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth().heightIn(min = viewport)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(start = Space.m, end = Space.m, top = Space.m, bottom = Space.l),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
                        Header(onAction)
                        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                            Text(
                                stringResource(R.string.home_tests_heading), style = SthiraType.title, color = Sthira.colors.text,
                                modifier = Modifier.semantics { heading() },
                            )
                            TESTS.forEachIndexed { index, info ->
                                val badge = if (info.action == HomeAction.ChairStand) counterBadge else null
                                TestCard(index + 1, info, badge) { onAction(info.action) }
                            }
                        }
                    }
                    Footer(onAction, Modifier.padding(top = Space.l))
                }
            }
        }
    }
}

/** Brand on the left, Language and Settings on the right; the buttons wrap below when the text is large. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(onAction: (HomeAction) -> Unit) {
    val c = Sthira.colors
    val language = shownLanguageName(LocalAppSettings.current.languageTag)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Row(
            Modifier.align(Alignment.CenterVertically).padding(end = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NeuSurface(Modifier.size(44.dp), shape = RoundedCornerShape(12.dp), fill = c.primary) {
                Image(
                    painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                    colorFilter = ColorFilter.tint(c.onPrimary), modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                stringResource(R.string.home_brand), style = SthiraType.headline, color = c.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        Row(Modifier.align(Alignment.CenterVertically), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            HeaderButton(
                icon = AppIcons.Globe, label = language,
                description = stringResource(R.string.common_language_change, language),
            ) { onAction(HomeAction.Language) }
            val settings = stringResource(R.string.common_settings_title)
            HeaderButton(icon = Icons.Filled.Settings, label = settings, description = settings) { onAction(HomeAction.Settings) }
        }
    }
}

/**
 * A big outlined button with the icon above its word. The word is capped in size (it still grows a
 * little with the text size) so the two buttons fit next to the brand on a phone.
 */
@Composable
private fun HeaderButton(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    val c = Sthira.colors
    NeuSurface(
        Modifier.heightIn(min = HEADER_BUTTON).widthIn(min = HEADER_BUTTON).semantics { contentDescription = description },
        shape = Neu.ButtonShape, onClick = onClick, rim = c.text, rimWidth = Neu.StrongEdgeWidth,
    ) {
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 10.dp, vertical = 6.dp).clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Text(label, style = SthiraType.label.copy(fontSize = cappedSp(HEADER_LABEL_MAX)), maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

/**
 * One test: number, big icon, title, one short line, and a visible "Start" pill. The whole card is
 * the button; TalkBack reads it as one item ("1. title. line. counter note").
 */
@Composable
private fun TestCard(number: Int, info: TestCardInfo, badge: CounterBadge?, onClick: () -> Unit) {
    val c = Sthira.colors
    val title = stringResource(info.title)
    val measures = stringResource(info.measures)
    // Only a counter that is not ready gets a line, in plain words; a working counter needs no note.
    val note = badge?.takeIf { it != CounterBadge.Ready }?.label?.asString()
    val spoken = listOfNotNull("$number. $title", measures, note).joinToString(". ")
    NeuSurface(
        Modifier.fillMaxWidth(), onClick = onClick, onClickLabel = stringResource(R.string.common_start),
        rimWidth = Neu.StrongEdgeWidth,
    ) {
        Column(
            Modifier.padding(Space.m).clearAndSetSemantics { contentDescription = spoken },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NumberedIcon(number, info.icon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(title, style = SthiraType.title, color = c.text)
                    Text(measures, style = SthiraType.body, color = c.muted)
                    if (note != null) Text(note, style = SthiraType.label.copy(fontWeight = FontWeight.Normal), color = c.text)
                }
            }
            StartPill(Modifier.align(Alignment.End))
        }
    }
}

/** The test icon in a 64dp well, with the test's number in a small filled circle on its corner. */
@Composable
private fun NumberedIcon(number: Int, icon: @Composable () -> Painter) {
    val c = Sthira.colors
    Box(Modifier.size(72.dp)) {
        NeuSurface(Modifier.size(64.dp).align(Alignment.BottomEnd), shape = RoundedCornerShape(16.dp), depth = NeuDepth.Inset) {
            Icon(icon(), contentDescription = null, tint = c.text, modifier = Modifier.align(Alignment.Center).size(48.dp))
        }
        NeuSurface(Modifier.size(28.dp).align(Alignment.TopStart), shape = CircleShape, fill = c.primary, contentColor = c.onPrimary) {
            Text(
                number.toString(), style = SthiraType.label.copy(fontSize = cappedSp(16.dp), fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.Center), maxLines = 1,
            )
        }
    }
}

/** Looks like the primary button, but is part of the card (the card is what takes the tap). */
@Composable
private fun StartPill(modifier: Modifier = Modifier) {
    val c = Sthira.colors
    NeuSurface(modifier.heightIn(min = MinTarget), shape = Neu.ButtonShape, fill = c.primary, contentColor = c.onPrimary) {
        Row(
            Modifier.align(Alignment.Center).padding(start = 18.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.common_start), style = SthiraType.titleSmall)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(26.dp))
        }
    }
}

/** Two short lines: what Sthira is not, then the Privacy, Terms and Licences links (each 48dp tall). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Footer(onAction: (HomeAction) -> Unit, modifier: Modifier = Modifier) {
    val c = Sthira.colors
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.home_footer_disclaimer), color = c.muted, textAlign = TextAlign.Center,
            style = SthiraType.label.copy(fontWeight = FontWeight.Normal),
        )
        FlowRow(horizontalArrangement = Arrangement.Center) {
            LinkButton(stringResource(R.string.home_link_privacy), { onAction(HomeAction.Privacy) }, small = true)
            FooterDot()
            LinkButton(stringResource(R.string.home_link_terms), { onAction(HomeAction.Terms) }, small = true)
            FooterDot()
            LinkButton(stringResource(R.string.home_link_licences), { onAction(HomeAction.Licences) }, small = true)
        }
    }
}

/** A separator between the footer links; silent for TalkBack. */
@Composable
private fun FooterDot() {
    Box(Modifier.heightIn(min = MinTarget).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Text("·", color = Sthira.colors.muted, style = SthiraType.label)
    }
}

/** A text size that grows with the font scale but never past [max] (in dp), for labels that must fit. */
@Composable
private fun cappedSp(max: Dp) = with(LocalDensity.current) {
    val scaled = SthiraType.label.fontSize.toDp()
    (if (scaled < max) scaled else max).toSp()
}

private val HEADER_BUTTON = 64.dp
private val HEADER_LABEL_MAX = 20.dp
