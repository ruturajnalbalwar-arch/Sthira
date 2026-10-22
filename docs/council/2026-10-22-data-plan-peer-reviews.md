# Peer reviews (data-plan council, 22 Oct 2026)

Anonymisation mapping: A = Executor, B = Contrarian, C = Outsider, D = Expansionist, E = First Principles Thinker.

**Unanimous (5 of 5 reviewers):**
- **Strongest: E.** It is the only response that removes the main threshold by design: a full stand is defined against the person's own calibrated seated and standing heights, and arms-crossed is a ratio to the calibrated shoulder width. Only a few unitless numbers remain to fit. Its three-class rule for "no hardcoded values" (cited protocol constant, per-person calibration value, fitted JSON from a re-runnable script) is strict and achievable, and its finale claim is narrow.
- **Runner-up: A.** It has the most actionable day-by-day plan and keeps the live-demo performer out of tuning. Its "no numeric literal except 0/1" test is brittle.
- **Biggest blind spot: D.** It is a growth pitch that ignores the 9-day timeline, demo risk, ethics of releasing participant data, leakage and evaluation. It repeats the Kinect-to-MediaPipe transfer and the camera-vs-Kinect/force-plate sway comparison that B shows are false.

**What all five advisors missed (raised by the reviewers):**
1. **Pipeline parity: Python vs browser.** (5 of 5 reviewers.) Parameters fitted on Python MediaPipe at about 30 fps will run in browser tasks-vision on a laptop without a GPU, possibly at 10–15 fps and with a different model variant. Dwell and smoothing must be expressed in **seconds, not frames**. Pilot landmarks should be captured by or replayed through the **browser** pipeline before parameters are frozen, and the frame rate checked at the venue.
2. **Biased error figure.** (2 of 5.) Choosing parameters by leave-one-subject-out error and then reporting that same error overstates accuracy. Use nested validation or a locked held-out set of people.
3. **Calibration failure.** (3 of 5.) Older adults may not stand fully upright, or may use their arms or fidget during the 5 seconds. A calibration quality gate is needed.
4. **Abstain instead of guessing.** (1 of 5.) When visibility or calibration is poor, show "uncertain" instead of a wrong count. Safer, and it protects the demo.
5. **Age bias and acted hard cases.** (3 of 5.) Young volunteers act out half-stands and arm use, and fast young stands skew dwell and smoothing toward undercounting slow elderly stands.
6. **Labelling protocol.** (1 of 5.) Write the full / half / arm-assisted rules before tuning, and use two labellers, one outside the team.
7. **Safety and ethics.** (2 of 5.) Gamified unsupervised balance exercises; consent and ethics for filming older adults; whether releasing any data is allowed.
8. **No clinician on the team** and no customer evidence. (1 of 5.)
9. **TUG claim.** (1 of 5.) Nobody said what to claim for TUG given ρ = 0.48 in prior work.
