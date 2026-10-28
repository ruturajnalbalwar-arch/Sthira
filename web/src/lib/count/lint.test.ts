import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { findDisallowedNumbers } from "../sthira/__tests__/numericLint";

const SOURCES = readdirSync(__dirname).filter((name) => name.endsWith(".ts") && !name.endsWith(".test.ts"));

describe("count-screen numeric-literal lint", () => {
  it("scans the count logic", () => {
    expect(SOURCES).toEqual(expect.arrayContaining(["liveCount.ts", "norms.ts", "verdict.ts"]));
  });

  it.each(SOURCES)("%s holds no threshold-like number (only 0 and 1)", (file) => {
    expect(findDisallowedNumbers(readFileSync(join(__dirname, file), "utf8"))).toEqual([]);
  });
});
