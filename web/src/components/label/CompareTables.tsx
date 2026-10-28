import type { RepComparison, RepRow, RowResult, SpanRow } from "@/lib/label/compare";
import { formatSeconds, type RepMark, type SpanMark } from "@/lib/label/reducer";
import { EVENT_KEY, EVENT_NAME, REP_KINDS } from "@/lib/label/schema";

const RESULT_TEXT: Readonly<Record<RowResult, string>> = {
  agree: "Agree",
  "different-key": "Different key",
  "only-a": "Only in A",
  "only-b": "Only in B",
};

const TH = "border-b border-border-strong px-2 py-2 text-left font-semibold";
const TD = "border-b border-border px-2 py-2 align-top";
const signed = (s: number | null): string => (s === null ? "—" : `${s >= 0 ? "+" : "−"}${formatSeconds(Math.abs(s))}`);
const markText = (m: RepMark | null): string => (m === null ? "—" : `${EVENT_KEY[m.kind]} ${EVENT_NAME[m.kind]}`);
const yesNo = (same: boolean): string => (same ? "Yes" : "No");

function CountTable({ reps }: { reps: RepComparison }) {
  return (
    <table className="w-full text-sm">
      <caption className="mb-1 text-left font-semibold">Counts per key</caption>
      <thead>
        <tr><th scope="col" className={TH}>Key</th><th scope="col" className={TH}>A</th><th scope="col" className={TH}>B</th><th scope="col" className={TH}>Same count</th></tr>
      </thead>
      <tbody>
        {REP_KINDS.map((kind) => (
          <tr key={kind}>
            <th scope="row" className={`${TD} text-left font-normal`}>{EVENT_KEY[kind]} {EVENT_NAME[kind]}</th>
            <td className={`${TD} tabular-nums`}>{reps.countsA[kind]}</td>
            <td className={`${TD} tabular-nums`}>{reps.countsB[kind]}</td>
            <td className={`${TD} ${reps.countMatch[kind] ? "" : "font-semibold text-danger"}`}>{yesNo(reps.countMatch[kind])}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function AttemptRow({ row, index }: { row: RepRow; index: number }) {
  const disagree = row.result !== "agree";
  return (
    <tr className={disagree ? "bg-warn-bg text-warn" : ""}>
      <th scope="row" className={`${TD} text-left font-normal tabular-nums`}>{index + 1}</th>
      <td className={`${TD} font-mono`}>{row.a === null ? "—" : formatSeconds(row.a.t)}</td>
      <td className={TD}>{markText(row.a)}</td>
      <td className={`${TD} font-mono`}>{row.b === null ? "—" : formatSeconds(row.b.t)}</td>
      <td className={TD}>{markText(row.b)}</td>
      <td className={`${TD} font-mono`}>{signed(row.timeDiffS)}</td>
      <td className={`${TD} ${disagree ? "font-semibold" : ""}`}>{RESULT_TEXT[row.result]}</td>
    </tr>
  );
}

/** Chair-stand comparison: headline numbers from the rules, counts, then every attempt for adjudication. */
export function RepTables({ reps }: { reps: RepComparison }) {
  const percent = reps.totalAttempts === 0 ? null : Math.round((reps.sameKeyAttempts / reps.totalAttempts) * 100);
  return (
    <div className="flex flex-col gap-4">
      <dl className="grid gap-2 sm:grid-cols-3">
        <div className="rounded-lg border border-border bg-surface p-3"><dt className="text-sm text-muted">Same full-stand count</dt><dd className="text-xl font-semibold">{yesNo(reps.fullCountMatch)}</dd></div>
        <div className="rounded-lg border border-border bg-surface p-3"><dt className="text-sm text-muted">Same number of attempts</dt><dd className="text-xl font-semibold">{yesNo(reps.attemptCountMatch)}</dd></div>
        <div className="rounded-lg border border-border bg-surface p-3"><dt className="text-sm text-muted">Attempts with the same key</dt><dd className="text-xl font-semibold">{reps.sameKeyAttempts} of {reps.totalAttempts}{percent === null ? "" : ` (${percent}%)`}</dd></div>
      </dl>
      <CountTable reps={reps} />
      <div className="overflow-x-auto">
        <table className="w-full min-w-[40rem] text-sm">
          <caption className="mb-1 text-left font-semibold">Attempts ({reps.disagreements.length} to adjudicate)</caption>
          <thead>
            <tr>
              {["#", "Time A", "Key A", "Time B", "Key B", "B − A", "Result"].map((h) => <th key={h} scope="col" className={TH}>{h}</th>)}
            </tr>
          </thead>
          <tbody>
            {reps.rows.map((row, i) => <AttemptRow key={`${row.a?.id ?? "-"}|${row.b?.id ?? "-"}`} row={row} index={i} />)}
          </tbody>
        </table>
      </div>
      <p className="text-sm text-muted">
        Attempts are paired when each mark is the other labeller&apos;s nearest mark in time. No time limit is applied
        yet, so check the B − A column: a large gap may be two different attempts.
      </p>
    </div>
  );
}

const spanText = (s: SpanMark | null): string => {
  if (s === null) return "—";
  if (s.endT === null) return `${formatSeconds(s.startT)} → open`;
  return `${formatSeconds(s.startT)} → ${formatSeconds(s.endT)} (${s.ending === "lost" ? "lost" : "end"})`;
};

/** Balance stages or TUG runs, in order, with time differences and the tolerance verdict when one is set. */
export function SpanTable({ rows, noun }: { rows: readonly SpanRow[]; noun: string }) {
  if (rows.length === 0) return <p className="text-muted">Neither file has a {noun.toLowerCase()}.</p>;
  return (
    <div className="flex flex-col gap-2">
    <p className="text-sm text-muted">
      {noun}s are paired in order: A&apos;s first with B&apos;s first, second with second. If one labeller marked an
      extra {noun.toLowerCase()}, the rows after it are shifted; check the start times.
    </p>
    <div className="overflow-x-auto">
      <table className="w-full min-w-[40rem] text-sm">
        <caption className="mb-1 text-left font-semibold">{noun}s</caption>
        <thead>
          <tr>{["#", "A", "B", "Start B − A", "End B − A", "Duration B − A", "Same ending", "Agree"].map((h) => <th key={h} scope="col" className={TH}>{h}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i} className={row.agree === false || !row.sameEnding ? "bg-warn-bg text-warn" : ""}>
              <th scope="row" className={`${TD} text-left font-normal`}>{i + 1}</th>
              <td className={`${TD} font-mono`}>{spanText(row.a)}</td>
              <td className={`${TD} font-mono`}>{spanText(row.b)}</td>
              <td className={`${TD} font-mono`}>{signed(row.startDiffS)}</td>
              <td className={`${TD} font-mono`}>{signed(row.endDiffS)}</td>
              <td className={`${TD} font-mono`}>{signed(row.durationDiffS)}</td>
              <td className={TD}>{yesNo(row.sameEnding)}</td>
              <td className={TD}>{row.agree === null ? "Tolerance not set" : yesNo(row.agree)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
    </div>
  );
}
