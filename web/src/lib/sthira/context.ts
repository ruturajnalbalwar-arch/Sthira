import type { Calibration } from './calibration';
import { orderingErrors } from './params/loader';
import { PARAM_NAMES, type ChairStandParams } from './params/required';
import type { VideoGeometry } from './types';

declare const validated: unique symbol;

/** Context accepted by `step`. Only `makeContext` can produce one. */
export interface StepContext {
  readonly geometry: VideoGeometry;
  readonly calib: Calibration;
  readonly params: ChairStandParams;
  readonly [validated]: true;
}

export type ContextResult =
  | { readonly ok: true; readonly ctx: StepContext }
  | { readonly ok: false; readonly errors: readonly string[] };

const isPositive = (value: number): boolean => Number.isFinite(value) && value > 0;

function contextErrors(geometry: VideoGeometry, calib: Calibration, params: ChairStandParams): string[] {
  const errors: string[] = [];
  if (!isPositive(geometry.width) || !isPositive(geometry.height)) {
    errors.push('geometry: width and height must be positive');
  }
  const finite = [calib.seatedHeadY, calib.standingHeadY, calib.bodyScalePx].every(Number.isFinite);
  if (!finite) errors.push('calibration: values must be finite');
  if (finite && !(calib.seatedHeadY > calib.standingHeadY)) {
    errors.push('calibration: seated head must be below standing head (range > 0)');
  }
  if (!isPositive(calib.bodyScalePx)) errors.push('calibration: body scale must be positive');
  const badParams = PARAM_NAMES.filter((name) => !Number.isFinite(params[name]));
  if (badParams.length > 0) errors.push(`params: not finite: ${badParams.join(', ')}`);
  else errors.push(...orderingErrors(params).map((message) => `params: ${message}`));
  return errors;
}

/** Validate geometry, calibration and params before the reducer may run. */
export function makeContext(
  geometry: VideoGeometry,
  calib: Calibration,
  params: ChairStandParams,
): ContextResult {
  const errors = contextErrors(geometry, calib, params);
  if (errors.length > 0) return { ok: false, errors };
  return { ok: true, ctx: { geometry, calib, params } as StepContext };
}
