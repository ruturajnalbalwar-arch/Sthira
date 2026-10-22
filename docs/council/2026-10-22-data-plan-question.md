# Council question: Sthira training and validation data plan (22 Oct 2026)

## Decision
What real-world data should Sthira's prototype be trained, tuned and validated on? How should each decision parameter be derived from that data, and what may we honestly claim at the finale?

## Context
- **Product:** Sthira, a browser web app. A physiotherapist props a phone or laptop camera, and MediaPipe Pose (33 2D landmarks with visibility, `@mediapipe/tasks-vision` 1.1.1) runs live. It covers three CDC STEADI tests for older adults:
  1. 30-second chair stand: count only **full** stands, arms crossed; reject half-stands and arm-assisted stands
  2. 4-stage balance: hold time plus head/shoulder sway; the physio selects the foot stage
  3. Timed Up-and-Go (TUG)
- **Differentiator:** counting must still work when a saree, kurta or dhoti hides the knees. Plan: head and shoulder vertical trajectory, normalised by a 5-second per-person calibration (sit still, stand straight), with knee/hip angles used only when MediaPipe visibility is high.
- **Positioning:** functional scores shown next to CDC reference norms (US data). The physiotherapist interprets them. Never "predicts falls" or "diagnoses".
- **Hard rule from the user:** "the prototype must be based on real-world data, trained; no hardcoded values." Every decision threshold must be learned or tuned from real data, or loaded from a cited published data file. No invented constants.
- **Deadlines:**
  - pitch deck 28 Oct 2026
  - finale 31 Oct 2026 (9 days away), with a live prototype demo in front of an expert jury; no prototype means disqualification
  - live demo commitment: chair-stand counter in a saree vs track-pants; balance and TUG as early modules
- **Team and machine:** 3 students, Windows laptop, no GPU, Python 3.11 with MediaPipe 1.1.1 available. **The network is very slow:** PhysioNet takes about 29 s per request, GitHub about 15 s, PyPI downloads time out repeatedly. Big downloads (videos, depth) are unrealistic; CSVs, keypoint archives and sample subsets are realistic.

## Verified research (full detail in docs/research/2026-10-22-prior-work-and-datasets.md)

**Prior work**
- **Sit2Stand.ai** (Stanford, 2023):
  - segments sit-to-stand reps from the head's vertical path (nose and neck), normalised by height, smoothed, peak detection with prominence = (q99 - q01) / 2
  - 5xSTS time vs motion capture R = 0.997
  - 405 home phone videos, ages 18–96 (mean 37), 45° camera, arms folded, count fixed at 5
  - publishes OpenPose 2D keypoints and de-identified videos on Google Drive
  - **the repo has no licence**
  - 20 videos with arm use were removed by hand, and one failed because of a skirt
- **Ameya** (closed LiDAR app): 228 older adults vs clinician, 30s-STS ICC 0.928, TUG ICC 0.962. This is the accuracy bar, not a parity claim.
- **MediaPipe's rep-counter pattern:** k-NN pose classifier plus hysteresis (enter and exit thresholds).
- **Gaps:** no open project auto-checks arms crossed or full stands, or validates on loose clothing. TUG phases from head and shoulders gave only ρ = 0.48 in one study. No open camera-sway method is validated against a force plate.

**Datasets**
- **KINECAL** (PhysioNet, **CC0** ✅ verified):
  - Kinect v2, 25-joint skeleton per frame (tracked/inferred flag) at 30 fps
  - 90 people aged 18–92 (57 aged 65+)
  - STS-5 with arms crossed, TUG, quiet stance, semi-tandem, tandem, one-leg
  - `register.csv` (age, height, falls history, clinically-at-risk)
  - `sway_metric.csv` (RMS/mean distance, velocity, frequency, ellipse area, ML/AP, per movement per person)
  - 86 GB ZIP; skeletons are one small file per frame (tens of thousands of requests on our slow network)
  - a `sample_set.zip` and helper code exist
- **HBEDB** (force-plate sway, 76 people aged 60+, ODC-By) and **LTMM** (TUG times, 71 people aged 65–87, ODC-By).
- Non-commercial or derivative-banning sets (NTU, Toyota, ETRI) should be avoided.
- **No public data exists for:** 30-second chair-stand video, labelled half or incomplete stands, 4-stage balance video, TUG phase labels, saree/dhoti/South Asian clothing. These must come from our own consented pilot clips (written consent, a spotter beside each participant).

## Options on the table (not exhaustive)
- **A.** Learn chair-stand parameters from **Sit2Stand OpenPose keypoints** (closest domain: phone 2D). This needs the licence or permission question settled.
- **B.** Use **KINECAL** skeleton pixel coordinates (CC0) for sit/stand and TUG, despite the Kinect-vs-MediaPipe domain gap and slow per-frame downloads. Use `sway_metric.csv` as the real reference distribution for balance.
- **C.** **Our own consented pilot clips** (about 10–20 people) recorded with MediaPipe, including sarees and dhotis, half-stands and arm use. Labelled by us. The only source of the hard cases, but a small n.
- **D.** **Per-person calibration** as the main mechanism, with population-level parameters (e.g. hysteresis band fractions, smoothing cut-off, minimum hold time) learned from A, B or C.
- **E.** CDC STEADI norm tables as a cited data file (already published values).

## What we need from the council
1. Which data sources, for which parameters, and why.
2. How to train and evaluate honestly in 9 days: subject-wise splits, no leakage, metrics, and the minimum pilot size.
3. What "no hardcoded values" should mean operationally, so it is strict but achievable.
4. What we may claim at the finale, and what we must not.
5. A fallback if downloads fail.

## Stakes
A wrong data plan means either:
- a demo that miscounts in front of the jury (disqualification-level embarrassment), or
- claims an expert juror can puncture ("trained on what?").

Getting it right is the core of our "Innovation" and "Feasibility" story.
