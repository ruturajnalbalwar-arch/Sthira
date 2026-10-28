/** Named math constants and helpers. Exempt from the numeric-literal lint. */
export const TAU = Math.PI * 2;
export const HALF = 0.5;

export const midpoint = (a: number, b: number): number => a + (b - a) * HALF;
