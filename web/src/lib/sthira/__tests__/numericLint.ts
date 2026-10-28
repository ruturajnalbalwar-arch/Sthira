/** Shared by the engine and count-screen numeric-literal lints (a test helper, not a test file). */
const ALLOWED = new Set(['0', '1']);

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

/** Numeric literals other than 0 and 1, ignoring comments and strings but not template expressions. */
export function findDisallowedNumbers(source: string): string[] {
  const tokens = stripCommentsAndStrings(source).match(NUMBER) ?? [];
  return tokens.filter((token) => !ALLOWED.has(token));
}
