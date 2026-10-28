"use client";

import { useCallback, useState } from "react";
import { snapToFrame, stepFrameT } from "@/lib/label/reducer";
import { PLAYBACK_RATES } from "./VideoPlayer";

export interface PlaybackNotice {
  readonly text: string;
  /** Changes every time, so the same text is announced again. */
  readonly seq: number;
}

export interface Playback {
  seek: (t: number) => void;
  togglePlay: () => void;
  stepFrame: (direction: -1 | 1) => void;
  stepSecond: (direction: -1 | 1) => void;
  changeSpeed: (direction: -1 | 1) => void;
  pause: () => void;
  /** Raw video time and that time snapped to a recorded frame (null when no frame is near enough). */
  position: () => { videoT: number; frameT: number | null };
  playError: string | null;
  notice: PlaybackNotice | null;
  clearNotice: () => void;
}

const clamp = (t: number, max: number | null): number => Math.max(0, max === null ? t : Math.min(t, max));

function nearestRateIndex(rate: number): number {
  return PLAYBACK_RATES.reduce((best, r, i) => (Math.abs(r - rate) < Math.abs(PLAYBACK_RATES[best] - rate) ? i : best), 0);
}

function setRate(video: HTMLVideoElement, rate: number): void {
  video.playbackRate = rate;
}

function seekVideo(video: HTMLVideoElement, t: number): void {
  video.currentTime = clamp(t, Number.isFinite(video.duration) ? video.duration : null);
}

/** Imperative video commands shared by the keyboard, the buttons and the timeline. */
export function usePlayback(video: HTMLVideoElement | null, frameTs: readonly number[]): Playback {
  const [playError, setPlayError] = useState<string | null>(null);
  const [notice, setNotice] = useState<PlaybackNotice | null>(null);
  const say = (text: string) => setNotice((n) => ({ text, seq: (n?.seq ?? 0) + 1 }));
  const togglePlay = () => {
    if (video === null) return;
    if (!video.paused) return video.pause();
    setPlayError(null);
    video.play().catch((error: unknown) => {
      setPlayError(error instanceof Error ? `The video cannot play: ${error.message}` : "The video cannot play.");
    });
  };
  const stepFrame = (direction: -1 | 1) => {
    if (video === null) return;
    video.pause();
    const target = stepFrameT(frameTs, video.currentTime, direction);
    if (target === null) return say(direction === 1 ? "Last recorded frame" : "First recorded frame");
    setNotice(null);
    seekVideo(video, target);
  };
  const stepSecond = (direction: -1 | 1) => {
    if (video !== null) seekVideo(video, video.currentTime + direction);
  };
  const changeSpeed = (direction: -1 | 1) => {
    if (video === null) return;
    const index = Math.min(Math.max(nearestRateIndex(video.playbackRate) + direction, 0), PLAYBACK_RATES.length - 1);
    setRate(video, PLAYBACK_RATES[index]);
  };
  const seek = useCallback(
    (t: number) => {
      if (video !== null) seekVideo(video, t);
    },
    [video],
  );
  const clearNotice = useCallback(() => setNotice(null), []);
  const pause = useCallback(() => video?.pause(), [video]);
  const position = () => {
    const videoT = video === null ? Number.NaN : video.currentTime;
    return { videoT, frameT: snapToFrame(frameTs, videoT) };
  };
  return { seek, togglePlay, stepFrame, stepSecond, changeSpeed, pause, position, playError, notice, clearNotice };
}
