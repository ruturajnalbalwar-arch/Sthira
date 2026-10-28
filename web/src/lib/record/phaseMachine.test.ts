import { describe, expect, it } from "vitest";
import paramsJson from "../sthira/params/params.json";
import { readProtocolConstants, type ProtocolConstants } from "../sthira/params/protocol";
import {
  INITIAL_PHASE_STATE,
  describePhase,
  machineConfigFor,
  phaseReducer,
  type MachineConfig,
  type PhaseEvent,
  type PhaseState,
} from "./phaseMachine";

function loadConstants(): ProtocolConstants {
  const result = readProtocolConstants(paramsJson);
  if (!result.ok) throw new Error(result.errors.join("; "));
  return result.constants;
}

const constants = loadConstants();
const chairStand = machineConfigFor(constants, "chair-stand-30s");
const balance = machineConfigFor(constants, "balance-tandem");

function run(config: MachineConfig, events: readonly PhaseEvent[]): PhaseState {
  return events.reduce((state, event) => phaseReducer(state, event, config), INITIAL_PHASE_STATE);
}

/** Protocol order: stand calibration first, then sit, so the person is seated at Go. */
const standEnd = constants.calibrationStandS;
const goT = standEnd + constants.calibrationSitS;

describe("machineConfigFor", () => {
  it("takes the countdowns from params.json and limits only the 30 s chair stand", () => {
    expect(chairStand).toEqual({
      calibrationSitS: constants.calibrationSitS,
      calibrationStandS: constants.calibrationStandS,
      testDurationS: constants.chairStandDurationS,
      isChairStand: true,
    });
    expect(balance.testDurationS).toBeNull();
    expect(balance.isChairStand).toBe(false);
  });
});

describe("phaseReducer", () => {
  it("starts in idle with no phases", () => {
    expect(INITIAL_PHASE_STATE).toEqual({ stage: "idle", stageStartT: 0, phases: [], endT: null });
  });

  it("opens calibration-stand on start", () => {
    const state = run(chairStand, [{ type: "start", t: 0 }]);
    expect(state.stage).toBe("calibration-stand");
    expect(state.phases).toEqual([{ phase: "calibration-stand", startT: 0, endT: 0 }]);
  });

  it("stays in calibration-stand until the stand countdown has elapsed", () => {
    const state = run(chairStand, [{ type: "start", t: 0 }, { type: "tick", t: standEnd - 0.01 }]);
    expect(state.stage).toBe("calibration-stand");
  });

  it("moves stand -> sit -> test at the exact countdown boundaries", () => {
    const state = run(chairStand, [
      { type: "start", t: 0 },
      { type: "tick", t: standEnd + 0.04 },
      { type: "tick", t: goT + 0.02 },
    ]);
    expect(state.stage).toBe("test");
    expect(state.stageStartT).toBe(goT);
    expect(state.phases).toEqual([
      { phase: "calibration-stand", startT: 0, endT: standEnd },
      { phase: "calibration-sit", startT: standEnd, endT: goT },
      { phase: "test", startT: goT, endT: goT },
    ]);
  });

  it("skips straight through both countdowns if a single late tick covers them", () => {
    const state = run(chairStand, [{ type: "start", t: 0 }, { type: "tick", t: goT + 1 }]);
    expect(state.stage).toBe("test");
    expect(state.phases.map((span) => span.phase)).toEqual(["calibration-stand", "calibration-sit", "test"]);
  });

  it("auto-stops the chair stand at exactly chairStandDurationS after Go", () => {
    const state = run(chairStand, [
      { type: "start", t: 0 },
      { type: "tick", t: goT },
      { type: "tick", t: goT + constants.chairStandDurationS + 0.03 },
    ]);
    expect(state.stage).toBe("done");
    expect(state.endT).toBe(goT + constants.chairStandDurationS);
    expect(state.phases.at(-1)).toEqual({ phase: "test", startT: goT, endT: goT + constants.chairStandDurationS });
  });

  it("does not auto-stop trials without a fixed duration", () => {
    const state = run(balance, [
      { type: "start", t: 0 },
      { type: "tick", t: goT },
      { type: "tick", t: goT + constants.chairStandDurationS * 10 },
    ]);
    expect(state.stage).toBe("test");
  });

  it("stops the test on Stop and closes the test phase at that time", () => {
    const state = run(balance, [
      { type: "start", t: 0 },
      { type: "tick", t: goT },
      { type: "stop", t: goT + 7.5 },
    ]);
    expect(state.stage).toBe("done");
    expect(state.endT).toBe(goT + 7.5);
    expect(state.phases.at(-1)).toEqual({ phase: "test", startT: goT, endT: goT + 7.5 });
  });

  it("ignores Stop during calibration (use cancel or redo instead)", () => {
    const before = run(chairStand, [{ type: "start", t: 0 }]);
    expect(phaseReducer(before, { type: "stop", t: 1 }, chairStand)).toBe(before);
  });

  it("cancels back to idle from any active stage and drops the phases", () => {
    const state = run(chairStand, [{ type: "start", t: 0 }, { type: "tick", t: standEnd }, { type: "cancel" }]);
    expect(state).toEqual(INITIAL_PHASE_STATE);
  });

  it("redoes calibration from the sit countdown or the test, restarting the phase list", () => {
    const fromStand = run(chairStand, [
      { type: "start", t: 0 },
      { type: "tick", t: standEnd },
      { type: "redo-calibration", t: standEnd + 2 },
    ]);
    expect(fromStand.stage).toBe("calibration-stand");
    expect(fromStand.stageStartT).toBe(standEnd + 2);
    expect(fromStand.phases).toEqual([{ phase: "calibration-stand", startT: standEnd + 2, endT: standEnd + 2 }]);

    const fromTest = run(balance, [{ type: "start", t: 0 }, { type: "tick", t: goT }, { type: "redo-calibration", t: 20 }]);
    expect(fromTest.stage).toBe("calibration-stand");
    expect(fromTest.phases).toHaveLength(1);
  });

  it("ignores events that do not apply to the current stage", () => {
    expect(phaseReducer(INITIAL_PHASE_STATE, { type: "tick", t: 3 }, chairStand)).toBe(INITIAL_PHASE_STATE);
    expect(phaseReducer(INITIAL_PHASE_STATE, { type: "redo-calibration", t: 3 }, chairStand)).toBe(INITIAL_PHASE_STATE);
    const done = run(balance, [{ type: "start", t: 0 }, { type: "tick", t: goT }, { type: "stop", t: 20 }]);
    expect(phaseReducer(done, { type: "start", t: 30 }, balance)).toBe(done);
    expect(phaseReducer(done, { type: "tick", t: 30 }, balance)).toBe(done);
  });

  it("interrupts any active stage into done, closing the open phase at that time", () => {
    const sitting = run(chairStand, [{ type: "start", t: 0 }, { type: "interrupt", t: 2.5 }]);
    expect(sitting.stage).toBe("done");
    expect(sitting.endT).toBe(2.5);
    expect(sitting.phases).toEqual([{ phase: "calibration-stand", startT: 0, endT: 2.5 }]);
    const testing = run(balance, [{ type: "start", t: 0 }, { type: "tick", t: goT }, { type: "interrupt", t: goT + 4 }]);
    expect(testing.phases.at(-1)).toEqual({ phase: "test", startT: goT, endT: goT + 4 });
  });

  it("ignores interrupt outside an active recording and never ends a phase before it began", () => {
    expect(phaseReducer(INITIAL_PHASE_STATE, { type: "interrupt", t: 1 }, chairStand)).toBe(INITIAL_PHASE_STATE);
    const late = run(chairStand, [{ type: "start", t: 0 }, { type: "tick", t: standEnd }, { type: "interrupt", t: standEnd - 1 }]);
    expect(late.phases.at(-1)).toEqual({ phase: "calibration-sit", startT: standEnd, endT: standEnd });
  });

  it("resets from done to idle for the next recording", () => {
    const done = run(balance, [{ type: "start", t: 0 }, { type: "tick", t: goT }, { type: "stop", t: 20 }]);
    expect(phaseReducer(done, { type: "reset" }, balance)).toEqual(INITIAL_PHASE_STATE);
  });

  it("never mutates the previous state", () => {
    const started = run(chairStand, [{ type: "start", t: 0 }]);
    const snapshot = JSON.stringify(started);
    phaseReducer(started, { type: "tick", t: goT }, chairStand);
    expect(JSON.stringify(started)).toBe(snapshot);
  });
});

describe("describePhase", () => {
  it("shows a whole-second countdown while standing and sitting", () => {
    const standing = run(chairStand, [{ type: "start", t: 0 }]);
    expect(describePhase(standing, 0, chairStand)).toMatchObject({ title: "Stand up tall and keep still", count: constants.calibrationStandS });
    expect(describePhase(standing, 0.2, chairStand).count).toBe(constants.calibrationStandS);
    expect(describePhase(standing, standEnd - 0.5, chairStand).count).toBe(1);

    const sitting = run(chairStand, [{ type: "start", t: 0 }, { type: "tick", t: standEnd }]);
    expect(describePhase(sitting, standEnd, chairStand)).toMatchObject({
      title: "Sit down and keep still",
      count: constants.calibrationSitS,
    });
  });

  it("uses the full CDC instruction for the chair stand with elapsed vs duration", () => {
    const testing = run(chairStand, [{ type: "start", t: 0 }, { type: "tick", t: goT }]);
    const view = describePhase(testing, goT + 12.4, chairStand);
    expect(view.title).toBe("Go! Stand up and sit down as many times as you can");
    expect(view.elapsedS).toBe(12);
    expect(view.limitS).toBe(constants.chairStandDurationS);
    expect(view.count).toBeNull();
  });

  it("says just Go for other trials and has no limit", () => {
    const testing = run(balance, [{ type: "start", t: 0 }, { type: "tick", t: goT }]);
    expect(describePhase(testing, goT + 3, balance)).toMatchObject({ title: "Go", elapsedS: 3, limitS: null });
  });

  it("describes idle and done", () => {
    expect(describePhase(INITIAL_PHASE_STATE, 0, chairStand).title).toBe("Ready");
    const done = run(balance, [{ type: "start", t: 0 }, { type: "tick", t: goT }, { type: "stop", t: 20 }]);
    expect(describePhase(done, 25, balance).title).toBe("Stop. Recording finished");
  });

  it("never shows a negative elapsed time or a countdown below zero", () => {
    const standing = run(chairStand, [{ type: "start", t: 4 }]);
    expect(describePhase(standing, 0, chairStand).count).toBe(constants.calibrationStandS);
    expect(describePhase(standing, 100, chairStand).count).toBe(0);
  });
});
