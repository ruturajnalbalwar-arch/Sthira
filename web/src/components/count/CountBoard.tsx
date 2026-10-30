"use client";

import { useEffect, useRef, type ReactNode } from "react";
import type { LiveCountView } from "@/lib/count/liveCount";
import type { PhaseView, Stage } from "@/lib/record/phaseMachine";
import { RepChips } from "./RepChips";

type Props = {
  stage: Stage;
  phaseView: PhaseView;
  countView: LiveCountView;
  idleHint: string;
};

/** What the physiotherapist reads out (and the participant reads from about 3 m) at each step. */
const SAY: Record<Exclude<Stage, "idle" | "done">, string> = {
  "calibration-stand": "Stand up tall in front of the chair, arms crossed on your chest. Keep still.",
  "calibration-sit": "Now sit back in the chair, arms still crossed. Keep still. The test starts when you are seated.",
  test: "Go! Stand up fully and sit down, as many times as you can.",
};

const STAGE_STYLE: Record<Stage, string> = {
  idle: "border-border bg-surface text-text",
  "calibration-sit": "border-warn bg-warn-bg text-warn",
  "calibration-stand": "border-warn bg-warn-bg text-warn",
  test: "border-primary bg-primary text-on-primary",
  done: "border-success bg-success-bg text-success",
};

const BIG = "font-black leading-none tabular-nums";

function Countdown({ seconds }: { seconds: number }) {
  return (
    <p className={`${BIG} text-8xl sm:text-9xl`} aria-hidden="true">
      {seconds}
    </p>
  );
}

function LiveCount({ view }: { view: Extract<LiveCountView, { kind: "counting" }> }) {
  const full = view.summary.full;
  return (
    <div className="flex w-full flex-col items-center gap-3">
      {/* Wraps instead of overflowing when the text-size switch enlarges the rem-sized digits on a narrow phone. */}
      <p className="flex flex-wrap items-baseline justify-center gap-x-3">
        <span className={`${BIG} text-[9rem] sm:text-[12rem]`}>{full}</span>
        <span className="text-2xl font-bold sm:text-4xl">{full === 1 ? "stand" : "stands"}</span>
      </p>
      <p className="text-3xl font-extrabold tabular-nums sm:text-5xl" aria-hidden="true">
        {Math.ceil(view.remainingS)} s left
      </p>
      <p aria-live="polite" className="sr-only">
        {full} full {full === 1 ? "stand" : "stands"}
      </p>
      <div className="rounded-xl bg-surface p-2 text-text empty:hidden">
        <RepChips events={view.summary.events} />
      </div>
    </div>
  );
}

function Elapsed({ phaseView }: { phaseView: PhaseView }) {
  if (phaseView.elapsedS === null) return null;
  return (
    <p className={`${BIG} text-6xl sm:text-8xl`} aria-hidden="true">
      {phaseView.elapsedS}
      <span className="text-3xl font-bold sm:text-5xl">{phaseView.limitS === null ? "" : ` / ${phaseView.limitS}`} s</span>
    </p>
  );
}

function Problem({ title, items }: { title: string; items: readonly string[] }) {
  return (
    <div role="alert" className="flex w-full flex-col gap-2 rounded-xl bg-surface p-4 text-left text-danger">
      <p className="text-2xl font-extrabold">{title}</p>
      <ul className="list-disc pl-6 text-lg font-semibold">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
      <p className="font-semibold text-text">Press Redo calibration, or Stop and save the recording.</p>
    </div>
  );
}

function TestDetail({ phaseView, countView }: Pick<Props, "phaseView" | "countView">) {
  switch (countView.kind) {
    case "counting":
      return <LiveCount view={countView} />;
    case "calibration-failed":
      return <Problem title="Calibration did not pass. Not counting." items={countView.reasons} />;
    case "context-failed":
      return <Problem title="The counter could not start." items={countView.errors} />;
    case "not-tuned":
      return (
        <>
          <Elapsed phaseView={phaseView} />
          <p className="text-xl font-bold">No automatic count for this run. Count the stands by hand.</p>
        </>
      );
    default:
      return <p className="text-2xl font-bold">Checking calibration...</p>;
  }
}

function Detail({ stage, phaseView, countView, idleHint }: Props): ReactNode {
  if (stage === "idle") return <p className="text-lg font-medium text-muted">{idleHint}</p>;
  if (stage === "test") return <TestDetail phaseView={phaseView} countView={countView} />;
  if (phaseView.count !== null) return <Countdown seconds={phaseView.count} />;
  return null;
}

function title(stage: Stage): string {
  if (stage === "idle") return "Ready";
  if (stage === "done") return "Stop. Test finished";
  return SAY[stage];
}

/**
 * The instruction and the live count. Digits that change every tick are hidden from screen
 * readers; the polite live regions announce the step and each new full stand instead.
 */
export function CountBoard(props: Props) {
  const { stage, phaseView } = props;
  const headingRef = useRef<HTMLHeadingElement>(null);
  const shownStage = useRef(stage);

  useEffect(() => {
    if (shownStage.current === stage) return;
    shownStage.current = stage;
    headingRef.current?.focus();
  }, [stage]);

  const heading = title(stage);
  const height = stage === "done" ? "min-h-24" : "min-h-64 sm:min-h-80";
  return (
    <section
      aria-labelledby="count-title"
      className={`flex flex-col items-center justify-center gap-4 rounded-xl border-2 p-4 text-center transition-colors duration-200 sm:p-6 ${height} ${STAGE_STYLE[stage]}`}
    >
      <h2 id="count-title" ref={headingRef} tabIndex={-1} className="text-3xl font-extrabold leading-tight text-balance sm:text-4xl">
        {heading}
        {phaseView.count !== null && <span className="sr-only">: {phaseView.count} seconds</span>}
      </h2>
      <Detail {...props} />
      <p aria-live="polite" className="sr-only">
        {stage === "idle" ? "" : heading}
      </p>
    </section>
  );
}
