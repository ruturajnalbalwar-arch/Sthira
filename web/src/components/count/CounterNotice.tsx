import type { CounterStatus } from "@/lib/count/counterStatus";

type Props = { counter: CounterStatus };

/** Technical detail for the Sthira team only, folded away so patients and physiotherapists see plain words. */
function TeamDetails({ items }: { items: readonly string[] }) {
  return (
    <details className="text-sm">
      <summary className="flex min-h-12 cursor-pointer items-center font-semibold">Details for the Sthira team</summary>
      <ul className="flex flex-col gap-1 pt-1 font-mono break-all">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </details>
  );
}

/** Says plainly when the counter cannot count. Shown above the camera, before and during a run. */
export function CounterNotice({ counter }: Props) {
  if (counter.status === "ready") return null;
  const items = counter.status === "invalid" ? counter.errors : counter.missing.map((name) => `missing in params.json: ${name}`);
  return (
    <section aria-labelledby="counter-notice-heading" className="flex flex-col gap-2 rounded-xl border-2 border-warn bg-warn-bg p-4 text-warn">
      <h2 id="counter-notice-heading" className="text-xl font-bold">
        Automatic counting is off in this preview
      </h2>
      <p className="text-lg font-semibold">
        The camera, body outline and setup steps work, but stands are not counted yet. Count the stands by hand and save the recording.
      </p>
      <TeamDetails items={items} />
    </section>
  );
}
