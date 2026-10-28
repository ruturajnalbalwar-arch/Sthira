import { ZodError } from "zod";
import type { PhaseSpan, RecordedFrame, Recording, SessionMeta } from "./schema";
import { buildRecording, type CaptureInfo } from "./session";

/** Tag for the fallback export. It is NOT a valid sthira.recording.v1 file and the fitter will reject it. */
export const RAW_EXPORT_SCHEMA = "sthira.recording.raw-unvalidated.v1";
/** How many validation messages to list (display only; the total is always reported). */
const MAX_LISTED_ERRORS = 10;

export interface RawRecordingExport {
  readonly schema: typeof RAW_EXPORT_SCHEMA;
  readonly validationErrors: readonly string[];
  readonly meta: SessionMeta;
  readonly capture: CaptureInfo;
  readonly phases: readonly PhaseSpan[];
  readonly frames: readonly RecordedFrame[];
}

export type AssembleResult =
  | { readonly ok: true; readonly recording: Recording }
  | { readonly ok: false; readonly errors: readonly string[]; readonly errorCount: number; readonly raw: RawRecordingExport };

function describe(error: unknown): { errors: string[]; count: number } {
  if (error instanceof ZodError) {
    const messages = error.issues.map((issue) => `${issue.path.join(".") || "recording"}: ${issue.message}`);
    return { errors: messages.slice(0, MAX_LISTED_ERRORS), count: messages.length };
  }
  return { errors: [error instanceof Error ? error.message : "Unknown validation error"], count: 1 };
}

/** Validates the capture. Never throws: on failure every frame is kept in a raw export so nothing is lost. */
export function assembleRecording(
  meta: SessionMeta,
  capture: CaptureInfo,
  phases: readonly PhaseSpan[],
  frames: readonly RecordedFrame[],
): AssembleResult {
  try {
    return { ok: true, recording: buildRecording(meta, capture, phases, frames) };
  } catch (error: unknown) {
    const { errors, count } = describe(error);
    return {
      ok: false,
      errors,
      errorCount: count,
      raw: { schema: RAW_EXPORT_SCHEMA, validationErrors: errors, meta, capture, phases, frames },
    };
  }
}
