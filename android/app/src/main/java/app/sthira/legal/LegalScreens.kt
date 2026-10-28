package app.sthira.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.sthira.ui.common.Body
import app.sthira.ui.common.Heading
import app.sthira.ui.common.LinkButton
import app.sthira.ui.common.Muted
import app.sthira.ui.common.ScreenScaffold

data class LegalSection(val heading: String, val body: List<String>)
data class LegalDoc(val title: String, val updated: String, val intro: String, val sections: List<LegalSection>)

private const val CONTACT = "Team The Bakchod Trinity, ruturajnalbalwar07@gmail.com."

/** Ported from web/src/app/privacy/page.tsx; browser and hosting wording adapted to this offline app. */
val PRIVACY = LegalDoc(
    title = "Privacy policy",
    updated = "28 October 2026",
    intro = "Sthira is a prototype that runs CDC STEADI strength and balance tests with a camera. It is built so that " +
        "your video and results never leave your device.",
    sections = listOf(
        LegalSection(
            "Camera video stays on your device",
            listOf(
                "Sthira asks for camera permission only when you open the chair-stand test. The video is processed by a pose " +
                    "model that runs on this phone. Sthira has no server that receives video, pose landmarks, scores or session " +
                    "details, and the app has no internet permission at all.",
                "Video is never saved. When you choose to save a landmark recording, it is kept in Sthira's private storage " +
                    "on this phone. It leaves the app only when you tap Export and pick where to save it, and Delete removes it.",
                "The pose library (Google MediaPipe) contains code that tries to send anonymous performance statistics to " +
                    "Google. The app has no internet permission, so Android blocks it and nothing is sent.",
                "Screenshots and screen recording are blocked while Sthira is open, and the recent-apps preview is hidden.",
            ),
        ),
        LegalSection(
            "What we do not collect",
            listOf(
                "No accounts, no names, no contact details, no analytics and no advertising trackers. Session forms ask for a " +
                    "participant code, never a name. Sex is used only to pick the CDC reference row and is never saved.",
            ),
        ),
        LegalSection(
            "Backups",
            listOf("Sthira opts out of Android cloud backup and device-to-device transfer, so private recordings are not copied off the phone."),
        ),
        LegalSection(
            "Pilot recordings",
            listOf(
                "Recordings made for our pilot study are collected only with signed, written consent from each participant, " +
                    "stored locally by the team, and handled as described in the consent form. Raw videos are deleted after labelling.",
            ),
        ),
        LegalSection("Contact", listOf("Questions about privacy: $CONTACT")),
    ),
)

/** Ported from web/src/app/terms/page.tsx. */
val TERMS = LegalDoc(
    title = "Terms of use",
    updated = "28 October 2026",
    intro = "By using Sthira you agree to these terms. Please read the safety section before running any test.",
    sections = listOf(
        LegalSection(
            "A prototype, not a medical device",
            listOf(
                "Sthira is an early research prototype. It has not been clinically validated and is not a certified medical " +
                    "device. It does not diagnose any condition and does not predict falls.",
                "Scores are shown next to CDC STEADI reference values, which come from US data and have not been validated for " +
                    "older adults in India. A qualified physiotherapist must interpret every result and decide on care.",
            ),
        ),
        LegalSection(
            "Safety during tests",
            listOf(
                "Run tests only under the supervision of a physiotherapist or trained helper, with a spotter standing close by. " +
                    "Use a stable chair without wheels, placed against a wall, and clear the floor of anything that could cause " +
                    "a trip. Stop the test at once if the person feels unwell, dizzy or unsteady.",
            ),
        ),
        LegalSection(
            "Your responsibilities",
            listOf(
                "Get the person's consent before recording them. Store any exported recordings securely and delete them when " +
                    "they are no longer needed. Use participant codes, not names.",
            ),
        ),
        LegalSection(
            "No warranty",
            listOf(
                "Sthira is provided as is, without any warranty. Counts can be wrong, for example in poor light, at a low frame " +
                    "rate or when the person leaves the frame. The team is not liable for decisions made using its output.",
            ),
        ),
        LegalSection("Contact", listOf(CONTACT)),
    ),
)

@Composable
fun LegalScreen(doc: LegalDoc, onBack: () -> Unit, onLicences: (() -> Unit)? = null) {
    ScreenScaffold(doc.title, onBack) {
        Muted("Last updated ${doc.updated}")
        Body(doc.intro, size = 18)
        doc.sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Heading(section.heading, size = 20)
                section.body.forEach { Body(it) }
            }
        }
        if (onLicences != null) LinkButton("Open-source licences", onLicences)
    }
}
