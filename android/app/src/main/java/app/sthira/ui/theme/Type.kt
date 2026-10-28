package app.sthira.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One type scale for the whole app: display / headline / title / body / label.
 * Body text is never below 16sp; label (14sp) is only for short metadata.
 */
object SthiraType {
    val headline = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
    val title = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    val titleSmall = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    val bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal)
    val label = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    /** Tabular figures so timers and counts do not jitter as digits change. */
    val numeric = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.Black)
}

/**
 * Display sizes for numbers read from across the room. They are in dp, not sp: at 140dp they already
 * fill the screen, so a 200% font scale must not push them off it. Labels next to them stay in sp.
 */
object DisplaySize {
    /** Live chair-stand count: about 15 mm tall on a 6.5" phone, readable from 3 m. */
    val liveCount: Dp = 150.dp
    val countdown: Dp = 140.dp
    val timer: Dp = 72.dp
    val result: Dp = 88.dp
}

val SthiraTypography = Typography(
    headlineSmall = SthiraType.headline,
    titleLarge = SthiraType.title,
    titleMedium = SthiraType.titleSmall,
    bodyLarge = SthiraType.body,
    bodyMedium = SthiraType.body,
    labelLarge = SthiraType.label.copy(fontSize = 16.sp),
    labelMedium = SthiraType.label,
)

/** The 8dp spacing scale. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
}
