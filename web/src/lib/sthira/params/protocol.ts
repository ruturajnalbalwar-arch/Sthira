import { ParamsFileSchema } from './schema';

/** Protocol constants are available before any parameter is fitted (e.g. for record mode). */
export const PROTOCOL_CONSTANT_NAMES = ['chairStandDurationS', 'calibrationSitS', 'calibrationStandS'] as const;
export type ProtocolConstantName = (typeof PROTOCOL_CONSTANT_NAMES)[number];
export type ProtocolConstants = Readonly<Record<ProtocolConstantName, number>>;

export type ProtocolResult =
  | { readonly ok: true; readonly constants: ProtocolConstants }
  | { readonly ok: false; readonly errors: readonly string[] };

/** Read the protocol constants from a parsed params.json. Never throws. */
export function readProtocolConstants(json: unknown): ProtocolResult {
  const parsed = ParamsFileSchema.safeParse(json);
  if (!parsed.success) {
    return { ok: false, errors: parsed.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`) };
  }
  const entries = parsed.data.params;
  const errors = PROTOCOL_CONSTANT_NAMES.flatMap((name) => {
    const entry = entries[name];
    if (entry === undefined) return [`${name}: missing`];
    if (entry.class !== 'protocol-constant') return [`${name}: must be a protocol-constant`];
    if (entry.unit !== 's' || entry.value <= 0) return [`${name}: must be a positive number of seconds`];
    return [];
  });
  if (errors.length > 0) return { ok: false, errors };
  const constants = Object.fromEntries(PROTOCOL_CONSTANT_NAMES.map((n) => [n, entries[n].value]));
  return { ok: true, constants: constants as ProtocolConstants };
}
