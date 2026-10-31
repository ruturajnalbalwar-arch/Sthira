package app.sthira.chairstand

import androidx.annotation.StringRes
import app.sthira.R

/** Why a run ended early. Shown on the result screen; nothing is saved. */
enum class CaptureIssue(@StringRes val text: Int) {
    AppHidden(R.string.cs_issue_app_hidden),
    DetectionFailed(R.string.cs_issue_detection_failed),
    CameraEnded(R.string.cs_issue_camera_ended),
}
