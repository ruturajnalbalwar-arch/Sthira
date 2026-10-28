import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { startVideoRecorder, type RecorderFactory, type RecorderLike } from "./videoRecorder";

class FakeRecorder extends EventTarget implements RecorderLike {
  state: "inactive" | "recording" = "inactive";
  mimeType = "video/webm;codecs=vp9";
  timeslice: number | undefined;
  start(timeslice?: number): void {
    this.timeslice = timeslice;
    this.state = "recording";
  }
  stop(): void {
    this.state = "inactive";
  }
  emitStart(): void {
    this.dispatchEvent(new Event("start"));
  }
  emitData(text: string): void {
    this.dispatchEvent(Object.assign(new Event("dataavailable"), { data: new Blob([text]) }));
  }
  emitStop(): void {
    this.dispatchEvent(new Event("stop"));
  }
}

function factory(recorder: FakeRecorder): RecorderFactory {
  return { isTypeSupported: () => true, create: () => recorder };
}

const stream = {} as MediaStream;
const options = (overrides = {}) => ({ timesliceMs: 1000, eventTimeoutMs: 3000, onStart: vi.fn(), onFailure: vi.fn(), ...overrides });

beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());

describe("startVideoRecorder", () => {
  it("starts with a timeslice so data is flushed while recording", () => {
    const fake = new FakeRecorder();
    startVideoRecorder(stream, options(), factory(fake));
    expect(fake.timeslice).toBe(1000);
  });

  it("calls onStart when the recorder reports it has started", () => {
    const fake = new FakeRecorder();
    const opts = options();
    startVideoRecorder(stream, opts, factory(fake));
    fake.emitStart();
    expect(opts.onStart).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(5000);
    expect(opts.onFailure).not.toHaveBeenCalled();
  });

  it("fails (before start) and stops the recorder when the start event never arrives", () => {
    const fake = new FakeRecorder();
    const opts = options();
    startVideoRecorder(stream, opts, factory(fake));
    vi.advanceTimersByTime(3000);
    expect(opts.onFailure).toHaveBeenCalledWith(false);
    expect(fake.state).toBe("inactive");
  });

  it("returns null when the recorder cannot be created or started", () => {
    const throwing: RecorderFactory = {
      isTypeSupported: () => true,
      create: () => {
        throw new Error("no");
      },
    };
    expect(startVideoRecorder(stream, options(), throwing)).toBeNull();
    const fake = new FakeRecorder();
    fake.start = () => {
      throw new Error("InvalidStateError");
    };
    expect(startVideoRecorder(stream, options(), factory(fake))).toBeNull();
  });

  it("keeps every chunk and resolves stop() with all of them", async () => {
    const fake = new FakeRecorder();
    const handle = startVideoRecorder(stream, options(), factory(fake));
    fake.emitStart();
    fake.emitData("ab");
    fake.emitData("cd");
    const stopped = handle!.stop();
    fake.emitData("ef");
    fake.emitStop();
    const blob = await stopped;
    expect(await blob.text()).toBe("abcdef");
    expect(blob.type).toBe("video/webm;codecs=vp9");
  });

  it("still resolves with the chunks so far if the recorder errors while stopping", async () => {
    const fake = new FakeRecorder();
    const handle = startVideoRecorder(stream, options(), factory(fake));
    fake.emitStart();
    fake.emitData("ab");
    const stopped = handle!.stop();
    fake.dispatchEvent(new Event("error"));
    expect(await (await stopped).text()).toBe("ab");
  });

  it("resolves with the chunks so far if the stop event never arrives", async () => {
    const fake = new FakeRecorder();
    const handle = startVideoRecorder(stream, options(), factory(fake));
    fake.emitStart();
    fake.emitData("ab");
    const stopped = handle!.stop();
    vi.advanceTimersByTime(3000);
    expect(await (await stopped).text()).toBe("ab");
  });

  it("reports an error during recording as a failure after start (data kept)", () => {
    const fake = new FakeRecorder();
    const opts = options();
    const handle = startVideoRecorder(stream, opts, factory(fake));
    fake.emitStart();
    fake.emitData("ab");
    fake.dispatchEvent(new Event("error"));
    expect(opts.onFailure).toHaveBeenCalledWith(true);
    expect(handle!.snapshot().size).toBe(2);
  });
});
