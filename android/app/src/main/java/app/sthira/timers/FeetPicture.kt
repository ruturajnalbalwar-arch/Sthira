package app.sthira.timers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.sthira.ui.theme.Sthira

/*
 * Drawing proportions only (no clinical values): a foot is a sole oval with five toes above it,
 * seen from above with the toes pointing up the screen.
 */
private val PICTURE_HEIGHT = 150.dp
private val LINE_WIDTH = 3.dp
private const val FOOT_LENGTH_OF_HEIGHT = 0.46f
private const val FOOT_WIDTH_OF_LENGTH = 0.38f
private const val SOLE_TOP_OF_LENGTH = 0.2f
private const val TOE_ROW_OF_LENGTH = 0.09f
private const val SIDE_GAP_OF_WIDTH = 0.15f
private const val HALF = 0.5f

/** Toes from the inner (big toe) side outwards: x offset and radius as parts of the foot width, y drop as part of the length. */
private val TOES = listOf(
    Triple(0.26f, 0.17f, 0.0f), Triple(0.02f, 0.11f, 0.01f), Triple(-0.17f, 0.10f, 0.03f),
    Triple(-0.32f, 0.09f, 0.06f), Triple(-0.44f, 0.08f, 0.1f),
)

/** A simple top view of where the feet go, so the stage is clear without reading. Decorative: the text says the same. */
@Composable
fun FeetPicture(position: FootPosition, modifier: Modifier = Modifier) {
    val ink = Sthira.colors.text
    Canvas(modifier.fillMaxWidth().height(PICTURE_HEIGHT)) {
        val length = size.height * FOOT_LENGTH_OF_HEIGHT
        val width = length * FOOT_WIDTH_OF_LENGTH
        val cx = size.width * HALF
        val cy = size.height * HALF
        val stroke = Stroke(width = LINE_WIDTH.toPx())
        val foot = { centreX: Float, top: Float, left: Boolean -> drawFoot(ink, stroke, centreX, top, length, width, left) }
        when (position) {
            FootPosition.SideBySide -> {
                val offset = width * (HALF + SIDE_GAP_OF_WIDTH)
                foot(cx - offset, cy - length * HALF, true)
                foot(cx + offset, cy - length * HALF, false)
            }
            FootPosition.SemiTandem -> {
                // The front foot is half a foot ahead, its instep beside the big toe of the back foot.
                foot(cx - width * HALF, cy - length * HALF * HALF, true)
                foot(cx + width * HALF, cy - length * (HALF + HALF * HALF), false)
            }
            FootPosition.Tandem -> {
                foot(cx, cy - length, false)
                foot(cx, cy, true)
            }
            FootPosition.OneFoot -> foot(cx, cy - length * HALF, true)
        }
    }
}

private fun DrawScope.drawFoot(ink: Color, stroke: Stroke, centreX: Float, top: Float, length: Float, width: Float, left: Boolean) {
    val soleTop = top + length * SOLE_TOP_OF_LENGTH
    drawOval(ink, Offset(centreX - width * HALF, soleTop), Size(width, length - length * SOLE_TOP_OF_LENGTH), style = stroke)
    // A left foot has its big toe on the right (inner) side.
    val inward = if (left) 1f else -1f
    TOES.forEach { (dx, r, dy) ->
        drawCircle(ink, radius = width * r, center = Offset(centreX + inward * dx * width, top + length * (TOE_ROW_OF_LENGTH + dy)), style = stroke)
    }
}
