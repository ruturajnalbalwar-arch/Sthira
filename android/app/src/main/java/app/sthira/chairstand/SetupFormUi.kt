package app.sthira.chairstand

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sthira.engine.norms.NormSex
import app.sthira.ui.common.ActionButton
import app.sthira.ui.common.CheckRow
import app.sthira.ui.common.ChoiceGroup
import app.sthira.ui.common.Heading
import app.sthira.ui.common.LabeledField
import app.sthira.ui.common.Muted
import app.sthira.ui.theme.Space

/** Participant, age and sex (for the CDC reference) and the optional pilot recording, as three clearly spaced sections. */
@Composable
fun SetupFormUi(form: SetupForm, onChange: (SetupForm) -> Unit) {
    val errors = SessionSetup.inlineErrors(form)
    Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
        Section("Participant") {
            LabeledField(
                "Participant code", form.participantCode, { onChange(form.copy(participantCode = it.uppercase())) },
                error = errors[SetupField.ParticipantCode], help = "A code like P07. Never a name.",
            )
        }
        Section("Age and sex") {
            ChoiceGroup(
                "Age band", AgeBand.entries, form.ageBand, { it.label }, { onChange(form.copy(ageBand = it)) },
                help = "Adults only. CDC chair-stand values exist for 60-64 to 90-94.",
            )
            ChoiceGroup(
                "Sex", NormSex.entries, form.sex, { it.label }, { onChange(form.copy(sex = it)) },
                help = "CDC lists separate values for women and men. Used on this screen only; never saved.",
            )
        }
        Section("Pilot recording (optional)") {
            CheckRow("Save a landmark recording of this run", form.saveRecording, { onChange(form.copy(saveRecording = it)) })
            AnimatedVisibility(form.saveRecording) { RecordingFields(form, errors, onChange) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading(title, size = 20)
        content()
    }
}

@Composable
private fun RecordingFields(form: SetupForm, errors: Map<SetupField, String>, onChange: (SetupForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Muted("Landmarks and these details only: no video, no name. Kept inside the app until you tap Export.")
        ChoiceGroup("Clothing", Clothing.entries, form.clothing, { it.label }, { onChange(form.copy(clothing = it)) })
        ChoiceGroup("Camera angle", ViewAngle.entries, form.viewAngle, { it.label }, { onChange(form.copy(viewAngle = it)) })
        LabeledField(
            "Camera height (cm, floor to lens)", form.cameraHeightCm, { onChange(form.copy(cameraHeightCm = it)) },
            error = errors[SetupField.CameraHeight], number = true,
        )
        LabeledField(
            "Camera distance (cm, lens to chair front)", form.cameraDistanceCm, { onChange(form.copy(cameraDistanceCm = it)) },
            error = errors[SetupField.CameraDistance], number = true,
        )
        LabeledField("Room", form.room, { onChange(form.copy(room = it)) }, error = errors[SetupField.Room], help = "e.g. living room")
        LabeledField("Notes (optional)", form.notes, { onChange(form.copy(notes = it)) }, error = errors[SetupField.Notes], singleLine = false)
        CheckRow(
            "Written consent has been signed by the participant", form.consentConfirmed,
            { onChange(form.copy(consentConfirmed = it)) },
        )
    }
}

/** Sticky bottom bar content: the button stays visible and says why it is disabled. */
@Composable
fun ContinueBar(form: SetupForm, onSubmit: () -> Unit) {
    val reason = SessionSetup.blockerSentence(form)
    if (reason != null) Muted(reason, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    ActionButton(
        "Continue to camera", onSubmit, Modifier.fillMaxWidth(), enabled = reason == null, big = true,
        icon = Icons.AutoMirrored.Filled.ArrowForward,
    )
}
