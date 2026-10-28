package app.sthira.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

enum class PoseDelegate(val label: String) { GPU("GPU"), CPU("CPU") }

/** One analysed frame. [landmarks] are in the analysed (rotated, and for the front camera mirrored) image. */
data class PoseSample(
    val captureNs: Long,
    val person: List<RawLandmark>?,
    val imageWidth: Int,
    val imageHeight: Int,
    val mirrored: Boolean,
)

/**
 * LIVE_STREAM pose landmarker, after Google's mediapipe-samples PoseLandmarkerHelper.
 * The model is loaded from assets; GPU is tried first and CPU is used if GPU setup fails.
 * Detection and tracking confidences stay at the library defaults (the web app does the same):
 * no threshold is invented here. Not thread-safe: call [detect] from one analyser thread.
 */
class PoseLandmarkerHelper private constructor(
    private val landmarker: PoseLandmarker,
    val delegate: PoseDelegate,
) {
    private var lastTimestampMs = Long.MIN_VALUE
    private val pending = java.util.concurrent.ConcurrentHashMap<Long, Pending>()

    private data class Pending(val captureNs: Long, val mirrored: Boolean)

    /** Rotates (and mirrors for the front camera) the RGBA frame, then queues it. Always closes [image]. */
    fun detect(image: ImageProxy, isFrontCamera: Boolean) {
        val captureNs = image.imageInfo.timestamp
        val timestampMs = captureNs / NANOS_PER_MILLI
        val bitmap: Bitmap
        try {
            // MediaPipe needs strictly increasing timestamps in LIVE_STREAM mode.
            if (timestampMs <= lastTimestampMs) return
            lastTimestampMs = timestampMs
            val raw = image.toBitmap()
            val matrix = Matrix().apply {
                postRotate(image.imageInfo.rotationDegrees.toFloat())
                if (isFrontCamera) postScale(-1f, 1f, raw.width.toFloat(), raw.height.toFloat())
            }
            bitmap = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        } finally {
            image.close()
        }
        pending[timestampMs] = Pending(captureNs, isFrontCamera)
        landmarker.detectAsync(BitmapImageBuilder(bitmap).build(), timestampMs)
    }

    fun close() = landmarker.close()

    internal fun toSample(result: PoseLandmarkerResult, input: MPImage): PoseSample? {
        val info = pending.remove(result.timestampMs()) ?: return null
        val person = result.landmarks().firstOrNull()?.map {
            RawLandmark(it.x(), it.y(), it.z(), it.visibility().orElse(null))
        }
        return PoseSample(info.captureNs, person, input.width, input.height, info.mirrored)
    }

    companion object {
        const val MODEL_ASSET = "pose_landmarker_full.task"
        private const val NANOS_PER_MILLI = 1_000_000L

        /** Creates the landmarker on GPU, falling back to CPU. Throws only if both fail. */
        fun create(
            context: Context,
            onResult: (PoseSample) -> Unit,
            onError: (RuntimeException) -> Unit,
        ): PoseLandmarkerHelper {
            val holder = arrayOfNulls<PoseLandmarkerHelper>(1)
            fun build(delegate: Delegate): PoseLandmarker {
                val base = BaseOptions.builder().setModelAssetPath(MODEL_ASSET).setDelegate(delegate).build()
                val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumPoses(1)
                    .setResultListener { result, input -> holder[0]?.toSample(result, input)?.let(onResult) }
                    .setErrorListener { error -> onError(error) }
                    .build()
                return PoseLandmarker.createFromOptions(context, options)
            }
            val helper = try {
                PoseLandmarkerHelper(build(Delegate.GPU), PoseDelegate.GPU)
            } catch (gpuFailure: RuntimeException) {
                // GPU delegate is unavailable on this device; CPU is slower but works.
                PoseLandmarkerHelper(build(Delegate.CPU), PoseDelegate.CPU)
            }
            holder[0] = helper
            return helper
        }
    }
}
