package app.sthira

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sthira.chairstand.AgeBand
import app.sthira.chairstand.labelRes
import app.sthira.engine.norms.NormSex
import app.sthira.ui.common.CHOICE_CHECK_TAG
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun str(id: Int, vararg args: Any): String = targetContext.getString(id, *args)

@RunWith(AndroidJUnit4::class)
class ChairStandSetupTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val codeError get() = str(R.string.cs_error_code_chars)
    private val continueText get() = str(R.string.cs_continue)
    private val codeLabel get() = str(R.string.cs_code_label)

    private fun blocked(vararg ids: Int): String =
        str(R.string.cs_continue_blocked, ids.joinToString(str(R.string.cs_list_separator)) { str(it) })

    @Before
    fun open() {
        // The home card title belongs to the home screen.
        rule.openCard("30-second chair stand")
        rule.eventually { rule.assertScreen(str(R.string.cs_title)) }
    }

    private fun assertBlocked(reason: String) {
        rule.eventually { rule.button(continueText).assertIsNotEnabled() }
        rule.eventually { rule.onNode(hasText(reason)).assertExists() }
    }

    private fun assertContinueEnabled() {
        rule.eventually { rule.button(continueText).assertIsEnabled() }
        val prefix = str(R.string.cs_continue_blocked, "").removeSuffix(".")
        assertEquals(0, rule.onAllNodes(hasText(prefix, substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun emptyFormListsEveryMissingChoiceInOrder() {
        rule.eventually { assertBlocked(blocked(R.string.cs_blocker_code, R.string.cs_blocker_age, R.string.cs_blocker_sex)) }
        rule.eventually { rule.onNode(hasText(codeError, substring = true)).assertDoesNotExist() }
    }

    @Test
    fun theFormHasOnlyCodeAgeAndSex_noRecordingInputs() {
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodes(isToggleable()).fetchSemanticsNodes().size)
        listOf("recording", "Clothing", "Camera height", "Room", "consent", "Export").forEach {
            rule.onNode(hasText(it, substring = true, ignoreCase = true)).assertDoesNotExist()
        }
    }

    @Test
    fun participantCodeValidation() {
        rule.chip(str(R.string.cs_age_70_74)).tap()
        rule.chip(str(R.string.cs_sex_female)).tap()
        rule.waitForIdle()
        // input -> (what the field holds after upper-casing, valid?)
        val cases = listOf(
            "" to ("" to false), "Ravi" to ("RAVI" to true), "P7" to ("P7" to true), "p07" to ("P07" to true),
            "A11" to ("A11" to true), "W3-B12" to ("W3-B12" to true), "P1234" to ("P1234" to true),
            "A 11" to ("A 11" to false), "A/11" to ("A/11" to false),
        )
        cases.forEach { (input, expected) ->
            val (stored, valid) = expected
            rule.type(codeLabel, input)
            if (stored.isNotEmpty()) rule.field(codeLabel).assert(hasText(stored))
            if (valid) {
                rule.eventually { assertContinueEnabled() }
                rule.eventually { rule.onNode(hasText(codeError, substring = true)).assertDoesNotExist() }
            } else {
                rule.eventually { assertBlocked(blocked(R.string.cs_blocker_code)) }
                // An empty field shows only the help line; a typed bad code shows the error.
                rule.eventually {
                    val errors = rule.onAllNodes(hasText(codeError, substring = true)).fetchSemanticsNodes().size
                    assertEquals("inline error for '$input'", if (input.isEmpty()) 0 else 1, errors)
                }
            }
        }
    }

    @Test
    fun everyAgeBandWithEverySex_selectsWithCheckMark_andEnablesContinue() {
        rule.type(codeLabel, "P07")
        rule.eventually { rule.onNode(hasText(str(R.string.cs_age_help))).assertExists() }
        val bands = AgeBand.entries.associateWith { str(it.label) }
        val sexes = NormSex.entries.associateWith { str(it.labelRes) }
        bands.forEach { (band, bandText) ->
            sexes.forEach { (sex, sexText) ->
                rule.chip(bandText).tap()
                rule.chip(sexText).tap()
                rule.waitForIdle()
                rule.eventually { rule.chip(bandText).assertIsSelected() }
                rule.eventually { rule.chip(sexText).assertIsSelected() }
                bands.filterKeys { it != band }.values.forEach { rule.chip(it).assertIsNotSelected() }
                sexes.filterKeys { it != sex }.values.forEach { rule.chip(it).assertIsNotSelected() }
                // Exactly two check marks, one beside each selected label.
                rule.eventually { assertEquals(2, rule.onAllNodes(hasTestTag(CHOICE_CHECK_TAG), useUnmergedTree = true).fetchSemanticsNodes().size) }
                rule.eventually { rule.onNode(hasTestTag(CHOICE_CHECK_TAG) and hasAnySibling(hasText(bandText)), useUnmergedTree = true).assertExists() }
                rule.eventually { rule.onNode(hasTestTag(CHOICE_CHECK_TAG) and hasAnySibling(hasText(sexText)), useUnmergedTree = true).assertExists() }
                rule.eventually { assertContinueEnabled() }
            }
        }
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
