import { describe, expect, it } from "vitest";
import { EMPTY_SESSION_FORM, parseSessionForm, sessionToForm, type SessionFormValues } from "./sessionForm";

const filled: SessionFormValues = {
  participantCode: " P07 ",
  ageBand: "18-29",
  clothing: "saree",
  trial: "chair-stand-30s",
  viewAngle: "45-degree",
  cameraHeightCm: "75",
  cameraDistanceCm: "300.5",
  room: "hostel common room",
  notes: "",
  consentConfirmed: true,
};

describe("parseSessionForm", () => {
  it("returns validated metadata for a complete form", () => {
    const result = parseSessionForm(filled);
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.meta.participantCode).toBe("P07");
    expect(result.meta.cameraHeightCm).toBe(75);
    expect(result.meta.cameraDistanceCm).toBe(300.5);
  });

  it("gives every invalid field its own friendly message", () => {
    const result = parseSessionForm(EMPTY_SESSION_FORM);
    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(Object.keys(result.errors).sort()).toEqual(
      [
        "ageBand",
        "cameraDistanceCm",
        "cameraHeightCm",
        "clothing",
        "consentConfirmed",
        "participantCode",
        "room",
        "trial",
        "viewAngle",
      ].sort(),
    );
    expect(result.errors.participantCode).toMatch(/participant code/i);
    expect(result.errors.consentConfirmed).toMatch(/consent/i);
    expect(result.errors.cameraHeightCm).toMatch(/cm/);
    expect(result.errors.trial).toMatch(/choose/i);
    Object.values(result.errors).forEach((message) => expect(message).not.toMatch(/expected|received|invalid input/i));
  });

  it("takes any code of letters, digits, dash and underscore", () => {
    for (const participantCode of ["A11", " w3-B12 ", "7", "Priya"]) {
      const result = parseSessionForm({ ...filled, participantCode });
      expect(result.ok, participantCode).toBe(true);
      if (result.ok) expect(result.meta.participantCode).toBe(participantCode.trim());
    }
  });

  it("explains why a code with a space or slash is refused", () => {
    const result = parseSessionForm({ ...filled, participantCode: "A 11" });
    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(result.errors.participantCode).toMatch(/letters, digits/i);
  });

  it("rejects zero or negative measurements and text in number fields", () => {
    const result = parseSessionForm({ ...filled, cameraHeightCm: "0", cameraDistanceCm: "abc" });
    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(result.errors.cameraHeightCm).toMatch(/greater than 0/);
    expect(result.errors.cameraDistanceCm).toMatch(/cm/);
  });
});

describe("sessionToForm", () => {
  it("round-trips validated metadata back into editable form values", () => {
    const result = parseSessionForm(filled);
    if (!result.ok) throw new Error("expected valid");
    const values = sessionToForm(result.meta);
    expect(values.cameraHeightCm).toBe("75");
    expect(parseSessionForm(values)).toEqual(result);
  });
});
