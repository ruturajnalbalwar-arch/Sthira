package app.sthira

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sthira.engine.CounterResult
import app.sthira.engine.createCounter
import app.sthira.legal.COMPONENTS
import app.sthira.legal.LegalDoc
import app.sthira.legal.LicenceText
import app.sthira.legal.PRIVACY
import app.sthira.legal.TERMS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAndLegalTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val testTitles = listOf("30-second chair stand", "4-stage balance", "Timed Up and Go")

    @Test
    fun chairStandCardSaysCounterNotTuned_matchingTheEngine() {
        rule.assertHome()
        assertTrue(createCounter(asset("params.json")) is CounterResult.Untuned)
        rule.onNode(hasContentDescription("30-second chair stand", substring = true) and hasClickAction())
            .assert(hasContentDescription("Counter not tuned", substring = true))
        // Only the chair-stand card carries the counter badge.
        listOf("4-stage balance", "Timed Up and Go").forEach { title ->
            rule.onNode(hasContentDescription(title, substring = true) and hasClickAction())
                .assert(!hasContentDescription("Counter not tuned", substring = true))
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
        rule.tapButton("Privacy")
        assertLegalDoc(PRIVACY)
        rule.tapButton("Open-source licences")
        rule.assertScreen("Open-source licences")
        rule.systemBack() // parent of Licences is Home
        rule.assertHome()

        rule.tapButton("Privacy")
        rule.tapBackArrow()
        rule.assertHome()
    }

    @Test
    fun termsOpens_showsEverySection_andBackReturns() {
        rule.tapButton("Terms")
        assertLegalDoc(TERMS)
        rule.onAllNodes(hasText("Open-source licences") and hasClickAction()).assertCountEquals0()
        rule.systemBack()
        rule.assertHome()
        rule.tapButton("Terms")
        rule.tapBackArrow()
        rule.assertHome()
    }

    @Test
    fun licencesListEveryComponent_andEachFullTextOpensWithTheRightTitleAndFirstLine() {
        rule.tapButton("Licences")
        rule.assertScreen("Open-source licences")
        COMPONENTS.forEach { c ->
            rule.onNode(hasText(c.name)).assertExists()
            rule.onAllNodes(hasText("${c.by} · ${c.licence.title}")).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
            c.note?.let { rule.onNode(hasText(it)).assertExists() }
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
            rule.assertScreen("Open-source licences")
            rule.tapButton(licence.title)
            rule.systemBack()
            rule.assertScreen("Open-source licences")
        }
        rule.systemBack()
        rule.assertHome()
    }

    private fun assertLegalDoc(doc: LegalDoc) {
        rule.assertScreen(doc.title)
        rule.onNode(hasText("Last updated ${doc.updated}")).assertExists()
        rule.onNode(hasText(doc.intro)).assertExists()
        doc.sections.forEach { s ->
            rule.onAllNodes(hasText(s.heading)).fetchSemanticsNodes().let { assertTrue(s.heading, it.isNotEmpty()) }
            s.body.forEach { rule.onNode(hasText(it)).assertExists() }
        }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals0() =
    assertEquals(0, fetchSemanticsNodes().size)
