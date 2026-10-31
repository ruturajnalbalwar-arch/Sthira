package app.sthira.pose

import android.content.Context
import androidx.camera.core.ImageProxy
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * One pose landmarker and the single thread it runs on, for the life of the chair-stand screen.
 * The model is loaded (GPU first, CPU if GPU fails) as soon as this is created, i.e. while the setup
 * form is still open, so the skeleton can be drawn on the first camera frame. Lens switches and
 * rotations rebind only the camera and reuse this landmarker.
 */
class PoseTracker(context: Context, onSample: (PoseSample) -> Unit, onError: () -> Unit) {
    sealed interface Ready {
        data class Loaded(val delegate: PoseDelegate) : Ready
        data object Failed : Ready
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    // Both confined to [executor].
    private var helper: PoseLandmarkerHelper? = null
    private var ready: Ready? = null

    @Volatile private var closed = false

    /** The camera's image analyser runs here, on the landmarker's own thread. */
    val analysisExecutor: Executor get() = executor

    init {
        val app = context.applicationContext
        executor.execute {
            ready = try {
                val created = PoseLandmarkerHelper.create(app, onSample) { onError() }
                helper = created
                Ready.Loaded(created.delegate)
            } catch (e: RuntimeException) {
                Ready.Failed
            }
        }
    }

    /** Calls [then] on the tracker thread once the model has loaded (at once if it already has). */
    fun whenReady(then: (Ready) -> Unit) {
        if (closed) return
        try {
            executor.execute { ready?.let(then) }
        } catch (e: RejectedExecutionException) {
            // Closed between the check and the call: the screen is gone, nobody is waiting.
        }
    }

    /** Image analyser callback. Always closes [image]. */
    fun analyze(image: ImageProxy, isFrontCamera: Boolean) {
        val current = helper
        if (closed || current == null) {
            image.close()
            return
        }
        current.detect(image, isFrontCamera)
    }

    fun close() {
        if (closed) return
        closed = true
        executor.execute {
            helper?.close()
            helper = null
        }
        executor.shutdown()
    }
}
