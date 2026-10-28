import type { Metadata } from "next";
import { BalanceTest } from "@/components/timers/BalanceTest";
import { TimerError } from "@/components/timers/TimerParts";
import normsJson from "@/lib/sthira/norms/norms.json";
import { readTimerNorms } from "@/lib/timers/norms";

export const metadata: Metadata = {
  title: "4-Stage Balance",
  description: "Physio-timed CDC STEADI 4-Stage Balance Test with the CDC reference for the tandem stand.",
};

export default function BalancePage() {
  const loaded = readTimerNorms(normsJson);
  if (!loaded.ok) return <TimerError title="Balance test is not available" errors={loaded.errors} />;
  const { balance } = loaded.norms;
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-5 px-4 py-6 sm:py-8">
      <header className="flex flex-col gap-2">
        <h1 className="text-3xl font-bold">4-Stage Balance Test</h1>
        <p className="text-muted">
          You time each stage. Stand beside the person, ready to steady them. No cane or walker, eyes open. {balance.progressionRule}
        </p>
      </header>
      <BalanceTest norms={balance} />
    </main>
  );
}
