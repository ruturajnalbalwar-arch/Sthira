import { describe, expect, it, vi } from "vitest";
import type { SessionMeta } from "./schema";
import { DOWNLOAD_URL_LIFETIME_MS, createUrlRegistry, recordingFileNames, saveBlob, type SaveEnvironment } from "./saveFile";

const meta: SessionMeta = {
  participantCode: "P07",
  ageBand: "18-29",
  clothing: "saree",
  trial: "chair-stand-30s",
  viewAngle: "45-degree",
  cameraHeightCm: 75,
  cameraDistanceCm: 300,
  room: "hostel common room",
  notes: "",
  consentConfirmed: true,
};

describe("recordingFileNames", () => {
  it("names both files from the shared base and the recorder's real container", () => {
    const names = recordingFileNames(meta, "2026-10-23T10:00:00.000Z", "video/webm;codecs=vp9");
    expect(names).toEqual({
      landmarks: "P07_chair-stand-30s_saree_20261023T100000Z.json",
      video: "P07_chair-stand-30s_saree_20261023T100000Z.webm",
    });
  });

  it("uses .mp4 when the recorder produced MP4", () => {
    expect(recordingFileNames(meta, "2026-10-23T10:00:00.000Z", "video/mp4").video).toMatch(/\.mp4$/);
  });
});

function fakeEnvironment(overrides: Partial<SaveEnvironment> = {}): SaveEnvironment {
  return { showSaveFilePicker: undefined, downloadWithAnchor: vi.fn(), ...overrides };
}

const blob = new Blob(["{}"], { type: "application/json" });
const options = { suggestedName: "P07_x.json", description: "Landmarks", mimeType: "application/json", extension: ".json" };

describe("saveBlob", () => {
  it("writes through the File System Access picker when available", async () => {
    const write = vi.fn(async () => undefined);
    const close = vi.fn(async () => undefined);
    const picker = vi.fn(async () => ({ createWritable: async () => ({ write, close }) }));
    const env = fakeEnvironment({ showSaveFilePicker: picker });

    await expect(saveBlob(blob, options, env)).resolves.toBe("saved");
    expect(picker).toHaveBeenCalledWith({
      suggestedName: "P07_x.json",
      types: [{ description: "Landmarks", accept: { "application/json": [".json"] } }],
    });
    expect(write).toHaveBeenCalledWith(blob);
    expect(close).toHaveBeenCalled();
    expect(env.downloadWithAnchor).not.toHaveBeenCalled();
  });

  it("reports a cancelled picker as cancelled, not as an error", async () => {
    const abort = Object.assign(new Error("The user aborted a request."), { name: "AbortError" });
    const env = fakeEnvironment({ showSaveFilePicker: vi.fn(async () => Promise.reject(abort)) });
    await expect(saveBlob(blob, options, env)).resolves.toBe("cancelled");
    expect(env.downloadWithAnchor).not.toHaveBeenCalled();
  });

  it("rethrows real write failures", async () => {
    const env = fakeEnvironment({ showSaveFilePicker: vi.fn(async () => Promise.reject(new Error("disk full"))) });
    await expect(saveBlob(blob, options, env)).rejects.toThrow("disk full");
  });

  it("falls back to a download link when the picker is not available", async () => {
    const env = fakeEnvironment();
    await expect(saveBlob(blob, options, env)).resolves.toBe("downloaded");
    expect(env.downloadWithAnchor).toHaveBeenCalledWith(blob, "P07_x.json");
  });
});

describe("createUrlRegistry (download fallback)", () => {
  it("does not revoke a download URL straight away", () => {
    const revoke = vi.fn();
    const schedule = vi.fn();
    createUrlRegistry(revoke, schedule).track("blob:a");
    expect(revoke).not.toHaveBeenCalled();
    expect(schedule).toHaveBeenCalledWith(expect.any(Function), DOWNLOAD_URL_LIFETIME_MS);
  });

  it("revokes after the long delay, on the next save, and on revokeAll", () => {
    const revoke = vi.fn();
    const timers: (() => void)[] = [];
    const registry = createUrlRegistry(revoke, (fn) => void timers.push(fn));
    registry.track("blob:a");
    timers[0]();
    expect(revoke).toHaveBeenCalledWith("blob:a");

    registry.track("blob:b");
    registry.track("blob:c");
    expect(revoke).toHaveBeenCalledWith("blob:b");
    registry.revokeAll();
    expect(revoke).toHaveBeenCalledWith("blob:c");
    expect(revoke).toHaveBeenCalledTimes(3);
    timers.forEach((fn) => fn());
    expect(revoke).toHaveBeenCalledTimes(3);
  });
});
