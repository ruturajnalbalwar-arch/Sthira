package app.sthira

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import app.sthira.chairstand.PhaseSpan
import app.sthira.chairstand.RecordingMeta
import app.sthira.chairstand.Stage
import app.sthira.engine.PoseFrame
import app.sthira.nav.SthiraApp
import app.sthira.record.CaptureInfo
import app.sthira.record.RecordingStore
import app.sthira.record.SaveResult
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private val KEPT = Regex("""^Recordings kept on this phone \(\d+\)$""")

private fun SemanticsNodeInteractionCollection.texts(): List<String> =
    fetchSemanticsNodes().map { n -> n.config[SemanticsProperties.Text].joinToString("") { it.text } }

/** Deletes [name] from the "kept on this phone" list through the two-step confirmation. */
private fun AppRule.deleteFromKeptList(name: String, saved: File) {
    waitFor(hasTextMatching(KEPT))
    val before = onNode(hasTextMatching(KEPT)).text().substringAfter("(").substringBefore(")").toInt()
    val index = onAllNodes(hasTextMatching(Regex("""^.+\.json$"""))).texts().indexOf(name)
    assertTrue("$name listed", index >= 0)
    onAllNodes(hasText("Delete") and hasClickAction())[index].tap()
    waitFor(hasText("Yes, delete"))
    assertTrue("nothing deleted before confirming", saved.isFile)
    tapButton("Yes, delete")
    waitFor(hasText("Recording deleted from this phone."))
    assertFalse(saved.exists())
    onNode(hasText(name)).assertDoesNotExist()
    if (before > 1) {
        onNode(hasText("Recordings kept on this phone (${before - 1})")).assertExists()
    } else {
        onNode(hasText("Recordings kept on this phone", substring = true)).assertDoesNotExist()
    }
    tapButton("Dismiss")
    waitGone(hasText("Recording deleted from this phone."))
}

/** A real camera run with the recording toggle on: saved privately, listed, deleted with confirmation. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class RecordingRunTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val fileName = Regex("""^P42_chair-stand-30s_saree_\d{8}T\d{6}Z\.json$""")

    private fun fillRecordingForm() {
        rule.openCard("30-second chair stand")
        rule.type("Participant code", "P42")
        rule.chip("80-84").tap()
        rule.chip("Male").tap()
        rule.check("Save a landmark recording of this run").tap()
        rule.eventually { rule.chip("Saree").assertExists() }
        rule.chip("Saree").tap()
        rule.chip("Side-on").tap()
        rule.type("Camera height (cm, floor to lens)", "90")
        rule.type("Camera distance (cm, lens to chair front)", "300")
        rule.type("Room", "test room")
        rule.check("Written consent has been signed by the participant").tap()
        rule.eventually { rule.button("Continue to camera").assertIsEnabled() }
        rule.tapButton("Continue to camera")
        val line = hasText("P42 · 80-84 · Male · recording on")
        rule.waitUntil(5_000) { rule.onAllNodes(line, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun stoppedRunSavesPrivately_isListedOnTheForm_andDeleteNeedsConfirmation() {
        assumeTrue("CAMERA must be granted", cameraGranted())
        rule.keepScreenOn()
        fillRecordingForm()
        rule.waitLive()
        rule.tapButton(START)
        rule.waitFor(hasText(NOT_COUNTING), STAGE_WAIT_MS * 2)
        rule.tapButton("Stop")
        rule.waitFor(hasText("Landmark recording saved inside the app"))
        rule.onNode(hasText("It stays private to Sthira on this phone until you export it.")).assertExists()
        val name = rule.onNode(hasTextMatching(fileName)).text()
        rule.button("Export").assertExists()
        rule.shot("e-08-recording-saved")

        val saved = File(targetContext.filesDir, "recordings/$name")
        assertTrue(saved.isFile)
        val json = JSONObject(saved.readText())
        assertEquals("sthira.recording.v1", json.getString("schema"))
        val meta = json.getJSONObject("meta")
        assertEquals("P42", meta.getString("participantCode"))
        assertEquals("80+", meta.getString("ageBand"))
        assertEquals("saree", meta.getString("clothing"))
        assertEquals("side", meta.getString("viewAngle"))
        assertEquals(90.0, meta.getDouble("cameraHeightCm"), 0.0)
        assertEquals(300.0, meta.getDouble("cameraDistanceCm"), 0.0)
        assertTrue(meta.getBoolean("consentConfirmed"))
        val phases = json.getJSONArray("phases").let { a -> List(a.length()) { a.getJSONObject(it).getString("phase") } }
        assertEquals(listOf("calibration-stand", "calibration-sit", "test"), phases)
        assertTrue(json.getJSONArray("frames").length() > 0)

        // Deleting from the run screen also asks first.
        rule.tapButton("Delete")
        rule.waitFor(hasText("Yes, delete"))
        assertTrue(saved.isFile)

        rule.tapButton("Edit session")
        rule.shot("e-09-kept-recordings")
        rule.deleteFromKeptList(name, saved)
    }
}

/**
 * Export from the kept-recordings list. The system "save as" picker is replaced by a test
 * ActivityResultRegistry, so no other app opens: it records the requested name and answers with a
 * file in Sthira's own cache, or with null as if the user cancelled. The recording is created
 * through the app's own RecordingStore, so no camera is needed.
 */
@RunWith(AndroidJUnit4::class)
class RecordingExportTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val launched = mutableListOf<String>()
    private var nextResult: Uri? = null

    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launched += input.toString()
            dispatchResult(requestCode, nextResult)
        }
    }

    private fun seedRecording(): File {
        val meta = RecordingMeta("P99", "60-64", "other", "front", 100.0, 250.0, "export test", "")
        val capture = CaptureInfo("2026-10-28T09:00:00.000Z", 480, 640, emptyList())
        val phases = listOf(PhaseSpan(Stage.CalibrationStand, 0.0, 5.0), PhaseSpan(Stage.CalibrationSit, 5.0, 10.0), PhaseSpan(Stage.Test, 10.0, 11.0))
        val frames = List(20) { PoseFrame(it * 0.5, emptyList()) }
        val saved = RecordingStore(targetContext.filesDir).save(meta, capture, phases, frames) as SaveResult.Saved
        return File(targetContext.filesDir, "recordings/${saved.recording.fileName}")
    }

    @Test
    fun exportAsksForTheFileName_copiesItExactly_andCancelWritesNothing() {
        val saved = seedRecording()
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        rule.runOnUiThread {
            rule.activity.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { SthiraApp() } }
        }
        rule.openCard("30-second chair stand")
        rule.waitFor(hasTextMatching(KEPT))
        val index = rule.onAllNodes(hasTextMatching(Regex("""^.+\.json$"""))).texts().indexOf(saved.name)
        assertTrue(index >= 0)

        val target = File(targetContext.cacheDir, "export-test.json").apply { delete() }
        nextResult = Uri.fromFile(target)
        rule.onAllNodes(hasText("Export") and hasClickAction())[index].tap()
        rule.waitFor(hasText("Exported. Delete the copy on this phone when you no longer need it."))
        assertEquals(listOf(saved.name), launched)
        assertArrayEquals(saved.readBytes(), target.readBytes())
        target.delete()
        rule.tapButton("Dismiss")
        rule.waitGone(hasText("Exported.", substring = true))

        nextResult = null
        rule.onAllNodes(hasText("Export") and hasClickAction())[index].tap()
        rule.waitForIdle()
        assertEquals(listOf(saved.name, saved.name), launched)
        assertFalse(target.exists())
        rule.onNode(hasText("Exported.", substring = true)).assertDoesNotExist()
        rule.onNode(hasText("Export failed. Nothing was written.")).assertDoesNotExist()

        rule.deleteFromKeptList(saved.name, saved)
    }
}
