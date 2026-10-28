import { describe, expect, it } from 'vitest';
import type { LabelsFile } from '../../../label/schema';
import { CONSENSUS_SUFFIX, chooseLabels } from '../consensus';
import { loadPilot } from '../data';
import { L1, L2, labelEvents, pilotFiles, repTrain, synthLabels, synthRecording } from '../../__tests__/syntheticPilot';

const PROTOCOL = { chairStandDurationS: 30, calibrationSitS: 5, calibrationStandS: 5 };
const reps = repTrain(4, 1, 6, { 1: { peak: 0.5 } });

function pairFiles(participant: string, extra: Partial<Parameters<typeof synthRecording>[0]> = {}) {
  const recording = synthRecording({ participant, reps, ...extra });
  const events = labelEvents(reps);
  return { recording, files: pilotFiles(recording, [synthLabels(recording, events, L1), synthLabels(recording, events, L2)]) };
}

describe('loadPilot', () => {
  it('pairs a recording with two agreeing labellers', () => {
    const { files } = pairFiles('P01');
    const data = loadPilot(files, PROTOCOL);
    expect(data.trials).toHaveLength(1);
    expect(data.trials[0].marks.map((m) => m.kind)).toEqual(['full', 'half', 'full', 'full']);
    expect(data.trials[0].labelSource).toBe('two labellers agree');
    expect(data.people).toEqual([{ code: 'P01', clothing: ['track-pants'] }]);
  });

  it('excludes recordings with capture issues, saying whether they were fatal', () => {
    const fatal = pairFiles('P01', { issues: ['camera-ended'] }).files;
    const flagged = pairFiles('P02', { issues: ['tab-hidden'] }).files;
    const data = loadPilot([...fatal, ...flagged], PROTOCOL);
    expect(data.trials).toHaveLength(0);
    expect(data.exclusions.map((e) => e.reason)).toEqual(['fatal capture issue: camera-ended', 'capture issue: tab-hidden']);
  });

  it('refuses a recording with only one labeller', () => {
    const recording = synthRecording({ participant: 'P01', reps });
    const data = loadPilot(pilotFiles(recording, [synthLabels(recording, labelEvents(reps), L1)]), PROTOCOL);
    expect(data.trials).toHaveLength(0);
    expect(data.exclusions[0].reason).toMatch(/only one labeller/);
  });

  it('drops labels after the timed window and skips balance trials', () => {
    const late = repTrain(2, 1, 28);
    const recording = synthRecording({ participant: 'P01', reps: late, testS: 31 });
    const balance = synthRecording({ participant: 'P01', reps: [], trial: 'balance-tandem' });
    const events = labelEvents(late);
    const files = [
      ...pilotFiles(recording, [synthLabels(recording, events, L1), synthLabels(recording, events, L2)]),
      ...pilotFiles(balance, []),
    ];
    const data = loadPilot(files, PROTOCOL);
    expect(data.trials[0].marks).toHaveLength(1);
    expect(data.exclusions.map((e) => e.reason)).toEqual([expect.stringMatching(/balance or TUG/)]);
  });

  it('reports invalid, unknown, orphan and duplicate files without throwing', () => {
    const { files, recording } = pairFiles('P01');
    const orphanRecording = synthRecording({ participant: 'P02', reps });
    const orphan = synthLabels(orphanRecording, labelEvents(reps), L1);
    const data = loadPilot(
      [
        ...files,
        { name: 'copy.json', text: JSON.stringify(recording) },
        { name: 'bad.json', text: '{not json' },
        { name: 'raw.json', text: JSON.stringify({ schema: 'sthira.recording.raw-unvalidated.v1' }) },
        { name: 'broken.json', text: JSON.stringify({ schema: 'sthira.recording.v1' }) },
        { name: 'broken_labels.json', text: JSON.stringify({ schema: 'sthira.labels.v1' }) },
        { name: 'orphan_labels_L1.json', text: JSON.stringify(orphan) },
      ],
      PROTOCOL,
    );
    expect(data.trials).toHaveLength(0);
    const reasons = data.exclusions.map((e) => `${e.file}: ${e.reason}`).join('\n');
    expect(reasons).toMatch(/bad.json: not JSON/);
    expect(reasons).toMatch(/raw.json: unknown schema/);
    expect(reasons).toMatch(/broken.json: invalid recording/);
    expect(reasons).toMatch(/broken_labels.json: invalid label file/);
    expect(reasons).toMatch(/orphan_labels_L1.json: label file matches no recording/);
    expect(reasons).toMatch(/duplicate recording/);
  });
});

describe('chooseLabels', () => {
  const recording = synthRecording({ participant: 'P01', reps });
  const events = labelEvents(reps);
  const file = (labels: LabelsFile, name = `x_labels_${labels.labeller.code}.json`) => ({ name, labels });
  const a = synthLabels(recording, events, L1);
  const b = synthLabels(recording, events, L2);
  const changed = synthLabels(recording, events.map((e, i) => (i === 0 ? { ...e, type: 'half' as const } : e)), L2);

  it('flags a disagreement for adjudication', () => {
    expect(chooseLabels([file(a), file(changed)])).toMatchObject({ ok: false, reason: expect.stringMatching(/disagree on 1 of 4/) });
  });

  it('uses an adjudicated consensus file over the individual labellers', () => {
    const consensus = file(changed, `x${CONSENSUS_SUFFIX}`);
    const choice = chooseLabels([file(a), file(b), consensus]);
    expect(choice.ok && choice.marks[0].kind).toBe('half');
    expect(chooseLabels([consensus, consensus])).toMatchObject({ ok: false });
  });

  it('needs one team member and one outsider', () => {
    const otherInternal = synthLabels(recording, events, { code: 'L3', isExternal: false });
    expect(chooseLabels([file(a), file(otherInternal)])).toMatchObject({ ok: false, reason: expect.stringMatching(/outsider/) });
  });

  it('refuses no files, duplicate labellers and more than two labellers', () => {
    const third = synthLabels(recording, events, { code: 'L3', isExternal: true });
    expect(chooseLabels([])).toMatchObject({ ok: false, reason: 'no label files' });
    expect(chooseLabels([file(a), file(a, 'y'), file(b)])).toMatchObject({ ok: false, reason: expect.stringMatching(/more than one label file/) });
    expect(chooseLabels([file(a), file(b), file(third)])).toMatchObject({ ok: false, reason: expect.stringMatching(/more than two/) });
  });
});
