import type { Metadata } from "next";
import { RecordStudio } from "@/components/record/RecordStudio";
import paramsJson from "@/lib/sthira/params/params.json";
import { readProtocolConstants } from "@/lib/sthira/params/protocol";

export const metadata: Metadata = { title: "Record", robots: { index: false } };

/** Server Component: reads the protocol constants from params.json and hands them to the client studio. */
export default function RecordPage() {
  const protocol = readProtocolConstants(paramsJson);
  if (!protocol.ok) {
    return (
      <main className="mx-auto flex w-full max-w-3xl flex-col gap-3 px-4 py-8">
        <h1 className="text-2xl font-bold">Record mode is not available</h1>
        <p role="alert" className="rounded-lg border-2 border-danger bg-surface p-3 font-semibold text-danger">
          The protocol durations in params.json are missing or invalid, so countdowns cannot be shown.
        </p>
        <ul className="list-disc pl-5 font-mono text-sm">
          {protocol.errors.map((message) => (
            <li key={message}>{message}</li>
          ))}
        </ul>
      </main>
    );
  }
  return <RecordStudio constants={protocol.constants} />;
}
