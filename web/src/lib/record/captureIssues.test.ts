import { describe, expect, it } from "vitest";
import { CAPTURE_ISSUES, CAPTURE_ISSUE_TEXT, isFatalIssue } from "./captureIssues";

describe("capture issues", () => {
  it("has user-facing text for every issue", () => {
    CAPTURE_ISSUES.forEach((issue) => expect(CAPTURE_ISSUE_TEXT[issue]).toMatch(/redo|again|stopped|limit/i));
  });

  it("treats a hidden tab as non-fatal and a lost camera or failed detection as fatal", () => {
    expect(isFatalIssue("tab-hidden")).toBe(false);
    expect(isFatalIssue("frame-limit")).toBe(false);
    expect(isFatalIssue("camera-ended")).toBe(true);
    expect(isFatalIssue("detection-failed")).toBe(true);
  });
});
