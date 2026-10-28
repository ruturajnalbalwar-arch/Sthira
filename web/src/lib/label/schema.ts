import { z } from "zod";
import type { RepKind } from "../sthira/chairStand";
import { sessionMetaSchema, type Recording, type SessionMeta } from "../record/schema";
import { recordingFileBase } from "../record/session";

/** Version tag written into every label file (docs/pilot/labelling-rules.md, v1). */
export const LABELS_SCHEMA = "sthira.labels.v1";

/** Upper bounds that protect the app from oversized or malicious files; far above any trial. */
export const MAX_EVENTS = 1000;
export const MAX_ID = 40;
export const MAX_TEXT = 200;
export const MAX_NOTES = 500;
/** How many validation messages a file field shows; a broken 36 000-frame file would otherwise flood the page. */
export const MAX_SHOWN_ERRORS = 8;

export type Trial = SessionMeta["trial"];
export type KeyFamily = "rep" | "balance" | "tug";

/** The label keys from labelling-rules.md. Z (undo) is a command, not a label, so it is not here. */
export const LABEL_KEYS = ["F", "H", "A", "U", "B", "L", "T"] as const;
export type LabelKey = (typeof LABEL_KEYS)[number];

export const REP_KINDS = ["full", "half", "arm-assisted", "uncertain"] as const satisfies readonly RepKind[];
export const EVENT_TYPES = [
  ...REP_KINDS,
  "balance-start",
  "balance-end",
  "balance-lost",
  "tug-start",
  "tug-end",
] as const;
export type EventType = (typeof EVENT_TYPES)[number];

export interface KeyInfo {
  readonly family: KeyFamily;
  readonly meaning: string;
}

/** Key table copied from labelling-rules.md ("Keys in the labelling tool"). */
export const KEY_INFO: Readonly<Record<LabelKey, KeyInfo>> = {
  F: { family: "rep", meaning: "Full stand" },
  H: { family: "rep", meaning: "Half stand" },
  A: { family: "rep", meaning: "Arm-assisted stand" },
  U: { family: "rep", meaning: "Uncertain (cannot decide)" },
  B: { family: "balance", meaning: "Balance stage start, then balance stage end (press once for each)" },
  L: { family: "balance", meaning: "Lost position (balance); also ends the stage" },
  T: { family: "tug", meaning: "TUG start, then TUG end (press once for each)" },
};

export const EVENT_KEY: Readonly<Record<EventType, LabelKey>> = {
  full: "F",
  half: "H",
  "arm-assisted": "A",
  uncertain: "U",
  "balance-start": "B",
  "balance-end": "B",
  "balance-lost": "L",
  "tug-start": "T",
  "tug-end": "T",
};

export const EVENT_NAME: Readonly<Record<EventType, string>> = {
  full: "Full stand",
  half: "Half stand",
  "arm-assisted": "Arm-assisted stand",
  uncertain: "Uncertain attempt",
  "balance-start": "Balance stage start",
  "balance-end": "Balance stage end",
  "balance-lost": "Lost position",
  "tug-start": "TUG start",
  "tug-end": "TUG end",
};

/** Which key family each recorded trial uses (labelling-rules.md sections). */
export const TRIAL_FAMILY: Readonly<Record<Trial, KeyFamily>> = {
  "chair-stand-30s": "rep",
  "half-stands": "rep",
  "arm-assisted-stands": "rep",
  "balance-side-by-side": "balance",
  "balance-semi-tandem": "balance",
  "balance-tandem": "balance",
  "balance-single-leg": "balance",
  tug: "tug",
};

export const eventFamily = (type: EventType): KeyFamily => KEY_INFO[EVENT_KEY[type]].family;
export const isRepKind = (type: EventType): type is RepKind => (REP_KINDS as readonly string[]).includes(type);

export const labelEventSchema = z.object({
  id: z.string().min(1).max(MAX_ID),
  type: z.enum(EVENT_TYPES),
  /** Seconds on the recording's time axis (the same `t` as its frames). */
  t: z.number().nonnegative(),
});
export type LabelEvent = z.infer<typeof labelEventSchema>;

export const recordingRefSchema = z
  .object({
    fileBase: z.string().min(1).max(MAX_TEXT).regex(/^[A-Za-z0-9_.-]+$/, "Letters, digits, _ . - only"),
    participantCode: sessionMetaSchema.shape.participantCode,
    trial: sessionMetaSchema.shape.trial,
    clothing: sessionMetaSchema.shape.clothing,
    /** Fingerprint: frame count plus first and last frame time identify the recording. */
    frameCount: z.number().int().positive(),
    firstT: z.number().nonnegative(),
    lastT: z.number().nonnegative(),
    /** "Go": start of the recording's test phase. Labels before it are not allowed. */
    goT: z.number().nonnegative(),
  })
  .refine((r) => r.lastT >= r.firstT, { message: "lastT cannot be before firstT", path: ["lastT"] })
  .refine((r) => r.goT <= r.lastT, { message: "Go (test start) is after the last frame", path: ["goT"] });
export type RecordingRef = z.infer<typeof recordingRefSchema>;

export const labellerSchema = z.object({
  code: z
    .string()
    .trim()
    .regex(/^L\d{1,2}$/, "Use a labeller code like L1, never a name"),
  /** True for the outside labeller (ideally a physiotherapist), false for the team member. */
  isExternal: z.boolean(),
});
export type Labeller = z.infer<typeof labellerSchema>;

const PAIRS: ReadonlyArray<{ family: KeyFamily; start: EventType; ends: readonly EventType[] }> = [
  { family: "balance", start: "balance-start", ends: ["balance-end", "balance-lost"] },
  { family: "tug", start: "tug-start", ends: ["tug-end"] },
];

/** Starts and ends of one family must alternate; a trailing start (still open) is allowed. */
export function pairingProblem(events: readonly LabelEvent[]): string | null {
  for (const pair of PAIRS) {
    let open = false;
    for (const event of events) {
      if (event.type === pair.start) {
        if (open) return `${EVENT_NAME[event.type]} at ${event.t} s: the previous one has no end yet`;
        open = true;
      } else if (pair.ends.includes(event.type)) {
        if (!open) return `${EVENT_NAME[event.type]} at ${event.t} s has no start before it`;
        open = false;
      }
    }
  }
  return null;
}

function eventProblem(event: LabelEvent, index: number, events: readonly LabelEvent[], ref: RecordingRef) {
  if (eventFamily(event.type) !== TRIAL_FAMILY[ref.trial]) return `${event.type} is not a ${ref.trial} label`;
  if (event.t < ref.goT) return `${event.type} at ${event.t} s is before Go (${ref.goT} s)`;
  if (event.t < ref.firstT || event.t > ref.lastT) return `${event.type} at ${event.t} s is outside the recording`;
  if (index > 0 && event.t <= events[index - 1].t) return `events must be in strictly increasing time (${event.id})`;
  if (events.findIndex((e) => e.id === event.id) !== index) return `duplicate event id ${event.id}`;
  return null;
}

/** Every rule a list of events must satisfy for this recording. Empty array means valid. */
export function eventProblems(events: readonly LabelEvent[], ref: RecordingRef): string[] {
  const perEvent = events.flatMap((event, i) => eventProblem(event, i, events, ref) ?? []);
  const pairing = pairingProblem(events);
  return pairing === null ? perEvent : [...perEvent, pairing];
}

export const labelsSchema = z
  .object({
    schema: z.literal(LABELS_SCHEMA),
    recording: recordingRefSchema,
    labeller: labellerSchema,
    createdAt: z.iso.datetime(),
    notes: z.string().trim().max(MAX_NOTES).default(""),
    events: z.array(labelEventSchema).max(MAX_EVENTS),
  })
  .superRefine((file, ctx) => {
    for (const message of eventProblems(file.events, file.recording)) {
      ctx.addIssue({ code: "custom", message, path: ["events"] });
    }
  });
export type LabelsFile = z.infer<typeof labelsSchema>;

export type RefResult = { readonly ok: true; readonly ref: RecordingRef } | { readonly ok: false; readonly error: string };

/** Build the label file's recording reference. Fails when the recording cannot be labelled. */
export function recordingRef(recording: Recording): RefResult {
  const frames = recording.frames;
  if (frames.length === 0) return { ok: false, error: "The recording has no frames, so there is nothing to label." };
  const test = recording.phases.find((p) => p.phase === "test");
  if (test === undefined) {
    return { ok: false, error: "The recording has no test phase, so there is no Go time to label from." };
  }
  const fileBase = recordingFileBase(recording.meta, recording.capture.startedAt).replace(/[^A-Za-z0-9_.-]/g, "-");
  const parsed = recordingRefSchema.safeParse({
    fileBase,
    participantCode: recording.meta.participantCode,
    trial: recording.meta.trial,
    clothing: recording.meta.clothing,
    frameCount: frames.length,
    firstT: frames[0].t,
    lastT: frames[frames.length - 1].t,
    goT: test.startT,
  });
  return parsed.success ? { ok: true, ref: parsed.data } : { ok: false, error: formatIssues(parsed.error).join("; ") };
}

export const sameRecording = (a: RecordingRef, b: RecordingRef): boolean =>
  (Object.keys(a) as (keyof RecordingRef)[]).every((key) => a[key] === b[key]);

export const labelFileName = (ref: RecordingRef, labellerCode: string): string =>
  `${ref.fileBase}_labels_${labellerCode}.json`;

/** Readable "path: message" strings for the first `limit` issues only (formatting is skipped for the rest). */
export function formatIssues(error: z.ZodError, limit: number = error.issues.length): string[] {
  return error.issues.slice(0, limit).map((issue) => {
    const path = issue.path.join(".");
    return path === "" ? issue.message : `${path}: ${issue.message}`;
  });
}

export type ParseResult<T> =
  | { readonly ok: true; readonly data: T }
  | { readonly ok: false; readonly errors: string[]; readonly totalErrors: number };

/** JSON.parse plus zod validation; never throws. Error strings are readable "path: message". */
export function parseJsonWith<T>(schema: z.ZodType<T>, text: string, maxErrors: number = MAX_SHOWN_ERRORS): ParseResult<T> {
  let json: unknown;
  try {
    json = JSON.parse(text);
  } catch (error: unknown) {
    const detail = error instanceof Error ? error.message : "unknown error";
    return { ok: false, errors: [`The file is not valid JSON (${detail}).`], totalErrors: 1 };
  }
  const parsed = schema.safeParse(json);
  if (parsed.success) return { ok: true, data: parsed.data };
  return { ok: false, errors: formatIssues(parsed.error, maxErrors), totalErrors: parsed.error.issues.length };
}
