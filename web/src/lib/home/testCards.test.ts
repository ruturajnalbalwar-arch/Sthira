import { describe, expect, it } from "vitest";
import normsJson from "../sthira/norms/norms.json";
import paramsJson from "../sthira/params/params.json";
import { readProtocolConstants } from "../sthira/params/protocol";
import { readTimerNorms } from "../timers/norms";
import { testCards } from "./testCards";

const FAILED = { ok: false, errors: ["unreadable"] } as const;

describe("testCards", () => {
  it("reads every shown time and distance from params.json and norms.json", () => {
    const protocol = readProtocolConstants(paramsJson);
    const timers = readTimerNorms(normsJson);
    if (!protocol.ok || !timers.ok) throw new Error("data files did not load");
    const [chair, balance, tug] = testCards(protocol, timers);

    expect(chair.time).toBe(`${protocol.constants.chairStandDurationS} seconds`);
    expect(balance.time).toBe(`${timers.norms.balance.stages.length} positions, ${timers.norms.balance.stageDurationS} seconds each`);
    expect(tug.task).toContain(`walk ${timers.norms.tug.walkDistanceMeters} metres`);
    expect(tug.time).toBeNull();
  });

  it("shows no numbers rather than invented ones when the data files fail to load", () => {
    const cards = testCards(FAILED, FAILED);

    expect(cards.map((card) => card.time)).toEqual([null, null, null]);
    expect(cards.map((card) => card.href)).toEqual(["/count", "/balance", "/tug"]);
    expect(cards[2].task).not.toMatch(/\d/);
  });
});
