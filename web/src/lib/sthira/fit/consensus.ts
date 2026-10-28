import { compareReps } from '../../label/compare';
import { deriveReps, type RepMark } from '../../label/reducer';
import type { LabelsFile } from '../../label/schema';

/**
 * Which labels the fitter may tune on (docs/pilot/labelling-rules.md): two independent labellers
 * (one team member, one outsider) whose attempts all agree, or an adjudicated consensus file.
 * A single labeller, or an unresolved disagreement, is never used.
 */

/** An adjudicated consensus file is saved as `<fileBase>_labels_consensus.json`. */
export const CONSENSUS_SUFFIX = '_labels_consensus.json';

/** labelling-rules.md "Who labels": Labeller A (team) and Labeller B (outsider). */
const RULE_LABELLERS = ['A', 'B'] as const;

export interface NamedLabels {
  readonly name: string;
  readonly labels: LabelsFile;
}

export type LabelChoice =
  | { readonly ok: true; readonly marks: readonly RepMark[]; readonly source: string }
  | { readonly ok: false; readonly reason: string };

const fail = (reason: string): LabelChoice => ({ ok: false, reason });

function fromTwoLabellers(files: readonly NamedLabels[]): LabelChoice {
  const internal = files.find((f) => !f.labels.labeller.isExternal);
  const external = files.find((f) => f.labels.labeller.isExternal);
  if (internal === undefined || external === undefined) {
    return fail('the two labellers must be one team member and one outsider (labelling-rules.md)');
  }
  const a = deriveReps(internal.labels.events);
  const b = deriveReps(external.labels.events);
  // No pairing time limit: labelling-rules.md compares the attempts in order, without a tolerance.
  const comparison = compareReps(a, b, null);
  if (comparison.disagreements.length > 0) {
    return fail(
      `labellers disagree on ${comparison.disagreements.length} of ${comparison.totalAttempts} attempts: ` +
        `adjudicate and save the result as <fileBase>${CONSENSUS_SUFFIX}`,
    );
  }
  return { ok: true, marks: b, source: 'two labellers agree' };
}

export function chooseLabels(files: readonly NamedLabels[]): LabelChoice {
  const consensus = files.filter((f) => f.name.endsWith(CONSENSUS_SUFFIX));
  if (consensus.length > 1) return fail('more than one consensus file');
  if (consensus.length === 1) {
    return { ok: true, marks: deriveReps(consensus[0].labels.events), source: 'adjudicated consensus' };
  }
  if (files.length === 0) return fail('no label files');
  const codes = [...new Set(files.map((f) => f.labels.labeller.code))];
  if (codes.length === 1) return fail(`only one labeller (${codes[0]}): labelling-rules.md needs two`);
  if (codes.length !== files.length) return fail('a labeller has more than one label file for this recording');
  if (files.length > RULE_LABELLERS.length) return fail('more than two labellers and no consensus file');
  return fromTwoLabellers(files);
}
