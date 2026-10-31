package app.sthira

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sthira.chairstand.AgeBand
import app.sthira.chairstand.TILE_CHECK_TAG
import app.sthira.chairstand.labelRes
import app.sthira.engine.norms.NormSex
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun str(id: Int, vararg args: Any): String = targetContext.getString(id, *args)

/** Step 1 of the chair-stand flow: age and sex as big tiles, one Next button. */
@RunWith(AndroidJUnit4::class)
class ChairStandSetupTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val nextText get() = str(R.string.cs_continue)

    private fun blocked(vararg ids: Int): String =
        str(R.string.cs_continue_blocked, ids.joinToString(str(R.string.cs_list_separator)) { str(it) })

    @Before
    fun open() {
        rule.openCard(str(R.string.home_test_chair_title))
        rule.eventually { rule.assertScreen(str(R.string.cs_title)) }
    }

    private fun assertBlocked(reason: String) {
        rule.eventually { rule.button(nextText).assertIsNotEnabled() }
        rule.eventually { rule.onNode(hasText(reason)).assertExists() }
    }

    private fun assertNextEnabled() {
        rule.eventually { rule.button(nextText).assertIsEnabled() }
        val prefix = str(R.string.cs_continue_blocked, "").removeSuffix(".")
        assertEquals(0, rule.onAllNodes(hasText(prefix, substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun showsStepOneOfThree_aboutThePerson() {
        val spoken = str(R.string.cs_details_line, str(R.string.cs_step_of, 1, 3), str(R.string.cs_step_person))
        rule.eventually { rule.onNode(isHeading() and hasContentDescription(spoken)).assertExists() }
    }

    @Test
    fun emptyFormListsEveryMissingChoiceInOrder() {
        rule.eventually { assertBlocked(blocked(R.string.cs_blocker_age, R.string.cs_blocker_sex)) }
    }

    @Test
    fun theFormHasOnlyAgeAndSex_noCodeAndNoRecordingInputs() {
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodes(isToggleable()).fetchSemanticsNodes().size)
        listOf("participant", "recording", "Clothing", "Camera height", "Room", "consent", "Export").forEach {
            rule.onNode(hasText(it, substring = true, ignoreCase = true)).assertDoesNotExist()
        }
    }

    @Test
    fun onlyAgeOrOnlySex_keepsNextDisabled() {
        rule.chip(str(R.string.cs_age_70_74)).tap()
        rule.eventually { assertBlocked(blocked(R.string.cs_blocker_sex)) }
    }

    @Test
    fun everyAgeBandWithEverySex_selectsWithCheckMark_andEnablesNext() {
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
                rule.eventually { assertEquals(2, rule.onAllNodes(hasTestTag(TILE_CHECK_TAG), useUnmergedTree = true).fetchSemanticsNodes().size) }
                rule.eventually { rule.onNode(hasTestTag(TILE_CHECK_TAG) and hasAnySibling(hasText(bandText)), useUnmergedTree = true).assertExists() }
                rule.eventually { rule.onNode(hasTestTag(TILE_CHECK_TAG) and hasAnySibling(hasText(sexText)), useUnmergedTree = true).assertExists() }
                rule.eventually { assertNextEnabled() }
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
