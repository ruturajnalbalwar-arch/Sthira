import type { SessionMeta } from "./schema";
import { recordingFileBase } from "./session";
import { extensionForMime } from "./videoMime";

export interface RecordingFileNames {
  readonly landmarks: string;
  readonly video: string;
}

/** `<base>.json` for landmarks and `<base>.<ext>` for the video, ext from the recorder's real mime type. */
export function recordingFileNames(meta: SessionMeta, startedAtIso: string, videoMime: string): RecordingFileNames {
  const base = recordingFileBase(meta, startedAtIso);
  return { landmarks: `${base}.json`, video: `${base}.${extensionForMime(videoMime)}` };
}

/** Minimal File System Access API surface (not in TypeScript's DOM lib). */
interface WritableLike {
  write(data: Blob): Promise<void>;
  close(): Promise<void>;
}
interface FileHandleLike {
  createWritable(): Promise<WritableLike>;
}
interface SavePickerOptions {
  suggestedName: string;
  types: { description: string; accept: Record<string, string[]> }[];
}
type SaveFilePicker = (options: SavePickerOptions) => Promise<FileHandleLike>;

export interface SaveEnvironment {
  readonly showSaveFilePicker: SaveFilePicker | undefined;
  readonly downloadWithAnchor: (blob: Blob, fileName: string) => void;
}

export interface SaveOptions {
  readonly suggestedName: string;
  readonly description: string;
  readonly mimeType: string;
  /** Including the dot, e.g. ".json". */
  readonly extension: string;
}

/** "downloaded" means a browser download was started; whether it finished cannot be checked. */
export type SaveOutcome = "saved" | "downloaded" | "cancelled";

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === "AbortError";
}

/**
 * How long a fallback download's object URL is kept alive. Revoking it immediately (setTimeout 0)
 * can cut off a large video download; it is revoked on the next save, on unmount, or after this.
 */
export const DOWNLOAD_URL_LIFETIME_MS = 5 * 60 * 1000;

export interface UrlRegistry {
  /** Keeps `url` alive; revokes any earlier ones and schedules this one for later revocation. */
  track(url: string): void;
  revokeAll(): void;
}

export function createUrlRegistry(revoke: (url: string) => void, schedule: (fn: () => void, ms: number) => unknown): UrlRegistry {
  let live: readonly string[] = [];
  const revokeOne = (url: string) => {
    if (!live.includes(url)) return;
    live = live.filter((other) => other !== url);
    revoke(url);
  };
  const revokeAll = () => live.forEach(revokeOne);
  return {
    track(url) {
      revokeAll();
      live = [url];
      schedule(() => revokeOne(url), DOWNLOAD_URL_LIFETIME_MS);
    },
    revokeAll,
  };
}

let downloadUrls: UrlRegistry | null = null;

function downloadRegistry(): UrlRegistry {
  downloadUrls ??= createUrlRegistry((url) => URL.revokeObjectURL(url), (fn, ms) => setTimeout(fn, ms));
  return downloadUrls;
}

/** Call when the save screen goes away: frees the memory held by fallback download URLs. */
export function releaseDownloadUrls(): void {
  downloadUrls?.revokeAll();
}

function anchorDownload(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  downloadRegistry().track(url);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  anchor.rel = "noopener";
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
}

/** The real browser environment. Call only in the browser (e.g. from a click handler). */
export function browserSaveEnvironment(): SaveEnvironment {
  const picker = (window as Window & { showSaveFilePicker?: SaveFilePicker }).showSaveFilePicker;
  return {
    showSaveFilePicker: typeof picker === "function" ? picker.bind(window) : undefined,
    downloadWithAnchor: anchorDownload,
  };
}

/**
 * Saves a blob where the user chooses (File System Access API) or, where that API is missing,
 * through a normal download. A cancelled picker resolves to "cancelled"; real failures reject.
 */
export async function saveBlob(blob: Blob, options: SaveOptions, env: SaveEnvironment = browserSaveEnvironment()): Promise<SaveOutcome> {
  if (env.showSaveFilePicker === undefined) {
    env.downloadWithAnchor(blob, options.suggestedName);
    return "downloaded";
  }
  try {
    const handle = await env.showSaveFilePicker({
      suggestedName: options.suggestedName,
      types: [{ description: options.description, accept: { [options.mimeType]: [options.extension] } }],
    });
    const writable = await handle.createWritable();
    await writable.write(blob);
    await writable.close();
    return "saved";
  } catch (error: unknown) {
    if (isAbortError(error)) return "cancelled";
    throw error;
  }
}
