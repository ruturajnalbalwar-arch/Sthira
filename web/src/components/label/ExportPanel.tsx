"use client";

import { useId, useState } from "react";
import { Button } from "@/components/ui/Button";
import type { SpanMark } from "@/lib/label/reducer";
import {
  LABELS_SCHEMA,
  formatIssues,
  labelFileName,
  labelsSchema,
  type LabelEvent,
  type Labeller,
  type LabelsFile,
  type RecordingRef,
} from "@/lib/label/schema";
import { DownloadIcon } from "./Icons";

interface ExportPanelProps {
  recordingRef: RecordingRef;
  labeller: Labeller;
  events: readonly LabelEvent[];
  spans: readonly SpanMark[];
  notes: string;
  onNotesChange: (notes: string) => void;
  onExported: (events: readonly LabelEvent[], notes: string) => void;
}

/** Save the file locally through a Blob; nothing is uploaded. */
function download(file: LabelsFile, name: string): void {
  const blob = new Blob([`${JSON.stringify(file, null, 2)}\n`], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = name;
  anchor.click();
  // Revoke after the click has been handled; revoking synchronously can cancel the download in some browsers.
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

export function ExportPanel({ recordingRef, labeller, events, spans, notes, onNotesChange, onExported }: ExportPanelProps) {
  const notesId = useId();
  const [errors, setErrors] = useState<string[]>([]);
  const [saved, setSaved] = useState<string | null>(null);
  const openSpans = spans.filter((s) => s.endT === null).length;
  const name = labelFileName(recordingRef, labeller.code);
  const onExport = () => {
    const parsed = labelsSchema.safeParse({
      schema: LABELS_SCHEMA,
      recording: recordingRef,
      labeller,
      createdAt: new Date().toISOString(),
      notes,
      events,
    });
    if (!parsed.success) {
      setErrors(formatIssues(parsed.error));
      setSaved(null);
      return;
    }
    download(parsed.data, name);
    setErrors([]);
    setSaved(`Saved ${name} with ${events.length} marks.`);
    onExported(events, notes);
  };
  return (
    <section aria-labelledby="export-heading" className="flex flex-col gap-3">
      <h2 id="export-heading" className="text-lg font-semibold">
        3. Export
      </h2>
      <div className="flex flex-col gap-1">
        <label htmlFor={notesId} className="font-semibold">
          Notes for this clip (optional)
        </label>
        <p id={`${notesId}-hint`} className="text-sm text-muted">
          For example &quot;no backrest&quot; for TUG. Never write a name.
        </p>
        <textarea
          id={notesId}
          value={notes}
          maxLength={500}
          onChange={(event) => onNotesChange(event.target.value)}
          aria-describedby={`${notesId}-hint`}
          rows={3}
          className="w-full rounded-lg border border-border-strong bg-surface p-2 text-base"
        />
      </div>
      {openSpans > 0 && (
        <p className="rounded-lg border border-warn bg-warn-bg p-3 text-sm text-warn">
          {openSpans} start mark{openSpans === 1 ? " has" : "s have"} no end yet. You can still export, but check the clip.
        </p>
      )}
      <Button onClick={onExport} className="w-full">
        <DownloadIcon />
        Export labels (.json)
      </Button>
      <p className="break-all text-sm text-muted">File name: {name}</p>
      <p role="status" className="text-sm text-success">
        {saved !== null && errors.length === 0 ? saved : ""}
      </p>
      {errors.length > 0 && (
        <div role="alert" className="rounded-lg border border-danger p-3 text-sm text-danger">
          <p className="font-semibold">The labels could not be exported:</p>
          <ul className="list-disc pl-5">
            {errors.map((message, index) => (
              <li key={`${index}-${message}`}>{message}</li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
