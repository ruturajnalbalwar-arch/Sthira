import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { POSE_MODEL_NAME, POSE_MODEL_PATH, TASKS_VISION_VERSION, WASM_BASE_PATH } from "./createPoseLandmarker";

const webRoot = join(__dirname, "..", "..", "..");

describe("pose model constants", () => {
  it("records the installed @mediapipe/tasks-vision version", () => {
    const pkg = JSON.parse(
      readFileSync(join(webRoot, "node_modules", "@mediapipe", "tasks-vision", "package.json"), "utf8"),
    ) as { version: string };
    expect(TASKS_VISION_VERSION).toBe(pkg.version);
  });

  it("points at the assets that scripts/fetch-assets.mjs puts in public/", () => {
    const script = readFileSync(join(webRoot, "scripts", "fetch-assets.mjs"), "utf8");
    expect(script).toContain(`"public", "models", "${POSE_MODEL_NAME}.task"`);
    expect(script).toContain(`"public", "mediapipe", "wasm"`);
    expect(POSE_MODEL_PATH).toBe(`/models/${POSE_MODEL_NAME}.task`);
    expect(WASM_BASE_PATH).toBe("/mediapipe/wasm");
  });
});
