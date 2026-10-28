import type { NormalizedLandmark, PoseLandmarker } from "@mediapipe/tasks-vision";

/** Must equal the installed package version (checked by createPoseLandmarker.test.ts). */
export const TASKS_VISION_VERSION = "1.1.1";
export const POSE_MODEL_NAME = "pose_landmarker_full";
/** Served from public/ by scripts/fetch-assets.mjs, so nothing is fetched from a CDN at runtime. */
export const POSE_MODEL_PATH = `/models/${POSE_MODEL_NAME}.task`;
export const WASM_BASE_PATH = "/mediapipe/wasm";

export type PoseDelegate = "GPU" | "CPU";

export interface OverlayStyle {
  readonly lineColor: string;
  readonly pointColor: string;
}

/** Draws one frame's poses onto a 2D canvas (clears it first). */
export type PoseDrawer = (poses: readonly NormalizedLandmark[][], style: OverlayStyle) => void;

export interface PoseEngine {
  readonly landmarker: PoseLandmarker;
  readonly delegate: PoseDelegate;
  readonly createDrawer: (context: CanvasRenderingContext2D) => PoseDrawer;
}

type VisionModule = typeof import("@mediapipe/tasks-vision");

async function createWith(vision: VisionModule, delegate: PoseDelegate): Promise<PoseLandmarker> {
  const fileset = await vision.FilesetResolver.forVisionTasks(WASM_BASE_PATH);
  // Detection/tracking confidences are left at the library defaults on purpose: we do not invent thresholds.
  return vision.PoseLandmarker.createFromOptions(fileset, {
    baseOptions: { modelAssetPath: POSE_MODEL_PATH, delegate },
    runningMode: "VIDEO",
    numPoses: 1,
  });
}

function drawerFactory(vision: VisionModule): PoseEngine["createDrawer"] {
  return (context) => {
    const drawing = new vision.DrawingUtils(context);
    return (poses, style) => {
      context.clearRect(0, 0, context.canvas.width, context.canvas.height);
      for (const landmarks of poses) {
        drawing.drawConnectors(landmarks, vision.PoseLandmarker.POSE_CONNECTIONS, { color: style.lineColor, lineWidth: 4 });
        drawing.drawLandmarks(landmarks, { color: style.pointColor, fillColor: style.pointColor, lineWidth: 1, radius: 3 });
      }
    };
  };
}

/**
 * Loads the MediaPipe runtime lazily (browser only) and creates a VIDEO-mode pose landmarker.
 * Tries the GPU delegate first and falls back to CPU if GPU setup fails. Rejects if both fail.
 */
export async function createPoseLandmarker(): Promise<PoseEngine> {
  const vision = await import("@mediapipe/tasks-vision");
  const createDrawer = drawerFactory(vision);
  try {
    return { landmarker: await createWith(vision, "GPU"), delegate: "GPU", createDrawer };
  } catch {
    // GPU (WebGL2) is unavailable or failed to initialise on this device: CPU is slower but works.
    return { landmarker: await createWith(vision, "CPU"), delegate: "CPU", createDrawer };
  }
}
