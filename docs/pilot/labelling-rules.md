# Sthira labelling rules (v1, written 22 Oct 2026, before any tuning)

These rules are fixed before anyone tunes a parameter. If a rule must change later, write the change, the date and the reason at the bottom of this file, and relabel every clip with the new rule. The rules for full, half and arm-assisted stands are copied from the council verdict (`docs/council/2026-10-22-data-plan-verdict.md`, section c).

## Who labels
- **Labeller A:** one student from the team.
- **Labeller B:** one outsider, ideally a physiotherapist.
- Each labeller works alone. Do not look at the other person's labels or at Sthira's detector output until both are done.
- Label from the recorded video, at normal speed first. Slow it down or step back as often as needed.

## Keys in the labelling tool
| Key | Meaning |
|---|---|
| F | Full stand |
| H | Half stand |
| A | Arm-assisted stand |
| U | Uncertain (cannot decide) |
| B | Balance stage start, then balance stage end (press once for each) |
| L | Lost position (balance) |
| T | TUG start, then TUG end (press once for each) |
| Z | Undo the last label |

## Chair stand (trials cs-loose, cs-track, half, arm)
Label every attempt to stand in the clip, one key per attempt. Press the key when the person reaches the highest point of that attempt.

- **Full stand (F):** hips and knees reach upright, then the person returns to the seat.
- **Half stand (H):** the person leaves the seat but does not reach upright.
- **Arm-assisted (A):** either hand leaves the chest, or pushes on the thigh or chair. This applies at any point in the rise or the sit. A stand that reaches upright with arm use is A, not F.
- **Uncertain (U):** use it when you cannot decide. Examples: the hands are hidden by clothing, part of the body is out of frame, or the video freezes. Never guess.
- Rocking forward without the seat leaving the chair is not an attempt. Do not label it.
- **End of the 30 s window.** CDC rule: "If the patient is over halfway to a standing position when 30 seconds have elapsed, count it as a stand." Label the last attempt as normal. The window and this rule are applied in analysis from the "Go" time, not by the labeller. Open item: the key list has no key for "Go". Until the tool has one, write the "Go" time (heard on the video) in the notes for each chair-stand trial.
- **CDC arm rule, for reference.** CDC stops the test and records 0 when arms are needed to stand. We still label each A attempt, because Sthira must show it as "arm-rejected".

## 4-stage balance (trials bal1 to bal4)
- **Stage start (B):** the moment the spotter lets go and the person is steady in the stage position.
- **Stage end (B):** the moment the 10 s are up ("Stop").
- **Lost position (L):** press at the moment any of these happens. L also ends the stage; do not press B after it.
  - a foot moves: it lifts, slides or steps out of the stage position. In stage 4, the raised foot touching the floor counts.
  - a hand reaches out to touch or grab support: a wall, the chair or the spotter.
  - the spotter touches the person.
- Holding the arms out to the side, or swaying the body, is **not** lost position. The CDC instructions allow it: "You can hold your arms out, or move your body to help keep your balance, but don't move your feet."
- Hold time = end time minus start time, up to 10 s.

## TUG (trials tug1, tug2)
- **TUG start (T):** the moment the back leaves the chair backrest.
- **TUG end (T):** the moment the back touches the chair backrest again after sitting down.
- If the person was not touching the backrest at "Go", mark start when the trunk first moves forward to stand, and write "no backrest" in the notes.
- Note: the CDC stopwatch rule is different. It starts on "Go" and stops when the person is seated. The stopwatch time in the session log follows CDC. Keep both. Do not edit one to match the other.

## Comparing the two labellers
- Fill in before comparing: balance and TUG times agree if they differ by no more than ______ s. (Decide this number before looking at any labels.)
- Chair stand: compare the two lists of attempts in each trial, in order. A disagreement is a different key for the same attempt, or a different number of attempts.
- Report raw agreement **before** adjudication: percent of attempts with the same key, and the number of trials with the same full-stand count.

## Adjudicating disagreements
1. Both labellers watch the disputed moment together, slowed down. Each says which rule above they applied.
2. If they now agree, that becomes the consensus label.
3. If they still disagree, the physiotherapist labeller decides. If Labeller B is not a physiotherapist, a third person who has not labelled and is not tuning the detector decides.
4. If it is still unclear, the consensus label is U. U attempts are reported, never counted as full.
5. Log every disagreement: clip, time, label from A, label from B, final label, rule used.

## Change log
| Date | Change | Reason | Clips relabelled |
|---|---|---|---|
| | | | |
