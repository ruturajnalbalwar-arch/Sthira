// Copies the MediaPipe WASM runtime into public/ and downloads the pose model if it is missing.
// Run with `npm run assets` (also runs automatically before `npm run build`).
import { copyFile, mkdir, readdir, stat, writeFile } from "node:fs/promises";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const wasmSource = join(root, "node_modules", "@mediapipe", "tasks-vision", "wasm");
const wasmTarget = join(root, "public", "mediapipe", "wasm");
const modelTarget = join(root, "public", "models", "pose_landmarker_full.task");
const modelUrl =
  "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task";

async function exists(path) {
  try {
    return (await stat(path)).size > 0;
  } catch {
    return false;
  }
}

async function copyWasm() {
  await mkdir(wasmTarget, { recursive: true });
  const files = await readdir(wasmSource);
  await Promise.all(files.map((file) => copyFile(join(wasmSource, file), join(wasmTarget, file))));
  console.log(`Copied ${files.length} MediaPipe WASM files to public/mediapipe/wasm`);
}

async function downloadModel() {
  if (await exists(modelTarget)) {
    console.log("Pose model already present");
    return;
  }
  await mkdir(dirname(modelTarget), { recursive: true });
  const response = await fetch(modelUrl);
  if (!response.ok) {
    throw new Error(`Model download failed: HTTP ${response.status}`);
  }
  await writeFile(modelTarget, Buffer.from(await response.arrayBuffer()));
  console.log("Downloaded pose_landmarker_full.task");
}

await copyWasm();
await downloadModel();
