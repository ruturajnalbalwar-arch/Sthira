"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { nowSeconds, useTicker } from "@/hooks/useTicker";
import type { TugNorms } from "@/lib/timers/norms";
import { tugVerdict } from "@/lib/timers/tug";
import { BigTime, CdcNote, ResultCard, formatSeconds } from "./TimerParts";

type Run = { kind: "idle" } | { kind: "running"; startT: number } | { kind: "done"; seconds: number };

const BIG = "min-h-14 flex-1 text-lg";

export function TugTest({ norms }: { norms: TugNorms }) {
  const [run, setRun] = useState<Run>({ kind: "idle" });
  const [now, setNow] = useState(0);
  const [usedAid, setUsedAid] = useState(false);
  useTicker(run.kind === "running", setNow);

  const go = () => {
    const t = nowSeconds();
    setNow(t);
    setRun({ kind: "running", startT: t });
  };
  const stop = () => {
    if (run.kind === "running") setRun({ kind: "done", seconds: nowSeconds() - run.startT });
  };

  const shown = run.kind === "running" ? Math.max(now - run.startT, 0) : run.kind === "done" ? run.seconds : 0;
  const verdict = run.kind === "done" ? tugVerdict(run.seconds, norms) : null;
  const limit = formatSeconds(norms.atRiskIfAtLeastS);

  return (
    <div className="flex flex-col gap-4">
      <BigTime seconds={shown} label="Timed Up and Go" />
      <div className="flex flex-col gap-3 sm:flex-row">
        {run.kind === "running" ? (
          <Button className={BIG} onClick={stop}>
            Sat back down: stop
          </Button>
        ) : (
          <Button className={BIG} onClick={go}>
            {run.kind === "done" ? "Run again: Go" : "Go"}
          </Button>
        )}
      </div>
      <label className="flex min-h-11 items-center gap-3 text-lg">
        <input type="checkbox" className="size-6" checked={usedAid} onChange={(e) => setUsedAid(e.target.checked)} />
        Used a walking aid
      </label>
      {run.kind === "done" && verdict !== null && (
        <ResultCard
          tone={verdict === "below" ? "meets" : "below"}
          title={`${formatSeconds(run.seconds)}: ${verdict === "below" ? "under" : "at or above"} the CDC reference of ${limit}`}
        >
          {usedAid && <p className="mb-2 font-semibold">A walking aid was used.</p>}
          <CdcNote source={norms.source} />
        </ResultCard>
      )}
    </div>
  );
}
