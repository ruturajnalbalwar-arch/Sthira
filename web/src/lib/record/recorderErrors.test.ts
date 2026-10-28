import { describe, expect, it } from "vitest";
import { RECORDER_ERROR_TEXT, cameraErrorKind, recorderError } from "./recorderErrors";

function domError(name: string): Error {
  return Object.assign(new Error(name), { name });
}

describe("cameraErrorKind", () => {
  it("maps getUserMedia failures to user-facing kinds", () => {
    expect(cameraErrorKind(domError("NotAllowedError"))).toBe("permission-denied");
    expect(cameraErrorKind(domError("SecurityError"))).toBe("permission-denied");
    expect(cameraErrorKind(domError("NotFoundError"))).toBe("no-camera");
    expect(cameraErrorKind(domError("OverconstrainedError"))).toBe("no-camera");
    expect(cameraErrorKind(domError("NotReadableError"))).toBe("camera-busy");
    expect(cameraErrorKind(domError("AbortError"))).toBe("camera-busy");
    expect(cameraErrorKind(domError("NotSupportedError"))).toBe("unsupported");
  });

  it("treats anything else as an unknown camera failure", () => {
    expect(cameraErrorKind(new Error("boom"))).toBe("camera-failed");
    expect(cameraErrorKind("not an error")).toBe("camera-failed");
  });
});

describe("recorderError", () => {
  it("builds a typed error with friendly text for every kind", () => {
    for (const kind of Object.keys(RECORDER_ERROR_TEXT) as (keyof typeof RECORDER_ERROR_TEXT)[]) {
      const error = recorderError(kind);
      expect(error.kind).toBe(kind);
      expect(error.message.length).toBeGreaterThan(0);
      expect(error.message).not.toMatch(/Error:|undefined|exception/i);
    }
  });
});
