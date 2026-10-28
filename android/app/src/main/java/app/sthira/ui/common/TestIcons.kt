package app.sthira.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Simple line icons for the three tests (24dp grid), drawn here so no icon library is added. */
object TestIcons {
    private fun line(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
        ).build()

    /** A person rising from a chair. */
    val ChairStand: ImageVector = line("ChairStand") {
        moveTo(4f, 21f); lineTo(4f, 11f) // chair back
        moveTo(4f, 15f); lineTo(10f, 15f); lineTo(10f, 21f) // seat and front leg
        moveTo(15f, 4.5f); arcToRelative(1.5f, 1.5f, 0f, true, true, 0.01f, 0f) // head
        moveTo(15f, 7f); lineTo(14f, 13f); lineTo(18f, 14f); lineTo(18f, 21f) // body and leg
        moveTo(14f, 13f); lineTo(12f, 21f)
    }

    /** One foot in front of the other on a line. */
    val Balance: ImageVector = line("Balance") {
        moveTo(12f, 3.5f); arcToRelative(1.5f, 1.5f, 0f, true, true, 0.01f, 0f)
        moveTo(12f, 6f); lineTo(12f, 14f)
        moveTo(6f, 9f); lineTo(18f, 9f) // arms out
        moveTo(12f, 14f); lineTo(11f, 20f)
        moveTo(12f, 14f); lineTo(13f, 20f)
        moveTo(4f, 21f); lineTo(20f, 21f)
    }

    /** A person walking. */
    val Walk: ImageVector = line("Walk") {
        moveTo(13f, 3.5f); arcToRelative(1.5f, 1.5f, 0f, true, true, 0.01f, 0f)
        moveTo(12f, 7f); lineTo(10f, 13f); lineTo(13f, 16f); lineTo(13f, 21f)
        moveTo(10f, 13f); lineTo(7f, 21f)
        moveTo(11.5f, 8.5f); lineTo(8f, 11f)
        moveTo(12f, 8f); lineTo(16f, 11f)
    }
}
