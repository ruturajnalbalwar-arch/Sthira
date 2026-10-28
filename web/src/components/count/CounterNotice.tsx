import type { CounterStatus } from "@/lib/count/counterStatus";

type Props = { counter: CounterStatus };

/** Says plainly when the counter cannot count. Shown above the camera, before and during a run. */
export function CounterNotice({ counter }: Props) {
  if (counter.status === "ready") return null;
  if (counter.status === "invalid") {
    return (
      <section role="alert" className="flex flex-col gap-2 rounded-xl border-2 border-danger bg-surface p-4 text-danger">
        <h2 className="text-xl font-bold">Counter settings are invalid</h2>
        <p className="font-semibold">params.json did not pass validation, so no stands are counted. The run can still be recorded as pilot data.</p>
        <ul className="list-disc pl-5 font-mono text-sm break-all">
          {counter.errors.map((message) => (
            <li key={message}>{message}</li>
          ))}
        </ul>
      </section>
    );
  }
  return (
    <section aria-labelledby="untuned-heading" className="flex flex-col gap-2 rounded-xl border-2 border-warn bg-warn-bg p-4 text-warn">
      <h2 id="untuned-heading" className="text-xl font-bold">
        Counter not tuned yet
      </h2>
      <p className="font-semibold">
        The camera, pose overlay and guided calibration run so the setup can be rehearsed, but no stands are counted and no score is shown until
        the detector settings are fitted from pilot recordings. Save each run: it is extra pilot data.
      </p>
      <details className="text-sm">
        <summary className="flex min-h-11 cursor-pointer items-center font-semibold">Missing from params.json ({counter.missing.length})</summary>
        <ul className="flex flex-wrap gap-2 pt-1 font-mono">
          {counter.missing.map((name) => (
            <li key={name} className="rounded border border-warn px-2 py-0.5">
              {name}
            </li>
          ))}
        </ul>
      </details>
    </section>
  );
}
