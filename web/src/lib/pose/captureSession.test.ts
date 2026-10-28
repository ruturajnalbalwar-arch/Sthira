import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { RecorderFactory, RecorderLike } from "../record/videoRecorder";
import { createCaptureSession } from "./captureSession";

class FakeRecorder extends EventTarget implements RecorderLike {
  state = "inactive";
  mimeType = "video/webm";
  start(): void {
    this.state = "recording";
  }
  stop(): void {
    this.state = "inactive";
    this.dispatchEvent(new Event("stop"));
  }
}

function setup() {
  const fake = new FakeRecorder();
  const factory: RecorderFactory = { isTypeSupported: () => true, create: () => fake };
  const onFailure = vi.fn();
  const session = createCaptureSession({} as MediaStream, { timesliceMs: 1000, eventTimeoutMs: 3000, onFailure }, factory);
  if (session === null) throw new Error("expected a session");
  return { fake, session, onFailure };
}

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe("createCaptureSession", () => {
  it("arms the recording clock only when the recorder's start event fires", () => {
    const { fake, session } = setup();
    expect(session.clock.stamp({ callbackMs: 10, captureMs: 5 })).toBeNull();
    fake.dispatchEvent(new Event("start"));
    expect(session.clock.stamp({ callbackMs: 20, captureMs: 15 })).toBe(0);
  });

  it("collects frames and de-duplicated issues, and hands everything over on finish", async () => {
    const { fake, session } = setup();
    fake.dispatchEvent(new Event("start"));
    const t = session.clock.stamp({ callbackMs: 20, captureMs: 15 });
    session.addFrame({ t: t ?? 0, landmarks: [] });
    session.flag("tab-hidden");
    session.flag("tab-hidden");
    expect(session.frameCount()).toBe(1);
    const data = await session.finish();
    expect(data.frames).toEqual([{ t: 0, landmarks: [] }]);
    expect(data.issues).toEqual(["tab-hidden"]);
    expect(data.frameClock).toBe("capture-time");
    expect(data.videoMime).toBe("video/webm");
    expect(data.startedAtIso).toMatch(/^\d{4}-\d{2}-\d{2}T/);
  });

  it("closes on finish: later frames do not change the data already handed over", async () => {
    const { fake, session } = setup();
    fake.dispatchEvent(new Event("start"));
    session.addFrame({ t: 0, landmarks: [] });
    const finishing = session.finish();
    session.addFrame({ t: 0.1, landmarks: [] });
    const data = await finishing;
    session.addFrame({ t: 0.2, landmarks: [] });
    expect(data.frames).toEqual([{ t: 0, landmarks: [] }]);
  });

  it("returns the same result if finish is called twice", async () => {
    const { fake, session } = setup();
    fake.dispatchEvent(new Event("start"));
    expect(session.finish()).toBe(session.finish());
  });

  it("returns a recorder failure through onFailure without losing frames", async () => {
    const { fake, session, onFailure } = setup();
    fake.dispatchEvent(new Event("start"));
    session.addFrame({ t: 0, landmarks: [] });
    fake.dispatchEvent(new Event("error"));
    expect(onFailure).toHaveBeenCalledWith(true);
    expect((await session.finish()).frames).toHaveLength(1);
  });

  it("returns null when the recorder cannot start", () => {
    const factory: RecorderFactory = {
      isTypeSupported: () => true,
      create: () => {
        throw new Error("no recorder");
      },
    };
    expect(createCaptureSession({} as MediaStream, { timesliceMs: 1000, eventTimeoutMs: 3000, onFailure: vi.fn() }, factory)).toBeNull();
  });
});
