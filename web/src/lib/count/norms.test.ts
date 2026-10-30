import { describe, expect, it } from "vitest";
import normsJson from "../sthira/norms/norms.json";
import { lookupChairStandNorm, parseAgeBand, parseChairStandNorms, type ChairStandNorms } from "./norms";

function realNorms(): ChairStandNorms {
  const parsed = parseChairStandNorms(normsJson);
  if (!parsed.ok) throw new Error(parsed.errors.join("; "));
  return parsed.norms;
}

describe("parseChairStandNorms", () => {
  it("keeps the CDC chair-stand table and the US-reference note from norms.json", () => {
    const norms = realNorms();
    expect(norms.note).toMatch(/Not validated for Indian older adults/);
    expect(norms.entries.length).toBeGreaterThan(0);
    expect(norms.entries.every((entry) => entry.source.url.startsWith("https://www.cdc.gov/"))).toBe(true);
  });

  it("reads the citation shown under a result from the protocol source in norms.json", () => {
    const norms = realNorms();
    const { source } = normsJson.chairStand30s.protocol;
    expect(norms.source).toEqual({ url: source.url, title: source.title, year: source.year });
  });

  it("rejects a malformed file instead of throwing", () => {
    const result = parseChairStandNorms({ note: "x", chairStand30s: { entries: [{ sex: "any" }] } });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.errors.length).toBeGreaterThan(0);
  });
});

describe("parseAgeBand", () => {
  it("reads closed and open-ended bands", () => {
    expect(parseAgeBand("70-74")).toEqual({ min: 70, max: 74 });
    expect(parseAgeBand("80+")).toEqual({ min: 80, max: null });
    expect(parseAgeBand("seventy")).toBeNull();
  });
});

describe("lookupChairStandNorm", () => {
  const norms = realNorms();

  it("returns the CDC cut-off for an exact 5-year band and sex, read from norms.json", () => {
    const female = lookupChairStandNorm(norms, "70-74", "female");
    const male = lookupChairStandNorm(norms, "60-64", "male");
    const expectedFemale = normsJson.chairStand30s.entries.find((e) => e.sex === "female" && e.ageMin === 70);
    const expectedMale = normsJson.chairStand30s.entries.find((e) => e.sex === "male" && e.ageMin === 60);
    expect(female.status === "available" && female.entry.belowAverageIfLessThan).toBe(expectedFemale?.belowAverageIfLessThan);
    expect(male.status === "available" && male.entry.belowAverageIfLessThan).toBe(expectedMale?.belowAverageIfLessThan);
  });

  it("is not available when sex was not recorded", () => {
    const result = lookupChairStandNorm(norms, "70-74", "not-recorded");
    expect(result).toEqual({ status: "not-available", reason: expect.stringMatching(/sex was not recorded/) });
  });

  it("is not available below 60, where the CDC table has no rows", () => {
    const result = lookupChairStandNorm(norms, "45-59", "female");
    expect(result).toEqual({ status: "not-available", reason: expect.stringMatching(/no value for age 45-59/) });
  });

  it("does not pick one value for 80+, which spans several CDC bands", () => {
    const result = lookupChairStandNorm(norms, "80+", "male");
    expect(result.status).toBe("not-available");
    if (result.status === "not-available") expect(result.reason).toMatch(/80-84, 85-89, 90-94/);
  });

  it("is not available for an unknown band", () => {
    expect(lookupChairStandNorm(norms, "old", "male").status).toBe("not-available");
  });
});
