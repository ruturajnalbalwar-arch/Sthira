/**
 * Container/codec preference for MediaRecorder: VP9 WebM, then VP8 WebM, then whatever WebM the
 * browser offers, then MP4 (Safari). Order is a preference list, not a tuned threshold.
 */
export const VIDEO_MIME_CANDIDATES: readonly string[] = [
  "video/webm;codecs=vp9",
  "video/webm;codecs=vp8",
  "video/webm",
  "video/mp4",
];

/** First candidate the browser can record, or null to let MediaRecorder use its own default. */
export function pickVideoMime(isTypeSupported: (mime: string) => boolean): string | null {
  for (const mime of VIDEO_MIME_CANDIDATES) {
    try {
      if (isTypeSupported(mime)) return mime;
    } catch {
      // A browser that throws here simply does not support this type; try the next one.
    }
  }
  return null;
}

const EXTENSIONS: Readonly<Record<string, string>> = {
  "video/webm": "webm",
  "video/mp4": "mp4",
  "video/x-matroska": "mkv",
};

/** File extension for a MediaRecorder mime type such as "video/webm;codecs=vp9". */
export function extensionForMime(mime: string): string {
  const base = mime.split(";")[0].trim().toLowerCase();
  return EXTENSIONS[base] ?? "webm";
}
