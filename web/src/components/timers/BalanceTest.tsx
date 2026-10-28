"use client";

import { useReducer, useState } from "react";
import { Button } from "@/components/ui/Button";
import { nowSeconds, useTicker } from "@/hooks/useTicker";
import { BALANCE_START, balanceReducer, balanceVerdict, type BalanceEvent, type BalanceState } from "@/lib/timers/balance";
import type { BalanceNorms } from "@/lib/timers/norms";
import { BigTime, CdcNote, ResultCard, formatSeconds } from "./TimerParts";

const BIG = "min-h-14 flex-1 text-lg";

function Results({ state }: { state: BalanceState }) {
  if (state.results.length === 0) return null;
  return (
    <table className="w-full border-collapse text-left">
      <caption className="sr-only">Stage results</caption>
      <thead>
        <tr className="border-b border-border-strong text-sm text-muted">
          <th className="py-2">Stage</th>
          <th className="py-2">Held</th>
          <th className="py-2">Result</th>
        </tr>
      </thead>
      <tbody>
        {state.results.map((r) => (
          <tr key={r.stage} className="border-b border-border">
            <td className="py-2 font-semibold">{r.stage}</td>
            <td className="py-2 font-mono tabular-nums">{formatSeconds(r.heldS)}</td>
            <td className="py-2">{r.completed ? "Held" : "Lost position"}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function Verdict({ state, norms }: { state: BalanceState; norms: BalanceNorms }) {
  const verdict = balanceVerdict(state, norms);
  if (verdict.kind === "not-reached") return null;
  const limit = formatSeconds(norms.riskHoldLessThanS);
  const title =
    verdict.kind === "stopped-before"
      ? `Stopped at stage ${verdict.stoppedAtStage}, before the tandem stand: below the CDC reference of ${limit} in tandem`
      : `Tandem stand held ${formatSeconds(verdict.heldS)}: ${verdict.kind === "meets" ? "meets" : "below"} the CDC reference of ${limit}`;
  return (
    <ResultCard tone={verdict.kind === "meets" ? "meets" : "below"} title={title}>
      <CdcNote source={norms.source} />
    </ResultCard>
  );
}

export function BalanceTest({ norms }: { norms: BalanceNorms }) {
  const [state, dispatch] = useReducer((s: BalanceState, e: BalanceEvent) => balanceReducer(s, e, norms), BALANCE_START);
  const [now, setNow] = useState(0);
  const running = state.current !== null && state.current.startT !== null;
  useTicker(running, (t) => {
    setNow(t);
    dispatch({ type: "tick", t });
  });

  const start = () => {
    const t = nowSeconds();
    setNow(t);
    dispatch({ type: "start-stage", t });
  };
  const stage = state.current === null ? null : norms.stages[state.current.index];
  const heldS = running && state.current?.startT != null ? Math.min(Math.max(now - state.current.startT, 0), norms.stageDurationS) : 0;

  return (
    <div className="flex flex-col gap-4">
      {stage !== null && (
        <section className="flex flex-col gap-3" aria-labelledby="stage-heading">
          <h2 id="stage-heading" className="text-2xl font-bold">
            Stage {stage.stage} of {norms.stages.length}: {stage.name}
          </h2>
          <p className="text-lg">&ldquo;{stage.cdcText}&rdquo;</p>
          <BigTime seconds={heldS} label={`Hold for ${formatSeconds(norms.stageDurationS)}`} />
          <div className="flex flex-col gap-3 sm:flex-row">
            {running ? (
              <>
                <Button variant="danger" className={BIG} onClick={() => dispatch({ type: "lost", t: nowSeconds() })}>
                  Lost position
                </Button>
                <Button variant="secondary" className={BIG} onClick={() => dispatch({ type: "cancel-stage" })}>
                  Not ready, restart stage
                </Button>
              </>
            ) : (
              <Button className={BIG} onClick={start}>
                Start stage {stage.stage}
              </Button>
            )}
          </div>
          <p className="text-sm text-muted">
            Press Lost position if the feet move or the person needs support. {norms.allowedMovement}
          </p>
        </section>
      )}
      {state.finished && <p className="text-lg font-semibold" aria-live="polite">Test complete.</p>}
      <Results state={state} />
      <Verdict state={state} norms={norms} />
      {state.results.length > 0 && (
        <Button variant="secondary" className="self-start" onClick={() => dispatch({ type: "reset" })}>
          Start over
        </Button>
      )}
    </div>
  );
}
