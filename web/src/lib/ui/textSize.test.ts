import { describe, expect, it } from "vitest";
import { DEFAULT_TEXT_SIZE, TEXT_SIZES, parseTextSize, textSizeBootScript } from "./textSize";

describe("parseTextSize", () => {
  it("accepts every offered size", () => {
    for (const size of TEXT_SIZES) expect(parseTextSize(size.value)).toBe(size.value);
  });

  it("falls back to the default for a missing or tampered stored value", () => {
    expect(parseTextSize(null)).toBe(DEFAULT_TEXT_SIZE);
    expect(parseTextSize("huge")).toBe(DEFAULT_TEXT_SIZE);
    expect(parseTextSize('"><script>')).toBe(DEFAULT_TEXT_SIZE);
  });
});

describe("textSizeBootScript", () => {
  it("only ever writes one of the offered sizes onto the page", () => {
    const script = textSizeBootScript();
    for (const size of TEXT_SIZES) expect(script).toContain(JSON.stringify(size.value));
    expect(script).toContain("try");
  });

  it("relies on the default being the first size, which needs no attribute", () => {
    expect(TEXT_SIZES[0].value).toBe(DEFAULT_TEXT_SIZE);
  });
});
