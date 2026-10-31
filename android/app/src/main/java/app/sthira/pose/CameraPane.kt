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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import app.sthira.R
import app.sthira.ui.common.UiText
import app.sthira.ui.common.uiText
import kotlinx.coroutines.flow.StateFlow

enum class Lens { Back, Front }

sealed interface CameraStatus {
    data object Starting : CameraStatus
    data class Live(val delegate: PoseDelegate) : CameraStatus
    /** [trackingStopped]: the camera ran but pose detection failed. */
    data class Failed(val message: UiText, val trackingStopped: Boolean = false) : CameraStatus
}

private const val PORTRAIT_RATIO = 3f / 4f
private const val LANDSCAPE_RATIO = 4f / 3f

/** Preview and analysis both ask for 4:3 so the overlay maps onto the preview with one FILL_CENTER transform. */
private fun fourByThree(): ResolutionSelector =
    ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()

/**
 * Live camera preview with the pose overlay. Frames are analysed by [tracker] (already loaded, and
 * kept across lens switches); the skeleton is drawn from [overlay], the latest sample only.
 * PreviewView uses COMPATIBLE (TextureView) mode so the window's FLAG_SECURE also covers the preview.
 */
@Composable
fun CameraPane(
    lens: Lens,
    tracker: PoseTracker,
    overlay: StateFlow<PoseSample?>,
    onStatus: (CameraStatus) -> Unit,
    modifier: Modifier = Modifier,
    overlays: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val orientation = LocalConfiguration.current.orientation
    val status by rememberUpdatedState(onStatus)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    DisposableEffect(lens, owner, orientation, tracker) {
        val session = CameraSession(context, owner, previewView, lens, tracker) { status(it) }
        session.start()
        onDispose { session.stop() }
    }
    val ratio = if (orientation == Configuration.ORIENTATION_LANDSCAPE) LANDSCAPE_RATIO else PORTRAIT_RATIO
    val description = stringResource(R.string.pose_preview_description)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .semantics { contentDescription = description },
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        PoseOverlay(overlay, Modifier.fillMaxSize())
        overlays()
    }
}

/** One bind of the camera to the shared [tracker]; [stop] releases the camera only. */
private class CameraSession(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val previewView: PreviewView,
    private val lens: Lens,
    private val tracker: PoseTracker,
    private val onStatus: (CameraStatus) -> Unit,
) {
    @Volatile private var stopped = false
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private val main = ContextCompat.getMainExecutor(context)

    fun start() {
        onStatus(CameraStatus.Starting)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (stopped) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                bindUseCases(cameraProvider)
            } catch (e: Exception) {
                // No such camera, camera in use by another app, or binding refused by the device.
                onStatus(CameraStatus.Failed(uiText(R.string.pose_error_camera)))
                return@addListener
            }
            // The preview is already showing; report Live once the (usually preloaded) model is ready.
            tracker.whenReady { ready -> main.execute { if (!stopped) onStatus(ready.toStatus()) } }
        }, main)
    }

    private fun PoseTracker.Ready.toStatus(): CameraStatus = when (this) {
        is PoseTracker.Ready.Loaded -> CameraStatus.Live(delegate)
        PoseTracker.Ready.Failed -> CameraStatus.Failed(uiText(R.string.pose_error_model))
    }

    private fun bindUseCases(cameraProvider: ProcessCameraProvider) {
        val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
        val preview = Preview.Builder().setResolutionSelector(fourByThree()).setTargetRotation(rotation).build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val front = lens == Lens.Front
        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(fourByThree())
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(tracker.analysisExecutor) { image -> tracker.analyze(image, front) } }
        analysis = imageAnalysis
        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(owner, selector, preview, imageAnalysis)
    }

    fun stop() {
        stopped = true
        // No more frames from this lens reach the shared landmarker.
        analysis?.clearAnalyzer()
        provider?.unbindAll()
    }
}
