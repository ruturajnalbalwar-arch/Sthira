import { describe, expect, it } from "vitest";
import { createRecordingClock } from "./recordingClock";

describe("createRecordingClock", () => {
  it("stamps nothing until the recorder's start event has armed it", () => {
    const clock = createRecordingClock();
    expect(clock.stamp({ callbackMs: 100, captureMs: 90 })).toBeNull();
    expect(clock.kind()).toBeNull();
    expect(clock.elapsedS(200)).toBeNull();
  });

  it("uses the capture time of the first frame after start as t = 0 (capture-time clock)", () => {
    const clock = createRecordingClock();
    clock.arm();
    expect(clock.stamp({ callbackMs: 1016, captureMs: 1000 })).toBe(0);
    expect(clock.kind()).toBe("capture-time");
    expect(clock.stamp({ callbackMs: 1049, captureMs: 1033.5 })).toBeCloseTo(0.0335);
    expect(clock.elapsedS(1500)).toBeCloseTo(0.5);
  });

  it("falls back to the callback time when the browser gives no capture time", () => {
    const clock = createRecordingClock();
    clock.arm();
    expect(clock.stamp({ callbackMs: 2000, captureMs: null })).toBe(0);
    expect(clock.kind()).toBe("callback-time");
    expect(clock.stamp({ callbackMs: 2040, captureMs: 2030 })).toBeCloseTo(0.04);
  });

  it("never mixes clocks: a capture-time recording skips frames without a capture time", () => {
    const clock = createRecordingClock();
    clock.arm();
    clock.stamp({ callbackMs: 10, captureMs: 5 });
    expect(clock.stamp({ callbackMs: 50, captureMs: null })).toBeNull();
    expect(clock.skippedCount()).toBe(1);
  });

  it("skips a frame whose time does not strictly increase", () => {
    const clock = createRecordingClock();
    clock.arm();
    clock.stamp({ callbackMs: 10, captureMs: 5 });
    clock.stamp({ callbackMs: 20, captureMs: 15 });
    expect(clock.stamp({ callbackMs: 30, captureMs: 15 })).toBeNull();
    expect(clock.stamp({ callbackMs: 30, captureMs: 12 })).toBeNull();
    expect(clock.skippedCount()).toBe(2);
  });

  it("never reports a negative elapsed time", () => {
    const clock = createRecordingClock();
    clock.arm();
    clock.stamp({ callbackMs: 10, captureMs: 500 });
    expect(clock.elapsedS(400)).toBe(0);
  });
});
