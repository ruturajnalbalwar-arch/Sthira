import { readdirSync, readFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { describe, expect, it } from 'vitest';
import * as landmarks from '../landmarks';
import * as mathConstants from '../mathConstants';

const ROOT = join(__dirname, '..');
/** Files allowed to hold numbers: model landmark indices, pure math, and type declarations. */
const EXEMPT = new Set(['landmarks.ts', 'mathConstants.ts', 'types.ts']);
const ALLOWED = new Set(['0', '1']);

function sourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return entry.name === '__tests__' ? [] : sourceFiles(path);
    return entry.name.endsWith('.ts') ? [relative(ROOT, path).split(sep).join('/')] : [];
  });
}

const DETECTOR_FILES = sourceFiles(ROOT).filter((file) => !EXEMPT.has(file));

/** Keep only the ${...} expressions of a template literal; they are code. */
const templateExpressions = (template: string): string =>
  ` ${[...template.matchAll(/\$\{([^}]*)\}/g)].map((m) => m[1]).join(' ')} `;

function stripCommentsAndStrings(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/`(?:\\.|[^`\\])*`/g, templateExpressions)
    .replace(/'(?:\\.|[^'\\\n])*'|"(?:\\.|[^"\\\n])*"/g, '""');
}

const NUMBER = /(?<![\w$.])0[xXbBoO][\da-fA-F_]+n?|(?<![\w$.])\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?n?(?![\w$])|(?<![\w$])\.\d+/g;

export function findDisallowedNumbers(source: string): string[] {
  const tokens = stripCommentsAndStrings(source).match(NUMBER) ?? [];
  return tokens.filter((token) => !ALLOWED.has(token));
}

describe('numeric-literal lint', () => {
  it('scans every engine source file outside the exempt list', () => {
    expect(DETECTOR_FILES).toEqual(expect.arrayContaining(['chairStand.ts', 'calibration.ts', 'params/loader.ts']));
  });

  it.each(DETECTOR_FILES)('%s contains no numeric literal other than 0 and 1', (file) => {
    expect(findDisallowedNumbers(readFileSync(join(ROOT, file), 'utf8'))).toEqual([]);
  });

  it('flags forbidden literals, including hex and template expressions, but ignores comments and strings', () => {
    const sample = [
      'const a = 0.5; // 99',
      'const b = "42"; /* 7 */',
      'const c = x3 + y_4 + 2 + 1 + 0;',
      'const d = 1e-3 + 30 + .25;',
      'const e = 0x1F + 0b10;',
      'const f = `n=${7} text 8`;',
    ].join('\n');
    expect(findDisallowedNumbers(sample)).toEqual(['0.5', '2', '1e-3', '30', '.25', '0x1F', '0b10', '7']);
  });

  it('keeps landmark indices in landmarks.ts only', () => {
    for (const file of DETECTOR_FILES) {
      expect(readFileSync(join(ROOT, file), 'utf8')).not.toMatch(/landmarks\[\d+\]/);
    }
  });

  it('exempt files export only their known names, so a threshold cannot hide there', () => {
    expect(Object.keys(mathConstants).sort()).toEqual(['HALF', 'TAU', 'midpoint']);
    expect(Object.keys(landmarks).sort()).toEqual([
      'KEY_LANDMARKS', 'LEFT_ANKLE', 'LEFT_HIP', 'LEFT_KNEE', 'LEFT_SHOULDER', 'LEFT_WRIST', 'NOSE',
      'POSE_LANDMARK_COUNT', 'RIGHT_ANKLE', 'RIGHT_HIP', 'RIGHT_KNEE', 'RIGHT_SHOULDER', 'RIGHT_WRIST',
    ]);
  });
});
