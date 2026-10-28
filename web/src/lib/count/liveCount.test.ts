import { describe, expect, it } from "vitest";
import { INITIAL_PHASE_STATE, machineConfigFor, phaseReducer, type PhaseEvent, type PhaseState } from "../record/phaseMachine";
import {
  buildSession,
  buildStatic,
  CALIB_FRAMES,
  GEOMETRY,
  paramEntry,
  paramsFile,
  SEATED_Y,
  STANDING_Y,
  TEST_PARAMS,
  type RepSpec,
} from "../sthira/__tests__/fixtures";
import { createCounter } from "../sthira/counter";
import paramsJson from "../sthira/params/params.json";
import type { PoseFrame, VideoGeometry } from "../sthira/types";
import { toCounterStatus, type CounterStatus } from "./counterStatus";
import { describeLiveCount, framesInSpan, INITIAL_LIVE_COUNT, liveCountReducer, type LiveCountState } from "./liveCount";

const CONFIG = machineConfigFor(
  { chairStandDurationS: TEST_PARAMS.chairStandDurationS, calibrationSitS: TEST_PARAMS.calibrationSitS, calibrationStandS: TEST_PARAMS.calibrationStandS },
  "chair-stand-30s",
);
/**
 * Phase spans include both ends, so the frame on the stand/sit boundary belongs to both postures.
 * The fixture switches posture instantly, so the stillness gate is loosened here to tolerate that one frame.
 */
const READY = toCounterStatus(createCounter(paramsFile({ calibStillnessMaxRatio: paramEntry("calibStillnessMaxRatio", 0.2) })));
const UNTUNED = toCounterStatus(createCounter(paramsJson));
const TEST_START = TEST_PARAMS.calibrationSitS + TEST_PARAMS.calibrationStandS;
const FIVE_REPS: RepSpec[] = [2, 6, 10, 14, 18].map((startS) => ({ startS }));

const shift = (frames: readonly PoseFrame[], by: number): PoseFrame[] => frames.map((f) => ({ ...f, t: f.t + by }));

/** Stand 5 s, then sit 5 s (10 fps, both ends included), then the test frames from t = 10 s, starting seated. */
function sessionFrames(standY = STANDING_Y, reps = FIVE_REPS, testS = 32): PoseFrame[] {
  const stand = buildStatic(standY, CALIB_FRAMES);
  const sit = shift(buildStatic(SEATED_Y, CALIB_FRAMES), TEST_PARAMS.calibrationStandS).slice(1);
  const test = shift(buildSession(30, testS, reps), TEST_START).slice(1);
  return [...stand, ...sit, ...test];
}

class Driver {
  phase: PhaseState = INITIAL_PHASE_STATE;
  live: LiveCountState = INITIAL_LIVE_COUNT;
  constructor(
    private readonly counter: CounterStatus,
    private readonly geometry: VideoGeometry = GEOMETRY,
  ) {}

  send(event: PhaseEvent): this {
    const next = phaseReducer(this.phase, event, CONFIG);
    if (next !== this.phase) {
      this.phase = next;
      this.live = liveCountReducer(this.live, { type: "phase", phase: next, geometry: this.geometry }, this.counter);
    }
    return this;
  }

  /** Like the studio: the phase machine sees each frame's t first, then the counter gets the frame. */
  frames(frames: readonly PoseFrame[], until = Number.POSITIVE_INFINITY): this {
    if (this.phase.stage === "idle") this.send({ type: "start", t: 0 });
    for (const frame of frames.filter((f) => f.t <= until)) {
      this.send({ type: "tick", t: frame.t });
      this.live = liveCountReducer(this.live, { type: "frame", frame }, this.counter);
    }
    return this;
  }
}

describe("live count session (ready counter)", () => {
  it("calibrates at Go, counts every stand and finalizes when the 30 s window ends", () => {
    const run = new Driver(READY).frames(sessionFrames());
    expect(run.phase.stage).toBe("done");
    const view = describeLiveCount(run.live, run.phase.endT ?? 0);
    expect(view.kind).toBe("finished");
    if (view.kind === "finished") {
      expect(view.summary.full).toBe(5);
      expect(view.summary.windowComplete).toBe(true);
      expect(view.summary.events.map((e) => e.kind)).toEqual(["full", "full", "full", "full", "full"]);
    }
  });

  it("shows the live count with elapsed and remaining seconds during the test", () => {
    const at = TEST_START + 12;
    const run = new Driver(READY).frames(sessionFrames(), at);
    const view = describeLiveCount(run.live, at);
    expect(view).toMatchObject({ kind: "counting", elapsedS: 12, remainingS: TEST_PARAMS.chairStandDurationS - 12 });
    if (view.kind === "counting") expect(view.summary.full).toBe(3);
  });

  it("clamps elapsed time to the test window", () => {
    const run = new Driver(READY).frames(sessionFrames(), TEST_START + 1);
    expect(describeLiveCount(run.live, TEST_START - 1)).toMatchObject({ elapsedS: 0 });
    expect(describeLiveCount(run.live, TEST_START + 99)).toMatchObject({ remainingS: 0 });
  });

  it("collects calibration frames only before Go and clears them once the test starts", () => {
    const run = new Driver(READY).frames(sessionFrames(), TEST_PARAMS.calibrationStandS + 1);
    expect(run.live.setup.kind).toBe("waiting");
    expect(run.live.calibrationFrames.length).toBeGreaterThan(CALIB_FRAMES);
    run.frames(sessionFrames().filter((f) => f.t > TEST_PARAMS.calibrationStandS + 1), TEST_START + 1);
    expect(run.live.calibrationFrames).toEqual([]);
  });

  it("marks a run stopped before the window ended as not complete", () => {
    const run = new Driver(READY).frames(sessionFrames(), TEST_START + 8);
    run.send({ type: "stop", t: TEST_START + 8 });
    const view = describeLiveCount(run.live, TEST_START + 8);
    expect(view.kind === "finished" && view.summary.windowComplete).toBe(false);
  });

  it("surfaces calibration reasons at Go and starts afresh on redo", () => {
    const run = new Driver(READY).frames(sessionFrames(SEATED_Y), TEST_START + 1);
    const view = describeLiveCount(run.live, TEST_START + 1);
    expect(view.kind).toBe("calibration-failed");
    if (view.kind === "calibration-failed") expect(view.reasons).toContain("Stand fully upright, taller than your seated position");
    run.send({ type: "redo-calibration", t: TEST_START + 2 });
    expect(run.live).toMatchObject({ stage: "calibration-stand", stageStartT: TEST_START + 2, calibrationFrames: [], setup: { kind: "waiting" } });
  });

  it("surfaces context errors when calibration passes but the context is invalid", () => {
    if (READY.status !== "ready") throw new Error("fixture params must be ready");
    const broken: CounterStatus = { status: "ready", params: { ...READY.params, smoothingCutoffHz: Number.NaN } };
    const run = new Driver(broken).frames(sessionFrames(), TEST_START + 1);
    const view = describeLiveCount(run.live, TEST_START + 1);
    expect(view.kind).toBe("context-failed");
    if (view.kind === "context-failed") expect(view.errors.join()).toMatch(/smoothingCutoffHz/);
  });

  it("keeps waiting when the capture ends during calibration", () => {
    const run = new Driver(READY).frames(sessionFrames(), 3);
    run.send({ type: "interrupt", t: 3 });
    expect(run.live).toMatchObject({ stage: "done", setup: { kind: "waiting" } });
  });

  it("calibrates at the end if the capture stops right at Go, before any later frame", () => {
    const frames = sessionFrames().filter((f) => f.t < TEST_START);
    const run = new Driver(READY).frames(frames).send({ type: "tick", t: TEST_START });
    expect(run.live.setup.kind).toBe("starting");
    expect(describeLiveCount(run.live, TEST_START).kind).toBe("waiting");
    run.send({ type: "interrupt", t: TEST_START });
    expect(run.live.setup).toEqual({ kind: "calibration-failed", reasons: ["Please stay seated for the full countdown"] });
  });

  it("still uses a calibration frame that arrives just after the tick that started the test", () => {
    const frames = sessionFrames();
    const boundary = frames.findIndex((f) => f.t === TEST_START);
    const run = new Driver(READY).frames(frames.slice(0, boundary)).send({ type: "tick", t: TEST_START + 0.01 });
    run.live = liveCountReducer(run.live, { type: "frame", frame: frames[boundary] }, READY);
    expect(run.live.setup.kind).toBe("counting");
  });

  it("finishes a counting run that ends exactly at Go", () => {
    const run = new Driver(READY).frames(sessionFrames(), TEST_START);
    run.send({ type: "stop", t: TEST_START });
    expect(run.live.setup.kind).toBe("finished");
  });

  it("resets on cancel", () => {
    const run = new Driver(READY).frames(sessionFrames(), TEST_START + 3);
    run.send({ type: "cancel" });
    expect(run.live).toBe(INITIAL_LIVE_COUNT);
  });

  it("ignores frames while idle and does not mutate the previous state", () => {
    const frame = sessionFrames()[0];
    expect(liveCountReducer(INITIAL_LIVE_COUNT, { type: "frame", frame }, READY)).toBe(INITIAL_LIVE_COUNT);
    const run = new Driver(READY).frames(sessionFrames(), TEST_START + 4);
    const snapshot = JSON.stringify(run.live);
    liveCountReducer(run.live, { type: "frame", frame: sessionFrames().at(-1) as PoseFrame }, READY);
    expect(JSON.stringify(run.live)).toBe(snapshot);
  });
});

describe("live count session (untuned counter)", () => {
  it("runs the guided flow but never counts", () => {
    const run = new Driver(UNTUNED).frames(sessionFrames());
    expect(run.phase.stage).toBe("done");
    expect(run.live.setup).toEqual({ kind: "not-tuned" });
    expect(describeLiveCount(run.live, TEST_START + 5)).toEqual({ kind: "not-tuned" });
  });
});

describe("framesInSpan", () => {
  it("includes both ends and handles a missing span", () => {
    const frames = buildStatic(SEATED_Y, CALIB_FRAMES);
    expect(framesInSpan(frames, { phase: "calibration-sit", startT: 1, endT: 2 }).map((f) => f.t)).toEqual([1, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2]);
    expect(framesInSpan(frames, undefined)).toEqual([]);
  });
});
