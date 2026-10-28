package app.sthira.pose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import app.sthira.ui.theme.Sthira
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

private val CONNECTIONS: List<Pair<Int, Int>> = PoseLandmarker.POSE_LANDMARKS.map { it.start() to it.end() }
private const val LINE_WIDTH = 6f
private const val POINT_RADIUS = 6f

/** Skeleton drawn over the FILL_CENTER preview. Decorative: the canvas has no semantics. */
@Composable
fun PoseOverlay(sample: PoseSample?, modifier: Modifier = Modifier) {
    val line = Sthira.colors.primary
    val point = Sthira.colors.onPrimary
    Canvas(modifier) {
        val person = sample?.person ?: return@Canvas
        if (person.size != PoseMapper.LANDMARK_COUNT) return@Canvas
        val t = FillCenterTransform.of(sample.imageWidth, sample.imageHeight, size.width, size.height) ?: return@Canvas
        val at = { i: Int -> Offset(t.x(person[i].x), t.y(person[i].y)) }
        CONNECTIONS.forEach { (a, b) -> drawLine(line, at(a), at(b), strokeWidth = LINE_WIDTH, cap = StrokeCap.Round) }
        person.indices.forEach { i ->
            drawCircle(line, POINT_RADIUS + 2f, at(i))
            drawCircle(point, POINT_RADIUS, at(i))
        }
    }
}
