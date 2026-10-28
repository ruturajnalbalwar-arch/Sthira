import type { Metadata } from "next";
import { CountStudio } from "@/components/count/CountStudio";
import { toCounterStatus } from "@/lib/count/counterStatus";
import { parseChairStandNorms } from "@/lib/count/norms";
import { createCounter } from "@/lib/sthira/counter";
import normsJson from "@/lib/sthira/norms/norms.json";
import paramsJson from "@/lib/sthira/params/params.json";
import { readProtocolConstants } from "@/lib/sthira/params/protocol";

export const metadata: Metadata = { title: "Count", robots: { index: false } };

/**
 * Server Component: validates params.json (counter status + protocol constants) and the CDC norms,
 * and hands only serialisable data to the client screen.
 */
export default function CountPage() {
  const protocol = readProtocolConstants(paramsJson);
  if (!protocol.ok) {
    return (
      <main className="mx-auto flex w-full max-w-3xl flex-col gap-3 px-4 py-8">
        <h1 className="text-2xl font-bold">The chair-stand test is not available</h1>
        <p role="alert" className="rounded-lg border-2 border-danger bg-surface p-3 font-semibold text-danger">
          The protocol durations in params.json are missing or invalid, so the test cannot be timed.
        </p>
        <ul className="list-disc pl-5 font-mono text-sm">
          {protocol.errors.map((message) => (
            <li key={message}>{message}</li>
          ))}
        </ul>
      </main>
    );
  }
  return <CountStudio constants={protocol.constants} counter={toCounterStatus(createCounter(paramsJson))} norms={parseChairStandNorms(normsJson)} />;
}
