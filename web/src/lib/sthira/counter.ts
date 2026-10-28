import type { Calibration } from './calibration';
import { makeContext, type ContextResult } from './context';
import { loadParams } from './params/loader';
import type { ChairStandParams } from './params/required';
import type { VideoGeometry } from './types';

export type CounterResult =
  | {
      readonly status: 'ready';
      readonly params: ChairStandParams;
      readonly makeContext: (geometry: VideoGeometry, calib: Calibration) => ContextResult;
    }
  | { readonly status: 'untuned'; readonly missing: readonly string[] }
  | { readonly status: 'invalid'; readonly errors: readonly string[] };

/** The engine only runs on a fully tuned, valid params.json. */
export function createCounter(paramsJson: unknown): CounterResult {
  const loaded = loadParams(paramsJson);
  if (loaded.status !== 'ready') return loaded;
  const { params } = loaded;
  return {
    status: 'ready',
    params,
    makeContext: (geometry, calib) => makeContext(geometry, calib, params),
  };
}
