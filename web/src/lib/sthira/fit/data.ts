import { LABELS_SCHEMA, TRIAL_FAMILY, labelsSchema, parseJsonWith, recordingRef, sameRecording, type LabelsFile, type RecordingRef } from '../../label/schema';
import type { RepMark } from '../../label/reducer';
import { isFatalIssue } from '../../record/captureIssues';
import { RECORDING_SCHEMA, recordingSchema, type Recording, type SessionMeta } from '../../record/schema';
import type { ProtocolConstants } from '../params/protocol';
import { chooseLabels, type NamedLabels } from './consensus';

/** A file as read from pilot-data/: its name (relative path) and text. */
export interface NamedText {
  readonly name: string;
  readonly text: string;
}

/** One chair-stand trial the fitter may score: a clean recording plus usable labels. */
export interface Trial {
  readonly fileBase: string;
  readonly participant: string;
  readonly clothing: SessionMeta['clothing'];
  readonly trial: SessionMeta['trial'];
  readonly recording: Recording;
  /** Labelled attempts inside the CDC timed window (Go to Go + chairStandDurationS). */
  readonly marks: readonly RepMark[];
  readonly labelSource: string;
}

export interface Exclusion {
  readonly file: string;
  readonly reason: string;
}

export interface Person {
  readonly code: string;
  readonly clothing: readonly SessionMeta['clothing'][];
}

export interface PilotData {
  readonly trials: readonly Trial[];
  readonly exclusions: readonly Exclusion[];
  /** Everyone with a valid chair-stand recording, labelled or not (for the split). */
  readonly people: readonly Person[];
}

interface ParsedRecording {
  readonly name: string;
  readonly recording: Recording;
  readonly ref: RecordingRef;
}

type Parsed =
  | { readonly kind: 'recording'; readonly item: ParsedRecording }
  | { readonly kind: 'labels'; readonly item: NamedLabels }
  | { readonly kind: 'excluded'; readonly item: Exclusion };

function schemaTag(text: string): string | null {
  try {
    const json: unknown = JSON.parse(text);
    const tag = (json as { schema?: unknown } | null)?.schema;
    return typeof tag === 'string' ? tag : null;
  } catch {
    return null;
  }
}

function parseFile(file: NamedText): Parsed {
  const excluded = (reason: string): Parsed => ({ kind: 'excluded', item: { file: file.name, reason } });
  const tag = schemaTag(file.text);
  if (tag === RECORDING_SCHEMA) {
    const parsed = parseJsonWith(recordingSchema, file.text);
    if (!parsed.ok) return excluded(`invalid recording: ${parsed.errors.join('; ')}`);
    const ref = recordingRef(parsed.data);
    if (!ref.ok) return excluded(ref.error);
    return { kind: 'recording', item: { name: file.name, recording: parsed.data, ref: ref.ref } };
  }
  if (tag === LABELS_SCHEMA) {
    const parsed = parseJsonWith(labelsSchema, file.text);
    if (!parsed.ok) return excluded(`invalid label file: ${parsed.errors.join('; ')}`);
    return { kind: 'labels', item: { name: file.name, labels: parsed.data as LabelsFile } };
  }
  return excluded(tag === null ? 'not JSON with a schema tag' : `unknown schema ${tag}`);
}

const isChairStand = (r: ParsedRecording): boolean => TRIAL_FAMILY[r.recording.meta.trial] === 'rep';

function captureProblem(recording: Recording): string | null {
  const issues = recording.capture.issues;
  if (issues.length === 0) return null;
  const kind = issues.some(isFatalIssue) ? 'fatal capture issue' : 'capture issue';
  // record/schema.ts: a recording with any issue is kept but excluded from tuning and evaluation.
  return `${kind}: ${issues.join(', ')}`;
}

function toTrial(r: ParsedRecording, labels: readonly NamedLabels[], protocol: ProtocolConstants): Trial | Exclusion {
  const problem = captureProblem(r.recording);
  if (problem !== null) return { file: r.name, reason: problem };
  const choice = chooseLabels(labels);
  if (!choice.ok) return { file: r.name, reason: choice.reason };
  const windowEnd = r.ref.goT + protocol.chairStandDurationS;
  const { meta } = r.recording;
  return {
    fileBase: r.ref.fileBase,
    participant: meta.participantCode,
    clothing: meta.clothing,
    trial: meta.trial,
    recording: r.recording,
    marks: choice.marks.filter((m) => m.t <= windowEnd),
    labelSource: choice.source,
  };
}

function peopleOf(recordings: readonly ParsedRecording[]): Person[] {
  const byCode = new Map<string, Set<SessionMeta['clothing']>>();
  for (const { recording } of recordings) {
    const code = recording.meta.participantCode;
    byCode.set(code, new Set([...(byCode.get(code) ?? []), recording.meta.clothing]));
  }
  return [...byCode.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([code, clothing]) => ({ code, clothing: [...clothing].sort() }));
}

/** Validate every file, pair labels with recordings by fingerprint, and keep only usable chair-stand trials. */
export function loadPilot(files: readonly NamedText[], protocol: ProtocolConstants): PilotData {
  const parsed = files.map(parseFile);
  const recordings = parsed.flatMap((p) => (p.kind === 'recording' ? [p.item] : []));
  const labels = parsed.flatMap((p) => (p.kind === 'labels' ? [p.item] : []));
  const exclusions: Exclusion[] = parsed.flatMap((p) => (p.kind === 'excluded' ? [p.item] : []));

  const bases = recordings.map((r) => r.ref.fileBase);
  const chairStand = recordings.filter(isChairStand);
  exclusions.push(
    ...recordings.filter((r) => !isChairStand(r)).map((r) => ({ file: r.name, reason: 'balance or TUG trial: not used by the chair-stand fitter' })),
    ...labels
      .filter((l) => !recordings.some((r) => sameRecording(r.ref, l.labels.recording)))
      .map((l) => ({ file: l.name, reason: 'label file matches no recording (fingerprint differs)' })),
  );
  const trials: Trial[] = [];
  for (const r of chairStand) {
    if (bases.indexOf(r.ref.fileBase) !== bases.lastIndexOf(r.ref.fileBase)) {
      exclusions.push({ file: r.name, reason: `duplicate recording ${r.ref.fileBase}` });
      continue;
    }
    const outcome = toTrial(r, labels.filter((l) => sameRecording(r.ref, l.labels.recording)), protocol);
    if ('reason' in outcome) exclusions.push(outcome);
    else trials.push(outcome);
  }
  return { trials, exclusions, people: peopleOf(chairStand) };
}
