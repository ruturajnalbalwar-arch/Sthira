package app.sthira.pose

import app.sthira.engine.Landmark

/** One landmark as MediaPipe gives it; visibility is null when the model gave no score. */
data class RawLandmark(val x: Float, val y: Float, val z: Float, val visibility: Float?)

object PoseMapper {
    const val LANDMARK_COUNT = 33

    /** The library writes 0 when the model gives no visibility score; the web app applies the same rule. */
    private const val LIBRARY_DEFAULT_VISIBILITY = 0.0

    /**
     * Converts the first detected person to engine landmarks. Returns an empty list ("no person")
     * when there is no person, the count is not 33, or any value is not finite.
     * [unmirror] flips x back for the front camera: the analysed bitmap is mirrored so the overlay
     * matches the selfie preview, but the counter always gets unmirrored landmarks (same as the web app).
     */
    fun toLandmarks(person: List<RawLandmark>?, unmirror: Boolean): List<Landmark> {
        if (person == null || person.size != LANDMARK_COUNT) return emptyList()
        val mapped = person.map { raw ->
            val x = raw.x.toDouble()
            Landmark(
                x = if (unmirror) 1.0 - x else x,
                y = raw.y.toDouble(),
                z = raw.z.toDouble(),
                visibility = raw.visibility?.toDouble() ?: LIBRARY_DEFAULT_VISIBILITY,
            )
        }
        val finite = mapped.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() && it.visibility.isFinite() }
        return if (finite) mapped else emptyList()
    }
}

/**
 * Maps camera capture timestamps onto the elapsedRealtime clock (one fixed offset per camera
 * session, so frame spacing stays capture-time) and drops any frame that does not strictly increase.
 */
data class FrameClock(private val offsetNs: Long? = null, private val lastCaptureNs: Long? = null) {
    data class Mapped(val clock: FrameClock, val realtimeNs: Long?)

    fun map(captureNs: Long, nowRealtimeNs: Long): Mapped {
        val last = lastCaptureNs
        if (last != null && captureNs <= last) return Mapped(this, null)
        val offset = offsetNs ?: (nowRealtimeNs - captureNs)
        return Mapped(FrameClock(offset, captureNs), captureNs + offset)
    }
}

/** Frames per second over a sliding window of recent frame times (seconds). */
data class FpsMeter(private val windowS: Double = WINDOW_S, private val times: List<Double> = emptyList()) {
    fun add(t: Double): FpsMeter = copy(times = (times + t).filter { it > t - windowS })

    val fps: Double?
        get() {
            if (times.size < 2) return null
            val span = times.last() - times.first()
            return if (span > 0) (times.size - 1) / span else null
        }

    companion object {
        const val WINDOW_S = 1.0
    }
}

/** How a normalised image point lands on a view that shows the image scaled to fill and centre-cropped (PreviewView FILL_CENTER). */
data class FillCenterTransform(val scale: Float, val dx: Float, val dy: Float, val imageWidth: Float, val imageHeight: Float) {
    fun x(normX: Float): Float = normX * imageWidth * scale + dx
    fun y(normY: Float): Float = normY * imageHeight * scale + dy

    companion object {
        fun of(imageWidth: Int, imageHeight: Int, viewWidth: Float, viewHeight: Float): FillCenterTransform? {
            if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) return null
            val scale = maxOf(viewWidth / imageWidth, viewHeight / imageHeight)
            val dx = (viewWidth - imageWidth * scale) / 2f
            val dy = (viewHeight - imageHeight * scale) / 2f
            return FillCenterTransform(scale, dx, dy, imageWidth.toFloat(), imageHeight.toFloat())
        }
    }
}
