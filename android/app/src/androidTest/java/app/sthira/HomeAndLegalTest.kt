package app.sthira

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sthira.engine.CounterResult
import app.sthira.engine.createCounter
import app.sthira.legal.COMPONENTS
import app.sthira.legal.LegalDoc
import app.sthira.legal.LicenceText
import app.sthira.legal.PRIVACY
import app.sthira.legal.TERMS
import app.sthira.settings.AppLocales
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAndLegalTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun str(id: Int, vararg args: Any): String = targetContext.getString(id, *args)

    private val chairTitle get() = str(R.string.home_test_chair_title)
    private val otherTitles get() = listOf(str(R.string.home_test_balance_title), str(R.string.home_test_tug_title))
    private val testTitles get() = listOf(chairTitle) + otherTitles
    private val notTuned get() = str(R.string.home_badge_not_tuned)
    private val licencesTitle get() = str(R.string.legal_licences_title)

    @Test
    fun chairStandCardSaysCounterNotTuned_matchingTheEngine() {
        rule.assertHome()
        assertTrue(createCounter(asset("params.json")) is CounterResult.Untuned)
        rule.onNode(hasContentDescription(chairTitle, substring = true) and hasClickAction())
            .assert(hasContentDescription(notTuned, substring = true))
        // Only the chair-stand card carries the counter badge.
        otherTitles.forEach { title ->
            rule.onNode(hasContentDescription(title, substring = true) and hasClickAction())
                .assert(!hasContentDescription(notTuned, substring = true))
        }
    }

    @Test
    fun eachTestCardOpensItsScreen_andBackArrowAndSystemBackReturnHome() {
        testTitles.forEach { title ->
            rule.openCard(title)
            rule.assertScreen(title)
            rule.tapBackArrow()
            rule.assertHome()

            rule.openCard(title)
            rule.assertScreen(title)
            rule.systemBack()
            rule.assertHome()
        }
    }

    @Test
    fun privacyOpens_showsEverySection_andLinksToLicences() {
        rule.tapButton(str(R.string.home_link_privacy))
        assertLegalDoc(PRIVACY)
        rule.tapButton(str(R.string.legal_licences_title))
        rule.assertScreen(licencesTitle)
        rule.systemBack() // parent of Licences is Home
        rule.assertHome()

        rule.tapButton(str(R.string.home_link_privacy))
        rule.tapBackArrow()
        rule.assertHome()
    }

    @Test
    fun termsOpens_showsEverySection_andBackReturns() {
        rule.tapButton(str(R.string.home_link_terms))
        assertLegalDoc(TERMS)
        rule.onAllNodes(hasText(licencesTitle) and hasClickAction()).assertCountEquals0()
        rule.systemBack()
        rule.assertHome()
        rule.tapButton(str(R.string.home_link_terms))
        rule.tapBackArrow()
        rule.assertHome()
    }

    @Test
    fun licencesListEveryComponent_andEachFullTextOpensWithTheRightTitleAndFirstLine() {
        rule.tapButton(str(R.string.home_link_licences))
        rule.assertScreen(licencesTitle)
        COMPONENTS.forEach { c ->
            rule.onNode(hasText(c.name)).assertExists()
            val byLine = str(R.string.legal_component_by_licence, c.by, c.licence.title)
            rule.onAllNodes(hasText(byLine)).fetchSemanticsNodes().let { assertTrue(byLine, it.isNotEmpty()) }
            c.note?.let { rule.onNode(hasText(str(it))).assertExists() }
        }
        val firstLines = mapOf(
            LicenceText.Apache to "Apache License",
            LicenceText.Bsd3 to "Copyright 2008 Google Inc. (protobuf-javalite)",
            LicenceText.Mit to "MIT License",
        )
        LicenceText.entries.forEach { licence ->
            rule.tapButton(licence.title)
            rule.assertScreen(licence.title)
            val raw = targetContext.resources.openRawResource(licence.res).bufferedReader().use { it.readText() }
            assertEquals(firstLines.getValue(licence), raw.lines().first { it.isNotBlank() }.trim())
            rule.onNode(hasText(raw)).assertExists()
            // Back arrow returns to the list; system back too.
            rule.tapBackArrow()
            rule.assertScreen(licencesTitle)
            rule.tapButton(licence.title)
            rule.systemBack()
            rule.assertScreen(licencesTitle)
        }
        rule.systemBack()
        rule.assertHome()
    }

    @Test
    fun displayRow_textZoomStepsAndLanguageScreenListsEveryLanguageInItsOwnScript() {
        rule.assertHome()
        val larger = str(R.string.common_text_larger)
        val smaller = str(R.string.common_text_smaller)
        rule.onNode(hasContentDescription(str(R.string.common_text_size_state, 100))).assertExists()
        rule.onNode(hasContentDescription(larger, substring = true) and hasClickAction()).tap()
        rule.waitForIdle()
        rule.onNode(hasContentDescription(str(R.string.common_text_size_state, 125))).assertExists()
        rule.onNode(hasContentDescription(smaller, substring = true) and hasClickAction()).tap()
        rule.waitForIdle()
        rule.onNode(hasContentDescription(str(R.string.common_text_size_state, 100))).assertExists()

        rule.onNode(hasText(str(R.string.common_language_button)) and hasClickAction()).tap()
        rule.waitForIdle()
        rule.assertScreen(str(R.string.common_language_title))
        AppLocales.LANGUAGES.forEach { rule.onNode(hasText(str(it.nativeName)) and isSelectable()).assertExists() }
        rule.onNode(hasText(str(R.string.common_language_phone)) and isSelectable()).assertExists()
        rule.systemBack()
        rule.assertHome()
    }

    private fun assertLegalDoc(doc: LegalDoc) {
        rule.assertScreen(str(doc.title))
        rule.onNode(hasText(str(R.string.legal_last_updated, str(doc.updated)))).assertExists()
        rule.onNode(hasText(str(doc.intro))).assertExists()
        doc.sections.forEach { s ->
            val heading = str(s.heading)
            rule.onAllNodes(hasText(heading)).fetchSemanticsNodes().let { assertTrue(heading, it.isNotEmpty()) }
            s.body.forEach { rule.onNode(hasText(it.resolve(targetContext))).assertExists() }
        }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals0() =
    assertEquals(0, fetchSemanticsNodes().size)
