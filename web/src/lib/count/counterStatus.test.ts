import { describe, expect, it } from "vitest";
import { paramsFile, TEST_PARAMS } from "../sthira/__tests__/fixtures";
import { createCounter } from "../sthira/counter";
import paramsJson from "../sthira/params/params.json";
import { toCounterStatus } from "./counterStatus";

describe("toCounterStatus", () => {
  it("lists what is missing for today's params.json (protocol constants only)", () => {
    const status = toCounterStatus(createCounter(paramsJson));
    expect(status.status).toBe("untuned");
    if (status.status === "untuned") expect(status.missing).toEqual(expect.arrayContaining(["reachFraction", "minFps"]));
  });

  it("keeps only serialisable data for a tuned file", () => {
    const status = toCounterStatus(createCounter(paramsFile()));
    expect(status).toEqual({ status: "ready", params: TEST_PARAMS });
    expect(JSON.parse(JSON.stringify(status))).toEqual(status);
  });

  it("passes validation errors through", () => {
    const status = toCounterStatus(createCounter({ version: "1" }));
    expect(status.status).toBe("invalid");
    if (status.status === "invalid") expect(status.errors.length).toBeGreaterThan(0);
  });
});
