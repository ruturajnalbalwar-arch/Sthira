/**
 * Offline parameter fitter for the Sthira chair-stand engine. Run from web/:
 *
 *   npx tsx scripts/fit-params.ts init-split --demo P07 [--seed <text>] [--data <dir>]
 *   npx tsx scripts/fit-params.ts tune [--grid-points 11] [--max-rounds 10] [--data <dir>] [--out <dir>] [--write]
 *   npx tsx scripts/fit-params.ts evaluate [--params <file>] [--data <dir>] [--out <dir>] [--force]
 *
 * Method and parameter origins: docs/pilot/fitting.md. All logic lives in src/lib/sthira/fit;
 * this file only reads and writes files and prints.
 */
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { basename, join, relative, resolve, sep } from 'node:path';
import { parseArgs } from 'node:util';
import { loadPilot, type Exclusion, type NamedText, type PilotData } from '../src/lib/sthira/fit/data';
import type { GroupMetrics } from '../src/lib/sthira/fit/metrics';
import { buildParamsFile, buildTuneReport, evaluateHeldOut, tunedOnSplit } from '../src/lib/sthira/fit/output';
import { fitParams, leaveOneSubjectOut, type FitOptions } from '../src/lib/sthira/fit/search';
import { initSplit, parseSplit, partition, sha256, type SplitFile, type SplitRules } from '../src/lib/sthira/fit/split';
import { loadParams } from '../src/lib/sthira/params/loader';
import { readProtocolConstants, type ProtocolConstants } from '../src/lib/sthira/params/protocol';
import { ParamsFileSchema, type ParamsFile } from '../src/lib/sthira/params/schema';

/** Council verdict 2026-10-22, section (c): lock 4 held-out people, at least 2 in a saree or dhoti, plus the demo performer. */
const SPLIT_RULES: SplitRules = {
  heldOutCount: 4,
  minLooseHeldOut: 2,
  looseClothing: ['saree', 'dhoti'],
  // Leave-one-subject-out needs one person to leave out and at least one to fit on.
  minTuning: 2,
};

/**
 * Methodological defaults (not detector thresholds). 11 grid points = the deciles of each observed
 * distribution, 0 % to 100 %, plus the midpoints between them. 10 rounds is only a safety cap:
 * the descent stops as soon as a round changes nothing.
 */
const DEFAULT_GRID_POINTS = 11;
const DEFAULT_MAX_ROUNDS = 10;
const DEFAULT_SEED = 'sthira-split-v1';
const MIN_GRID_POINTS = 2;
const JSON_INDENT = 2;
const SHORT_HASH = 12;
const DECIMALS = 2;
const PERCENT = 100;
/** process.argv = [node, script, ...args]. */
const ARGV_OFFSET = 2;

const PARAMS_PATH = 'src/lib/sthira/params/params.json';
const SPLIT_NAME = 'split.json';
const OUT_DIR_NAME = 'fit';

const USAGE = `Usage (from web/):
  npx tsx scripts/fit-params.ts init-split --demo <code> [--seed <text>] [--data <dir>]
  npx tsx scripts/fit-params.ts tune [--grid-points <n>] [--max-rounds <n>] [--data <dir>] [--out <dir>] [--write]
  npx tsx scripts/fit-params.ts evaluate [--params <file>] [--data <dir>] [--out <dir>] [--force]
See docs/pilot/fitting.md.`;

class CliError extends Error {}

const today = (iso: string): string => iso.slice(0, iso.indexOf('T'));
const writeJson = (path: string, value: unknown): void => writeFileSync(path, `${JSON.stringify(value, null, JSON_INDENT)}\n`);

/** Every .json file under dir (recursively), named by its path relative to root, skipping the fitter's own outputs. */
function readJsonFiles(dir: string, root: string, skip: readonly string[]): NamedText[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (skip.includes(resolve(path))) return [];
    if (entry.isDirectory()) return readJsonFiles(path, root, skip);
    if (!entry.name.endsWith('.json')) return [];
    return [{ name: relative(root, path).split(sep).join('/'), text: readFileSync(path, 'utf8') }];
  });
}

function readParamsFile(path: string): ParamsFile {
  if (!existsSync(path)) throw new CliError(`params file not found: ${path}`);
  const parsed = ParamsFileSchema.safeParse(JSON.parse(readFileSync(path, 'utf8')));
  if (!parsed.success) throw new CliError(`invalid params file ${path}: ${parsed.error.issues.map((i) => i.message).join('; ')}`);
  return parsed.data;
}

function protocolOf(file: ParamsFile): ProtocolConstants {
  const result = readProtocolConstants(file);
  if (!result.ok) throw new CliError(`params.json protocol constants: ${result.errors.join('; ')}`);
  return result.constants;
}

function loadData(dataDir: string, outDir: string, protocol: ProtocolConstants): PilotData {
  if (!existsSync(dataDir)) throw new CliError(noDataMessage(dataDir));
  const files = readJsonFiles(dataDir, dataDir, [resolve(outDir), resolve(dataDir, SPLIT_NAME)]);
  const data = loadPilot(files, protocol);
  if (data.people.length === 0) {
    printExclusions(data.exclusions);
    throw new CliError(noDataMessage(dataDir));
  }
  return data;
}

const noDataMessage = (dataDir: string): string =>
  [
    `No usable chair-stand recordings in ${dataDir}.`,
    'Record trials in the app (record mode, docs/pilot/protocol.md), have both labellers label each',
    'clip (docs/pilot/labelling-rules.md), and copy the recording and label .json files into that folder.',
  ].join('\n');

function readSplit(dataDir: string): SplitFile {
  const path = join(dataDir, SPLIT_NAME);
  if (!existsSync(path)) {
    throw new CliError(`No ${SPLIT_NAME} in ${dataDir}: lock the held-out split first with init-split. Tuning is refused without it.`);
  }
  const result = parseSplit(readFileSync(path, 'utf8'));
  if (!result.ok) throw new CliError(`${SPLIT_NAME}: ${result.problems.join('; ')}`);
  return result.split;
}

function printExclusions(exclusions: readonly Exclusion[]): void {
  if (exclusions.length === 0) return;
  console.log(`\nNot used (${exclusions.length}):`);
  for (const e of exclusions) console.log(`  ${e.file}: ${e.reason}`);
}

const fmt = (value: number | null, scale = 1, suffix = ''): string =>
  value === null ? 'n/a' : `${(value * scale).toFixed(DECIMALS)}${suffix}`;

function printMetrics(title: string, rows: readonly GroupMetrics[]): void {
  console.log(`\n${title}`);
  const header = ['clothing', 'trials', 'people', '30s trials', 'count MAE', 'exact match', 'false counts', 'half rejected', 'arm rejected', 'abstain'];
  const body = rows.map((r) => [
    r.group, `${r.trials}`, `${r.people}`, `${r.countTrials}`, fmt(r.countMAE), fmt(r.exactMatchRate, PERCENT, '%'),
    `${r.falseCounts}`, fmt(r.halfRejection, PERCENT, '%'), fmt(r.armRejection, PERCENT, '%'), fmt(r.abstainRate, PERCENT, '%'),
  ]);
  const widths = header.map((h, i) => Math.max(h.length, ...body.map((row) => row[i].length)));
  for (const row of [header, ...body]) console.log(`  ${row.map((cell, i) => cell.padEnd(widths[i])).join('  ')}`);
}

type Values = Record<string, string | boolean | undefined>;

function commandInitSplit(values: Values, dataDir: string, outDir: string, protocol: ProtocolConstants): void {
  const demo = values.demo;
  if (typeof demo !== 'string') throw new CliError('init-split needs --demo <participant code> (the demo performer)');
  const path = join(dataDir, SPLIT_NAME);
  if (existsSync(path)) throw new CliError(`${path} already exists: the split is locked and is never redrawn.`);
  const data = loadData(dataDir, outDir, protocol);
  const seed = typeof values.seed === 'string' ? values.seed : DEFAULT_SEED;
  const result = initSplit(data.people, demo, SPLIT_RULES, seed, new Date().toISOString());
  if (!result.ok) throw new CliError(`Cannot lock the split yet:\n  ${result.problems.join('\n  ')}`);
  writeJson(path, result.split);
  console.log(`Locked ${path} (hash ${result.split.hash.slice(0, SHORT_HASH)})`);
  console.log(`  held out: ${result.split.heldOut.join(', ')}\n  tuning:   ${result.split.tuning.join(', ')}`);
}

function positiveInt(values: Values, key: string, fallback: number, min: number): number {
  const raw = values[key];
  const n = typeof raw === 'string' ? Number(raw) : fallback;
  if (!Number.isInteger(n) || n < min) throw new CliError(`--${key} must be a whole number >= ${min}`);
  return n;
}

function commandTune(values: Values, dataDir: string, outDir: string, paramsPath: string): void {
  const current = readParamsFile(paramsPath);
  const protocol = protocolOf(current);
  const data = loadData(dataDir, outDir, protocol);
  const split = readSplit(dataDir);
  const parts = partition(data.trials, split);
  const options: FitOptions = {
    gridPoints: positiveInt(values, 'grid-points', DEFAULT_GRID_POINTS, MIN_GRID_POINTS),
    maxRounds: positiveInt(values, 'max-rounds', DEFAULT_MAX_ROUNDS, 1),
  };
  console.log(`Tuning on ${parts.tuning.length} trials (held-out people never loaded into the fit: ${split.heldOut.join(', ')})`);
  const fit = fitParams(parts.tuning, protocol, options);
  if (!fit.ok) {
    printExclusions([...data.exclusions, ...fit.skipped]);
    throw new CliError(`Cannot fit:\n  ${fit.errors.join('\n  ')}`);
  }
  const folds = leaveOneSubjectOut(parts.tuning, protocol, options);
  const generatedAt = new Date().toISOString();
  const report = buildTuneReport({ fit, folds, options, splitHash: split.hash, generatedAt, exclusions: data.exclusions, addedAfterLock: parts.addedAfterLock });
  const output = buildParamsFile(current, fit, { splitHash: split.hash, date: today(generatedAt), generatedAt });
  if (!output.ok) throw new CliError(`Fitted params do not load: ${output.errors.join('; ')}`);
  mkdirSync(outDir, { recursive: true });
  writeJson(join(outDir, 'params.candidate.json'), output.file);
  writeJson(join(outDir, 'tune-report.json'), report);
  printExclusions(report.exclusions);
  console.log(`\nFinal fit: loss [count error, attempt disagreements, flag mismatches] = [${fit.loss.join(', ')}], ${fit.rounds} rounds${fit.converged ? '' : ' (hit --max-rounds)'}`);
  for (const f of folds.filter((x) => x.errors.length > 0)) console.log(`  LOSO fold ${f.participant} failed: ${f.errors.join('; ')}`);
  printMetrics('Leave-one-subject-out on the tuning set (each person scored by a fit that never saw them):', report.loso.metrics);
  console.log(`\nWrote ${join(outDir, 'params.candidate.json')} and ${join(outDir, 'tune-report.json')}`);
  if (values.write === true) {
    writeJson(paramsPath, output.file);
    console.log(`Wrote ${paramsPath}`);
  } else console.log(`params.json unchanged; re-run with --write to install the candidate.`);
}

function evaluationPath(outDir: string, split: SplitFile, force: boolean): string {
  const base = join(outDir, `evaluation-${split.hash.slice(0, SHORT_HASH)}`);
  if (!existsSync(`${base}.json`)) return `${base}.json`;
  if (!force) {
    throw new CliError(`The held-out set was already evaluated for this split (${base}.json). It is run once; --force adds a logged re-run.`);
  }
  return `${base}-rerun-${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
}

function commandEvaluate(values: Values, dataDir: string, outDir: string, paramsPath: string): void {
  const text = readFileSync(paramsPath, 'utf8');
  const file = readParamsFile(paramsPath);
  const loaded = loadParams(file);
  if (loaded.status !== 'ready') throw new CliError(`${paramsPath} is ${loaded.status}: run tune --write first.`);
  const split = readSplit(dataDir);
  if (!tunedOnSplit(file, split.hash)) throw new CliError(`${paramsPath} was not tuned on this split (${split.hash.slice(0, SHORT_HASH)}).`);
  const target = evaluationPath(outDir, split, values.force === true);
  const data = loadData(dataDir, outDir, protocolOf(file));
  const heldOut = partition(data.trials, split).heldOut;
  // Record mode names files <participant>_<trial>_..., so a held-out person's unusable files are listed too.
  const exclusions = data.exclusions.filter((e) => split.heldOut.some((code) => basename(e.file).startsWith(`${code}_`)));
  const record = evaluateHeldOut({
    trials: heldOut,
    params: loaded.params,
    heldOutPeople: split.heldOut,
    splitHash: split.hash,
    paramsSha256: sha256(text),
    generatedAt: new Date().toISOString(),
    exclusions,
  });
  mkdirSync(outDir, { recursive: true });
  writeJson(target, record);
  printExclusions(exclusions);
  printMetrics(`Held-out evaluation (${split.heldOut.join(', ')}; ${heldOut.length} trials):`, record.metrics);
  console.log(`\nWrote ${target}`);
}

function main(argv: readonly string[]): void {
  const { values, positionals } = parseArgs({
    args: [...argv],
    allowPositionals: true,
    options: {
      demo: { type: 'string' }, seed: { type: 'string' }, data: { type: 'string' }, out: { type: 'string' },
      params: { type: 'string' }, 'grid-points': { type: 'string' }, 'max-rounds': { type: 'string' },
      write: { type: 'boolean' }, force: { type: 'boolean' }, help: { type: 'boolean' },
    },
  });
  const [command] = positionals;
  const dataDir = resolve(typeof values.data === 'string' ? values.data : join('..', 'pilot-data'));
  const outDir = resolve(typeof values.out === 'string' ? values.out : join(dataDir, OUT_DIR_NAME));
  const paramsPath = resolve(typeof values.params === 'string' ? values.params : PARAMS_PATH);
  if (command === 'init-split') commandInitSplit(values, dataDir, outDir, protocolOf(readParamsFile(resolve(PARAMS_PATH))));
  else if (command === 'tune') commandTune(values, dataDir, outDir, paramsPath);
  else if (command === 'evaluate') commandEvaluate(values, dataDir, outDir, paramsPath);
  else {
    console.log(USAGE);
    if (values.help !== true) process.exitCode = 1;
  }
}

try {
  main(process.argv.slice(ARGV_OFFSET));
} catch (error: unknown) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
}

