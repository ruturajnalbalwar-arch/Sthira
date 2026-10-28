import type { FRAME_CLOCKS } from "../record/schema";
import type { FrameTiming } from "./frameLoop";

export type FrameClock = (typeof FRAME_CLOCKS)[number];

/**
 * The time origin shared by the saved video and the landmark frames.
 *
 * `arm()` is called from MediaRecorder's "start" event. The first frame that arrives after that
 * becomes t = 0, stamped with its camera capture time (requestVideoFrameCallback
 * metadata.captureTime, performance.now() time base) when the browser gives one ("capture-time"),
 * otherwise with its frame-callback time ("callback-time"). Every later frame uses the same clock,
 * so frames are never stamped with a mix of the two.
 *
 * Residual error: the recorder's first encoded frame is the camera frame current when recording
 * began; t = 0 is the first frame our loop handles after the start event. While a detection is
 * running, newly presented frames get no callback, so that first handled frame can be up to one
 * processed-frame interval later than the video's first frame: video time 0 and frame t 0 differ
 * by at most about 1 / (effective fps), e.g. ~33 ms at 30 fps, ~200 ms at 5 fps on a slow device.
 * (It can also be captured up to one camera frame before the start event.) This offset is a
 * constant shift for the whole recording, not drift. With "callback-time" the capture-to-callback
 * latency is added on top; it is roughly constant and so mostly cancels out of t.
 */
export interface RecordingClock {
  arm(): void;
  /** Seconds since t = 0 for a frame that belongs to the recording, or null to leave it out. */
  stamp(timing: FrameTiming): number | null;
  kind(): FrameClock | null;
  /** Seconds since t = 0 at `nowMs` (performance.now()), or null before the first frame. */
  elapsedS(nowMs: number): number | null;
  /** Frames left out because they lacked a capture time or did not advance the clock. */
  skippedCount(): number;
}

export function createRecordingClock(): RecordingClock {
  let armed = false;
  let origin: number | null = null;
  let clock: FrameClock | null = null;
  let lastT = Number.NEGATIVE_INFINITY;
  let skipped = 0;

  const skip = (): null => {
    skipped += 1;
    return null;
  };

  return {
    arm() {
      armed = true;
    },
    stamp(timing) {
      if (!armed) return null;
      if (origin === null) {
        clock = timing.captureMs === null ? "callback-time" : "capture-time";
        origin = timing.captureMs ?? timing.callbackMs;
        lastT = 0;
        return 0;
      }
      const ms = clock === "capture-time" ? timing.captureMs : timing.callbackMs;
      if (ms === null) return skip();
      const t = (ms - origin) / 1000;
      if (!(t > lastT)) return skip();
      lastT = t;
      return t;
    },
    kind: () => clock,
    elapsedS: (nowMs) => (origin === null ? null : Math.max(0, (nowMs - origin) / 1000)),
    skippedCount: () => skipped,
  };
}
