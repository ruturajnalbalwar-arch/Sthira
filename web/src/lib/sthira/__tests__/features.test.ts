import { describe, expect, it } from 'vitest';
import { armsCrossedRatio, bodyScalePx, headY, inFrame, keyVisibility, shoulderWidthPx } from '../features';
import { LEFT_HIP, LEFT_SHOULDER, RIGHT_SHOULDER } from '../landmarks';
import type { PoseFrame } from '../types';
import { BODY_SCALE, GEOMETRY, SHOULDER_WIDTH, makeFrame } from './fixtures';

const withLandmark = (frame: PoseFrame, index: number, patch: Partial<PoseFrame['landmarks'][number]>): PoseFrame => ({
  ...frame,
  landmarks: frame.landmarks.map((lm, i) => (i === index ? { ...lm, ...patch } : lm)),
});

describe('features', () => {
  it('computes head y in pixels from nose and shoulder midpoint', () => {
    expect(headY(makeFrame(0, 200), GEOMETRY)).toBeCloseTo(200, 6);
  });

  it('converts normalised coordinates to pixels before measuring shoulder width', () => {
    expect(shoulderWidthPx(makeFrame(0, 200), GEOMETRY)).toBeCloseTo(SHOULDER_WIDTH, 6);
  });

  it('uses the larger of shoulder width and torso length as body scale, falling back without hips', () => {
    expect(bodyScalePx(makeFrame(0, 200), GEOMETRY)).toBeCloseTo(BODY_SCALE, 6);
    const noHip = withLandmark(makeFrame(0, 200), LEFT_HIP, { x: Number.NaN });
    expect(bodyScalePx(noHip, GEOMETRY)).toBeCloseTo(SHOULDER_WIDTH, 6);
  });

  it('gives a small arms ratio when crossed and a large one when uncrossed', () => {
    expect(armsCrossedRatio(makeFrame(0, 200), GEOMETRY)).toBeCloseTo(0, 6);
    expect(armsCrossedRatio(makeFrame(0, 200, { armsUncrossed: true }), GEOMETRY)).toBeGreaterThan(1);
  });

  it('keeps the arms ratio unchanged when the person leans towards the camera', () => {
    const near = armsCrossedRatio(makeFrame(0, 200, { armsUncrossed: true, scale: 1.5 }), GEOMETRY);
    const far = armsCrossedRatio(makeFrame(0, 200, { armsUncrossed: true }), GEOMETRY);
    expect(near).toBeCloseTo(far, 6);
  });

  it('keeps the arms ratio finite in a side view where the shoulders overlap', () => {
    const base = makeFrame(0, 200);
    const shoulderX = base.landmarks[LEFT_SHOULDER].x;
    const side = withLandmark(base, RIGHT_SHOULDER, { x: shoulderX });
    expect(Number.isFinite(armsCrossedRatio(side, GEOMETRY))).toBe(true);
    expect(bodyScalePx(side, GEOMETRY)).toBeGreaterThan(0);
  });

  it('takes the minimum visibility over key landmarks', () => {
    expect(keyVisibility(makeFrame(0, 200, { visibility: 0.3 }))).toBeCloseTo(0.3, 6);
  });

  it('detects landmarks outside the image and short landmark lists', () => {
    expect(inFrame(makeFrame(0, 200))).toBe(true);
    expect(inFrame(makeFrame(0, 200, { offsetX: 400 }))).toBe(false);
    expect(inFrame(makeFrame(0, 1000))).toBe(false);
    expect(inFrame({ t: 0, landmarks: [] })).toBe(false);
  });
});
