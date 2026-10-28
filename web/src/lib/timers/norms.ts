import { z } from "zod";

const sourceSchema = z.object({ url: z.url(), title: z.string() });
const positive = z.number().positive();

const fileSchema = z.object({
  balance4Stage: z.object({
    stages: z.array(z.object({ stage: z.number().int(), name: z.string(), cdcText: z.string() })).min(1),
    protocol: z.object({ stageDurationSeconds: positive, progressionRule: z.string(), allowedMovement: z.string(), source: sourceSchema }),
    entries: z.array(z.object({ stage: z.number().int(), increasedRiskIfHoldLessThan: positive, source: sourceSchema })).length(1),
  }),
  tug: z.object({
    entries: z.array(z.object({ atRiskIfAtLeast: positive, source: sourceSchema })).length(1),
    protocol: z.object({ walkDistanceMeters: positive, timingRule: z.string(), footwearAndAids: z.string(), source: sourceSchema }),
  }),
});

export type CdcSource = z.infer<typeof sourceSchema>;
export type BalanceStage = { readonly stage: number; readonly name: string; readonly cdcText: string };

export interface BalanceNorms {
  readonly stages: readonly BalanceStage[];
  readonly stageDurationS: number;
  readonly progressionRule: string;
  readonly allowedMovement: string;
  readonly riskStage: number;
  readonly riskHoldLessThanS: number;
  readonly source: CdcSource;
}

export interface TugNorms {
  readonly atRiskIfAtLeastS: number;
  readonly walkDistanceMeters: number;
  readonly timingRule: string;
  readonly footwearAndAids: string;
  readonly source: CdcSource;
}

export type TimerNorms = { readonly balance: BalanceNorms; readonly tug: TugNorms };

export type TimerNormsResult = { readonly ok: true; readonly norms: TimerNorms } | { readonly ok: false; readonly errors: readonly string[] };

/** Reads the CDC STEADI balance and TUG reference values from norms.json; nothing is defaulted. */
export function readTimerNorms(json: unknown): TimerNormsResult {
  const parsed = fileSchema.safeParse(json);
  if (!parsed.success) {
    return { ok: false, errors: parsed.error.issues.map((issue) => `${issue.path.join(".")}: ${issue.message}`) };
  }
  const { balance4Stage, tug } = parsed.data;
  const [risk] = balance4Stage.entries;
  const [tugEntry] = tug.entries;
  return {
    ok: true,
    norms: {
      balance: {
        stages: balance4Stage.stages,
        stageDurationS: balance4Stage.protocol.stageDurationSeconds,
        progressionRule: balance4Stage.protocol.progressionRule,
        allowedMovement: balance4Stage.protocol.allowedMovement,
        riskStage: risk.stage,
        riskHoldLessThanS: risk.increasedRiskIfHoldLessThan,
        source: balance4Stage.protocol.source,
      },
      tug: {
        atRiskIfAtLeastS: tugEntry.atRiskIfAtLeast,
        walkDistanceMeters: tug.protocol.walkDistanceMeters,
        timingRule: tug.protocol.timingRule,
        footwearAndAids: tug.protocol.footwearAndAids,
        source: tug.protocol.source,
      },
    },
  };
}
