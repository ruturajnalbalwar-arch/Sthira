package app.sthira.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sthira.R
import app.sthira.ui.common.BannerKind
import app.sthira.ui.common.NeuBackdrop
import app.sthira.ui.common.NeuDepth
import app.sthira.ui.common.NeuSurface
import app.sthira.ui.common.LinkButton
import app.sthira.ui.common.Muted
import app.sthira.ui.common.StatusChip
import app.sthira.ui.common.TestIcons
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

enum class HomeAction { ChairStand, Balance, Tug, Privacy, Terms, Licences }

private data class TestCardInfo(
    val action: HomeAction,
    val icon: ImageVector,
    val title: String,
    val measures: String,
    val duration: String,
)

private val TESTS = listOf(
    TestCardInfo(HomeAction.ChairStand, TestIcons.ChairStand, "30-second chair stand", "Leg strength: full stands, counted by the camera", "30 s"),
    TestCardInfo(HomeAction.Balance, TestIcons.Balance, "4-stage balance", "Standing balance in four positions, timed by you", "4 × 10 s"),
    TestCardInfo(HomeAction.Tug, TestIcons.Walk, "Timed Up and Go", "Mobility: stand, walk, turn and sit, timed by you", "under a minute"),
)

@Composable
fun HomeScreen(onAction: (HomeAction) -> Unit, vm: HomeViewModel = viewModel()) {
    HomeContent(vm.counterBadge, onAction)
}

/** Stateless home, so it can be previewed (including at 200% font scale). */
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
                        TrustChips()
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Tests", style = SthiraType.title, color = Sthira.colors.text, modifier = Modifier.semantics { heading() })
                            TESTS.forEach { info ->
                                val badge = if (info.action == HomeAction.ChairStand) counterBadge else null
                                TestCard(info, badge) { onAction(info.action) }
                            }
                        }
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
            Text("Sthira", style = SthiraType.headline, color = c.text)
            Text("Steady scores for home physiotherapy", style = SthiraType.body, color = c.muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrustChips() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        StatusChip("Works offline · no internet permission", BannerKind.Success, icon = Icons.Filled.Lock)
        StatusChip("CDC STEADI tests", BannerKind.Plain, icon = Icons.Filled.CheckCircle)
    }
}

@Composable
private fun TestCard(info: TestCardInfo, badge: CounterBadge?, onClick: () -> Unit) {
    val c = Sthira.colors
    val spoken = listOfNotNull(info.title, info.measures, "takes ${info.duration}", badge?.label).joinToString(". ")
    NeuSurface(Modifier.fillMaxWidth(), onClick = onClick, onClickLabel = "Open") {
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
                Text(info.title, style = SthiraType.titleSmall, color = c.text)
                Text(info.measures, style = SthiraType.body, color = c.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(info.duration, BannerKind.Plain, icon = null)
                    badge?.let { StatusChip(it.label, if (it == CounterBadge.Ready) BannerKind.Success else BannerKind.Warn) }
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Footer(onAction: (HomeAction) -> Unit, modifier: Modifier = Modifier) {
    // A pressed-in well: it is reading text, not a control.
    NeuSurface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), depth = NeuDepth.Inset, elevation = 4.dp) {
    Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Muted(
            "Scores appear next to CDC reference values (US data, not validated for India) for the physiotherapist to " +
                "interpret. Sthira does not diagnose or predict falls.",
        )
        FlowRow(verticalArrangement = Arrangement.Center) {
            LinkButton("Privacy", { onAction(HomeAction.Privacy) })
            LinkButton("Terms", { onAction(HomeAction.Terms) })
            LinkButton("Licences", { onAction(HomeAction.Licences) })
        }
        Muted("Prototype. Not a medical device.")
    }
    }
}
