import { z } from "zod";
import { POSE_LANDMARK_COUNT } from "../sthira/landmarks";

/** Version tag written into every recording so the fitter can reject unknown formats. */
export const RECORDING_SCHEMA = "sthira.recording.v1";

/** Matches docs/pilot/protocol.md; the 60+ bands follow the CDC STEADI norm table (5-year bands). */
export const AGE_BANDS = ["18-29", "30-44", "45-59", "60-64", "65-69", "70-74", "75-79", "80+"] as const;
export const CLOTHING = ["saree", "dhoti", "lungi", "salwar-kameez", "kurta-pyjama", "track-pants", "other"] as const;
export const VIEW_ANGLES = ["side", "45-degree", "front"] as const;
export const TRIALS = [
  "chair-stand-30s",
  "half-stands",
  "arm-assisted-stands",
  "balance-side-by-side",
  "balance-semi-tandem",
  "balance-tandem",
  "balance-single-leg",
  "tug",
] as const;
/** Problems during capture. A recording with any issue is kept but must be excluded from tuning/evaluation. */
export const CAPTURE_ISSUES = ["tab-hidden", "detection-failed", "camera-ended", "recorder-failed", "frame-limit"] as const;
export const FRAME_CLOCKS = ["capture-time", "callback-time"] as const;
export const PHASES = ["calibration-sit", "calibration-stand", "test"] as const;

export { POSE_LANDMARK_COUNT };
/** Upper bounds that protect the app and the fitter from oversized or malicious files. */
export const MAX_FRAMES = 60 * 60 * 10; // ten minutes at 60 fps
const MAX_PHASES = 16;
const MAX_TEXT = 200;
/** The code is part of every saved file name, so it is limited to characters that are safe there. Same limit as the labelling tool ids. */
export const MAX_PARTICIPANT_CODE = 40;

const positiveMeasure = z.number().positive("Enter a measured value greater than 0");

export const sessionMetaSchema = z.object({
  participantCode: z
    .string()
    .trim()
    .min(1, "Enter a participant code")
    .max(MAX_PARTICIPANT_CODE, `Use at most ${MAX_PARTICIPANT_CODE} characters`)
    .regex(/^[A-Za-z0-9_-]+$/, "Use letters, digits, - or _ only (it is part of the file name)"),
  ageBand: z.enum(AGE_BANDS),
  clothing: z.enum(CLOTHING),
  trial: z.enum(TRIALS),
  viewAngle: z.enum(VIEW_ANGLES),
  cameraHeightCm: positiveMeasure,
  cameraDistanceCm: positiveMeasure,
  room: z.string().trim().min(1, "Describe the room, e.g. 'hostel common room'").max(80),
  notes: z.string().trim().max(500).default(""),
  consentConfirmed: z.literal(true, { error: "Written consent must be signed before recording" }),
});
export type SessionMeta = z.infer<typeof sessionMetaSchema>;

export const landmarkSchema = z.object({
  x: z.number(),
  y: z.number(),
  z: z.number(),
  visibility: z.number(),
});

export const recordedFrameSchema = z.object({
  /** Seconds since the recording started (monotonic). */
  t: z.number().nonnegative(),
  /** 33 MediaPipe landmarks, or an empty array when no person was detected. */
  landmarks: z
    .array(landmarkSchema)
    .refine((list) => list.length === 0 || list.length === POSE_LANDMARK_COUNT, {
      message: `A frame has either no person (0 landmarks) or exactly ${POSE_LANDMARK_COUNT}`,
    }),
});
export type RecordedFrame = z.infer<typeof recordedFrameSchema>;

export const phaseSpanSchema = z
  .object({
    phase: z.enum(PHASES),
    startT: z.number().nonnegative(),
    endT: z.number().nonnegative(),
  })
  .refine((span) => span.endT >= span.startT, { message: "A phase cannot end before it starts" });
export type PhaseSpan = z.infer<typeof phaseSpanSchema>;

export const recordingSchema = z.object({
  schema: z.literal(RECORDING_SCHEMA),
  meta: sessionMetaSchema,
  /** Strict: unknown keys (e.g. a device fingerprint such as userAgent) are rejected, not stored. */
  capture: z
    .object({
      startedAt: z.string().max(MAX_TEXT),
      videoWidth: z.number().int().positive(),
      videoHeight: z.number().int().positive(),
      model: z.string().max(MAX_TEXT),
      tasksVisionVersion: z.string().max(MAX_TEXT),
      /**
       * Where each frame's t comes from. "capture-time": the camera's capture timestamp
       * (requestVideoFrameCallback metadata.captureTime), aligned to the video. "callback-time":
       * the frame callback time, later than capture by the display/detection latency.
       */
      frameClock: z.enum(FRAME_CLOCKS),
      /** Empty for a clean capture. */
      issues: z.array(z.enum(CAPTURE_ISSUES)).max(CAPTURE_ISSUES.length),
    })
    .strict(),
  phases: z.array(phaseSpanSchema).max(MAX_PHASES),
  frames: z
    .array(recordedFrameSchema)
    .max(MAX_FRAMES)
    .superRefine((frames, ctx) => {
      const bad = frames.findIndex((frame, i) => i > 0 && frame.t <= frames[i - 1].t);
      if (bad > 0) ctx.addIssue({ code: "custom", path: [bad, "t"], message: `Frame timestamps must strictly increase (frame ${bad})` });
    }),
});
export type Recording = z.infer<typeof recordingSchema>;
