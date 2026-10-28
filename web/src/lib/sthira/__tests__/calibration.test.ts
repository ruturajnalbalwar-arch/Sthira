import { describe, expect, it } from 'vitest';
import { calibrate } from '../calibration';
import {
  buildStatic,
  CALIB_FPS,
  CALIB_FRAMES,
  GEOMETRY,
  SEATED_Y,
  BODY_SCALE,
  STANDING_Y,
  TEST_PARAMS,
  makeFrame,
} from './fixtures';

const good = () => ({
  sit: buildStatic(SEATED_Y, CALIB_FRAMES),
  stand: buildStatic(STANDING_Y, CALIB_FRAMES),
});

const wobble = (headY: number) =>
  Array.from({ length: CALIB_FRAMES }, (_, i) => makeFrame(i / CALIB_FPS, headY + (i % 2 === 0 ? 40 : -40)));

const reasonsOf = (result: ReturnType<typeof calibrate>): readonly string[] =>
  result.ok ? [] : result.reasons;

describe('calibrate', () => {
  it('accepts a still, fully visible sit and stand and returns medians', () => {
    const { sit, stand } = good();
    const result = calibrate(sit, stand, GEOMETRY, TEST_PARAMS);
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.calib.seatedHeadY).toBeCloseTo(SEATED_Y, 5);
      expect(result.calib.standingHeadY).toBeCloseTo(STANDING_Y, 5);
      expect(result.calib.bodyScalePx).toBeCloseTo(BODY_SCALE, 5);
    }
  });

  it('is robust to a single outlier frame thanks to the median', () => {
    const { sit, stand } = good();
    const withOutlier = [...sit.slice(0, -1), makeFrame(sit.at(-1)?.t ?? 0, SEATED_Y + 30)];
    const result = calibrate(withOutlier, stand, GEOMETRY, { ...TEST_PARAMS, calibStillnessMaxRatio: 0.2 });
    expect(result.ok && Math.abs(result.calib.seatedHeadY - SEATED_Y) < 1).toBe(true);
  });

  it('asks the person to sit still when they fidget', () => {
    expect(reasonsOf(calibrate(wobble(SEATED_Y), good().stand, GEOMETRY, TEST_PARAMS))).toContain('Please sit still');
  });

  it('asks the person to stand still when they sway while standing', () => {
    expect(reasonsOf(calibrate(good().sit, wobble(STANDING_Y), GEOMETRY, TEST_PARAMS))).toContain('Please stand still');
  });

  it('rejects a standing head that is not above the seated head', () => {
    const result = calibrate(good().sit, buildStatic(SEATED_Y + 20, CALIB_FRAMES), GEOMETRY, TEST_PARAMS);
    expect(reasonsOf(result).join(' ')).toMatch(/Stand fully upright/);
  });

  it('rejects a head-height change that is too small relative to shoulder width', () => {
    const result = calibrate(good().sit, buildStatic(SEATED_Y - 20, CALIB_FRAMES), GEOMETRY, TEST_PARAMS);
    expect(reasonsOf(result)).toContain('Stand fully upright so we can see your full height change');
  });

  it('rejects a capture shorter than the protocol countdown', () => {
    const short = buildStatic(SEATED_Y, CALIB_FRAMES - 1);
    expect(reasonsOf(calibrate(short, good().stand, GEOMETRY, TEST_PARAMS))).toContain(
      'Please stay seated for the full countdown',
    );
    const shortStand = buildStatic(STANDING_Y, CALIB_FRAMES - 1);
    expect(reasonsOf(calibrate(good().sit, shortStand, GEOMETRY, TEST_PARAMS))).toContain(
      'Please stay standing for the full countdown',
    );
  });

  it('rejects a capture with fewer frames than minFps over the countdown', () => {
    const sparse = good().sit.filter((_, i) => i % 2 === 0);
    expect(reasonsOf(calibrate(sparse, good().stand, GEOMETRY, TEST_PARAMS))).toContain(
      'The camera is too slow: close other apps and try again',
    );
  });

  it('survives a single dropout frame in the middle of a capture', () => {
    const { sit, stand } = good();
    const withDropout = sit.map((f, i) => (i === 20 ? { t: f.t, landmarks: [] } : f));
    expect(calibrate(withDropout, stand, GEOMETRY, TEST_PARAMS).ok).toBe(true);
  });

  it('says the person was lost (not that the camera is slow) when many frames leave the view', () => {
    const { sit, stand } = good();
    const cropped = sit.map((f, i) => (i % 2 === 0 ? makeFrame(f.t, SEATED_Y, { offsetX: 400 }) : f));
    expect(reasonsOf(calibrate(cropped, stand, GEOMETRY, TEST_PARAMS))).toContain(
      'We lost sight of you while seated: keep your whole body in view',
    );
  });

  it('does not count repeated timestamps towards the frame-rate gate', () => {
    const { stand } = good();
    const sparse = buildStatic(SEATED_Y, CALIB_FRAMES).filter((_, i) => i % 2 === 0);
    const doubled = sparse.flatMap((f) => [f, f]);
    expect(calibrate(doubled, stand, GEOMETRY, TEST_PARAMS).ok).toBe(false);
  });

  it('asks the person into view when no frame is usable', () => {
    const { sit, stand } = good();
    const gone = stand.map((f) => makeFrame(f.t, STANDING_Y, { offsetX: 400 }));
    expect(reasonsOf(calibrate(sit, gone, GEOMETRY, TEST_PARAMS))).toEqual(['Move so your whole body is visible']);
  });

  it('rejects low landmark visibility', () => {
    const lowVis = { visibility: 0.1 };
    const result = calibrate(
      buildStatic(SEATED_Y, CALIB_FRAMES, lowVis),
      buildStatic(STANDING_Y, CALIB_FRAMES, lowVis),
      GEOMETRY,
      TEST_PARAMS,
    );
    expect(reasonsOf(result).join(' ')).toMatch(/clearly visible/);
  });

  it('rejects empty captures and malformed frames without throwing', () => {
    const { sit, stand } = good();
    expect(reasonsOf(calibrate([], stand, GEOMETRY, TEST_PARAMS))).toHaveLength(1);
    expect(reasonsOf(calibrate(sit, [], GEOMETRY, TEST_PARAMS))).toHaveLength(1);
    expect(calibrate(sit, [{ t: 0, landmarks: [] }], GEOMETRY, TEST_PARAMS).ok).toBe(false);
  });
});
