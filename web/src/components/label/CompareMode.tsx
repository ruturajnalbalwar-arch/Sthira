"use client";

import { useId, useState } from "react";
import { compareLabelFiles, parseToleranceSeconds, type FileComparison } from "@/lib/label/compare";
import { LABELS_MAX_BYTES } from "@/lib/label/files";
import { labelsSchema, parseJsonWith, type LabelsFile } from "@/lib/label/schema";
import { RepTables, SpanTable } from "./CompareTables";
import { FileField, readCapped } from "./FileField";

interface Loaded {
  file: LabelsFile;
  name: string;
}

function useLabelFile() {
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [errors, setErrors] = useState({ list: [] as string[], total: 0 });
  const onFile = async (file: File) => {
    const read = await readCapped(file, LABELS_MAX_BYTES, "a label file");
    if ("error" in read) {
      setLoaded(null);
      setErrors({ list: [read.error], total: 1 });
      return;
    }
    const parsed = parseJsonWith(labelsSchema, read.text);
    setLoaded(parsed.ok ? { file: parsed.data, name: file.name } : null);
    setErrors(parsed.ok ? { list: [], total: 0 } : { list: parsed.errors, total: parsed.totalErrors });
  };
  return { loaded, errors, onFile };
}

const describe = (l: Loaded | null): string | null =>
  l === null ? null : `Loaded ${l.name}: labeller ${l.file.labeller.code}${l.file.labeller.isExternal ? " (outside)" : ""}, ${l.file.events.length} marks.`;

function Results({ result, a, b }: { result: FileComparison; a: LabelsFile; b: LabelsFile }) {
  if (!result.ok) return <p role="alert" className="rounded-lg border border-danger p-3 text-danger">{result.error}</p>;
  const noun = result.family === "tug" ? "TUG run" : "Balance stage";
  return (
    <section aria-labelledby="compare-results-heading" className="flex flex-col gap-4">
      <h2 id="compare-results-heading" className="text-lg font-semibold">
        {a.recording.fileBase}: {a.labeller.code} (A) vs {b.labeller.code} (B)
      </h2>
      {result.sameLabeller && (
        <p className="rounded-lg border border-warn bg-warn-bg p-3 text-warn">Both files have the same labeller code. Check that you picked two different labellers.</p>
      )}
      {result.family === "rep" ? <RepTables reps={result.reps} /> : <SpanTable rows={result.spans} noun={noun} />}
    </section>
  );
}

/** One sentence for screen readers when a comparison appears or changes. */
function announcement(result: FileComparison): string {
  if (!result.ok) return result.error;
  if (result.family === "rep") {
    const r = result.reps;
    return `Compared: ${r.sameKeyAttempts} of ${r.totalAttempts} attempts have the same key; ${r.disagreements.length} to adjudicate.`;
  }
  const agree = result.spans.filter((s) => s.agree === true).length;
  return `Compared ${result.spans.length} ${result.family === "tug" ? "TUG runs" : "balance stages"}; ${agree} agree within the tolerance.`;
}

/** Second mode: agreement between two labellers, for adjudication (labelling-rules.md). */
export function CompareMode() {
  const a = useLabelFile();
  const b = useLabelFile();
  const [toleranceText, setToleranceText] = useState("");
  const toleranceId = useId();
  const tolerance = parseToleranceSeconds(toleranceText);
  const result =
    a.loaded !== null && b.loaded !== null
      ? compareLabelFiles(a.loaded.file, b.loaded.file, { maxPairGapS: null, spanToleranceS: tolerance.value })
      : null;
  return (
    <div className="flex flex-col gap-6">
      <section aria-labelledby="compare-load-heading" className="flex max-w-2xl flex-col gap-4">
        <h2 id="compare-load-heading" className="text-lg font-semibold">Compare two label files</h2>
        <p className="text-muted">Pick the two labellers&apos; exports for the same recording. Raw agreement is shown before any adjudication.</p>
        <FileField label="Labels from labeller A" hint="Usually the student labeller's file." accept="application/json,.json" errors={a.errors.list} totalErrors={a.errors.total} loaded={describe(a.loaded)} onFile={(f) => void a.onFile(f)} />
        <FileField label="Labels from labeller B" hint="Usually the outside labeller's file." accept="application/json,.json" errors={b.errors.list} totalErrors={b.errors.total} loaded={describe(b.loaded)} onFile={(f) => void b.onFile(f)} />
        <div className="flex flex-col gap-1">
          <label htmlFor={toleranceId} className="font-semibold">Balance and TUG agreement tolerance (seconds, optional)</label>
          <p id={`${toleranceId}-hint`} className="text-sm text-muted">
            Use only the number written in labelling-rules.md before anyone looked at labels. Leave empty if it is not decided: differences are still shown.
          </p>
          <input
            id={toleranceId}
            inputMode="decimal"
            value={toleranceText}
            onChange={(event) => setToleranceText(event.target.value)}
            aria-invalid={tolerance.error !== null}
            aria-describedby={tolerance.error === null ? `${toleranceId}-hint` : `${toleranceId}-hint ${toleranceId}-error`}
            className="min-h-11 w-40 rounded-lg border border-border-strong bg-surface px-3 font-mono text-base"
          />
          {tolerance.error !== null && <p id={`${toleranceId}-error`} className="text-sm text-danger">{tolerance.error}</p>}
        </div>
      </section>
      <p role="status" className="sr-only">
        {result === null ? "" : announcement(result)}
      </p>
      {result !== null && a.loaded !== null && b.loaded !== null && <Results result={result} a={a.loaded.file} b={b.loaded.file} />}
    </div>
  );
}
