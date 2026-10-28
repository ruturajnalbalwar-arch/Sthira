"use client";

import { useEffect, useRef } from "react";
import { LABEL_KEYS, type LabelKey } from "@/lib/label/schema";

export interface KeyboardCommands {
  togglePlay: () => void;
  stepFrame: (direction: -1 | 1) => void;
  stepSecond: (direction: -1 | 1) => void;
  changeSpeed: (direction: -1 | 1) => void;
  press: (key: LabelKey) => void;
  undo: () => void;
}

/** Input types where letters, space and arrows are not typed text, so shortcuts stay on. */
const NON_TEXT_INPUTS = new Set(["range", "checkbox", "radio", "button", "submit", "reset", "file"]);

/** True while the person is typing (labeller code, notes, ...): shortcuts must not fire. */
function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  if (target.isContentEditable || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) {
    return true;
  }
  return target instanceof HTMLInputElement && !NON_TEXT_INPUTS.has(target.type);
}

const activatesNatively = (target: EventTarget | null): boolean =>
  target instanceof HTMLElement && target.closest("button, a, summary, input") !== null;

const asLabelKey = (key: string): LabelKey | null => {
  const upper = key.toUpperCase();
  return (LABEL_KEYS as readonly string[]).includes(upper) ? (upper as LabelKey) : null;
};

function handle(event: KeyboardEvent, commands: KeyboardCommands): boolean {
  const { key, target } = event;
  if (key === " ") {
    if (activatesNatively(target)) return false;
    // A held Space would toggle play/pause over and over; swallow the repeats.
    if (!event.repeat) commands.togglePlay();
    return true;
  }
  if (key === "ArrowLeft" || key === "ArrowRight") {
    // Also on the seek slider: arrows step one recorded frame there too, instead of the native step.
    const direction = key === "ArrowLeft" ? -1 : 1;
    if (event.shiftKey) commands.stepSecond(direction);
    else commands.stepFrame(direction);
    return true;
  }
  if (key === "," || key === ".") {
    commands.changeSpeed(key === "," ? -1 : 1);
    return true;
  }
  if (event.repeat) return false;
  if (key.toUpperCase() === "Z") {
    commands.undo();
    return true;
  }
  const labelKey = asLabelKey(key);
  if (labelKey === null) return false;
  commands.press(labelKey);
  return true;
}

/** Window-level shortcuts for the labelling workspace. Held keys (auto-repeat) never add marks twice. */
export function useLabelKeyboard(active: boolean, commands: KeyboardCommands): void {
  const latest = useRef(commands);
  useEffect(() => {
    latest.current = commands;
  });
  useEffect(() => {
    if (!active) return undefined;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.metaKey || event.altKey || isTypingTarget(event.target)) return;
      if (handle(event, latest.current)) event.preventDefault();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [active]);
}
