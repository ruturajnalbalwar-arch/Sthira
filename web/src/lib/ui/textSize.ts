/** Text-size steps offered in the header. The CSS for each lives in globals.css (html[data-text-size]). */
export const TEXT_SIZES = [
  { value: "normal", label: "A", name: "Normal text" },
  { value: "large", label: "A+", name: "Large text" },
  { value: "largest", label: "A++", name: "Largest text" },
] as const;

export type TextSize = (typeof TEXT_SIZES)[number]["value"];

export const DEFAULT_TEXT_SIZE: TextSize = "normal";

/** localStorage key. Only this choice is stored; nothing about the person or the test. */
export const TEXT_SIZE_STORAGE_KEY = "sthira.textSize";

const isTextSize = (value: string | null): value is TextSize => TEXT_SIZES.some((size) => size.value === value);

/** Reads a stored value, which the browser (or anyone with devtools) can change, so it is checked. */
export function parseTextSize(stored: string | null): TextSize {
  return isTextSize(stored) ? stored : DEFAULT_TEXT_SIZE;
}

/**
 * Runs before the page paints so a saved size does not flash at the default first. Built only
 * from the constants above, never from stored or user text; storage can throw in private windows.
 * `> 0` skips index 0, the default, which needs no attribute.
 */
export function textSizeBootScript(): string {
  const allowed = JSON.stringify(TEXT_SIZES.map((size) => size.value));
  const key = JSON.stringify(TEXT_SIZE_STORAGE_KEY);
  return `try{var s=localStorage.getItem(${key});if(${allowed}.indexOf(s)>0)document.documentElement.dataset.textSize=s}catch(e){}`;
}
