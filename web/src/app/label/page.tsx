import type { Metadata } from "next";
import { LabelApp } from "@/components/label/LabelApp";

export const metadata: Metadata = { title: "Label", robots: { index: false } };

export default function LabelPage() {
  return (
    <main className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-6 sm:px-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold">Label pilot recordings</h1>
        <p className="max-w-3xl text-muted">
          Mark ground truth by the rules in docs/pilot/labelling-rules.md. Work alone: do not look at the other
          labeller&apos;s file or at Sthira&apos;s count until you are both done.
        </p>
      </header>
      <LabelApp />
    </main>
  );
}
