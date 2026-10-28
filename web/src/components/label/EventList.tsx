"use client";

import { memo } from "react";
import { formatSeconds } from "@/lib/label/reducer";
import { EVENT_KEY, EVENT_NAME, type LabelEvent } from "@/lib/label/schema";
import { FrameBackIcon, FrameForwardIcon, SeekIcon, TrashIcon } from "./Icons";

interface EventListProps {
  id: string;
  events: readonly LabelEvent[];
  goT: number;
  onSeek: (t: number) => void;
  onNudge: (event: LabelEvent, direction: -1 | 1) => void;
  onDelete: (id: string) => void;
}

const ROW_BUTTON =
  "inline-flex min-h-11 min-w-11 cursor-pointer items-center justify-center rounded-lg border border-border-strong bg-surface text-text transition-colors duration-150 hover:bg-bg";

function EventRow({ event, goT, onSeek, onNudge, onDelete }: Omit<EventListProps, "events" | "id"> & { event: LabelEvent }) {
  const name = `${EVENT_NAME[event.type]} at ${formatSeconds(event.t)}`;
  return (
    <li className="flex flex-wrap items-center gap-2 border-b border-border py-2 last:border-b-0">
      <span aria-hidden="true" className="inline-flex size-8 items-center justify-center rounded-md border-2 border-border-strong font-mono font-bold">
        {EVENT_KEY[event.type]}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block font-semibold">{EVENT_NAME[event.type]}</span>
        <span className="block font-mono text-sm text-muted">
          {formatSeconds(event.t)} · Go +{formatSeconds(event.t - goT)}
        </span>
      </span>
      <span className="flex gap-1">
        <button type="button" className={ROW_BUTTON} onClick={() => onSeek(event.t)} aria-label={`Go to ${name}`} title="Go to this mark">
          <SeekIcon />
        </button>
        <button type="button" className={ROW_BUTTON} onClick={() => onNudge(event, -1)} aria-label={`Move ${name} one frame earlier`} title="One frame earlier">
          <FrameBackIcon />
        </button>
        <button type="button" className={ROW_BUTTON} onClick={() => onNudge(event, 1)} aria-label={`Move ${name} one frame later`} title="One frame later">
          <FrameForwardIcon />
        </button>
        <button
          type="button"
          className={`${ROW_BUTTON} text-danger`}
          onClick={() => onDelete(event.id)}
          aria-label={`Delete ${name}`}
          title="Delete this mark"
        >
          <TrashIcon />
        </button>
      </span>
    </li>
  );
}

/** Every placed mark in time order, with seek, one-frame nudge and delete. */
function EventListView({ id, events, ...rest }: EventListProps) {
  return (
    <section id={id} aria-labelledby="event-list-heading" className="flex flex-col gap-2">
      <h2 id="event-list-heading" className="text-lg font-semibold">
        Marks ({events.length})
      </h2>
      {events.length === 0 ? (
        <p className="text-muted">No marks yet. Play the video and press a label key at each moment.</p>
      ) : (
        <ol className="max-h-[28rem] overflow-y-auto rounded-lg border border-border bg-surface px-3">
          {events.map((event) => (
            <EventRow key={event.id} event={event} {...rest} />
          ))}
        </ol>
      )}
    </section>
  );
}

export const EventList = memo(EventListView);
