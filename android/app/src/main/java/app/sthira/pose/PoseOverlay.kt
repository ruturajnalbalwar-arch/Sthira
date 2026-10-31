package app.sthira.pose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import app.sthira.ui.theme.Sthira
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlinx.coroutines.flow.StateFlow

private val CONNECTIONS: List<Pair<Int, Int>> = PoseLandmarker.POSE_LANDMARKS.map { it.start() to it.end() }
private const val LINE_WIDTH = 6f
private const val POINT_RADIUS = 6f

/**
 * Skeleton drawn over the FILL_CENTER preview from the latest sample only. The sample is read inside
 * the draw block, so a new frame redraws this canvas without recomposing the screen. For the front
 * camera the landmarks come from a mirrored image, matching PreviewView's mirrored selfie preview.
 * Decorative: the canvas has no semantics.
 */
@Composable
fun PoseOverlay(samples: StateFlow<PoseSample?>, modifier: Modifier = Modifier) {
    val latest = samples.collectAsState()
    val line = Sthira.colors.primary
    val point = Sthira.colors.onPrimary
    Canvas(modifier) {
        val sample = latest.value ?: return@Canvas
        val person = sample.person ?: return@Canvas
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
