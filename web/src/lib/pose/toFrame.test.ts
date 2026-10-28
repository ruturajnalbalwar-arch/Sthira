import { describe, expect, it } from "vitest";
import { POSE_LANDMARK_COUNT, recordedFrameSchema } from "../record/schema";
import { toFrame, type PoseResultLike } from "./toFrame";

function person(overrides: Partial<{ x: number; y: number; z: number; visibility: number }> = {}) {
  return Array.from({ length: POSE_LANDMARK_COUNT }, (_, index) => ({
    x: index / POSE_LANDMARK_COUNT,
    y: 0.5,
    z: -0.1,
    visibility: 0.9,
    presence: 0.95,
    ...overrides,
  }));
}

describe("toFrame", () => {
  it("returns an empty landmark list when no person was detected", () => {
    const frame = toFrame({ landmarks: [] }, 1.25);
    expect(frame).toEqual({ t: 1.25, landmarks: [] });
    expect(recordedFrameSchema.safeParse(frame).success).toBe(true);
  });

  it("keeps the first person's 33 landmarks with x, y, z and visibility only", () => {
    const frame = toFrame({ landmarks: [person()] }, 0);
    expect(frame.landmarks).toHaveLength(POSE_LANDMARK_COUNT);
    expect(Object.keys(frame.landmarks[0]).sort()).toEqual(["visibility", "x", "y", "z"]);
    expect(frame.landmarks[3]).toEqual({ x: 3 / POSE_LANDMARK_COUNT, y: 0.5, z: -0.1, visibility: 0.9 });
    expect(recordedFrameSchema.safeParse(frame).success).toBe(true);
  });

  it("keeps a visibility of 0 exactly as the library reports it", () => {
    const frame = toFrame({ landmarks: [person({ visibility: 0 })] }, 2);
    expect(frame.landmarks.every((landmark) => landmark.visibility === 0)).toBe(true);
  });

  it("uses the library's own default (0) when visibility is missing at runtime", () => {
    const withoutVisibility = person().map(({ x, y, z }) => ({ x, y, z }));
    const result = { landmarks: [withoutVisibility] } as unknown as PoseResultLike;
    const frame = toFrame(result, 2);
    expect(frame.landmarks[0].visibility).toBe(0);
    expect(recordedFrameSchema.safeParse(frame).success).toBe(true);
  });

  it("records no person rather than an invalid frame when a value is not finite", () => {
    const frame = toFrame({ landmarks: [person({ x: Number.NaN })] }, 3);
    expect(frame.landmarks).toEqual([]);
  });

  it("records no person when the landmark count is not the model's 33", () => {
    const frame = toFrame({ landmarks: [person().slice(0, 10)] }, 3);
    expect(frame.landmarks).toEqual([]);
  });

  it("does not mutate the library result", () => {
    const input = { landmarks: [person()] };
    const snapshot = JSON.stringify(input);
    toFrame(input, 1);
    expect(JSON.stringify(input)).toBe(snapshot);
  });

  it("rejects a negative or non-finite time", () => {
    expect(() => toFrame({ landmarks: [] }, -0.5)).toThrow();
    expect(() => toFrame({ landmarks: [] }, Number.POSITIVE_INFINITY)).toThrow();
  });
});
