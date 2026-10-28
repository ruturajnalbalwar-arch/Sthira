"use client";

import { useState } from "react";
import { CompareMode } from "./CompareMode";
import { LabelMode } from "./LabelMode";

type Mode = "label" | "compare";

const MODES: ReadonlyArray<readonly [Mode, string]> = [
  ["label", "Label a recording"],
  ["compare", "Compare two label files"],
];

/** Both modes stay mounted so switching never loses unsaved marks; shortcuts run only in label mode. */
export function LabelApp() {
  const [mode, setMode] = useState<Mode>("label");
  return (
    <div className="flex flex-col gap-6">
      <nav aria-label="Mode">
        <ul className="flex flex-wrap gap-2">
          {MODES.map(([value, text]) => (
            <li key={value}>
              <button
                type="button"
                aria-pressed={mode === value}
                onClick={() => setMode(value)}
                className={`min-h-11 cursor-pointer rounded-lg border px-4 font-semibold transition-colors duration-150 ${
                  mode === value ? "border-primary bg-primary text-on-primary" : "border-border-strong bg-surface text-text hover:bg-bg"
                }`}
              >
                {text}
              </button>
            </li>
          ))}
        </ul>
      </nav>
      <div hidden={mode !== "label"}>
        <LabelMode active={mode === "label"} />
      </div>
      <div hidden={mode !== "compare"}>
        <CompareMode />
      </div>
    </div>
  );
}
