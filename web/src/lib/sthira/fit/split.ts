import { createHash } from 'node:crypto';
import { z } from 'zod';
import { formatIssues } from '../../label/schema';
import { sessionMetaSchema, type SessionMeta } from '../../record/schema';
import type { Person } from './data';

/**
 * The locked held-out split (council verdict, section c): before any tuning, lock held-out people
 * in a hashed split.json, including people in a saree or dhoti and the demo performer. Tuning
 * never sees them; the held-out set is evaluated once.
 */
export const SPLIT_SCHEMA = 'sthira.split.v1';

/** The protocol numbers come from the caller (the CLI cites the verdict); nothing is fixed here. */
export interface SplitRules {
  readonly heldOutCount: number;
  readonly minLooseHeldOut: number;
  readonly looseClothing: readonly SessionMeta['clothing'][];
  /** Leave-one-subject-out needs people to leave out and people to fit on. */
  readonly minTuning: number;
}

const code = sessionMetaSchema.shape.participantCode;

const splitCoreSchema = z.object({
  schema: z.literal(SPLIT_SCHEMA),
  createdAt: z.iso.datetime(),
  seed: z.string().min(1),
  demoPerformer: code,
  heldOut: z.array(code).min(1),
  tuning: z.array(code),
});
export const splitFileSchema = splitCoreSchema.extend({ hash: z.string().min(1) });
export type SplitCore = z.infer<typeof splitCoreSchema>;
export type SplitFile = z.infer<typeof splitFileSchema>;

export const sha256 = (text: string): string => createHash('sha256').update(text).digest('hex');

/** Hash of the split's content (lists sorted, keys in a fixed order), so any edit is detected. */
export function splitHash(core: SplitCore): string {
  const canonical = {
    schema: core.schema,
    createdAt: core.createdAt,
    seed: core.seed,
    demoPerformer: core.demoPerformer,
    heldOut: [...core.heldOut].sort(),
    tuning: [...core.tuning].sort(),
  };
  return sha256(JSON.stringify(canonical));
}

export type SplitResult = { readonly ok: true; readonly split: SplitFile } | { readonly ok: false; readonly problems: readonly string[] };

const isLoose = (person: Person, rules: SplitRules): boolean => person.clothing.some((c) => rules.looseClothing.includes(c));

function splitProblems(people: readonly Person[], demo: string, rules: SplitRules): string[] {
  const problems: string[] = [];
  const needed = rules.heldOutCount + rules.minTuning;
  if (people.length < needed) {
    problems.push(`${people.length} participants recorded; the split needs at least ${needed} (${rules.heldOutCount} held out + ${rules.minTuning} to tune on)`);
  }
  if (!people.some((p) => p.code === demo)) problems.push(`demo performer ${demo} has no chair-stand recording`);
  const loose = people.filter((p) => isLoose(p, rules)).length;
  if (loose < rules.minLooseHeldOut) {
    problems.push(`${loose} participants recorded in ${rules.looseClothing.join(' or ')}; the split needs at least ${rules.minLooseHeldOut}`);
  }
  return problems;
}

/**
 * Seeded, label-blind choice: the demo performer, then people in loose clothing until there are
 * enough, then anyone, each in the order of sha256(seed:code). Nobody looks at counts or labels.
 */
export function initSplit(people: readonly Person[], demo: string, rules: SplitRules, seed: string, createdAt: string): SplitResult {
  const problems = splitProblems(people, demo, rules);
  if (problems.length > 0) return { ok: false, problems };
  const ordered = [...people].sort((a, b) => sha256(`${seed}:${a.code}`).localeCompare(sha256(`${seed}:${b.code}`)));
  const demoPerson = people.find((p) => p.code === demo) as Person;
  const others = ordered.filter((p) => p.code !== demo);
  const looseNeeded = Math.max(0, rules.minLooseHeldOut - (isLoose(demoPerson, rules) ? 1 : 0));
  const loosePicked = others.filter((p) => isLoose(p, rules)).slice(0, looseNeeded);
  const rest = others.filter((p) => !loosePicked.includes(p));
  const heldOutPeople = [demoPerson, ...loosePicked, ...rest].slice(0, rules.heldOutCount);
  const heldOut = heldOutPeople.map((p) => p.code).sort();
  const tuning = people.map((p) => p.code).filter((c) => !heldOut.includes(c)).sort();
  const core: SplitCore = { schema: SPLIT_SCHEMA, createdAt, seed, demoPerformer: demo, heldOut, tuning };
  return { ok: true, split: { ...core, hash: splitHash(core) } };
}

/** Parse and verify a split.json text. A hash mismatch means the file was edited after locking. */
export function parseSplit(text: string): SplitResult {
  let json: unknown;
  try {
    json = JSON.parse(text);
  } catch {
    return { ok: false, problems: ['split.json is not valid JSON'] };
  }
  const parsed = splitFileSchema.safeParse(json);
  if (!parsed.success) return { ok: false, problems: formatIssues(parsed.error) };
  const { hash, ...core } = parsed.data;
  if (splitHash(core) !== hash) return { ok: false, problems: ['split.json hash does not match its content: it was edited after locking'] };
  if (core.heldOut.some((c) => core.tuning.includes(c))) return { ok: false, problems: ['a participant is both held out and tuning'] };
  return { ok: true, split: parsed.data };
}

/** Tuning = everyone who is not held out (people recorded after locking join tuning, never the held-out set). */
export function partition<T extends { readonly participant: string }>(items: readonly T[], split: SplitFile) {
  return {
    tuning: items.filter((i) => !split.heldOut.includes(i.participant)),
    heldOut: items.filter((i) => split.heldOut.includes(i.participant)),
    addedAfterLock: [...new Set(items.map((i) => i.participant))].filter(
      (c) => !split.heldOut.includes(c) && !split.tuning.includes(c),
    ),
  };
}
