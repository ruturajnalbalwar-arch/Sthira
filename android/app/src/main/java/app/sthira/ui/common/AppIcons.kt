package app.sthira.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Settings icons (24dp grid), drawn here because the extended icon set is not a dependency. */
object AppIcons {
    private val stroke = SolidColor(Color.Black)

    /** A globe: outline, one meridian and three parallels. Used for the language button. */
    val Globe: ImageVector = ImageVector.Builder("Globe", 24.dp, 24.dp, 24f, 24f).path(
        fill = null, stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(3f, 12f); arcTo(9f, 9f, 0f, false, true, 21f, 12f); arcTo(9f, 9f, 0f, false, true, 3f, 12f)
        moveTo(12f, 3f); arcTo(4f, 9f, 0f, false, true, 12f, 21f); arcTo(4f, 9f, 0f, false, true, 12f, 3f)
        moveTo(3f, 12f); lineTo(21f, 12f)
        moveTo(4.8f, 7.5f); lineTo(19.2f, 7.5f)
        moveTo(4.8f, 16.5f); lineTo(19.2f, 16.5f)
    }.build()

    /** A circle, half filled: light or dark theme. */
    val Contrast: ImageVector = ImageVector.Builder("Contrast", 24.dp, 24.dp, 24f, 24f)
        .path(fill = null, stroke = stroke, strokeLineWidth = 2f) {
            moveTo(3f, 12f); arcTo(9f, 9f, 0f, false, true, 21f, 12f); arcTo(9f, 9f, 0f, false, true, 3f, 12f)
        }
        .path(fill = stroke) {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, false, true, 12f, 21f); close()
        }
        .build()
}
