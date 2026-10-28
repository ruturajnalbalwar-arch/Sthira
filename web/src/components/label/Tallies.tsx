"use client";

import { formatSeconds, type RepTally, type SpanMark } from "@/lib/label/reducer";
import { EVENT_NAME, type KeyFamily } from "@/lib/label/schema";

interface TalliesProps {
  family: KeyFamily;
  tally: RepTally;
  spans: readonly SpanMark[];
}

const REP_ROWS = [
  ["F", "full"],
  ["H", "half"],
  ["A", "arm-assisted"],
  ["U", "uncertain"],
] as const;

function RepTallies({ tally }: { tally: RepTally }) {
  const total = REP_ROWS.reduce((sum, [, kind]) => sum + tally[kind], 0);
  return (
    <dl className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-2">
      {REP_ROWS.map(([key, kind]) => (
        <div key={kind} className="rounded-lg border border-border bg-surface p-3">
          <dt className="text-sm text-muted">
            <span className="font-mono font-bold text-text">{key}</span> {EVENT_NAME[kind]}
          </dt>
          <dd className="text-2xl font-semibold tabular-nums">{tally[kind]}</dd>
        </div>
      ))}
      <div className="col-span-full text-sm text-muted">
        <dt className="inline">Attempts: </dt>
        <dd className="inline font-semibold text-text">{total}</dd>
      </div>
    </dl>
  );
}

function spanText(span: SpanMark, family: KeyFamily): string {
  if (span.endT === null || span.durationS === null) return `Started at ${formatSeconds(span.startT)}, no end yet`;
  const ending = family === "tug" ? "TUG time" : span.ending === "lost" ? "lost position after" : "held";
  return `${ending} ${formatSeconds(span.durationS)} (${formatSeconds(span.startT)} to ${formatSeconds(span.endT)})`;
}

function SpanTallies({ spans, family }: { spans: readonly SpanMark[]; family: KeyFamily }) {
  const noun = family === "tug" ? "TUG run" : "Balance stage";
  if (spans.length === 0) return <p className="text-muted">No {noun.toLowerCase()} marked yet.</p>;
  return (
    <ol className="flex flex-col gap-2">
      {spans.map((span, i) => (
        <li key={span.startId} className={`rounded-lg border p-3 ${span.endT === null ? "border-warn bg-warn-bg text-warn" : "border-border bg-surface"}`}>
          <span className="font-semibold">
            {noun} {i + 1}:
          </span>{" "}
          {spanText(span, family)}
        </li>
      ))}
    </ol>
  );
}

/** Live counts while labelling. */
export function Tallies({ family, tally, spans }: TalliesProps) {
  return (
    <section aria-labelledby="tallies-heading" className="flex flex-col gap-2">
      <h2 id="tallies-heading" className="text-lg font-semibold">
        Tally
      </h2>
      {family === "rep" ? <RepTallies tally={tally} /> : <SpanTallies spans={spans} family={family} />}
    </section>
  );
}
