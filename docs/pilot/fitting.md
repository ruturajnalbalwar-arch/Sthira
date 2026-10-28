# Sthira parameter fitting (v1, 28 Oct 2026)

How the 16 fitted chair-stand parameters in `web/src/lib/sthira/params/params.json` are produced from the pilot clips. The fitter is `web/scripts/fit-params.ts`; the logic is in `web/src/lib/sthira/fit/` and the replay in `web/src/lib/sthira/replay.ts`. The rules come from the council verdict (`docs/council/2026-10-22-data-plan-verdict.md`, sections b and c) and `labelling-rules.md`.

## Rules the fitter enforces
- **Pilot clips are the only tuning data.** Synthetic fixtures are used in tests only.
- **One detector.** Every trial is replayed through the same engine the browser runs: `calibrate`, then `makeContext`, then `step` for each frame from Go, then `summarize`.
- **Clean recordings only.** A recording with any `capture.issues` entry is excluded. The reason says whether the issue was fatal. `record/schema.ts` says a recording with any issue is kept but excluded from tuning and evaluation.
- **Two-labeller labels only.** A trial is used when one of these holds:
  - an adjudicated file `<fileBase>_labels_consensus.json` exists, or
  - exactly two labellers (one team member, one outsider) agree on every attempt.

  A single labeller, a third labeller or an unresolved disagreement excludes the trial and is listed in the output. Labels are matched to recordings by fingerprint (frame count, first and last frame time, Go time, names), not by file name.
- **Timed window.** Only labelled attempts from Go to Go + 30 s count.
- **Locked split.** `tune` refuses to run without `pilot-data/split.json`. Held-out people are never passed to the fit. Anyone recorded after the split was locked joins tuning, never the held-out set. Editing `split.json` breaks its hash, and the fitter then refuses it.

## Commands (run from `web/`; data defaults to `../pilot-data/`, outputs to `../pilot-data/fit/`)
```
npx tsx scripts/fit-params.ts init-split --demo P07 [--seed <text>]
npx tsx scripts/fit-params.ts tune [--grid-points 11] [--max-rounds 10] [--write]
npx tsx scripts/fit-params.ts evaluate [--params <file>] [--force]
```
- **`init-split`** locks `split.json` once and never redraws it. It holds out 4 people:
  - the demo performer;
  - at least 2 people recorded in a saree or dhoti;
  - the rest, chosen in the order of sha256(seed:code). This never looks at counts or labels.

  It stops and says what is missing if there are fewer than 4 + 2 participants, fewer than 2 in a saree or dhoti, or the demo performer has no recording.
- **`tune`** fits on the tuning people and runs leave-one-subject-out (LOSO). It writes `fit/params.candidate.json` and `fit/tune-report.json`, and prints the LOSO table per clothing type. It only overwrites `params.json` when you pass `--write`.
- **`evaluate`** scores the held-out people once with `params.json` (or `--params`). It refuses in three cases:
  - the params are untuned;
  - the params were tuned on a different split;
  - `fit/evaluation-<hash>.json` already exists. `--force` writes a separate, timestamped re-run file; it never overwrites the first record.

## Method
1. **Measure each tuning trial** with the engine's own feature code on the frames replay feeds it. The calibration phases are covered by the frames inside them plus the nearest frame on each side, because phase boundaries rarely fall on a frame.
2. **Set four gates directly from the data**, at the tightest value every tuning trial passes. See the table below.
3. **Search the other twelve parameters by coordinate descent.**
   - Each round scans every parameter over all of its candidates and keeps a new value only if it strictly improves the loss. Ties keep the current value, so the result is deterministic and needs no random seed.
   - Candidates that break the loader's ordering rules are skipped.
   - The search stops when a round changes nothing, or at `--max-rounds`.
   - **Start point: the least restrictive detector.** No dwell, least smoothing, no arm, visibility or abstain rejection. The seat thresholds start just above the seated noise seen in calibration, and reach starts at the lowest labelled full-stand peak.
4. **Loss, compared in order, with no weights:**
   1. total |engine full − labelled full|;
   2. attempts where engine and labels disagree, from `compareWithEngine` (time overlap, no tolerance);
   3. trials whose abstain flag disagrees with whether the count was wrong.

   A failed calibration counts as missing every labelled full stand.
5. **LOSO.** For each tuning person, steps 1 to 4 are re-run without them, including the ranges and gates, and their trials are then scored. The table reports these scores. The final params are fitted on all tuning people.

## Where each fitted parameter comes from
| Parameter | How |
|---|---|
| minFps | lowest rate at which every tuning trial passes the calibration frame-count check and the test fps check |
| maxGapS | longest inter-frame gap in any clean tuning trial |
| calibStillnessMaxRatio | largest calibration stillness ratio among tuning trials |
| calibMinRangeToBodyScaleRatio | smallest calibration range ÷ body scale among tuning trials |
| reachFraction, leaveSeatFraction, seatFraction, timeUpCountFraction | quantiles of observed head progress (0 to 1 of each person's calibrated range), plus midpoints between them |
| minStandDwellS, minSitDwellS, armsViolationMinS | even grid from 0 to the shortest interval between labelled attempts |
| smoothingCutoffHz | even grid from the fastest labelled attempt rate to the Nyquist frequency of minFps |
| armsCrossedMaxRatio | quantiles and midpoints of observed wrist-to-opposite-shoulder ÷ body-scale ratios |
| visibilityMin | quantiles and midpoints of observed key-landmark visibility, capped so every tuning calibration passes |
| uncertainFrameMaxFraction, unreliableUncertainFraction | even grid on [0, 1) |

The only numbers the fitter chooses are methodological, and each is a named CLI option:
- `--grid-points` (default 11, the deciles of each distribution);
- `--max-rounds` (default 10, a safety cap);
- `--seed` (default `sthira-split-v1`).

The split sizes (4 held out, at least 2 in a saree or dhoti) come from the verdict. 2 is the minimum number of tuning people for LOSO.

## Metrics (per clothing type, then all)
- **Count metrics:** count MAE, exact-match rate and false counts, on 30 s chair-stand trials with an engine count.
- **Rejection rates:** half-stand and arm-assisted rejection, over all labelled half and arm attempts.
- **Abstain rate:** the share of trials where calibration failed, the frame rate was low, the count was unreliable, or the 30 s window was cut short.

Calibration failures in the held-out set count as abstentions. They are never excluded.

## Known limits (open)
- **Go comes while the person is standing.** The record flow goes straight from "Stand still" to "Go", so the person stands at Go. The engine starts in SEATED, so the first sit-down can be counted as a rep. Fix this in the protocol or the engine before tuning on real clips.
- **The calibration-stand phase includes the rise.** The 5 s countdown starts while the person is still seated. The fitted stillness gate absorbs this, which loosens it. Trimming the start of that phase would tighten it.
- **minFps is the lowest frame rate seen in tuning.** A venue running slower than every tuning clip shows the low-fps banner. That is intended.
- **No person-bootstrap CIs yet.** The verdict asks for them, but with 4 held-out people they would be close to meaningless. Report raw per-person results alongside the table.
- **No CDC "over halfway" judgement in the labels.** The labels hold no time-up judgement, so timeUpCountFraction is fitted only against the labeller's call on the last attempt.
