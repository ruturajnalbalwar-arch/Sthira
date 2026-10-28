import { PARAM_NAMES, REQUIRED_PARAMS, type ChairStandParams, type ParamName } from './required';
import { ParamsFileSchema, type ParamEntry } from './schema';

export type LoadResult =
  | { readonly status: 'ready'; readonly params: ChairStandParams }
  | { readonly status: 'untuned'; readonly missing: readonly string[] }
  | { readonly status: 'invalid'; readonly errors: readonly string[] };

function isKnownName(name: string): name is ParamName {
  return Object.prototype.hasOwnProperty.call(REQUIRED_PARAMS, name);
}

function entryErrors(name: ParamName, entry: ParamEntry): string[] {
  const required = REQUIRED_PARAMS[name];
  const errors: string[] = [];
  if (entry.class !== required.class) {
    errors.push(`${name}: class must be ${required.class}, got ${entry.class}`);
  }
  if (entry.unit !== required.unit) {
    errors.push(`${name}: unit must be ${required.unit}, got ${entry.unit}`);
  }
  if (entry.value < 0) errors.push(`${name}: value must be >= 0`);
  if (required.unit === 'fraction' && entry.value > 1) {
    errors.push(`${name}: a fraction must be <= 1`);
  }
  if (entry.class === 'fitted' && entry.script === undefined) {
    errors.push(`${name}: fitted values must name the script that produced them`);
  }
  return errors;
}

/** Cross-parameter consistency rules; also applied by makeContext to hand-built params. */
export function orderingErrors(v: Readonly<Record<string, number>>): string[] {
  const rules: ReadonlyArray<readonly [boolean, string]> = [
    [v.seatFraction > v.leaveSeatFraction, 'seatFraction must be <= leaveSeatFraction'],
    [v.leaveSeatFraction >= v.reachFraction, 'leaveSeatFraction must be below reachFraction'],
    [v.seatFraction >= v.reachFraction, 'seatFraction must be below reachFraction'],
    [v.reachFraction <= 0, 'reachFraction must be > 0'],
    [v.uncertainFrameMaxFraction >= 1, 'uncertainFrameMaxFraction must be < 1'],
    [v.unreliableUncertainFraction >= 1, 'unreliableUncertainFraction must be < 1'],
    [v.timeUpCountFraction < v.seatFraction || v.timeUpCountFraction > 1, 'timeUpCountFraction must be between seatFraction and 1'],
    [v.smoothingCutoffHz <= 0, 'smoothingCutoffHz must be > 0'],
    [v.maxGapS <= 0, 'maxGapS must be > 0'],
    [v.minFps <= 0, 'minFps must be > 0'],
    [v.maxGapS * v.minFps <= 1, 'maxGapS must be longer than one frame at minFps'],
  ];
  return rules.filter(([violated]) => violated).map(([, message]) => message);
}

/** Validate a parsed params.json. Never throws on bad input. */
export function loadParams(json: unknown): LoadResult {
  const parsed = ParamsFileSchema.safeParse(json);
  if (!parsed.success) {
    const errors = parsed.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`);
    return { status: 'invalid', errors };
  }
  const entries = parsed.data.params;
  const errors = Object.entries(entries).flatMap(([name, entry]) =>
    isKnownName(name) ? entryErrors(name, entry) : [],
  );
  if (errors.length > 0) return { status: 'invalid', errors };

  const missing = PARAM_NAMES.filter((name) => !Object.hasOwn(entries, name));
  if (missing.length > 0) return { status: 'untuned', missing };

  const values = Object.fromEntries(PARAM_NAMES.map((n) => [n, entries[n].value]));
  const ordering = orderingErrors(values);
  if (ordering.length > 0) return { status: 'invalid', errors: ordering };
  return { status: 'ready', params: values as ChairStandParams };
}
