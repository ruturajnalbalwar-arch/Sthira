# Council verdict: Sthira data plan (22 Oct 2026)

The chairman's synthesis of the 5 advisors and 5 peer reviews. Claude implements this.

## Where the council agrees
- The hard cases (saree or dhoti, half-stands, arm use, the 30-second count, 4-stage balance) exist **only in our own pilot clips**. Those clips are the training and validation set. Public data only provides citations.
- **Per-person calibration is the core of the method.**
  - A full stand means reaching the person's own calibrated standing head height.
  - Arms crossed means the wrist-to-opposite-shoulder distance divided by the calibrated shoulder width.
  - Only a few unitless numbers are left to fit.
- Say **"tuned"**, not "trained". Keep other people's figures (R 0.997, ICC 0.928) off our results slides.

## Where the council clashes, and how it was resolved
- **KINECAL tuning and sway percentiles were rejected.** Kinect is not MediaPipe, and centre-of-pressure or Kinect sway is not camera head sway.
- **Enforcement:** a provenance schema plus a scoped lint, not a brittle "no number except 0 or 1" test.
- **No data release** without ethics approval.

## Blind spots caught in peer review
- Python-vs-browser parity and frame rate
- a biased leave-one-subject-out (LOSO) error figure
- calibration failure in older adults
- abstaining instead of guessing
- young volunteers acting out the hard cases
- labelling rules written before tuning, with an outside labeller
- ethics
- no clinician on the team
- no TUG claim

## Recommendation

### (a) Where each parameter comes from
| Parameter | Class | Source |
|---|---|---|
| 30 s, balance stage duration, TUG 3 m | protocol-constant | CDC STEADI protocol PDFs (page cited) |
| Calibration length, camera height and distance | protocol-constant | Sthira protocol v1 (declared, with reason) |
| Seated and standing head height, shoulder width, sway noise floor | per-person-calibration | 5 s calibration |
| Reach fraction, hysteresis enter/exit, smoothing cut-off (Hz), min dwell (s), arms-crossed ratio, visibility gate, calibration-gate tolerances, balance-loss fraction, TUG start/stop rule | fitted | Pilot tuning set, via `fit_params` |
| Chair-stand, balance and TUG norms by age and sex | cited-reference | `norms.json` (CDC, page per value) |

### (b) What "no hardcoded values" means, and how it is enforced
- Every number belongs to one of the 4 classes above.
- `params.json` records, for each entry: value, class, unit, source, script and date.
- Tests check that:
  - the schema is valid;
  - re-running `fit_params` reproduces the file;
  - the detector modules contain no numeric literals except 0, 1 and indices, and read every threshold through the loader;
  - time parameters are in seconds, never frames.

### (c) Validation protocol
- **Participants:** 12–15 consenting adults, plus the demo performer.
- **Per person, with a spotter and the venue camera setup:**
  - the 30-second chair stand twice: once in a saree or dhoti, once in track-pants
  - 3 half-stands and 3 arm-assisted stands
  - the 4 balance stages
  - 2 TUG trials against a stopwatch
- **Labelling rules, written before any tuning:**
  - Full: hips and knees reach upright, then return to the seat.
  - Half: leaves the seat but does not reach upright.
  - Arm-assisted: either hand leaves the chest, or pushes on the thigh or chair.
- **Labellers:** one student plus one outsider (ideally a physio). Report agreement and adjudicate disagreements.
- **Split:** before tuning, lock 4 held-out people in a hashed `split.json`, including at least 2 in a saree or dhoti and the demo performer. Tune with LOSO on the rest, and run the held-out set **once**.
- **Metrics, per clothing type, with person-bootstrap confidence intervals:**
  - count MAE, exact-match rate and false-count rate
  - half-stand and arm-use rejection rates
  - abstain rate
  - balance hold-time MAE
  - TUG total-time MAE against the stopwatch
- **Parity:**
  - Pilot landmarks are captured by the browser app's own record mode.
  - One JS detector is used everywhere, and the fitter runs it in Node.
  - A replay test checks that browser and Node counts are identical.
  - Decimation tests run at 10, 15 and 30 fps.
  - Check the frame rate at the venue.

### (d) Abstain and quality gates
- **Calibration gate:** checks stillness, head-height range, landmark visibility and that the full body is in frame. If it fails, the person redoes it; never guess.
- **Per rep:** a rep is "uncertain" when visibility drops, and "arms not verified" when the wrists can't be seen.
- **Session:**
  - a banner if the frame rate is below the fitted minimum
  - "count not reliable" if too many frames are uncertain
- **Display:** full / half-rejected / arm-rejected / uncertain.

### (e) Finale claims
- **Chair stand:** "On N held-out people (M in a saree or dhoti), counts matched two-labeller consensus in X of Y trials. Tuned on K other people, mostly young adults acting out the hard cases." Show the failures.
  - **Not allowed:** accuracy for older adults, clinical validity, parity with Ameya, "AI-trained", fall prediction.
- **Balance:** hold time per stage and detection of a lost position, checked against labels. Sway appears only as an uncalibrated exploratory trace, with no norms and no force-plate claim.
- **TUG:** an early module. Total-time error against a stopwatch on N people. No phase segmentation (prior work reached only ρ 0.48). The CDC 12 s figure is a US reference for the physio to interpret.
- **CDC norms** are labelled "US reference; no Indian norms exist yet".

### (f) Ideas from the Expansionist that survive, as roadmap slides only
- a physio tap to confirm or reject each counted stand, collecting ground truth
- Indian norms built with a physio college as the target partner
- a future open keypoint dataset, released only with ethics approval

The email to the Sit2Stand authors goes out today but must not block anything. **Dropped:** the Kinect-to-MediaPipe transfer, the KINECAL sway percentile, and any clothing claims beyond what we tested.

### (g) Role of outside datasets
- **Sit2Stand:** we re-implement and cite its method; we use none of its data without the authors' permission.
- **KINECAL:** download only `sample_set` and the CSVs, in the background, for optional smoke tests. It never sets a threshold.
- **HBEDB and LTMM:** cited in the deck only.
- **CDC:** `norms.json`.

If the downloads fail, nothing breaks.

### (h) Build order
- **22 Oct (Claude):**
  - browser record mode (landmarks, timestamps, local video)
  - `params.json` loader, schema and lint tests
  - `norms.json`
  - protocol, labelling-rules and consent documents
  - calibration plus its gate
- **22 Oct (students):** recruit participants, confirm the demo performer and outfit, rehearse the protocol.
- **23 Oct (students):** record.
- **23 Oct (Claude):**
  - chair-stand detector (time-based, with abstain logic)
  - keypress labelling tool
  - Node replay harness
  - decimation and parity tests
- **24 Oct (both labellers):** label all clips.
- **24 Oct (Claude):**
  - lock `split.json`
  - run the LOSO fit and generate `params.json`
  - balance hold time and TUG total time
- **25 Oct:** run the held-out set once.
- **26–27 Oct:** evaluation table and deck.
- **29–30 Oct:** freeze, rehearse, record a backup clip with a known correct count.

## The one thing to do first
Build the browser record mode, together with the one-page protocol and labelling rules. Recording is the bottleneck, and capturing landmarks through the browser pipeline is what makes the fitted parameters valid for the live demo.
