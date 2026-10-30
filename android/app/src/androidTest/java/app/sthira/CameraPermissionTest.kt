package app.sthira

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sthira.pose.Permission
import app.sthira.pose.PermissionCard
import app.sthira.ui.theme.SthiraTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every state of the camera permission card, rendered directly (the system dialog itself is never opened). */
@RunWith(AndroidJUnit4::class)
class CameraPermissionCardTest {
    @get:Rule val rule = createComposeRule()

    private var asks = 0
    private var settings = 0

    private fun show(state: Permission) {
        rule.setContent { SthiraTheme { PermissionCard(state, onAsk = { asks++ }, onSettings = { settings++ }) } }
    }

    private val why = "Sthira watches the head and shoulders to count each stand. The video is processed on this phone only: " +
        "it is never uploaded, and the app has no internet access."

    @Test
    fun notAskedExplainsAndAsks() {
        show(Permission.NotAsked)
        rule.onNode(hasText("Camera needed for this test")).assertExists()
        rule.onNode(hasText(why)).assertExists()
        rule.onNode(hasText("Allow camera") and hasClickAction()).tap()
        assertEquals(1, asks)
        rule.onNode(hasText("Open settings")).assertDoesNotExist()
    }

    @Test
    fun deniedOnceOffersAllowAgain() {
        show(Permission.Denied)
        rule.onNode(hasText("Camera permission is off")).assertExists()
        rule.onNode(hasText("Allow camera") and hasClickAction()).tap()
        assertEquals(1, asks)
        rule.onNode(hasText("Open settings")).assertDoesNotExist()
    }

    @Test
    fun permanentlyDeniedOffersSettings() {
        show(Permission.Blocked)
        rule.onNode(hasText("Camera permission is off")).assertExists()
        rule.onNode(hasText("Android will not ask again. Open Settings, choose Permissions, then Camera, and allow it.")).assertExists()
        rule.onNode(hasText("Allow camera")).assertDoesNotExist()
        rule.onNode(hasText("Open settings") and hasClickAction()).tap()
        assertEquals(1, settings)
        assertEquals(0, asks)
    }
}

/** The real gate in the chair-stand flow while CAMERA is not granted (run with the permission revoked). */
@RunWith(AndroidJUnit4::class)
class CameraDeniedFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun continueWithoutPermissionShowsTheRationaleCard_andBackLeaves() {
        assumeFalse("CAMERA is granted; this test needs it revoked", cameraGranted())
        rule.openCard("30-second chair stand")
        rule.type("Participant code", "P07")
        rule.chip("75-79").tap()
        rule.chip("Not recorded").tap()
        rule.eventually { rule.button("Continue to camera").assertIsEnabled() }
        rule.tapButton("Continue to camera")
        rule.assertScreen("30-second chair stand")
        rule.onNode(hasText("Camera needed for this test")).assertExists()
        rule.onNode(hasText("Allow camera") and hasClickAction()).assertExists() // not tapped: it opens the system dialog
        rule.onNode(hasText("Start calibration and test")).assertDoesNotExist()
        rule.shot("e-01-permission-card")
        rule.tapBackArrow()
        rule.assertHome()
        // Back in: the session is kept, so the card shows again; system back leaves too.
        rule.openCard("30-second chair stand")
        rule.onNode(hasText("Camera needed for this test")).assertExists()
        rule.systemBack()
        rule.assertHome()
    }
}
