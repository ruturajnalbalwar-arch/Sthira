package app.sthira.pose

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.sthira.ui.common.ActionButton
import app.sthira.ui.common.Banner
import app.sthira.ui.common.BannerKind
import app.sthira.ui.common.BannerText
import app.sthira.ui.common.Body
import app.sthira.ui.common.Tone

/** Internal so the on-device tests can render every card state without the system permission dialog. */
internal enum class Permission { Granted, NotAsked, Denied, Blocked }

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun hasCamera(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * Shows [content] once camera permission is granted. Before that it explains why the camera is
 * needed; after a permanent denial it offers the app's system settings page.
 */
@Composable
fun CameraPermissionGate(
    denied: @Composable (card: @Composable () -> Unit) -> Unit = { it() },
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(if (hasCamera(context)) Permission.Granted else Permission.NotAsked) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val rationale = context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: false
        state = when {
            granted -> Permission.Granted
            rationale -> Permission.Denied
            else -> Permission.Blocked
        }
    }
    // Re-check when the user comes back from the system settings page.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasCamera(context)) state = Permission.Granted
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (state == Permission.Granted) {
        content()
        return
    }
    denied { PermissionCard(state, onAsk = { launcher.launch(Manifest.permission.CAMERA) }, onSettings = { openAppSettings(context) }) }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Composable
internal fun PermissionCard(state: Permission, onAsk: () -> Unit, onSettings: () -> Unit) {
    val kind = if (state == Permission.NotAsked) BannerKind.Plain else BannerKind.Warn
    Banner(kind) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BannerText(if (state == Permission.NotAsked) "Camera needed for this test" else "Camera permission is off")
            Body(
                "Sthira watches the head and shoulders to count each stand. The video is processed on this phone only: " +
                    "it is never uploaded, and the app has no internet access.",
            )
            when (state) {
                Permission.Blocked -> {
                    Body("Android will not ask again. Open Settings, choose Permissions, then Camera, and allow it.")
                    ActionButton("Open settings", onSettings, Modifier.fillMaxWidth(), big = true)
                }
                Permission.Denied -> ActionButton("Allow camera", onAsk, Modifier.fillMaxWidth(), big = true)
                else -> ActionButton("Allow camera", onAsk, Modifier.fillMaxWidth(), big = true, tone = Tone.Primary)
            }
        }
    }
}
