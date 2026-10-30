"use client";

import { useEffect, useRef } from "react";
import { Button } from "@/components/ui/Button";
import type { LiveCountView } from "@/lib/count/liveCount";
import { lookupChairStandNorm, type ChairStandNorms, type NormLookup, type NormSex, type NormsParseResult } from "@/lib/count/norms";
import { chairStandVerdict, type Verdict } from "@/lib/count/verdict";
import type { ChairStandSummary } from "@/lib/sthira/chairStand";
import { RepChips } from "./RepChips";

type Props = {
  countView: LiveCountView;
  norms: NormsParseResult;
  ageBand: string;
  sex: NormSex;
  /** The protocol test length from params.json. */
  durationS: number;
  onRunAgain: () => void;
};

const NO_COUNT: Partial<Record<LiveCountView["kind"], string>> = {
  "not-tuned": "No automatic count in this preview. Use your hand count, and save the recording.",
  "calibration-failed": "No count: calibration did not pass, so the stands were not counted. Save the recording, then run again.",
  "context-failed": "No count: the counter could not start. Save the recording and tell the coordinator.",
  waiting: "No count: the test was stopped before Go.",
};

function normLookup(norms: NormsParseResult, ageBand: string, sex: NormSex): NormLookup {
  if (!norms.ok) return { status: "not-available", reason: "the CDC reference file could not be read." };
  return lookupChairStandNorm(norms.norms, ageBand, sex);
}

function Rejected({ summary }: { summary: ChairStandSummary }) {
  const rows: [string, number][] = [
    ["Half stands", summary.half],
    ["Arms used", summary.armAssisted],
    ["Unclear on camera", summary.uncertain],
  ];
  return (
    <div className="flex flex-col gap-2">
      <h3 className="font-bold">Not counted</h3>
      <dl className="grid grid-cols-3 gap-2">
        {rows.map(([term, value]) => (
          <div key={term} className="flex flex-col rounded-lg border border-border bg-bg px-3 py-2">
            <dt className="text-sm text-muted">{term}</dt>
            <dd className="text-2xl font-bold tabular-nums">{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

function Comparison({ verdict }: { verdict: Verdict }) {
  if (verdict.kind === "not-compared") {
    return (
      <div className="flex flex-col gap-1 rounded-lg border-2 border-warn bg-warn-bg p-3 text-warn">
        <h3 className="font-bold">No CDC comparison for this run</h3>
        <ul className="list-disc pl-5 font-semibold">
          {verdict.reasons.map((reason) => (
            <li key={reason}>{reason}</li>
          ))}
        </ul>
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-1 rounded-lg border-2 border-border-strong bg-bg p-3">
      <h3 className="font-bold">Next to the CDC reference</h3>
      <p className="text-lg font-semibold">{verdict.sentence}</p>
    </div>
  );
}

function Score({ summary, durationS }: { summary: ChairStandSummary; durationS: number }) {
  return (
    <p className="flex items-baseline gap-3">
      <span className="text-7xl font-black leading-none tabular-nums sm:text-8xl">{summary.full}</span>
      <span className="text-2xl font-bold">
        full {summary.full === 1 ? "stand" : "stands"}
        {summary.windowComplete ? ` in ${durationS} s` : ", test stopped early"}
      </span>
    </p>
  );
}

function SourceNote({ norms }: { norms: ChairStandNorms }) {
  const { url, title, year } = norms.source;
  return (
    <p className="text-base text-muted">
      Reference:{" "}
      <a href={url} target="_blank" rel="noopener noreferrer" className="font-semibold text-primary underline">
        {title} ({year})
      </a>
      . {norms.note} Shown for the physiotherapist to interpret; Sthira does not diagnose.
    </p>
  );
}

export function CountResult({ countView, norms, ageBand, sex, durationS, onRunAgain }: Props) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => headingRef.current?.focus(), []);
  const summary = countView.kind === "finished" ? countView.summary : null;
  const verdict = summary === null ? null : chairStandVerdict(summary, normLookup(norms, ageBand, sex));
  return (
    <section aria-labelledby="result-heading" className="flex flex-col gap-4 rounded-xl border-2 border-success bg-surface p-4 sm:p-6">
      <h2 id="result-heading" ref={headingRef} tabIndex={-1} className="text-xl font-bold">
        Result
      </h2>
      {summary === null ? (
        <p className="text-lg font-semibold">{NO_COUNT[countView.kind] ?? NO_COUNT.waiting}</p>
      ) : (
        <>
          <Score summary={summary} durationS={durationS} />
          <RepChips events={summary.events} />
          <Rejected summary={summary} />
          {verdict !== null && <Comparison verdict={verdict} />}
          {norms.ok && <SourceNote norms={norms.norms} />}
        </>
      )}
      <Button variant="secondary" onClick={onRunAgain} className="self-start">
        Run again
      </Button>
    </section>
  );
}
