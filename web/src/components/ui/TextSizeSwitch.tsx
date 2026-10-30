"use client";

import { useSyncExternalStore } from "react";
import { DEFAULT_TEXT_SIZE, TEXT_SIZES, TEXT_SIZE_STORAGE_KEY, parseTextSize, type TextSize } from "@/lib/ui/textSize";

const CHANGE_EVENT = "sthira:text-size";

function subscribe(onChange: () => void): () => void {
  window.addEventListener(CHANGE_EVENT, onChange);
  return () => window.removeEventListener(CHANGE_EVENT, onChange);
}

const readSize = (): TextSize => parseTextSize(document.documentElement.dataset.textSize ?? null);

function applySize(size: TextSize) {
  const root = document.documentElement;
  if (size === DEFAULT_TEXT_SIZE) delete root.dataset.textSize;
  else root.dataset.textSize = size;
  try {
    localStorage.setItem(TEXT_SIZE_STORAGE_KEY, size);
  } catch {
    // Private windows can block storage: the size still applies to this visit.
  }
  window.dispatchEvent(new Event(CHANGE_EVENT));
}

/** Three big buttons (A, A+, A++) that scale the whole page; the choice is remembered on this device. */
export function TextSizeSwitch() {
  const current = useSyncExternalStore(subscribe, readSize, () => DEFAULT_TEXT_SIZE);
  return (
    <div role="group" aria-label="Text size" className="flex items-center gap-1">
      <span className="mr-1 hidden text-sm font-semibold text-muted sm:inline">Text size</span>
      {TEXT_SIZES.map((size) => (
        <button
          key={size.value}
          type="button"
          aria-label={size.name}
          aria-pressed={current === size.value}
          onClick={() => applySize(size.value)}
          className="inline-flex min-h-12 min-w-12 cursor-pointer items-center justify-center rounded-lg border-2 border-border-strong bg-surface px-2 font-bold text-text transition-colors duration-200 hover:bg-bg aria-pressed:border-primary aria-pressed:bg-primary aria-pressed:text-on-primary"
        >
          {size.label}
        </button>
      ))}
    </div>
  );
}
