import { describe, expect, it } from "vitest";
import { VIDEO_MIME_CANDIDATES, extensionForMime, pickVideoMime } from "./videoMime";

describe("pickVideoMime", () => {
  it("prefers VP9 WebM when the browser supports it", () => {
    expect(pickVideoMime(() => true)).toBe(VIDEO_MIME_CANDIDATES[0]);
    expect(VIDEO_MIME_CANDIDATES[0]).toContain("vp9");
  });

  it("falls back to VP8 WebM, then later candidates", () => {
    const supported = (mime: string) => mime.includes("vp8");
    expect(pickVideoMime(supported)).toContain("vp8");
    expect(pickVideoMime((mime) => mime === "video/mp4")).toBe("video/mp4");
  });

  it("returns null when nothing is supported so the recorder can use its own default", () => {
    expect(pickVideoMime(() => false)).toBeNull();
  });

  it("treats a throwing support check as unsupported", () => {
    expect(
      pickVideoMime(() => {
        throw new Error("not available");
      }),
    ).toBeNull();
  });
});

describe("extensionForMime", () => {
  it("maps WebM and MP4 (with codec parameters) to their extensions", () => {
    expect(extensionForMime("video/webm;codecs=vp9")).toBe("webm");
    expect(extensionForMime("video/webm")).toBe("webm");
    expect(extensionForMime("video/mp4;codecs=avc1.42E01E")).toBe("mp4");
    expect(extensionForMime("VIDEO/MP4")).toBe("mp4");
    expect(extensionForMime("video/x-matroska;codecs=avc1")).toBe("mkv");
  });

  it("defaults to webm for an empty or unknown type (MediaRecorder's usual container)", () => {
    expect(extensionForMime("")).toBe("webm");
    expect(extensionForMime("video/unknown")).toBe("webm");
  });
});
