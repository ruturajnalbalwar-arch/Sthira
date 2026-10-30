package app.sthira

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry

typealias AppRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

const val SHOT_TAG = "SthiraShot"
private const val SHOT_PAUSE_MS = 3_000L
private const val DEFAULT_WAIT_MS = 10_000L

val targetContext: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

fun asset(name: String): String = targetContext.assets.open(name).bufferedReader().use { it.readText() }

fun cameraGranted(): Boolean =
    ContextCompat.checkSelfPermission(targetContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

fun hasTextMatching(re: Regex) = SemanticsMatcher("text matches $re") { node ->
    node.config.getOrNull(SemanticsProperties.Text)?.any { re.matches(it.text) } == true
}

fun hasDescriptionMatching(re: Regex) = SemanticsMatcher("contentDescription matches $re") { node ->
    node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { re.matches(it) } == true
}

fun SemanticsNodeInteraction.text(): String =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

/** A tap through the semantics click action: never lands on the floating bars that can cover scrolled content. */
fun SemanticsNodeInteraction.tap(): SemanticsNodeInteraction = performSemanticsAction(SemanticsActions.OnClick)

/** Keeps the phone awake for long timed tests (window flag on Sthira's own window only). */
fun AppRule.keepScreenOn() = runOnUiThread { activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }

/** System back, dispatched in-process through the activity's back dispatcher (no device-wide key injection). */
fun AndroidComposeTestRule<*, out ComponentActivity>.systemBack() {
    runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
    waitForIdle()
}

fun AppRule.exists(m: SemanticsMatcher): Boolean = onAllNodes(m).fetchSemanticsNodes().isNotEmpty()

fun AppRule.waitFor(m: SemanticsMatcher, timeoutMs: Long = DEFAULT_WAIT_MS) {
    waitUntil(timeoutMs) { exists(m) }
}

fun AppRule.waitGone(m: SemanticsMatcher, timeoutMs: Long = DEFAULT_WAIT_MS) {
    waitUntil(timeoutMs) { !exists(m) }
}

fun AppRule.button(text: String): SemanticsNodeInteraction = onNode(hasText(text) and hasClickAction())
fun AppRule.tapButton(text: String) = button(text).tap().also { waitForIdle() }
fun AppRule.chip(label: String): SemanticsNodeInteraction = onNode(hasText(label) and isSelectable())
fun AppRule.check(label: String): SemanticsNodeInteraction = onNode(hasText(label) and isToggleable())
fun AppRule.field(label: String): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasText(label, substring = true))
fun AppRule.type(label: String, value: String) = field(label).performTextReplacement(value).also { waitForIdle() }

/** The heading in the top bar of an inner screen. */
fun AppRule.assertScreen(title: String) {
    onNode(isHeading() and hasContentDescription(title)).assertExists()
}

fun AppRule.assertHome() {
    onNodeWithTextExact("Tests").assertExists()
    onNode(hasText("Sthira") and isHeading()).assertExists()
}

private fun AppRule.onNodeWithTextExact(text: String) = onNode(hasText(text))

fun AppRule.openCard(title: String) {
    onNode(hasContentDescription(title, substring = true) and hasClickAction()).tap()
    waitForIdle()
}

fun AppRule.tapBackArrow() {
    onNode(hasContentDescription("Back") and hasClickAction()).tap()
    waitForIdle()
}

/**
 * Asks the host-side watcher (scratchpad phone.sh, guarded: only when Sthira is in front) for a
 * screenshot, then waits so the frame is captured. Nothing is captured on the phone itself.
 */
fun AppRule.shot(name: String) {
    waitForIdle()
    Log.i(SHOT_TAG, name)
    Thread.sleep(SHOT_PAUSE_MS)
}

/**
 * Retries [block] until it stops throwing an AssertionError. Chair-stand form changes travel
 * through the ViewModel's inbox coroutine (Dispatchers.Default), which waitForIdle() does not track.
 */
fun AppRule.eventually(timeoutMs: Long = 3_000, block: () -> Unit) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        try {
            waitForIdle()
            block()
            return
        } catch (e: AssertionError) {
            if (System.currentTimeMillis() > deadline) throw e
            Thread.sleep(50)
        }
    }
}
