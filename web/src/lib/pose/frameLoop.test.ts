import { describe, expect, it, vi } from "vitest";
import {
  createFpsMeter,
  createTimestampGate,
  startFrameLoop,
  type FrameScheduler,
  type FrameSource,
  type FrameTiming,
} from "./frameLoop";

describe("createTimestampGate", () => {
  it("accepts only strictly increasing timestamps", () => {
    const gate = createTimestampGate();
    expect(gate.accept(10)).toBe(true);
    expect(gate.accept(10)).toBe(false);
    expect(gate.accept(9.5)).toBe(false);
    expect(gate.accept(10.001)).toBe(true);
  });
});

type VideoCallback = (now: number, metadata: { captureTime?: number }) => void;

function fakeVideoSource(): FrameSource & { fire: (now: number, captureTime?: number) => void } {
  let callback: VideoCallback | null = null;
  return {
    requestVideoFrameCallback: (cb: VideoCallback) => {
      callback = cb;
      return 1;
    },
    cancelVideoFrameCallback: vi.fn(() => {
      callback = null;
    }),
    fire: (now, captureTime) => {
      const current = callback;
      callback = null;
      current?.(now, captureTime === undefined ? {} : { captureTime });
    },
  };
}

const noAnimationFrame: FrameScheduler = {
  request: () => {
    throw new Error("should use requestVideoFrameCallback");
  },
  cancel: () => undefined,
};

describe("startFrameLoop with requestVideoFrameCallback", () => {
  it("passes the callback time and the camera capture time of each presented frame", () => {
    const video = fakeVideoSource();
    const seen: FrameTiming[] = [];
    startFrameLoop(video, (timing) => void seen.push(timing), { fallback: noAnimationFrame });
    video.fire(116, 100);
    video.fire(149);
    expect(seen).toEqual([
      { callbackMs: 116, captureMs: 100 },
      { callbackMs: 149, captureMs: null },
    ]);
  });

  it("stops scheduling after stop() and cancels the pending callback", () => {
    const video = fakeVideoSource();
    const onFrame = vi.fn();
    const loop = startFrameLoop(video, onFrame, { fallback: noAnimationFrame });
    loop.stop();
    video.fire(1);
    expect(onFrame).not.toHaveBeenCalled();
    expect(video.cancelVideoFrameCallback).toHaveBeenCalled();
  });

  it("stops when onFrame returns false (e.g. detection failed)", () => {
    const video = fakeVideoSource();
    const onFrame = vi.fn(() => false);
    startFrameLoop(video, onFrame, { fallback: noAnimationFrame });
    video.fire(1);
    video.fire(2);
    expect(onFrame).toHaveBeenCalledTimes(1);
  });
});

describe("startFrameLoop with the animation-frame fallback", () => {
  function fallbackScheduler() {
    let callback: ((now: number) => void) | null = null;
    const scheduler: FrameScheduler = { request: (cb) => ((callback = cb), 1), cancel: vi.fn() };
    const fire = (now: number) => {
      const current = callback;
      callback = null;
      current?.(now);
    };
    return { scheduler, fire };
  }

  it("uses the animation-frame time (taken before detection) and has no capture time", () => {
    const { scheduler, fire } = fallbackScheduler();
    const source = { currentTime: 0 };
    const seen: FrameTiming[] = [];
    const loop = startFrameLoop(source, (timing) => void seen.push(timing), { fallback: scheduler });
    fire(16);
    loop.stop();
    expect(seen).toEqual([{ callbackMs: 16, captureMs: null }]);
    expect(scheduler.cancel).toHaveBeenCalled();
  });

  it("skips animation frames where the video has not moved to a new frame", () => {
    const { scheduler, fire } = fallbackScheduler();
    const source = { currentTime: 0.5 };
    const seen: number[] = [];
    startFrameLoop(source, (timing) => void seen.push(timing.callbackMs), { fallback: scheduler });
    fire(16);
    fire(32);
    source.currentTime = 0.533;
    fire(48);
    expect(seen).toEqual([16, 48]);
  });
});

describe("createFpsMeter", () => {
  it("reports frames per second once each window closes", () => {
    const meter = createFpsMeter(1000);
    const readings = [0, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000].map((t) => meter.frame(t));
    expect(readings.slice(0, -1).every((reading) => reading === null)).toBe(true);
    expect(readings.at(-1)).toBeCloseTo(10);
  });

  it("starts a fresh window after each reading", () => {
    const meter = createFpsMeter(1000);
    [0, 500, 1000].forEach((t) => meter.frame(t));
    expect(meter.frame(1500)).toBeNull();
    expect(meter.frame(2000)).toBeCloseTo(2);
  });
});
