package app.sthira.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

/** Minimum touch target (Android accessibility guidance); the big actions use more. */
val MinTarget = 48.dp
val BigTarget = 60.dp
val CardShape = RoundedCornerShape(16.dp)

enum class Tone { Primary, Secondary, Danger }

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Primary,
    enabled: Boolean = true,
    big: Boolean = false,
    icon: ImageVector? = null,
) {
    val c = Sthira.colors
    val (fill, content) = when {
        !enabled -> null to c.muted
        tone == Tone.Primary -> c.primary to c.onPrimary
        tone == Tone.Danger -> c.danger to c.onDanger
        else -> null to c.text
    }
    NeuSurface(
        modifier.heightIn(min = if (big) BigTarget else MinTarget),
        shape = Neu.ButtonShape, elevation = 6.dp, onClick = onClick, enabled = enabled, fill = fill, contentColor = content,
    ) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, Modifier.padding(end = Space.s).size(20.dp))
            Text(text, style = if (big) SthiraType.titleSmall else SthiraType.label.copy(fontSize = SthiraType.body.fontSize))
        }
    }
}

/**
 * A text link. Underlined because the palette is black and white: colour alone cannot mark a link.
 * [small] is the compact footer style (16sp, tight padding) that still keeps a 48dp touch target.
 */
@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTarget),
        contentPadding = if (small) PaddingValues(horizontal = Space.s) else ButtonDefaults.TextButtonContentPadding,
    ) {
        val size = if (small) SthiraType.label.fontSize else SthiraType.body.fontSize
        Text(text, color = Sthira.colors.primary, style = SthiraType.label.copy(fontSize = size, textDecoration = TextDecoration.Underline))
    }
}

enum class BannerKind { Danger, Warn, Success, Plain }

/**
 * A compact notice: the tone colour marks only the icon, the rim and the short title; body text
 * stays in the normal text colour. Danger and warn notices are announced by TalkBack.
 */
@Composable
fun Banner(kind: BannerKind, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val tone = toneColor(kind)
    val live = if (kind == BannerKind.Danger) LiveRegionMode.Assertive else LiveRegionMode.Polite
    NeuSurface(
        modifier.fillMaxWidth().animateContentSize(Sthira.motion())
            .semantics { if (kind != BannerKind.Plain) liveRegion = live },
        shape = CardShape, elevation = 5.dp, rim = if (kind == BannerKind.Plain) null else tone,
    ) {
        Row(Modifier.padding(Space.m), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            toneIcon(kind)?.let { Icon(it, contentDescription = null, tint = tone, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s), content = content)
        }
    }
}

@Composable
fun toneColor(kind: BannerKind): Color = when (kind) {
    BannerKind.Danger -> Sthira.colors.danger
    BannerKind.Warn -> Sthira.colors.warn
    BannerKind.Success -> Sthira.colors.success
    BannerKind.Plain -> Sthira.colors.text
}

fun toneIcon(kind: BannerKind): ImageVector? = when (kind) {
    BannerKind.Danger, BannerKind.Warn -> Icons.Filled.Warning
    BannerKind.Success -> Icons.Filled.CheckCircle
    BannerKind.Plain -> Icons.Filled.Info
}

/** Short notice title. Colour defaults to the surrounding tone; keep it to one line of meaning. */
@Composable
fun BannerText(text: String, color: Color = Color.Unspecified, size: Int = 19) {
    Text(text, style = SthiraType.titleSmall.copy(fontSize = size.sp, lineHeight = (size + 6).sp), color = color)
}

@Composable
fun Heading(text: String, modifier: Modifier = Modifier, size: Int = 20) {
    val style = if (size >= 24) SthiraType.headline else if (size >= 19) SthiraType.title else SthiraType.titleSmall
    Text(text, modifier = modifier.semantics { heading() }, style = style, color = Sthira.colors.text)
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, size: Int = 16, bold: Boolean = false) {
    val style = if (size >= 18) SthiraType.bodyLarge else SthiraType.body
    Text(
        text, modifier = modifier, color = Sthira.colors.text,
        style = if (bold) style.copy(fontWeight = SthiraType.titleSmall.fontWeight) else style,
    )
}

/** Secondary text. 16sp minimum; [size] below 16 is used only for short labels. */
@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 16) {
    Text(text, modifier = modifier, color = Sthira.colors.muted, style = if (size < 16) SthiraType.label.copy(fontWeight = null) else SthiraType.body)
}

@Composable
fun BulletList(items: List<String>, color: Color = Color.Unspecified, mono: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        items.forEach { item ->
            Row {
                Text("•  ", color = color, style = SthiraType.body)
                Text(item, color = color, style = if (mono) SthiraType.label.copy(fontFamily = FontFamily.Monospace) else SthiraType.body)
            }
        }
    }
}

/** A small raised pill with an optional icon, for status and metadata. Text carries the meaning, not colour. */
@Composable
fun StatusChip(text: String, kind: BannerKind, modifier: Modifier = Modifier, icon: ImageVector? = toneIcon(kind)) {
    val c = Sthira.colors
    val (fg, fill) = when (kind) {
        BannerKind.Warn -> c.warn to c.warnBg
        BannerKind.Success -> c.success to c.successBg
        BannerKind.Danger -> c.danger to null
        BannerKind.Plain -> c.text to null
    }
    NeuSurface(modifier, shape = Neu.ChipShape, elevation = 3.dp, fill = fill, contentColor = fg) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Text(text, color = fg, style = SthiraType.label)
        }
    }
}
