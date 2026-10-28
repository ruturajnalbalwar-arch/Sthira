package app.sthira.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sthira.ui.theme.Sthira

/** Minimum touch target (Android accessibility guidance); the big actions use more. */
val MinTarget = 48.dp
val BigTarget = 64.dp
val CardShape = RoundedCornerShape(12.dp)

enum class Tone { Primary, Secondary, Danger }

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Primary,
    enabled: Boolean = true,
    big: Boolean = false,
) {
    val c = Sthira.colors
    val mod = modifier.heightIn(min = if (big) BigTarget else MinTarget)
    val padding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    val label: @Composable () -> Unit = {
        Text(text, fontSize = if (big) 20.sp else 16.sp, fontWeight = FontWeight.Bold)
    }
    if (tone == Tone.Secondary) {
        OutlinedButton(
            onClick = onClick, enabled = enabled, modifier = mod, shape = CardShape, contentPadding = padding,
            border = BorderStroke(1.dp, c.borderStrong),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = c.surface, contentColor = c.text),
        ) { label() }
        return
    }
    val danger = tone == Tone.Danger
    Button(
        onClick = onClick, enabled = enabled, modifier = mod, shape = CardShape, contentPadding = padding,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) c.danger else c.primary,
            contentColor = if (danger) c.onDanger else c.onPrimary,
        ),
    ) { label() }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = MinTarget)) {
        Text(text, color = Sthira.colors.primary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

enum class BannerKind { Danger, Warn, Success, Plain }

/** A boxed message. Danger and warn banners are announced by TalkBack when they appear or change. */
@Composable
fun Banner(kind: BannerKind, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Sthira.colors
    val fg = when (kind) {
        BannerKind.Danger -> c.danger
        BannerKind.Warn -> c.warn
        BannerKind.Success -> c.success
        BannerKind.Plain -> c.text
    }
    val bg = when (kind) {
        BannerKind.Warn -> c.warnBg
        BannerKind.Success -> c.successBg
        else -> c.surface
    }
    val border = if (kind == BannerKind.Plain) BorderStroke(1.dp, c.border) else BorderStroke(2.dp, fg)
    val live = if (kind == BannerKind.Danger) LiveRegionMode.Assertive else LiveRegionMode.Polite
    Surface(
        color = bg, contentColor = fg, shape = CardShape, border = border,
        modifier = modifier.fillMaxWidth().semantics { if (kind != BannerKind.Plain) liveRegion = live },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun BannerText(text: String, color: Color = Color.Unspecified, size: Int = 18) {
    Text(text, fontSize = size.sp, lineHeight = (size + 6).sp, fontWeight = FontWeight.Bold, color = color)
}

@Composable
fun Heading(text: String, modifier: Modifier = Modifier, size: Int = 24) {
    Text(
        text, modifier = modifier.semantics { heading() }, fontSize = size.sp,
        fontWeight = FontWeight.Bold, color = Sthira.colors.text, lineHeight = (size + 6).sp,
    )
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, size: Int = 16, bold: Boolean = false) {
    Text(
        text, modifier = modifier, color = Sthira.colors.text, fontSize = size.sp, lineHeight = (size + 6).sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 15) {
    Text(text, modifier = modifier, color = Sthira.colors.muted, fontSize = size.sp, lineHeight = (size + 6).sp)
}

@Composable
fun BulletList(items: List<String>, color: Color = Color.Unspecified, mono: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row {
                Text("•  ", color = color, fontSize = 16.sp)
                Text(
                    item, color = color, fontSize = if (mono) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = if (mono) FontFamily.Monospace else null,
                )
            }
        }
    }
}
