package app.sthira.chairstand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sthira.engine.norms.NormSex
import app.sthira.ui.common.ActionButton
import app.sthira.ui.common.Banner
import app.sthira.ui.common.BannerKind
import app.sthira.ui.common.CheckRow
import app.sthira.ui.common.ChoiceGroup
import app.sthira.ui.common.Heading
import app.sthira.ui.common.LabeledField
import app.sthira.ui.common.Muted

/** Participant code, CDC age band and sex; the pilot fields appear only when a recording is wanted. */
@Composable
fun SetupFormUi(form: SetupForm, errors: Map<SetupField, String>, onChange: (SetupForm) -> Unit, onSubmit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Heading("Session", size = 20)
        LabeledField(
            "Participant code", form.participantCode, { onChange(form.copy(participantCode = it.uppercase())) },
            error = errors[SetupField.ParticipantCode], help = "A code like P07. Never a name.",
        )
        ChoiceGroup(
            "Age band", AgeBand.entries, form.ageBand, { it.label }, { onChange(form.copy(ageBand = it)) },
            error = errors[SetupField.AgeBand],
            help = "Adults only. CDC chair-stand values exist for 60-64 to 90-94.",
        )
        ChoiceGroup(
            "Sex, for the CDC reference table", NormSex.entries, form.sex, { it.label }, { onChange(form.copy(sex = it)) },
            error = errors[SetupField.Sex],
            help = "CDC lists separate values for women and men. Used on this screen only; never saved.",
        )
        CheckRow("Save a landmark recording of this run (pilot data)", form.saveRecording, { onChange(form.copy(saveRecording = it)) })
        if (form.saveRecording) RecordingFields(form, errors, onChange)
        ActionButton("Continue to camera", onSubmit, Modifier.fillMaxWidth(), big = true)
    }
}

@Composable
private fun RecordingFields(form: SetupForm, errors: Map<SetupField, String>, onChange: (SetupForm) -> Unit) {
    Banner(BannerKind.Plain) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Muted(
                "The file holds body landmarks and these details only, no video and no name. It stays inside this app " +
                    "until you tap Export, and Delete removes it.",
            )
            ChoiceGroup(
                "Clothing", Clothing.entries, form.clothing, { it.label }, { onChange(form.copy(clothing = it)) },
                error = errors[SetupField.Clothing],
            )
            ChoiceGroup(
                "Camera angle", ViewAngle.entries, form.viewAngle, { it.label }, { onChange(form.copy(viewAngle = it)) },
                error = errors[SetupField.ViewAngle],
            )
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
                { onChange(form.copy(consentConfirmed = it)) }, error = errors[SetupField.Consent],
            )
        }
    }
}
