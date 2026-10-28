import type { RepEvent, RepKind } from "@/lib/sthira/chairStand";

type Props = { events: readonly RepEvent[] };

/** Text, not colour alone, says whether each attempt counted. */
const KIND: Record<RepKind, { label: string; className: string }> = {
  full: { label: "Full stand", className: "border-success bg-success-bg text-success" },
  half: { label: "Half: not counted", className: "border-warn bg-warn-bg text-warn" },
  "arm-assisted": { label: "Arms used: not counted", className: "border-warn bg-warn-bg text-warn" },
  uncertain: { label: "Unclear: not counted", className: "border-border-strong bg-surface text-muted" },
};

export function RepChips({ events }: Props) {
  if (events.length === 0) return null;
  return (
    <ol aria-label="Each attempt" className="flex flex-wrap gap-2">
      {events.map((event, index) => {
        const kind = KIND[event.kind];
        return (
          <li key={event.startT} className={`flex min-h-11 items-center gap-2 rounded-full border-2 px-3 text-base font-bold ${kind.className}`}>
            <span className="tabular-nums">{index + 1}</span>
            <span>{kind.label}</span>
          </li>
        );
      })}
    </ol>
  );
}
