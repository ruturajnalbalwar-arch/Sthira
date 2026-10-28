/**
 * Golden file shared with the Kotlin port (android/). The TypeScript engine is the reference: this test
 * recomputes every case and fails if shared/golden/chair-stand.golden.json is out of date.
 * Regenerate after an intentional engine change with:  UPDATE_GOLDEN=1 npx vitest run golden
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { calibrate } from '../calibration';
import { initialState, step, summarize } from '../chairStand';
import { createCounter } from '../counter';
import { loadParams } from '../params/loader';
import type { ChairStandParams } from '../params/required';
import realParams from '../params/params.json';
import type { PoseFrame } from '../types';
import {
  CALIB,
  CALIB_FRAMES,
  GEOMETRY,
  SEATED_Y,
  STANDING_Y,
  TEST_PARAMS,
  buildSession,
  buildStatic,
  makeCtx,
  paramEntry,
  paramsFile,
  type RepSpec,
} from './fixtures';

const GOLDEN_PATH = resolve(__dirname, '../../../../../shared/golden/chair-stand.golden.json');
const DECIMALS = 1e6;

/** Inputs are rounded before the engine runs, so the file is small and both engines read identical numbers. */
const round = (v: number): number => Math.round(v * DECIMALS) / DECIMALS;
const roundFrames = (frames: readonly PoseFrame[]): PoseFrame[] =>
  frames.map((f) => ({
    t: round(f.t),
    landmarks: f.landmarks.map((l) => ({ x: round(l.x), y: round(l.y), z: round(l.z), visibility: round(l.visibility) })),
  }));
const encode = (frames: readonly PoseFrame[]) =>
  frames.map((f) => ({ t: f.t, lm: f.landmarks.flatMap((l) => [l.x, l.y, l.z, l.visibility]) }));

const every = (n: number, from: number): RepSpec[] => Array.from({ length: n }, (_, i) => ({ startS: from + i * 5 }));

interface SessionCase {
  readonly name: string;
  readonly fps: number;
  readonly durationS: number;
  readonly reps: readonly RepSpec[];
  readonly testStartT?: number;
  readonly dropBetween?: readonly [number, number];
  readonly params?: Partial<ChairStandParams>;
}

const SESSION_CASES: readonly SessionCase[] = [
  { name: 'five-full', fps: 10, durationS: 30, reps: every(5, 1) },
  {
    name: 'mixed-kinds',
    fps: 10,
    durationS: 30,
    reps: [{ startS: 1 }, { startS: 6, peak: 0.4 }, { startS: 11, armsUncrossed: true }, { startS: 16, lowVisibility: true }, { startS: 21 }],
  },
  { name: 'time-up-credit', fps: 10, durationS: 30, reps: [{ startS: 1 }, { startS: 28.5, riseS: 2 }] },
  { name: 'time-up-below-threshold', fps: 10, durationS: 30, reps: [{ startS: 1 }, { startS: 29.6, riseS: 2 }] },
  { name: 'early-stop', fps: 10, durationS: 15, reps: [{ startS: 1 }, { startS: 6 }, { startS: 13.5, riseS: 2 }] },
  { name: 'gap-inside-rep', fps: 10, durationS: 30, reps: every(3, 1), dropBetween: [6.3, 7.6] },
  { name: 'thirty-fps', fps: 30, durationS: 12, reps: every(2, 1) },
  { name: 'test-start-offset', fps: 10, durationS: 34, reps: every(5, 4), testStartT: 3 },
  { name: 'unreliable', fps: 10, durationS: 30, reps: [{ startS: 1, lowVisibility: true }, { startS: 6, lowVisibility: true }, { startS: 11 }] },
  { name: 'low-fps', fps: 5, durationS: 30, reps: every(3, 1) },
  { name: 'heavier-smoothing', fps: 10, durationS: 30, reps: every(4, 1), params: { smoothingCutoffHz: 0.8, minStandDwellS: 0.4 } },
];

function sessionFrames(c: SessionCase): PoseFrame[] {
  const frames = buildSession(c.fps, c.durationS, c.reps);
  const kept = c.dropBetween ? frames.filter((f) => f.t < c.dropBetween![0] || f.t > c.dropBetween![1]) : frames;
  return roundFrames(kept);
}

function sessionGolden(c: SessionCase) {
  const params = { ...TEST_PARAMS, ...c.params };
  const ctx = makeCtx(params);
  const frames = sessionFrames(c);
  const final = frames.reduce((s, f) => step(s, f, ctx), initialState(c.testStartT));
  return { name: c.name, params, calib: CALIB, testStartT: c.testStartT ?? null, frames: encode(frames), expected: summarize(final, ctx) };
}

const CALIBRATION_CASES = [
  { name: 'good', sit: buildStatic(SEATED_Y, CALIB_FRAMES), stand: buildStatic(STANDING_Y, CALIB_FRAMES) },
  { name: 'too-few-frames', sit: buildStatic(SEATED_Y, 5), stand: buildStatic(STANDING_Y, 5) },
  { name: 'did-not-stand', sit: buildStatic(SEATED_Y, CALIB_FRAMES), stand: buildStatic(SEATED_Y + 5, CALIB_FRAMES) },
  { name: 'low-visibility', sit: buildStatic(SEATED_Y, CALIB_FRAMES, { visibility: 0.2 }), stand: buildStatic(STANDING_Y, CALIB_FRAMES, { visibility: 0.2 }) },
  { name: 'small-range', sit: buildStatic(SEATED_Y, CALIB_FRAMES), stand: buildStatic(SEATED_Y - 20, CALIB_FRAMES) },
  {
    name: 'fidgeting',
    sit: buildStatic(SEATED_Y, CALIB_FRAMES).map((f, i) => ({ ...f, landmarks: f.landmarks.map((l) => ({ ...l, y: l.y + (i % 2) * 0.04 })) })),
    stand: buildStatic(STANDING_Y, CALIB_FRAMES),
  },
  { name: 'empty', sit: [], stand: [] },
];

const LOADER_CASES = [
  { name: 'complete', json: paramsFile() },
  { name: 'repo-params-json', json: realParams },
  { name: 'ordering-violation', json: paramsFile({ seatFraction: paramEntry('seatFraction', 0.9) }) },
  { name: 'fraction-above-one', json: paramsFile({ reachFraction: paramEntry('reachFraction', 1.5) }) },
  { name: 'wrong-class', json: paramsFile({ minFps: { ...paramEntry('minFps', 8), class: 'cited-reference' } }) },
  { name: 'fitted-without-script', json: paramsFile({ maxGapS: { ...paramEntry('maxGapS', 1), script: undefined } }) },
];

function buildGolden() {
  return {
    schema: 'sthira.engine-golden.v1',
    note: 'Generated by web/src/lib/sthira/__tests__/golden.test.ts from the TypeScript engine. Do not edit by hand.',
    geometry: GEOMETRY,
    loader: LOADER_CASES.map((c) => ({ name: c.name, json: c.json, expected: loadParams(c.json) })),
    counter: LOADER_CASES.map((c) => ({ name: c.name, expectedStatus: createCounter(c.json).status })),
    calibration: CALIBRATION_CASES.map((c) => {
      const sit = roundFrames(c.sit);
      const stand = roundFrames(c.stand);
      return { name: c.name, params: TEST_PARAMS, sit: encode(sit), stand: encode(stand), expected: calibrate(sit, stand, GEOMETRY, TEST_PARAMS) };
    }),
    sessions: SESSION_CASES.map(sessionGolden),
  };
}

describe('engine golden file (shared with the Kotlin port)', () => {
  it('matches shared/golden/chair-stand.golden.json', () => {
    const golden = JSON.parse(JSON.stringify(buildGolden()));
    if (process.env.UPDATE_GOLDEN === '1' || !existsSync(GOLDEN_PATH)) {
      mkdirSync(dirname(GOLDEN_PATH), { recursive: true });
      writeFileSync(GOLDEN_PATH, `${JSON.stringify(golden)}\n`);
    }
    expect(JSON.parse(readFileSync(GOLDEN_PATH, 'utf8'))).toEqual(golden);
  });

  it('covers every outcome the Kotlin port must reproduce', () => {
    const golden = buildGolden();
    const statuses = new Set(golden.loader.map((c) => c.expected.status));
    expect(statuses).toEqual(new Set(['ready', 'untuned', 'invalid']));
    const kinds = new Set(golden.sessions.flatMap((s) => s.expected.events.map((e) => e.kind)));
    expect(kinds).toEqual(new Set(['full', 'half', 'arm-assisted', 'uncertain']));
    expect(golden.calibration.some((c) => c.expected.ok)).toBe(true);
    expect(golden.calibration.filter((c) => !c.expected.ok).length).toBeGreaterThanOrEqual(4);
    expect(golden.sessions.some((s) => !s.expected.windowComplete)).toBe(true);
    expect(golden.sessions.some((s) => s.expected.lowFps)).toBe(true);
    expect(golden.sessions.some((s) => s.expected.unreliable)).toBe(true);
    expect(golden.sessions.some((s) => s.expected.gaps > 0)).toBe(true);
  });
});
