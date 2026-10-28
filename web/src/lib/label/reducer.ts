import type { RepKind } from "../sthira/chairStand";
import {
  EVENT_NAME,
  KEY_INFO,
  MAX_EVENTS,
  REP_KINDS,
  TRIAL_FAMILY,
  eventFamily,
  isRepKind,
  pairingProblem,
  type EventType,
  type LabelEvent,
  type LabelKey,
  type RecordingRef,
} from "./schema";

export type RejectReason =
  | "wrong-trial"
  | "before-go"
  | "outside-recording"
  | "occupied"
  | "no-open-stage"
  | "pairing"
  | "not-found"
  | "not-finite"
  | "no-landmarks"
  | "too-many";

export type Outcome =
  | { readonly kind: "added"; readonly event: LabelEvent }
  | { readonly kind: "removed"; readonly event: LabelEvent; readonly via: "undo" | "delete" }
  | { readonly kind: "moved"; readonly event: LabelEvent; readonly fromT: number }
  | { readonly kind: "ignored"; readonly reason: "duplicate" | "nothing-to-undo" }
  | { readonly kind: "rejected"; readonly reason: RejectReason; readonly detail: string };

export interface LabelState {
  readonly ref: RecordingRef;
  /** Always sorted by strictly increasing t. */
  readonly events: readonly LabelEvent[];
  /** Ids in the order they were added; undo (Z) removes the last one still present. */
  readonly addOrder: readonly string[];
  readonly nextId: number;
  /** What the last action did, for the on-screen and aria-live message. */
  readonly outcome: Outcome | null;
}

export type LabelAction =
  /** t: videoT snapped to a recorded frame (snapToFrame), or null when no frame is near enough. */
  | { readonly type: "press"; readonly key: LabelKey; readonly t: number | null; readonly videoT: number }
  | { readonly type: "undo" }
  | { readonly type: "delete"; readonly id: string }
  | { readonly type: "nudge"; readonly id: string; readonly deltaS: number };

const makeId = (n: number): string => `e${n}`;

/** Start a session, optionally from saved events (already validated by labelsSchema). */
export function initLabelState(ref: RecordingRef, saved: readonly LabelEvent[] = []): LabelState {
  const events = saved.map((event, i) => ({ ...event, id: makeId(i + 1) }));
  return { ref, events, addOrder: events.map((e) => e.id), nextId: events.length + 1, outcome: null };
}

const reject = (state: LabelState, reason: RejectReason, detail: string): LabelState => ({
  ...state,
  outcome: { kind: "rejected", reason, detail },
});

/** Display format for times (centiseconds). Stored times keep full precision. */
export const formatSeconds = (t: number): string => `${t.toFixed(2)} s`;
const fmt = formatSeconds;

/** Checks shared by press and nudge: the time must be finite, after Go and inside the recording. */
function timeProblem(ref: RecordingRef, t: number): { reason: RejectReason; detail: string } | null {
  if (!Number.isFinite(t)) return { reason: "not-finite", detail: "There is no usable time there; nothing changed." };
  if (t < ref.goT) {
    return { reason: "before-go", detail: `${fmt(t)} is before Go (test start at ${fmt(ref.goT)}). Not labelled.` };
  }
  if (t < ref.firstT || t > ref.lastT) {
    return { reason: "outside-recording", detail: `${fmt(t)} is outside the recorded frames. Not labelled.` };
  }
  return null;
}

const insertSorted = (events: readonly LabelEvent[], event: LabelEvent): LabelEvent[] =>
  [...events, event].sort((a, b) => a.t - b.t);

/** B and T toggle: the mark is an end when the last mark of the same family before t is a start. */
function resolveType(events: readonly LabelEvent[], key: LabelKey, t: number): EventType {
  const repByKey: Partial<Record<LabelKey, EventType>> = { F: "full", H: "half", A: "arm-assisted", U: "uncertain" };
  const fixed = repByKey[key];
  if (fixed !== undefined) return fixed;
  if (key === "L") return "balance-lost";
  const family = KEY_INFO[key].family;
  const before = events.filter((e) => e.t < t && eventFamily(e.type) === family);
  const last = before.length > 0 ? before[before.length - 1].type : null;
  if (family === "balance") return last === "balance-start" ? "balance-end" : "balance-start";
  return last === "tug-start" ? "tug-end" : "tug-start";
}

const PAIRING_DETAIL = "That would break a start/end pair. Delete or move the neighbouring mark first.";

function press(state: LabelState, key: LabelKey, t: number | null, videoT: number): LabelState {
  const { ref, events } = state;
  if (KEY_INFO[key].family !== TRIAL_FAMILY[ref.trial]) {
    return reject(state, "wrong-trial", `${key} is not used in a ${ref.trial} recording.`);
  }
  if (t === null) {
    if (!Number.isFinite(videoT)) return reject(state, "not-finite", "The video is not ready yet: this mark was not added.");
    return reject(state, "no-landmarks", `No landmark data at ${videoT.toFixed(1)} s: this mark was not added.`);
  }
  const problem = timeProblem(ref, t);
  if (problem !== null) return reject(state, problem.reason, problem.detail);
  if (events.length >= MAX_EVENTS) return reject(state, "too-many", `A label file holds at most ${MAX_EVENTS} marks.`);
  const type = resolveType(events, key, t);
  const clash = events.find((e) => e.t === t);
  if (clash !== undefined && clash.type === type) return { ...state, outcome: { kind: "ignored", reason: "duplicate" } };
  if (clash !== undefined) {
    return reject(state, "occupied", `${EVENT_NAME[clash.type]} is already on this frame. Delete it or step a frame.`);
  }
  const last = events.filter((e) => e.t < t && eventFamily(e.type) === "balance").at(-1);
  if (type === "balance-lost" && last?.type !== "balance-start") {
    return reject(state, "no-open-stage", "Lost position needs a stage start (B) before it.");
  }
  const event: LabelEvent = { id: makeId(state.nextId), type, t };
  const next = insertSorted(events, event);
  if (pairingProblem(next) !== null) return reject(state, "pairing", PAIRING_DETAIL);
  return {
    ...state,
    events: next,
    addOrder: [...state.addOrder, event.id],
    nextId: state.nextId + 1,
    outcome: { kind: "added", event },
  };
}

function remove(state: LabelState, id: string, via: "undo" | "delete"): LabelState {
  const event = state.events.find((e) => e.id === id);
  if (event === undefined) return reject(state, "not-found", "That mark no longer exists.");
  const next = state.events.filter((e) => e.id !== id);
  if (pairingProblem(next) !== null) return reject(state, "pairing", PAIRING_DETAIL);
  const addOrder = state.addOrder.filter((x) => x !== id);
  return { ...state, events: next, addOrder, outcome: { kind: "removed", event, via } };
}

function undo(state: LabelState): LabelState {
  const lastId = state.addOrder.at(-1);
  if (lastId === undefined) return { ...state, outcome: { kind: "ignored", reason: "nothing-to-undo" } };
  return remove(state, lastId, "undo");
}

function nudge(state: LabelState, id: string, deltaS: number): LabelState {
  const event = state.events.find((e) => e.id === id);
  if (event === undefined) return reject(state, "not-found", "That mark no longer exists.");
  const t = event.t + deltaS;
  const problem = timeProblem(state.ref, t);
  if (problem !== null) return reject(state, problem.reason, problem.detail);
  const others = state.events.filter((e) => e.id !== id);
  const clash = others.find((e) => e.t === t);
  if (clash !== undefined) return reject(state, "occupied", `${EVENT_NAME[clash.type]} is already on that frame.`);
  const moved: LabelEvent = { ...event, t };
  const next = insertSorted(others, moved);
  if (pairingProblem(next) !== null) return reject(state, "pairing", PAIRING_DETAIL);
  return { ...state, events: next, outcome: { kind: "moved", event: moved, fromT: event.t } };
}

/** Pure, immutable reducer for the labelling session. */
export function labelReducer(state: LabelState, action: LabelAction): LabelState {
  switch (action.type) {
    case "press":
      return press(state, action.key, action.t, action.videoT);
    case "undo":
      return undo(state);
    case "delete":
      return remove(state, action.id, "delete");
    case "nudge":
      return nudge(state, action.id, action.deltaS);
  }
}

/** Short text for the status line and aria-live region, e.g. "Full stand marked at 12.4 s". */
export function outcomeMessage(outcome: Outcome | null): string {
  if (outcome === null) return "";
  switch (outcome.kind) {
    case "added":
      return `${EVENT_NAME[outcome.event.type]} marked at ${outcome.event.t.toFixed(1)} s`;
    case "removed":
      return `${EVENT_NAME[outcome.event.type]} at ${outcome.event.t.toFixed(1)} s removed`;
    case "moved":
      return `${EVENT_NAME[outcome.event.type]} moved to ${outcome.event.t.toFixed(2)} s`;
    case "ignored":
      return outcome.reason === "duplicate" ? "Already marked on this frame" : "Nothing to undo";
    case "rejected":
      return outcome.detail;
  }
}

export interface RepMark {
  readonly id: string;
  readonly kind: RepKind;
  /** Time of the highest point of the attempt (labelling-rules.md: one key per attempt). */
  readonly t: number;
}

/** One attempt per rep key; no pairing because the rules mark each attempt once, at its peak. */
export function deriveReps(events: readonly LabelEvent[]): RepMark[] {
  return events.flatMap((e) => (isRepKind(e.type) ? [{ id: e.id, kind: e.type, t: e.t }] : []));
}

export type RepTally = Readonly<Record<RepKind, number>>;

export function tallyReps(events: readonly LabelEvent[]): RepTally {
  const reps = deriveReps(events);
  const entries = REP_KINDS.map((kind) => [kind, reps.filter((r) => r.kind === kind).length] as const);
  return Object.fromEntries(entries) as Record<RepKind, number>;
}

export interface SpanMark {
  readonly startId: string;
  readonly startT: number;
  readonly endId: string | null;
  readonly endT: number | null;
  /** "end" = B (or T) end, "lost" = L, null = still open. */
  readonly ending: "end" | "lost" | null;
  /** End minus start. The CDC 10 s cap on balance hold time is applied in analysis, not here. */
  readonly durationS: number | null;
}

/** Pair the start/end marks of balance stages or TUG runs, in time order. */
export function deriveSpans(events: readonly LabelEvent[], family: "balance" | "tug"): SpanMark[] {
  const marks = events.filter((e) => eventFamily(e.type) === family);
  return marks.flatMap((start, i) => {
    if (start.type !== "balance-start" && start.type !== "tug-start") return [];
    const end = marks[i + 1];
    const closed = end !== undefined && end.type !== start.type;
    return [
      {
        startId: start.id,
        startT: start.t,
        endId: closed ? end.id : null,
        endT: closed ? end.t : null,
        ending: closed ? (end.type === "balance-lost" ? "lost" : "end") : null,
        durationS: closed ? end.t - start.t : null,
      },
    ];
  });
}

/** Nearest recorded frame time (frame times sorted ascending). Labels sit on the recording's own t axis. */
export function nearestFrameT(frameTs: readonly number[], t: number): number {
  if (frameTs.length === 0) return t;
  let lo = 0;
  let hi = frameTs.length - 1;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (frameTs[mid] < t) lo = mid + 1;
    else hi = mid;
  }
  const prev = lo > 0 ? frameTs[lo - 1] : frameTs[lo];
  return Math.abs(prev - t) <= Math.abs(frameTs[lo] - t) ? prev : frameTs[lo];
}

function frameGaps(frameTs: readonly number[]): { min: number; max: number } {
  let min = Number.POSITIVE_INFINITY;
  let max = 0;
  for (let i = 1; i < frameTs.length; i += 1) {
    const gap = frameTs[i] - frameTs[i - 1];
    min = Math.min(min, gap);
    max = Math.max(max, gap);
  }
  return { min: Number.isFinite(min) ? min : 0, max };
}

/**
 * Nearest recorded frame time, or null when t is outside the recorded frames. The data is taken
 * to reach half of this recording's largest frame gap past the first and last frame (no literal).
 */
export function snapToFrame(frameTs: readonly number[], t: number): number | null {
  if (frameTs.length === 0 || !Number.isFinite(t)) return null;
  const reach = frameGaps(frameTs).max / 2;
  if (t < frameTs[0] - reach || t > frameTs[frameTs.length - 1] + reach) return null;
  return nearestFrameT(frameTs, t);
}

/**
 * The next (+1) or previous (-1) frame relative to the current position, or null at either end.
 * A position within half of the smallest frame gap of a frame counts as on that frame, so float
 * noise after a seek never makes a step stick, and inside a long gap no frame is skipped.
 */
export function stepFrameT(frameTs: readonly number[], currentT: number, direction: -1 | 1): number | null {
  const sameFrame = frameGaps(frameTs).min / 2;
  if (direction === 1) return frameTs.find((ft) => ft > currentT + sameFrame) ?? null;
  return frameTs.findLast((ft) => ft < currentT - sameFrame) ?? null;
}
