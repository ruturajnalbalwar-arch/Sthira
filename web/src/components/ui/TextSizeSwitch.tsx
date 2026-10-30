"use client";

import { useSyncExternalStore } from "react";
import { DEFAULT_TEXT_SIZE, TEXT_SIZES, TEXT_SIZE_STORAGE_KEY, parseTextSize, type TextSize } from "@/lib/ui/textSize";

const CHANGE_EVENT = "sthira:text-size";

function setRootSize(size: TextSize) {
  const root = document.documentElement;
  if (size === DEFAULT_TEXT_SIZE) delete root.dataset.textSize;
  else root.dataset.textSize = size;
}

/** Another tab changed the size: follow it here too. */
function onStorage(event: StorageEvent) {
  if (event.key !== TEXT_SIZE_STORAGE_KEY) return;
  setRootSize(parseTextSize(event.newValue));
  window.dispatchEvent(new Event(CHANGE_EVENT));
}

function subscribe(onChange: () => void): () => void {
  window.addEventListener(CHANGE_EVENT, onChange);
  window.addEventListener("storage", onStorage);
  return () => {
    window.removeEventListener(CHANGE_EVENT, onChange);
    window.removeEventListener("storage", onStorage);
  };
}

const readSize = (): TextSize => parseTextSize(document.documentElement.dataset.textSize ?? null);

function applySize(size: TextSize) {
  setRootSize(size);
  try {
    localStorage.setItem(TEXT_SIZE_STORAGE_KEY, size);
  } catch {
    // Private windows can block storage: the size still applies to this visit.
  }
  window.dispatchEvent(new Event(CHANGE_EVENT));
}

/**
 * Three big buttons (A, A+, A++) that scale the whole page; the choice is remembered on this device.
 * Each accessible name starts with the visible label ("A plus, Large text") so voice control can say what it sees.
 */
export function TextSizeSwitch() {
  const current = useSyncExternalStore(subscribe, readSize, () => DEFAULT_TEXT_SIZE);
  return (
    <div role="group" aria-labelledby="text-size-label" className="flex items-center gap-1">
      <span id="text-size-label" className="mr-1 text-base font-semibold text-muted">
        Text size
      </span>
      {TEXT_SIZES.map((size) => (
        <button
          key={size.value}
          type="button"
          aria-pressed={current === size.value}
          onClick={() => applySize(size.value)}
          className="inline-flex min-h-12 min-w-12 cursor-pointer items-center justify-center rounded-lg border-2 border-border-strong bg-surface px-2 font-bold text-text transition-colors duration-200 hover:bg-bg aria-pressed:border-primary aria-pressed:bg-primary aria-pressed:text-on-primary"
        >
          {size.label}
          <span className="sr-only">, {size.name}</span>
        </button>
      ))}
    </div>
  );
}
