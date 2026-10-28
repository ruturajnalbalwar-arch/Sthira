"use client";

import { memo, useId, type PointerEvent } from "react";
import type { PhaseSpan } from "@/lib/record/schema";
import { formatSeconds, type SpanMark } from "@/lib/label/reducer";
import { EVENT_KEY, type EventType, type LabelEvent } from "@/lib/label/schema";
import { readTime, useVideoValue } from "./useVideoValue";

const PHASE_NAME: Readonly<Record<PhaseSpan["phase"], string>> = {
  "calibration-sit": "Sit calibration",
  "calibration-stand": "Stand calibration",
  test: "Test",
};

const PHASE_FILL: Readonly<Record<PhaseSpan["phase"], string>> = {
  "calibration-sit": "fill-bg",
  "calibration-stand": "fill-bg",
  test: "fill-primary/15",
};

/** Colour is a second cue only: every mark also carries its key letter. */
const EVENT_STROKE: Readonly<Record<EventType, string>> = {
  full: "stroke-success",
  half: "stroke-warn",
  "arm-assisted": "stroke-danger",
  uncertain: "stroke-border-strong",
  "balance-start": "stroke-primary",
  "balance-end": "stroke-primary",
  "balance-lost": "stroke-danger",
  "tug-start": "stroke-primary",
  "tug-end": "stroke-primary",
};

interface Scale {
  firstT: number;
  lastT: number;
}

const pct = ({ firstT, lastT }: Scale, time: number): string => {
  const span = lastT > firstT ? lastT - firstT : 1;
  return `${((Math.min(Math.max(time, firstT), lastT) - firstT) / span) * 100}%`;
};
const width = ({ firstT, lastT }: Scale, from: number, to: number): string => {
  const span = lastT > firstT ? lastT - firstT : 1;
  return `${((Math.max(to, from) - from) / span) * 100}%`;
};

function EventMark({ event, x }: { event: LabelEvent; x: string }) {
  return (
    <svg x={x} y="30" overflow="visible">
      <line x1="0" x2="0" y1="0" y2="36" strokeWidth="2" className={EVENT_STROKE[event.type]} />
      <rect x="-10" y="6" width="20" height="22" rx="4" strokeWidth="2" className={`fill-surface ${EVENT_STROKE[event.type]}`} />
      <text x="0" y="22" textAnchor="middle" fontSize="13" fontWeight="700" className="fill-text">
        {EVENT_KEY[event.type]}
      </text>
    </svg>
  );
}

function PhaseBand({ phase, x, w }: { phase: PhaseSpan; x: string; w: string }) {
  return (
    <svg x={x} y="2" width={w} height="24">
      <rect width="100%" height="100%" strokeWidth="1" className={`${PHASE_FILL[phase.phase]} stroke-border-strong`} />
      <text x="6" y="16" fontSize="12" className="fill-text">
        {PHASE_NAME[phase.phase]}
      </text>
    </svg>
  );
}

/** The only part of the strip that changes every animation frame. */
function Playhead({ video, scale }: { video: HTMLVideoElement | null; scale: Scale }) {
  const t = useVideoValue(video, readTime, 0, true);
  return <line x1={pct(scale, t)} x2={pct(scale, t)} y1="0" y2="88" strokeWidth="2" className="stroke-danger" />;
}

function SeekSlider({ video, scale, onSeek }: { video: HTMLVideoElement | null; scale: Scale; onSeek: (t: number) => void }) {
  const id = useId();
  const t = useVideoValue(video, readTime, 0, true);
  return (
    <>
      <label htmlFor={id} className="text-sm font-semibold">
        Seek (dashed line = Go, red line = video position)
      </label>
      <input
        id={id}
        type="range"
        min={scale.firstT}
        max={scale.lastT}
        step="any"
        value={Math.min(Math.max(t, scale.firstT), scale.lastT)}
        onChange={(event) => onSeek(Number(event.target.value))}
        aria-valuetext={formatSeconds(t)}
        className="h-11 w-full cursor-pointer accent-primary"
      />
    </>
  );
}

interface TimelineProps {
  video: HTMLVideoElement | null;
  phases: readonly PhaseSpan[];
  events: readonly LabelEvent[];
  spans: readonly SpanMark[];
  firstT: number;
  lastT: number;
  goT: number;
  /** id of the mark list, which describes the strip for screen readers. */
  describedBy: string;
  onSeek: (t: number) => void;
}

/** Phases, Go, placed marks, paired spans and the playhead on one strip, plus an accessible seek slider. */
function TimelineView({ video, phases, events, spans, firstT, lastT, goT, describedBy, onSeek }: TimelineProps) {
  const scale = { firstT, lastT };
  const onPointer = (event: PointerEvent<HTMLDivElement>) => {
    const box = event.currentTarget.getBoundingClientRect();
    if (box.width > 0) onSeek(firstT + ((event.clientX - box.left) / box.width) * (lastT - firstT));
  };
  return (
    <section aria-label="Timeline" className="flex flex-col gap-2">
      <div onPointerDown={onPointer} className="cursor-pointer rounded-lg border border-border bg-surface px-3 py-2">
        <svg role="img" aria-label={`Timeline: Go at ${formatSeconds(goT)}, ${events.length} marks`} aria-describedby={describedBy} width="100%" height="88" className="block overflow-visible">
          {phases.map((p, i) => (
            <PhaseBand key={`${p.phase}-${i}`} phase={p} x={pct(scale, p.startT)} w={width(scale, p.startT, p.endT)} />
          ))}
          {spans.map((s) => (
            <rect key={s.startId} x={pct(scale, s.startT)} y="74" height="8" rx="2" width={width(scale, s.startT, s.endT ?? lastT)} className={s.endT === null ? "fill-warn" : "fill-primary"} />
          ))}
          <line x1={pct(scale, goT)} x2={pct(scale, goT)} y1="0" y2="88" strokeWidth="2" strokeDasharray="4 3" className="stroke-text" />
          {events.map((e) => (
            <EventMark key={e.id} event={e} x={pct(scale, e.t)} />
          ))}
          <Playhead video={video} scale={scale} />
        </svg>
      </div>
      <SeekSlider video={video} scale={scale} onSeek={onSeek} />
    </section>
  );
}

export const Timeline = memo(TimelineView);
