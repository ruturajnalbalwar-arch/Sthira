package app.sthira

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sthira.chairstand.AgeBand
import app.sthira.chairstand.Clothing
import app.sthira.chairstand.ViewAngle
import app.sthira.engine.CounterResult
import app.sthira.engine.createCounter
import app.sthira.engine.norms.NormLookup
import app.sthira.engine.norms.NormSex
import app.sthira.engine.norms.NormsParseResult
import app.sthira.engine.norms.lookupChairStandNorm
import app.sthira.engine.norms.parseChairStandNorms
import app.sthira.ui.common.CHOICE_CHECK_TAG
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChairStandSetupTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val codeError = "Use letters, digits, - or _ only"
    private val continueText = "Continue to camera"

    @Before
    fun open() {
        rule.openCard("30-second chair stand")
        rule.eventually { rule.assertScreen("30-second chair stand") }
    }

    private fun assertBlocked(reason: String) {
        rule.eventually { rule.button(continueText).assertIsNotEnabled() }
        rule.eventually { rule.onNode(hasText(reason)).assertExists() }
    }

    private fun assertContinueEnabled() {
        rule.eventually { rule.button(continueText).assertIsEnabled() }
        assertEquals(0, rule.onAllNodes(hasText("To continue, add:", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun emptyFormListsEveryMissingChoiceInOrder() {
        rule.eventually { assertBlocked("To continue, add: a participant code, an age band, sex (or Not recorded).") }
        rule.eventually { rule.onNode(hasText(codeError, substring = true)).assertDoesNotExist() }
        rule.eventually { rule.onNode(hasText("Counter not tuned yet")).assertDoesNotExist() }
    }

    @Test
    fun participantCodeValidation() {
        rule.chip("70-74").tap()
        rule.chip("Female").tap()
        rule.waitForIdle()
        // input -> (what the field holds after upper-casing, valid?)
        val cases = listOf(
            "" to ("" to false), "Ravi" to ("RAVI" to true), "P7" to ("P7" to true), "p07" to ("P07" to true),
            "A11" to ("A11" to true), "W3-B12" to ("W3-B12" to true), "P1234" to ("P1234" to true),
            "A 11" to ("A 11" to false), "A/11" to ("A/11" to false),
        )
        cases.forEach { (input, expected) ->
            val (stored, valid) = expected
            rule.type("Participant code", input)
            if (stored.isNotEmpty()) rule.field("Participant code").assert(hasText(stored))
            if (valid) {
                rule.eventually { assertContinueEnabled() }
                rule.eventually { rule.onNode(hasText(codeError, substring = true)).assertDoesNotExist() }
            } else {
                rule.eventually { assertBlocked("To continue, add: a participant code.") }
                // An empty field shows only the help line; a typed bad code shows the error.
                rule.eventually {
                    val errors = rule.onAllNodes(hasText(codeError, substring = true)).fetchSemanticsNodes().size
                    assertEquals("inline error for '$input'", if (input.isEmpty()) 0 else 1, errors)
                }
            }
        }
    }

    @Test
    fun everyAgeBandWithEverySex_selectsWithCheckMark_andEnablesContinue_normsMatchEngine() {
        val norms = (parseChairStandNorms(asset("norms.json")) as NormsParseResult.Ok).norms
        rule.type("Participant code", "P07")
        rule.eventually { rule.onNode(hasText("Adults only. CDC chair-stand values exist for 60-64 to 90-94.")).assertExists() }
        AgeBand.entries.forEach { band ->
            NormSex.entries.forEach { sex ->
                rule.chip(band.label).tap()
                rule.chip(sex.label).tap()
                rule.waitForIdle()
                rule.eventually { rule.chip(band.label).assertIsSelected() }
                rule.eventually { rule.chip(sex.label).assertIsSelected() }
                AgeBand.entries.filter { it != band }.forEach { rule.chip(it.label).assertIsNotSelected() }
                NormSex.entries.filter { it != sex }.forEach { rule.chip(it.label).assertIsNotSelected() }
                // Exactly two check marks, one beside each selected label.
                rule.eventually { assertEquals(2, rule.onAllNodes(hasTestTag(CHOICE_CHECK_TAG), useUnmergedTree = true).fetchSemanticsNodes().size) }
                rule.eventually { rule.onNode(hasTestTag(CHOICE_CHECK_TAG) and hasAnySibling(hasText(band.label)), useUnmergedTree = true).assertExists() }
                rule.eventually { rule.onNode(hasTestTag(CHOICE_CHECK_TAG) and hasAnySibling(hasText(sex.label)), useUnmergedTree = true).assertExists() }
                rule.eventually { assertContinueEnabled() }

                val cdcBand = band.ordinal in AgeBand.A60_64.ordinal..AgeBand.A90_94.ordinal
                val expectAvailable = cdcBand && sex != NormSex.NOT_RECORDED
                val lookup = lookupChairStandNorm(norms, band.label, sex)
                assertEquals("${band.label}/${sex.label}", expectAvailable, lookup is NormLookup.Available)
            }
        }
    }

    @Test
    fun recordingToggleAndEveryPilotField() {
        rule.type("Participant code", "P07")
        rule.chip("65-69").tap()
        rule.chip("Male").tap()
        rule.waitForIdle()
        rule.eventually { assertContinueEnabled() }

        val toggle = "Save a landmark recording of this run"
        rule.eventually { rule.check(toggle).assertIsOff() }
        rule.check(toggle).tap()
        rule.waitForIdle()
        rule.eventually { rule.check(toggle).assertIsOn() }
        rule.eventually { assertBlocked("To continue, add: clothing, camera angle, camera height, camera distance, the room, consent confirmation.") }

        Clothing.entries.forEach { c ->
            rule.chip(c.label).tap()
            rule.waitForIdle()
            rule.eventually { rule.chip(c.label).assertIsSelected() }
            Clothing.entries.filter { it != c }.forEach { rule.chip(it.label).assertIsNotSelected() }
        }
        ViewAngle.entries.forEach { a ->
            rule.chip(a.label).tap()
            rule.waitForIdle()
            rule.eventually { rule.chip(a.label).assertIsSelected() }
            ViewAngle.entries.filter { it != a }.forEach { rule.chip(it.label).assertIsNotSelected() }
        }
        rule.eventually { assertBlocked("To continue, add: camera height, camera distance, the room, consent confirmation.") }

        val heightError = "Enter the camera height in cm (floor to lens)"
        listOf("abc", "-5", "0", "1e", " ").forEach { bad ->
            rule.type("Camera height (cm, floor to lens)", bad)
            rule.eventually {
                val shown = rule.onAllNodes(hasText(heightError, substring = true)).fetchSemanticsNodes().size
                assertEquals("height '$bad'", if (bad.isBlank()) 0 else 1, shown)
            }
            rule.eventually { rule.button(continueText).assertIsNotEnabled() }
        }
        rule.type("Camera height (cm, floor to lens)", "150")
        rule.eventually { rule.onNode(hasText(heightError, substring = true)).assertDoesNotExist() }

        val distanceError = "Enter the camera distance in cm (lens to chair front)"
        listOf("x", "-1", "0").forEach { bad ->
            rule.type("Camera distance (cm, lens to chair front)", bad)
            rule.eventually { rule.onNode(hasText(distanceError, substring = true)).assertExists() }
        }
        rule.type("Camera distance (cm, lens to chair front)", "250.5")
        rule.eventually { rule.onNode(hasText(distanceError, substring = true)).assertDoesNotExist() }
        rule.eventually { assertBlocked("To continue, add: the room, consent confirmation.") }

        val roomError = "Describe the room, e.g. 'hostel common room'"
        rule.type("Room", "r".repeat(81))
        rule.eventually { rule.onNode(hasText(roomError, substring = true)).assertExists() }
        rule.type("Room", "living room")
        rule.eventually { rule.onNode(hasText(roomError, substring = true)).assertDoesNotExist() }

        rule.type("Notes (optional)", "n".repeat(501))
        rule.eventually { rule.onNode(hasText("Keep notes under 500 characters", substring = true)).assertExists() }
        rule.eventually { assertBlocked("To continue, add: shorter notes, consent confirmation.") }
        rule.type("Notes (optional)", "")
        rule.eventually { assertBlocked("To continue, add: consent confirmation.") }

        val consent = "Written consent has been signed by the participant"
        rule.eventually { rule.check(consent).assertIsOff() }
        rule.check(consent).tap()
        rule.waitForIdle()
        rule.eventually { rule.check(consent).assertIsOn() }
        rule.eventually { assertContinueEnabled() }
        rule.check(consent).tap()
        rule.waitForIdle()
        rule.eventually { assertBlocked("To continue, add: consent confirmation.") }

        // Turning recording off hides the pilot fields and drops their requirements.
        rule.check(toggle).tap()
        rule.waitForIdle()
        rule.eventually { rule.check(toggle).assertIsOff() }
        rule.eventually { rule.onNode(hasText("Saree")).assertDoesNotExist() }
        rule.eventually { assertContinueEnabled() }
    }

    @Test
    fun noDeveloperDetailsAreShownOnTheSetupScreen() {
        rule.waitForIdle()
        listOf("Counter not tuned yet", "Missing from params.json", "Why?", "Hide details", "leaveSeatFraction").forEach {
            rule.onNode(hasText(it, substring = true)).assertDoesNotExist()
        }
    }

    @Test
    fun systemBackFromSetupReturnsHome() {
        rule.systemBack()
        rule.eventually { rule.assertHome() }
    }
}
