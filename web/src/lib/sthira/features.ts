import {
  KEY_LANDMARKS,
  LEFT_HIP,
  LEFT_SHOULDER,
  LEFT_WRIST,
  NOSE,
  POSE_LANDMARK_COUNT,
  RIGHT_HIP,
  RIGHT_SHOULDER,
  RIGHT_WRIST,
} from './landmarks';
import { midpoint } from './mathConstants';
import type { PoseFrame, VideoGeometry } from './types';

interface Point {
  readonly x: number;
  readonly y: number;
}

const isFiniteNumber = (value: unknown): boolean =>
  typeof value === 'number' && Number.isFinite(value);

function pixelPoint(frame: PoseFrame, index: number, geometry: VideoGeometry): Point {
  const landmark = frame.landmarks[index];
  return { x: landmark.x * geometry.width, y: landmark.y * geometry.height };
}

const distance = (a: Point, b: Point): number => Math.hypot(a.x - b.x, a.y - b.y);

/** True when a person is present: every key landmark exists with finite x and y. */
export function isUsableFrame(frame: PoseFrame): boolean {
  const landmarks = frame.landmarks;
  if ((landmarks?.length ?? 0) < POSE_LANDMARK_COUNT) return false;
  return KEY_LANDMARKS.every((index) => {
    const landmark = landmarks[index];
    return landmark != null && isFiniteNumber(landmark.x) && isFiniteNumber(landmark.y);
  });
}

/** Pixel y of mean(nose, shoulder midpoint): a head proxy (MediaPipe has no neck). */
export function headY(frame: PoseFrame, geometry: VideoGeometry): number {
  const nose = pixelPoint(frame, NOSE, geometry);
  const left = pixelPoint(frame, LEFT_SHOULDER, geometry);
  const right = pixelPoint(frame, RIGHT_SHOULDER, geometry);
  return midpoint(nose.y, midpoint(left.y, right.y));
}

export function shoulderWidthPx(frame: PoseFrame, geometry: VideoGeometry): number {
  const left = pixelPoint(frame, LEFT_SHOULDER, geometry);
  const right = pixelPoint(frame, RIGHT_SHOULDER, geometry);
  return distance(left, right);
}

/**
 * This frame's body scale in pixels: the larger of shoulder width and torso length (shoulder
 * midpoint to hip midpoint). Shoulder width holds up when the trunk flexes in a front view; torso
 * length holds up in a side view, where shoulder width collapses towards 0. Hips that are not
 * finite fall back to shoulder width.
 * ponytail: one global ratio for all views; split armsCrossedMaxRatio per viewAngle if pilot data shows view dependence.
 */
export function bodyScalePx(frame: PoseFrame, geometry: VideoGeometry): number {
  const width = shoulderWidthPx(frame, geometry);
  const hips = [frame.landmarks[LEFT_HIP], frame.landmarks[RIGHT_HIP]];
  if (!hips.every((hip) => hip != null && isFiniteNumber(hip.x) && isFiniteNumber(hip.y))) return width;
  const left = pixelPoint(frame, LEFT_SHOULDER, geometry);
  const right = pixelPoint(frame, RIGHT_SHOULDER, geometry);
  const leftHip = pixelPoint(frame, LEFT_HIP, geometry);
  const rightHip = pixelPoint(frame, RIGHT_HIP, geometry);
  const shoulderMid = { x: midpoint(left.x, right.x), y: midpoint(left.y, right.y) };
  const hipMid = { x: midpoint(leftHip.x, rightHip.x), y: midpoint(leftHip.y, rightHip.y) };
  return Math.max(width, distance(shoulderMid, hipMid));
}

/** Larger value means wrists are further from the opposite shoulder (arms not crossed), per this frame's body scale. */
export function armsCrossedRatio(frame: PoseFrame, geometry: VideoGeometry): number {
  const leftWrist = pixelPoint(frame, LEFT_WRIST, geometry);
  const rightWrist = pixelPoint(frame, RIGHT_WRIST, geometry);
  const leftShoulder = pixelPoint(frame, LEFT_SHOULDER, geometry);
  const rightShoulder = pixelPoint(frame, RIGHT_SHOULDER, geometry);
  const worst = Math.max(distance(leftWrist, rightShoulder), distance(rightWrist, leftShoulder));
  return worst / bodyScalePx(frame, geometry);
}

/** Minimum visibility over key landmarks. Missing or non-finite visibility counts as 0 (low). */
export function keyVisibility(frame: PoseFrame): number {
  const values = KEY_LANDMARKS.map((index) => {
    const visibility = frame.landmarks?.[index]?.visibility;
    return isFiniteNumber(visibility) ? (visibility as number) : 0;
  });
  return Math.min(...values);
}

/** True when every key landmark lies inside the image. */
export function inFrame(frame: PoseFrame): boolean {
  if (!isUsableFrame(frame)) return false;
  return KEY_LANDMARKS.every((index) => {
    const { x, y } = frame.landmarks[index];
    return x >= 0 && x <= 1 && y >= 0 && y <= 1;
  });
}
