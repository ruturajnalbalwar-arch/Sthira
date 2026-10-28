import type { Metadata } from "next";
import { TimerError } from "@/components/timers/TimerParts";
import { TugTest } from "@/components/timers/TugTest";
import normsJson from "@/lib/sthira/norms/norms.json";
import { readTimerNorms } from "@/lib/timers/norms";

export const metadata: Metadata = {
  title: "Timed Up and Go",
  description: "Physio-timed CDC STEADI Timed Up and Go test with the CDC reference time.",
};

export default function TugPage() {
  const loaded = readTimerNorms(normsJson);
  if (!loaded.ok) return <TimerError title="Timed Up and Go is not available" errors={loaded.errors} />;
  const { tug } = loaded.norms;
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-5 px-4 py-6 sm:py-8">
      <header className="flex flex-col gap-2">
        <h1 className="text-3xl font-bold">Timed Up and Go</h1>
        <p className="text-muted">
          Mark a line {tug.walkDistanceMeters} m from the chair. The person sits back in the chair. Press Go as you say
          &ldquo;Go&rdquo;; they stand, walk to the line at a normal pace, turn, walk back and sit down. {tug.timingRule}{" "}
          {tug.footwearAndAids}
        </p>
      </header>
      <TugTest norms={tug} />
    </main>
  );
}
