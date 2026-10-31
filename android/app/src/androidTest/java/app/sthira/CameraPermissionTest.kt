package app.sthira

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
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
private fun str(id: Int, vararg args: Any): String = targetContext.getString(id, *args)

@RunWith(AndroidJUnit4::class)
class CameraPermissionCardTest {
    @get:Rule val rule = createComposeRule()

    private var asks = 0
    private var settings = 0

    private fun show(state: Permission) {
        rule.setContent { SthiraTheme { PermissionCard(state, onAsk = { asks++ }, onSettings = { settings++ }) } }
    }

    private val why get() = str(R.string.pose_perm_why)

    @Test
    fun notAskedExplainsAndAsks() {
        show(Permission.NotAsked)
        rule.onNode(hasText(str(R.string.pose_perm_needed_title))).assertExists()
        rule.onNode(hasText(why)).assertExists()
        rule.onNode(hasText(str(R.string.pose_perm_allow)) and hasClickAction()).tap()
        assertEquals(1, asks)
        rule.onNode(hasText(str(R.string.pose_perm_open_settings))).assertDoesNotExist()
    }

    @Test
    fun deniedOnceOffersAllowAgain() {
        show(Permission.Denied)
        rule.onNode(hasText(str(R.string.pose_perm_off_title))).assertExists()
        rule.onNode(hasText(str(R.string.pose_perm_allow)) and hasClickAction()).tap()
        assertEquals(1, asks)
        rule.onNode(hasText(str(R.string.pose_perm_open_settings))).assertDoesNotExist()
    }

    @Test
    fun permanentlyDeniedOffersSettings() {
        show(Permission.Blocked)
        rule.onNode(hasText(str(R.string.pose_perm_off_title))).assertExists()
        rule.onNode(hasText(str(R.string.pose_perm_blocked))).assertExists()
        rule.onNode(hasText(str(R.string.pose_perm_allow))).assertDoesNotExist()
        rule.onNode(hasText(str(R.string.pose_perm_open_settings)) and hasClickAction()).tap()
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
        rule.openCard(str(R.string.home_test_chair_title))
        rule.chip(str(R.string.cs_age_75_79)).tap()
        rule.chip(str(R.string.cs_sex_not_recorded)).tap()
        rule.eventually { rule.button(str(R.string.cs_continue)).assertIsEnabled() }
        rule.tapButton(str(R.string.cs_continue))
        rule.assertScreen(str(R.string.cs_title))
        rule.onNode(hasText(str(R.string.pose_perm_needed_title))).assertExists()
        // Still step 2 of 3: the step indicator stays above the permission card.
        rule.onNode(hasContentDescription(str(R.string.cs_step_of, 2, 3), substring = true)).assertExists()
        rule.onNode(hasText(str(R.string.pose_perm_allow)) and hasClickAction()).assertExists() // not tapped: it opens the system dialog
        rule.onNode(hasText(str(R.string.cs_start))).assertDoesNotExist()
        rule.shot("e-01-permission-card")
        rule.tapBackArrow()
        rule.assertHome()
        // Back in: the session is kept, so the card shows again; system back leaves too.
        rule.openCard(str(R.string.home_test_chair_title))
        rule.onNode(hasText(str(R.string.pose_perm_needed_title))).assertExists()
        rule.systemBack()
        rule.assertHome()
    }
}
