"use client";

import { useId, type ChangeEvent } from "react";
import { fileSizeProblem } from "@/lib/label/files";

const UNREADABLE = "The browser could not read this file. Pick it again.";

/** Size check first (never read a huge wrong file into memory), then the text. */
export async function readCapped(file: File, maxBytes: number, kind: string): Promise<{ text: string } | { error: string }> {
  const tooLarge = fileSizeProblem(file, maxBytes, kind);
  if (tooLarge !== null) return { error: tooLarge };
  try {
    return { text: await file.text() };
  } catch {
    return { error: UNREADABLE };
  }
}


interface FileFieldProps {
  label: string;
  hint: string;
  accept: string;
  /** Already capped (parseJsonWith formats only the first few). */
  errors: readonly string[];
  /** How many problems the file has in total, when more than `errors` shows. */
  totalErrors?: number;
  /** Short confirmation shown after a successful load. */
  loaded: string | null;
  disabled?: boolean;
  onFile: (file: File) => void;
}

/** File input with a visible label, hint and inline errors (nothing is uploaded anywhere). */
export function FileField({ label, hint, accept, errors, totalErrors = errors.length, loaded, disabled = false, onFile }: FileFieldProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const hasErrors = errors.length > 0;
  const onChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file !== undefined) onFile(file);
    // Clear the input so picking the same file again (after fixing it) fires onChange again.
    event.target.value = "";
  };
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="font-semibold">
        {label}
      </label>
      <p id={hintId} className="text-sm text-muted">
        {hint}
      </p>
      <input
        id={id}
        type="file"
        accept={accept}
        disabled={disabled}
        onChange={onChange}
        aria-describedby={hasErrors ? `${hintId} ${errorId}` : hintId}
        aria-invalid={hasErrors}
        className="min-h-11 w-full max-w-full rounded-lg border border-border-strong bg-surface p-2 text-base file:mr-3 file:min-h-9 file:cursor-pointer file:rounded-md file:border-0 file:bg-bg file:px-3 file:font-semibold file:text-text disabled:opacity-50"
      />
      <p role="status" className="text-sm text-success">
        {loaded !== null && !hasErrors ? loaded : ""}
      </p>
      {hasErrors && (
        <div id={errorId} role="alert" className="rounded-lg border border-danger bg-surface p-3 text-sm text-danger">
          <p className="font-semibold">This file cannot be used:</p>
          <ul className="list-disc pl-5">
            {errors.map((message, index) => (
              <li key={`${index}-${message}`} className="break-words">
                {message}
              </li>
            ))}
          </ul>
          {totalErrors > errors.length && <p>…and {totalErrors - errors.length} more problems.</p>}
        </div>
      )}
    </div>
  );
}
