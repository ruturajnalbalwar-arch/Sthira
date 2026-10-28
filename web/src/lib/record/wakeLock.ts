interface WakeLockSentinelLike {
  release(): Promise<void>;
}

interface NavigatorWithWakeLock {
  readonly wakeLock?: { request(type: "screen"): Promise<WakeLockSentinelLike> };
}

export interface WakeLockHandle {
  release(): void;
}

/**
 * Keeps the screen on during a capture (a sleeping screen would pause the camera frames).
 * Unsupported browsers and refusals are ignored: the capture still works, it is just not protected.
 */
export function holdScreenWakeLock(): WakeLockHandle {
  const api = (navigator as Navigator & NavigatorWithWakeLock).wakeLock;
  let released = false;
  const pending = api === undefined ? Promise.resolve(null) : api.request("screen").catch(() => null);
  return {
    release() {
      if (released) return;
      released = true;
      void pending.then((sentinel) => sentinel?.release().catch(() => undefined));
    },
  };
}
