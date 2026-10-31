import type { CounterStatus } from "@/lib/count/counterStatus";

type Props = { counter: CounterStatus };

/** Technical detail for the Sthira team only, folded away so patients and physiotherapists see plain words. */
function TeamDetails({ items }: { items: readonly string[] }) {
  return (
    <details className="text-base text-text">
      <summary className="flex min-h-12 cursor-pointer items-center font-semibold">Details for the Sthira team</summary>
      <ul className="flex flex-col gap-1 pt-1 font-mono text-sm break-all">
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
  if (counter.status === "invalid") {
    // A broken settings file must not look like the planned preview: danger tone and an alert.
    return (
      <section role="alert" className="flex flex-col gap-2 rounded-xl border-2 border-danger bg-surface p-4">
        <h2 className="text-xl font-bold text-danger">Automatic counting could not start</h2>
        <p className="text-lg font-semibold text-text">Count the stands by hand, save the recording and tell the Sthira team.</p>
        <TeamDetails items={counter.errors} />
      </section>
    );
  }
  return (
    <section aria-labelledby="counter-notice-heading" className="flex flex-col gap-2 rounded-xl border-2 border-warn bg-warn-bg p-4">
      <h2 id="counter-notice-heading" className="text-xl font-bold text-warn">
        Automatic counting is off in this preview
      </h2>
      <p className="text-lg font-semibold text-text">
        The camera, body outline and setup steps work, but stands are not counted yet. Count the stands by hand and save the recording.
      </p>
      <TeamDetails items={counter.missing.map((name) => `missing in params.json: ${name}`)} />
    </section>
  );
}
