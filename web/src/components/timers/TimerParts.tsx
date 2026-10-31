import type { ReactNode } from "react";
import type { CdcSource } from "@/lib/timers/norms";

const ONE_DECIMAL = 1;

export const formatSeconds = (seconds: number): string => `${seconds.toFixed(ONE_DECIMAL)} s`;

export function BigTime({ seconds, label }: { seconds: number; label: string }) {
  return (
    <p className="flex flex-col items-center gap-1 rounded-2xl border border-border bg-surface p-6">
      <span className="text-base font-semibold text-muted">{label}</span>
      <span className="font-mono text-6xl font-bold tabular-nums sm:text-7xl">{formatSeconds(seconds)}</span>
    </p>
  );
}

type ResultTone = "meets" | "below";

export function ResultCard({ tone, title, children }: { tone: ResultTone; title: string; children: ReactNode }) {
  const toneClass = tone === "meets" ? "border-success bg-success-bg text-success" : "border-warn bg-warn-bg text-warn";
  return (
    <section aria-live="polite" className={`flex flex-col gap-2 rounded-2xl border-2 p-4 ${toneClass}`}>
      <h2 className="text-xl font-bold">{title}</h2>
      <div className="text-text">{children}</div>
    </section>
  );
}

export function CdcNote({ source, children }: { source: CdcSource; children?: ReactNode }) {
  return (
    <p className="text-base text-muted">
      {children} Reference values from{" "}
      <a href={source.url} target="_blank" rel="noopener noreferrer" className="underline underline-offset-2">
        {source.title}
      </a>{" "}
      (US data, not validated for older adults in India). For the physiotherapist to interpret; Sthira does not diagnose.
    </p>
  );
}

export function TimerError({ title, errors }: { title: string; errors: readonly string[] }) {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-3 px-4 py-8">
      <h1 className="text-2xl font-bold">{title}</h1>
      <p role="alert" className="rounded-lg border-2 border-danger bg-surface p-3 font-semibold text-danger">
        The CDC reference values in norms.json are missing or invalid.
      </p>
      <ul className="list-disc pl-5 font-mono text-sm">
        {errors.map((message) => (
          <li key={message}>{message}</li>
        ))}
      </ul>
    </main>
  );
}
