"use client";

import { useCallback, useSyncExternalStore } from "react";

const MEDIA_EVENTS = [
  "timeupdate",
  "seeked",
  "play",
  "pause",
  "ratechange",
  "loadedmetadata",
  "durationchange",
  "emptied",
  "error",
];

/** Length state: a finite length, still loading, or unknown (Chrome MediaRecorder WebM reports Infinity). */
export type VideoLength = number | "loading" | "unknown";

export const readTime = (video: HTMLVideoElement): number => video.currentTime;
export const readPlaying = (video: HTMLVideoElement): boolean => !video.paused;
export const readRate = (video: HTMLVideoElement): number => video.playbackRate;
export function readLength(video: HTMLVideoElement): VideoLength {
  if (video.readyState < video.HAVE_METADATA) return "loading";
  return Number.isFinite(video.duration) ? video.duration : "unknown";
}

/**
 * Subscribe a component to one primitive value of the video element. React re-renders only that
 * component, and only when the value changes. `live` adds animation-frame updates while playing,
 * for the playhead and time readout; everything else updates on media events only.
 */
export function useVideoValue<T extends string | number | boolean>(
  video: HTMLVideoElement | null,
  read: (video: HTMLVideoElement) => T,
  fallback: T,
  live = false,
): T {
  const subscribe = useCallback(
    (notify: () => void) => {
      if (video === null) return () => undefined;
      let frame = 0;
      const loop = () => {
        notify();
        frame = live && !video.paused ? requestAnimationFrame(loop) : 0;
      };
      const onEvent = () => {
        cancelAnimationFrame(frame);
        loop();
      };
      MEDIA_EVENTS.forEach((name) => video.addEventListener(name, onEvent));
      return () => {
        cancelAnimationFrame(frame);
        MEDIA_EVENTS.forEach((name) => video.removeEventListener(name, onEvent));
      };
    },
    [video, live],
  );
  return useSyncExternalStore(
    subscribe,
    () => (video === null ? fallback : read(video)),
    () => fallback,
  );
}
