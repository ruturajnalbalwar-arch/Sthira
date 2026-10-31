package app.sthira.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import app.sthira.chairstand.CameraOverlays
import app.sthira.chairstand.ChairStandUiState
import app.sthira.chairstand.ContinueBar
import app.sthira.chairstand.CountView
import app.sthira.chairstand.LiveStats
import app.sthira.chairstand.ResultCard
import app.sthira.chairstand.ResultView
import app.sthira.chairstand.SetupForm
import app.sthira.chairstand.SetupFormUi
import app.sthira.chairstand.Stage
import app.sthira.engine.ChairStandSummary
import app.sthira.engine.RepEvent
import app.sthira.engine.RepKind
import app.sthira.engine.norms.Verdict
import app.sthira.home.CounterBadge
import app.sthira.home.HomeContent
import app.sthira.pose.CameraStatus
import app.sthira.pose.PoseDelegate
import app.sthira.timers.BigTime
import app.sthira.timers.StageStepper
import app.sthira.timers.StepStatus
import app.sthira.engine.timers.BalanceStage
import app.sthira.ui.theme.SthiraTheme

/*
 * Debug-only previews at 200% font scale (largest Android setting) in both themes, phone width.
 * Review points: no clipped text, giant digits stay on screen (they are sized in dp on purpose),
 * the sticky bar keeps its button visible, chips wrap instead of overflowing.
 */

private val events = listOf(
    RepEvent(RepKind.FULL, 1.0, 1.8, 2.6),
    RepEvent(RepKind.HALF, 3.0, null, 3.9),
    RepEvent(RepKind.FULL, 4.2, 5.0, 5.8),
    RepEvent(RepKind.ARM_ASSISTED, 6.1, 7.0, 7.9),
    RepEvent(RepKind.UNCERTAIN, 8.2, 9.0, 9.8),
)

private val summary = ChairStandSummary(
    full = 12, half = 1, armAssisted = 1, uncertain = 1, events = events, effectiveFps = 24.0, lowFps = false,
    unreliable = false, droppedFrames = 0, gaps = 0, windowComplete = true,
)

private fun runState(stage: Stage, count: CountView) = ChairStandUiState(
    durationS = 30.0, stage = stage, countdownS = 4, elapsedS = 12, remainingS = 18, count = count,
    camera = CameraStatus.Live(PoseDelegate.GPU), stats = LiveStats(24.3, personDetected = true, framesRecorded = 0),
)

@Composable
private fun Camera(state: ChairStandUiState) {
    // A white frame is the worst case for text on the overlay panels; the overlays must stay readable on it.
    Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(Color.White)) { CameraOverlays(state) }
}

@Preview(name = "Home 200% dark", fontScale = 2f, widthDp = 360, heightDp = 800, uiMode = 0x21)
@Preview(name = "Home 200% light", fontScale = 2f, widthDp = 360, heightDp = 800)
@Composable
private fun HomeLarge() = SthiraTheme { HomeContent(CounterBadge.NotTuned) {} }

@Preview(name = "Setup 200%", fontScale = 2f, widthDp = 360, heightDp = 1600, uiMode = 0x21)
@Composable
private fun SetupLarge() = SthiraTheme {
    Column {
        SetupFormUi(SetupForm(participantCode = "Asha")) {}
        ContinueBar(SetupForm()) {}
    }
}

@Preview(name = "Calibration overlay 200%", fontScale = 2f, widthDp = 360, uiMode = 0x21)
@Composable
private fun CalibrationLarge() = SthiraTheme { Camera(runState(Stage.CalibrationStand, CountView.Waiting)) }

@Preview(name = "Live count overlay 200%", fontScale = 2f, widthDp = 360)
@Composable
private fun CountingLarge() = SthiraTheme { Camera(runState(Stage.Test, CountView.Counting(summary))) }

@Preview(name = "Result 200%", fontScale = 2f, widthDp = 360, heightDp = 1400, uiMode = 0x21)
@Composable
private fun ResultLarge() = SthiraTheme {
    ResultCard(
        ResultView(summary, Verdict.Compared(12, false, 10, "At or above the CDC average for women aged 70-74: ..."), null),
        30.0, "US reference values (CDC STEADI). Not validated for Indian older adults.",
    )
}

@Preview(name = "Balance stepper 200%", fontScale = 2f, widthDp = 360, uiMode = 0x21)
@Composable
private fun StepperLarge() = SthiraTheme {
    Column {
        StageStepper(
            listOf(BalanceStage(1, "feet side-by-side", ""), BalanceStage(2, "semi-tandem", ""), BalanceStage(3, "tandem", ""), BalanceStage(4, "single leg", "")),
            listOf(StepStatus.Held, StepStatus.Lost, StepStatus.Skipped, StepStatus.Skipped),
        )
        BigTime(7.4, "Hold for 10.0 s")
    }
}
