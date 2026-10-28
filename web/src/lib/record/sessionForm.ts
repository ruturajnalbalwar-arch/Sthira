import { sessionMetaSchema, type SessionMeta } from "./schema";

/** Raw form state: every field is what the input holds, before validation. */
export interface SessionFormValues {
  readonly participantCode: string;
  readonly ageBand: string;
  readonly clothing: string;
  readonly trial: string;
  readonly viewAngle: string;
  readonly cameraHeightCm: string;
  readonly cameraDistanceCm: string;
  readonly room: string;
  readonly notes: string;
  readonly consentConfirmed: boolean;
}

export type SessionField = keyof SessionFormValues;
export type SessionFieldErrors = Partial<Record<SessionField, string>>;

export type SessionFormResult =
  | { readonly ok: true; readonly meta: SessionMeta }
  | { readonly ok: false; readonly errors: SessionFieldErrors };

export const EMPTY_SESSION_FORM: SessionFormValues = {
  participantCode: "",
  ageBand: "",
  clothing: "",
  trial: "",
  viewAngle: "",
  cameraHeightCm: "",
  cameraDistanceCm: "",
  room: "",
  notes: "",
  consentConfirmed: false,
};

/**
 * Used only where zod's own message would be technical ("expected number, received NaN").
 * The schema's own messages (code format, consent, > 0) are always shown as written.
 */
const FRIENDLY: SessionFieldErrors = {
  ageBand: "Choose an age band",
  clothing: "Choose what the participant is wearing",
  trial: "Choose the trial you are recording",
  viewAngle: "Choose the camera angle",
  cameraHeightCm: "Enter the camera height in cm (floor to lens)",
  cameraDistanceCm: "Enter the camera distance in cm (lens to chair front)",
};

const ENUM_FIELDS: readonly SessionField[] = ["ageBand", "clothing", "trial", "viewAngle"];

function toNumber(raw: string): number | undefined {
  const trimmed = raw.trim();
  return trimmed === "" ? undefined : Number(trimmed);
}

function messageFor(field: SessionField, code: string, schemaMessage: string): string {
  const friendly = FRIENDLY[field];
  const technical = code === "invalid_type" || (code === "invalid_value" && ENUM_FIELDS.includes(field));
  return technical && friendly !== undefined ? friendly : schemaMessage;
}

/** Validates the form with `sessionMetaSchema` and returns one message per invalid field. */
export function parseSessionForm(values: SessionFormValues): SessionFormResult {
  const candidate = {
    ...values,
    cameraHeightCm: toNumber(values.cameraHeightCm),
    cameraDistanceCm: toNumber(values.cameraDistanceCm),
  };
  const parsed = sessionMetaSchema.safeParse(candidate);
  if (parsed.success) return { ok: true, meta: parsed.data };
  const errors = parsed.error.issues.reduce<SessionFieldErrors>((acc, issue) => {
    const field = issue.path[0] as SessionField;
    if (acc[field] !== undefined) return acc;
    return { ...acc, [field]: messageFor(field, issue.code, issue.message) };
  }, {});
  return { ok: false, errors };
}

/** Back from validated metadata to editable values (e.g. to record the next trial). */
export function sessionToForm(meta: SessionMeta): SessionFormValues {
  return {
    ...meta,
    cameraHeightCm: String(meta.cameraHeightCm),
    cameraDistanceCm: String(meta.cameraDistanceCm),
  };
}
