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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sthira.R
import app.sthira.settings.DisplaySettingsRow
import app.sthira.ui.common.BannerKind
import app.sthira.ui.common.LinkButton
import app.sthira.ui.common.MinTarget
import app.sthira.ui.common.NeuBackdrop
import app.sthira.ui.common.NeuDepth
import app.sthira.ui.common.NeuSurface
import app.sthira.ui.common.StatusChip
import app.sthira.ui.common.TestIcons
import app.sthira.ui.common.asString
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

enum class HomeAction { ChairStand, Balance, Tug, Privacy, Terms, Licences, Language }

private data class TestCardInfo(
    val action: HomeAction,
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val measures: Int,
    @StringRes val duration: Int,
)

private val TESTS = listOf(
    TestCardInfo(HomeAction.ChairStand, TestIcons.ChairStand, R.string.home_test_chair_title, R.string.home_test_chair_measures, R.string.home_test_chair_duration),
    TestCardInfo(HomeAction.Balance, TestIcons.Balance, R.string.home_test_balance_title, R.string.home_test_balance_measures, R.string.home_test_balance_duration),
    TestCardInfo(HomeAction.Tug, TestIcons.Walk, R.string.home_test_tug_title, R.string.home_test_tug_measures, R.string.home_test_tug_duration),
)

@Composable
fun HomeScreen(onAction: (HomeAction) -> Unit, vm: HomeViewModel = viewModel()) {
    HomeContent(vm.counterBadge, onAction)
}

/**
 * Stateless home, so it can be previewed (including at 200% font scale). Order: brand, the display
 * settings (language, theme, text size) in one row, the tests, the trust chips, and a two-line footer.
 */
@Composable
fun HomeContent(counterBadge: CounterBadge, onAction: (HomeAction) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        NeuBackdrop()
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val viewport = maxHeight
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Fills at least the screen so the footer sits at the bottom instead of leaving a dead third.
                Column(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth().heightIn(min = viewport).padding(Space.m),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
                        BrandHeader()
                        DisplaySettingsRow(onLanguage = { onAction(HomeAction.Language) })
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                stringResource(R.string.home_tests_heading), style = SthiraType.title, color = Sthira.colors.text,
                                modifier = Modifier.semantics { heading() },
                            )
                            TESTS.forEach { info ->
                                val badge = if (info.action == HomeAction.ChairStand) counterBadge else null
                                TestCard(info, badge) { onAction(info.action) }
                            }
                        }
                        TrustChips()
                    }
                    Footer(onAction, Modifier.padding(top = Space.l))
                }
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    val c = Sthira.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NeuSurface(Modifier.size(52.dp), shape = RoundedCornerShape(14.dp), elevation = 5.dp, fill = c.primary) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                colorFilter = ColorFilter.tint(c.onPrimary), modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
            Text(stringResource(R.string.home_brand), style = SthiraType.headline, color = c.text)
            Text(stringResource(R.string.home_tagline), style = SthiraType.body, color = c.muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrustChips() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        StatusChip(stringResource(R.string.home_chip_offline), BannerKind.Success, icon = Icons.Filled.Lock)
        StatusChip(stringResource(R.string.home_chip_steadi), BannerKind.Plain, icon = Icons.Filled.CheckCircle)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestCard(info: TestCardInfo, badge: CounterBadge?, onClick: () -> Unit) {
    val c = Sthira.colors
    val title = stringResource(info.title)
    val measures = stringResource(info.measures)
    val duration = stringResource(info.duration)
    val badgeLabel = badge?.label?.asString()
    val spoken = listOfNotNull(title, measures, stringResource(R.string.home_card_takes, duration), badgeLabel).joinToString(". ")
    NeuSurface(Modifier.fillMaxWidth(), onClick = onClick, onClickLabel = stringResource(R.string.home_card_open)) {
        // The clickable surface merges this one description, so TalkBack reads the card as a single button.
        Row(
            Modifier.padding(Space.m).heightIn(min = 72.dp).clearAndSetSemantics { contentDescription = spoken },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NeuSurface(Modifier.size(48.dp), shape = CircleShape, depth = NeuDepth.Inset, elevation = 3.dp) {
                Icon(info.icon, contentDescription = null, tint = c.primary, modifier = Modifier.align(Alignment.Center).size(28.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(title, style = SthiraType.titleSmall, color = c.text)
                Text(measures, style = SthiraType.body, color = c.muted)
                // Wraps at large text sizes instead of squeezing the two chips.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    StatusChip(duration, BannerKind.Plain, icon = null)
                    if (badge != null && badgeLabel != null) {
                        StatusChip(badgeLabel, if (badge == CounterBadge.Ready) BannerKind.Success else BannerKind.Warn)
                    }
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted)
        }
    }
}

/** Two small lines: what Sthira is not, then the Privacy, Terms and Licences links (each 48dp tall). */
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
