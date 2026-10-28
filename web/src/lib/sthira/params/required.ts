import type { ParamClass } from './schema';

export interface ParamRequirement {
  readonly class: ParamClass;
  readonly unit: string;
}

const fitted = (unit: string): ParamRequirement => ({ class: 'fitted', unit });

/** Every parameter the chair-stand detector reads, with required class and unit. */
export const REQUIRED_PARAMS = {
  chairStandDurationS: { class: 'protocol-constant', unit: 's' },
  calibrationSitS: { class: 'protocol-constant', unit: 's' },
  calibrationStandS: { class: 'protocol-constant', unit: 's' },
  leaveSeatFraction: fitted('fraction'),
  reachFraction: fitted('fraction'),
  seatFraction: fitted('fraction'),
  minStandDwellS: fitted('s'),
  minSitDwellS: fitted('s'),
  smoothingCutoffHz: fitted('Hz'),
  armsCrossedMaxRatio: fitted('ratio'),
  armsViolationMinS: fitted('s'),
  visibilityMin: fitted('fraction'),
  uncertainFrameMaxFraction: fitted('fraction'),
  calibStillnessMaxRatio: fitted('ratio'),
  calibMinRangeToBodyScaleRatio: fitted('ratio'),
  minFps: fitted('fps'),
  timeUpCountFraction: fitted('fraction'),
  unreliableUncertainFraction: fitted('fraction'),
  maxGapS: fitted('s'),
} as const satisfies Record<string, ParamRequirement>;

export type ParamName = keyof typeof REQUIRED_PARAMS;

export const PARAM_NAMES = Object.keys(REQUIRED_PARAMS) as ParamName[];

/** Validated, flat view of the parameters the detector reads. */
export type ChairStandParams = Readonly<Record<ParamName, number>>;
