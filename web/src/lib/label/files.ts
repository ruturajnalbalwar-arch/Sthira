import type { z } from "zod";
import { CLOTHING, POSE_LANDMARK_COUNT, TRIALS, recordingSchema } from "../record/schema";
import { VIDEO_MIME_CANDIDATES, extensionForMime } from "../record/videoMime";
import { EVENT_TYPES, LABELS_SCHEMA, MAX_EVENTS, MAX_ID, MAX_NOTES, MAX_TEXT } from "./schema";

/**
 * File checks done before reading a file into memory. The size caps are guards against picking a
 * wrong (huge) file and freezing the tab; zod still validates every field after reading.
 */

/** The longest JSON text a double can take (sign, 17 significant digits, point, 3-digit exponent). */
const WORST_NUMBER = -1.2345678901234568e-300;
/** A character JSON.stringify escapes as \u0001: the most bytes one string unit can cost. */
const WORST_CHAR = "\u0001";
const BYTES_PER_MB = 1024 * 1024;

function maxLengthOf(schema: z.ZodType): number | null {
  for (const check of schema._zod.def.checks ?? []) {
    const def = check._zod.def as { check: string; maximum?: unknown };
    if (def.check === "max_length" && typeof def.maximum === "number") return def.maximum;
  }
  return null;
}

/** The record schema's frame cap, read from recordingSchema so the two can never drift. */
export function maxRecordingFrames(): number {
  const max = maxLengthOf(recordingSchema.shape.frames);
  if (max === null) throw new Error("recordingSchema.frames has no .max(): cannot bound recording files");
  return max;
}

const utf8Bytes = (text: string): number => new TextEncoder().encode(text).length;
const longest = (options: readonly string[]): string => options.reduce((a, b) => (b.length > a.length ? b : a));

/** One frame as record mode writes it (compact JSON.stringify), at its largest, plus the separating comma. */
export const WORST_FRAME_BYTES =
  utf8Bytes(
    JSON.stringify({
      t: WORST_NUMBER,
      landmarks: Array.from({ length: POSE_LANDMARK_COUNT }, () => ({
        x: WORST_NUMBER,
        y: WORST_NUMBER,
        z: WORST_NUMBER,
        visibility: WORST_NUMBER,
      })),
    }),
  ) + ",".length;

/**
 * Largest valid recording file: the frame cap times the largest frame, plus one frame's budget for
 * meta, capture and phases (a header at its text and phase caps is under one frame; see files.test.ts).
 */
export const RECORDING_MAX_BYTES = (maxRecordingFrames() + 1) * WORST_FRAME_BYTES;

/** Largest label file this tool writes (pretty-printed, as exported), every field at its cap. */
export const LABELS_MAX_BYTES = utf8Bytes(
  JSON.stringify(
    {
      schema: LABELS_SCHEMA,
      recording: {
        fileBase: "x".repeat(MAX_TEXT),
        participantCode: "P999",
        trial: longest(TRIALS),
        clothing: longest(CLOTHING),
        frameCount: Number.MAX_SAFE_INTEGER,
        firstT: WORST_NUMBER,
        lastT: WORST_NUMBER,
        goT: WORST_NUMBER,
      },
      labeller: { code: "L99", isExternal: false },
      createdAt: new Date(0).toISOString(),
      notes: WORST_CHAR.repeat(MAX_NOTES),
      events: Array.from({ length: MAX_EVENTS }, () => ({ id: WORST_CHAR.repeat(MAX_ID), type: longest(EVENT_TYPES), t: WORST_NUMBER })),
    },
    null,
    2,
  ),
);

const mb = (bytes: number): string => `${(bytes / BYTES_PER_MB).toFixed(1)} MB`;

/** A readable message when the file is larger than any valid file of its kind, else null. */
export function fileSizeProblem(file: { readonly size: number }, maxBytes: number, kind: string): string | null {
  if (file.size <= maxBytes) return null;
  return `This file is ${mb(file.size)}, larger than ${kind} can be (${mb(maxBytes)}). Check that you picked the right file.`;
}

/** Extensions record mode can write (its MIME list, plus Matroska which some browsers report). */
export const VIDEO_EXTENSIONS: readonly string[] = [
  ...new Set([...VIDEO_MIME_CANDIDATES, "video/x-matroska"].map(extensionForMime)),
];

const extensionOf = (name: string): string => (name.includes(".") ? (name.split(".").at(-1) ?? "").toLowerCase() : "");

/** Windows often reports an empty file.type, so the extension is accepted too. */
export function isVideoFile(name: string, type: string): boolean {
  return type.startsWith("video/") || VIDEO_EXTENSIONS.includes(extensionOf(name));
}

/** Record mode saves `<fileBase>.json` and `<fileBase>.<ext>`; a different stem may be the wrong video. */
export function videoMatchesRecording(videoName: string, fileBase: string): boolean {
  return videoName.replace(/\.[^.]*$/, "") === fileBase;
}
