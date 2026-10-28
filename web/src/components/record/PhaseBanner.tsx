"use client";

import { useEffect, useRef } from "react";
import type { PhaseView, Stage } from "@/lib/record/phaseMachine";

type Props = {
  stage: Stage;
  view: PhaseView;
  /** Shown under the title before recording starts. */
  idleHint: string;
};

const STAGE_STYLE: Record<Stage, string> = {
  idle: "border-border bg-surface text-text",
  "calibration-sit": "border-warn bg-warn-bg text-warn",
  "calibration-stand": "border-warn bg-warn-bg text-warn",
  test: "border-primary bg-primary text-on-primary",
  done: "border-success bg-success-bg text-success",
};

function Detail({ view, stage, idleHint }: Props) {
  if (view.count !== null) {
    return (
      <p className="text-8xl font-black leading-none tabular-nums sm:text-9xl" aria-hidden="true">
        {view.count}
      </p>
    );
  }
  if (view.elapsedS !== null) {
    const limit = view.limitS === null ? "" : ` / ${view.limitS}`;
    return (
      <p className="text-6xl font-black leading-none tabular-nums sm:text-8xl">
        {view.elapsedS}
        <span className="text-3xl font-bold sm:text-5xl">{limit} s</span>
      </p>
    );
  }
  if (stage === "idle") return <p className="text-lg font-medium text-muted">{idleHint}</p>;
  return null;
}

/**
 * The instruction the participant reads from about 3 m away. Countdown digits are hidden from
 * screen readers; the polite live region announces only the phase change itself.
 */
export function PhaseBanner(props: Props) {
  const { stage, view } = props;
  const headingRef = useRef<HTMLHeadingElement>(null);
  const shownStage = useRef(stage);

  // Move focus to the new instruction when the stage changes (not on first render).
  useEffect(() => {
    if (shownStage.current === stage) return;
    shownStage.current = stage;
    headingRef.current?.focus();
  }, [stage]);

  return (
    <section
      aria-labelledby="phase-title"
      className={`flex min-h-48 flex-col items-center justify-center gap-3 rounded-xl border-2 p-4 text-center transition-colors duration-200 sm:min-h-64 sm:p-6 ${STAGE_STYLE[stage]}`}
    >
      <h2 id="phase-title" ref={headingRef} tabIndex={-1} className="text-3xl font-extrabold leading-tight text-balance sm:text-5xl">
        {view.title}
        {view.count !== null && <span className="sr-only">: {view.count} seconds</span>}
      </h2>
      <Detail {...props} />
      <p aria-live="polite" className="sr-only">
        {stage === "idle" ? "" : view.title}
      </p>
    </section>
  );
}
