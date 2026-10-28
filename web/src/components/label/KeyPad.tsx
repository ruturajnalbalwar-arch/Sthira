"use client";

import { outcomeMessage, type Outcome } from "@/lib/label/reducer";
import { KEY_INFO, LABEL_KEYS, TRIAL_FAMILY, type LabelKey, type Trial } from "@/lib/label/schema";
import { UndoIcon } from "./Icons";

interface KeyPadProps {
  trial: Trial;
  outcome: Outcome | null;
  /** Changes on every action or notice so a repeated message is announced again. */
  seq: string;
  /** Video problems and playback notices ("First recorded frame") win over the last action. */
  notice: string | null;
  onPress: (key: LabelKey) => void;
  onUndo: () => void;
}

const SHORT_NAME: Readonly<Record<LabelKey, string>> = {
  F: "Full stand",
  H: "Half stand",
  A: "Arm-assisted",
  U: "Uncertain",
  B: "Stage start / end",
  L: "Lost position",
  T: "TUG start / end",
};

const KEY_BUTTON =
  "inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-lg border border-border-strong bg-surface px-3 text-left text-text transition-colors duration-150 hover:bg-bg";

function statusTone(outcome: Outcome | null): string {
  if (outcome?.kind === "rejected") return "border-warn bg-warn-bg text-warn";
  if (outcome?.kind === "added") return "border-success bg-success-bg text-success";
  return "border-border bg-surface text-text";
}

/** On-screen label keys (mouse / touch) and the live status line announced to screen readers. */
export function KeyPad({ trial, outcome, seq, notice, onPress, onUndo }: KeyPadProps) {
  const keys = LABEL_KEYS.filter((key) => KEY_INFO[key].family === TRIAL_FAMILY[trial]);
  const message = notice ?? (outcomeMessage(outcome) || "Ready. Press a label key at each moment.");
  return (
    <section aria-label="Label keys" className="flex flex-col gap-2">
      <div className="flex flex-wrap gap-2">
        {keys.map((key) => (
          <button key={key} type="button" className={KEY_BUTTON} onClick={() => onPress(key)} aria-keyshortcuts={key} title={KEY_INFO[key].meaning}>
            <kbd className="font-mono text-lg font-bold">{key}</kbd>
            <span className="text-sm">{SHORT_NAME[key]}</span>
          </button>
        ))}
        <button type="button" className={KEY_BUTTON} onClick={onUndo} aria-keyshortcuts="Z">
          <UndoIcon />
          <kbd className="font-mono text-lg font-bold">Z</kbd>
          <span className="text-sm">Undo last</span>
        </button>
      </div>
      <p role="status" aria-live="polite" className={`min-h-11 rounded-lg border px-3 py-2 ${notice === null ? statusTone(outcome) : statusTone(null)}`}>
        <span key={seq}>{message}</span>
      </p>
    </section>
  );
}
