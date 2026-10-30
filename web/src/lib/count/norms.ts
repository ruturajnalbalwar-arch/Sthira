import { z } from "zod";

/** Sex as asked on the count screen; CDC gives separate chair-stand cut-offs for women and men only. */
export const NORM_SEXES = ["female", "male", "not-recorded"] as const;
export type NormSex = (typeof NORM_SEXES)[number];

export const NORM_SEX_LABELS: Readonly<Record<NormSex, string>> = {
  female: "Female",
  male: "Male",
  "not-recorded": "Not recorded",
};

const sourceSchema = z.object({ url: z.string().min(1), title: z.string().min(1) });

const entrySchema = z.object({
  sex: z.enum(["female", "male"]),
  ageMin: z.number().int(),
  ageMax: z.number().int(),
  belowAverageIfLessThan: z.number().int().nonnegative(),
  source: sourceSchema,
});

const citationSchema = sourceSchema.extend({ year: z.number().int() });

const normsFileSchema = z.object({
  note: z.string().min(1),
  chairStand30s: z.object({
    protocol: z.object({ source: citationSchema }),
    entries: z.array(entrySchema).min(1),
  }),
});

export type ChairStandNormEntry = z.infer<typeof entrySchema>;
export type ChairStandCitation = z.infer<typeof citationSchema>;

/** The serialisable slice of norms.json the count screen needs. */
export interface ChairStandNorms {
  readonly note: string;
  /** The CDC sheet cited under every result. */
  readonly source: ChairStandCitation;
  readonly entries: readonly ChairStandNormEntry[];
}

export type NormsParseResult =
  | { readonly ok: true; readonly norms: ChairStandNorms }
  | { readonly ok: false; readonly errors: readonly string[] };

/** Validates norms.json (external data) and keeps only the chair-stand table. Never throws. */
export function parseChairStandNorms(json: unknown): NormsParseResult {
  const parsed = normsFileSchema.safeParse(json);
  if (!parsed.success) {
    return { ok: false, errors: parsed.error.issues.map((issue) => `${issue.path.join(".")}: ${issue.message}`) };
  }
  const { protocol, entries } = parsed.data.chairStand30s;
  const { url, title, year } = protocol.source;
  return { ok: true, norms: { note: parsed.data.note, source: { url, title, year }, entries } };
}

export type NormLookup =
  | { readonly status: "available"; readonly entry: ChairStandNormEntry }
  | { readonly status: "not-available"; readonly reason: string };

interface AgeRange {
  readonly min: number;
  /** Null for an open-ended band such as "80+". */
  readonly max: number | null;
}

/** Reads "60-64" or "80+" (the session form's age bands). Null for anything else. */
export function parseAgeBand(band: string): AgeRange | null {
  const closed = /^(\d+)-(\d+)$/.exec(band);
  if (closed !== null) {
    const [, min, max] = closed;
    return { min: Number(min), max: Number(max) };
  }
  const open = /^(\d+)\+$/.exec(band);
  if (open === null) return null;
  const [, min] = open;
  return { min: Number(min), max: null };
}

const within = (entry: ChairStandNormEntry, range: AgeRange): boolean =>
  entry.ageMin >= range.min && (range.max === null || entry.ageMax <= range.max);

const bandText = (entry: ChairStandNormEntry): string => `${entry.ageMin}-${entry.ageMax}`;

const notAvailable = (reason: string): NormLookup => ({ status: "not-available", reason });

/**
 * The CDC cut-off for this age band and sex. Only an exact CDC 5-year band matches: a band that
 * spans several CDC rows (e.g. 80+) has more than one cut-off, so no single value is picked.
 */
export function lookupChairStandNorm(norms: ChairStandNorms, ageBand: string, sex: NormSex): NormLookup {
  if (sex === "not-recorded") {
    return notAvailable("The CDC table gives separate values for women and men, and sex was not recorded.");
  }
  const range = parseAgeBand(ageBand);
  if (range === null) return notAvailable(`The age band "${ageBand}" is not one the CDC table uses.`);
  const matches = norms.entries.filter((entry) => entry.sex === sex && within(entry, range));
  if (matches.length === 0) return notAvailable(`The CDC table has no value for age ${ageBand}.`);
  const exact = matches.find((entry) => entry.ageMin === range.min && entry.ageMax === range.max);
  if (exact !== undefined) return { status: "available", entry: exact };
  return notAvailable(
    `Age ${ageBand} covers several CDC age bands (${matches.map(bandText).join(", ")}) with their own values; no single value applies.`,
  );
}
