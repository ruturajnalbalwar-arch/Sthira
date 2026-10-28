package app.sthira.pose

import android.content.Context
import android.content.res.Configuration
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class Lens { Back, Front }

sealed interface CameraStatus {
    data object Starting : CameraStatus
    data class Live(val delegate: PoseDelegate) : CameraStatus
    /** [trackingStopped]: the camera ran but pose detection failed. */
    data class Failed(val message: String, val trackingStopped: Boolean = false) : CameraStatus
}

private const val PORTRAIT_RATIO = 3f / 4f
private const val LANDSCAPE_RATIO = 4f / 3f

/** Preview and analysis both ask for 4:3 so the overlay maps onto the preview with one FILL_CENTER transform. */
private fun fourByThree(): ResolutionSelector =
    ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()

/**
 * Live camera preview with the pose overlay. Frames go to [onSample] on a background thread.
 * PreviewView uses COMPATIBLE (TextureView) mode so the window's FLAG_SECURE also covers the preview.
 */
@Composable
fun CameraPane(
    lens: Lens,
    overlay: PoseSample?,
    onSample: (PoseSample) -> Unit,
    onStatus: (CameraStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val orientation = LocalConfiguration.current.orientation
    val sample by rememberUpdatedState(onSample)
    val status by rememberUpdatedState(onStatus)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    DisposableEffect(lens, owner, orientation) {
        val session = CameraSession(context, owner, previewView, lens, { sample(it) }, { status(it) })
        session.start()
        onDispose { session.stop() }
    }
    val ratio = if (orientation == Configuration.ORIENTATION_LANDSCAPE) LANDSCAPE_RATIO else PORTRAIT_RATIO
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .semantics { contentDescription = "Camera preview with the tracked body outline" },
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        PoseOverlay(overlay, Modifier.fillMaxSize())
    }
}

/** One bind of the camera and one pose landmarker; [stop] releases both. */
private class CameraSession(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val previewView: PreviewView,
    private val lens: Lens,
    private val onSample: (PoseSample) -> Unit,
    private val onStatus: (CameraStatus) -> Unit,
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    @Volatile private var helper: PoseLandmarkerHelper? = null
    @Volatile private var stopped = false
    private var provider: ProcessCameraProvider? = null

    fun start() {
        onStatus(CameraStatus.Starting)
        analysisExecutor.execute {
            val created = try {
                PoseLandmarkerHelper.create(context, onSample) { onStatus(CameraStatus.Failed("Pose tracking stopped on this phone. Go back and open the test again.", trackingStopped = true)) }
            } catch (e: RuntimeException) {
                onStatus(CameraStatus.Failed("The pose model could not be loaded on this phone."))
                return@execute
            }
            if (stopped) {
                created.close()
                return@execute
            }
            helper = created
            ContextCompat.getMainExecutor(context).execute { bind(created) }
        }
    }

    private fun bind(created: PoseLandmarkerHelper) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (stopped) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                bindUseCases(cameraProvider, created)
                onStatus(CameraStatus.Live(created.delegate))
            } catch (e: Exception) {
                // No such camera, camera in use by another app, or binding refused by the device.
                onStatus(CameraStatus.Failed("The camera could not start. Close other camera apps and try again."))
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases(cameraProvider: ProcessCameraProvider, created: PoseLandmarkerHelper) {
        val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
        val preview = Preview.Builder().setResolutionSelector(fourByThree()).setTargetRotation(rotation).build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val front = lens == Lens.Front
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(fourByThree())
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(analysisExecutor) { image -> created.detect(image, front) } }
        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(owner, selector, preview, analysis)
    }

    fun stop() {
        stopped = true
        provider?.unbindAll()
        analysisExecutor.execute { helper?.close() }
        analysisExecutor.shutdown()
    }
}
