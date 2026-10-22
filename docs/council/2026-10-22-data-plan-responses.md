# Anonymised advisor responses (data-plan council, 22 Oct 2026)

**Response A:**
Only Option C (own pilot clips) protects the live demo. Sit2Stand has no licence and is on Google Drive, and the full KINECAL set is 86 GB. Neither one has a single saree in it. Start C today and treat everything else as optional.

Next hour:
1. Write `record.py`: OpenCV webcam, MediaPipe 1.1.1, saves per-frame landmarks plus visibility to CSV and keeps the video locally.
2. Write a one-page consent form.
3. Start background downloads of only `register.csv`, `sway_metric.csv` and `sample_set.zip` from KINECAL. Do not try the 86 GB ZIP.
4. Email the Sit2Stand authors. Don't block on the reply.
5. Copy the CDC norm tables into `norms.json`, with a citation and page numbers for each value (about 30 minutes).

What "no hardcoded values" means in practice: every detector constant lives in `params.json`, and each entry records its source (dataset name, fit script, date). `fit_params.py` rebuilds the whole file from the CSVs. A unit test fails if the detector has any numeric literal other than 0 or 1.

Day by day:
- **22 Oct:** recorder, consent form, `norms.json`, downloads started.
- **23–24 Oct:** record 12–15 people. Each does a 30-second chair stand twice (once in a saree or dhoti, once in track-pants), 3 deliberate half-stands, 3 arm-assisted stands, the 4 balance stages and 2 TUG trials. Label in a key-press tool.
- **25 Oct:** grid-search the enter/exit band fractions, the smoothing cut-off and the minimum dwell time, with leave-one-subject-out validation. Report count error and the half-stand and arm-use rejection rates. Keep the live-demo performer out of tuning.
- **26 Oct:** port the logic to the browser.
- **27 Oct:** evaluation table for the deck. Use KINECAL `sway_metric.csv` as context only.
- **28 Oct:** deck.
- **29–30 Oct:** freeze the parameters, rehearse 20 times, record a backup video.

Fallback: the core of the plan needs no downloads. Claim at the finale: "Tuned on N consented participants; leave-one-subject-out count error X."

**Response B:**
The fatal flaw is that the public data contains none of the hard cases. Sarees, half-stands, arm-assisted stands, 30-second counts and 4-stage balance exist only in your own 10–20 pilot clips. Sit2Stand removed its arm-use cases and its one skirt failure by hand, so it cannot teach you to reject anything. The honest answer to "trained on what?" is "about 15 people we recorded ourselves this week, probably young relatives and classmates, not older adults." Say that first.

Option B is a trap. Tens of thousands of per-frame requests at 29 s each will not finish in 9 days; only `sample_set.zip` is realistic. Kinect skeleton coordinates are not MediaPipe landmarks, so any threshold you transfer is a guess with extra steps.

The sway "reference distributions" are false comparisons. `sway_metric.csv` is Kinect-skeleton based and HBEDB is force-plate centre of pressure. Normalised head sway from a camera is a different quantity, so plotting it against either implies a validation you have not done.

Leakage is almost guaranteed at n≈15. Only leave-one-subject-out is defensible, and you must report confidence intervals, which will be wide.

"No hardcoded values" is literally impossible. The calibration duration, smoothing window, visibility cut-off and prominence rule are all constants. Define the rule operationally: every threshold is fitted or cited, and the remaining design constants are listed and justified.

Ethics: recording older adults doing fall-risk tests without institutional ethics approval is a risk with expert judges in the room. Recruiting older participants in sarees within about 4 days is optimistic.

The demo is the biggest risk. The camera-placement study found about 61% detection across setups. Fix the camera position and distance, record the pilot in conditions like the venue's, and rehearse with the actual saree.

**Response C:**
The phrase that will sink you is "trained on real-world data." To an outsider, "trained" means a model learned from lots of examples. You actually have a handful of cut-off numbers tuned on about 10–20 people you recorded yourselves. Call it "tuned on" or "checked against." If the honest answer is "15 people, mostly classmates," the word "trained" will look inflated.

The headline saree claim has no outside evidence. A juror will ask who was in the clips, whether any were older adults, who decided what a "full stand" was, and whether that person built the system. Have someone outside the team count the reps by eye. Show one plain table: people, reps, correct, missed and false counts, split by saree and track-pants. Include the failures; two misses earn more trust than a perfect live demo.

Keep other people's numbers off your slides. If R = 0.997 or ICC 0.928 appears near your results, a juror will assume they are yours. Borrowed data needs a one-sentence reason, otherwise it sounds like padding. Using Sit2Stand when its repo has no licence undercuts the "honest data" story. Showing US CDC norms next to Indian older adults invites "do these apply here?", so answer it on the slide.

Define the jargon (STEADI, TUG, ICC, hysteresis, landmark visibility, prominence) in five words or drop it. One live run with one person proves little and can go wrong, so bring a recorded backup clip with a known correct count.

**Response D:**
You're treating your own pilot clips as the weak option. They're your biggest asset: nobody has labelled chair-stand data with sarees, dhotis, half-stands and arm-assisted stands. Collect 15–20 consented people, label every rep as full, half or arm-assisted, and present it as "Sthira-STS-Clothing v0, the first open keypoint dataset of functional tests in South Asian clothing". Release keypoints only, not video, for privacy.

Bigger upsides:
1. Every clinic session is labelled data. One physio tap confirming or rejecting each counted stand gives free ground truth. Pitch it as a system that improves with every clinic visit.
2. Indian norms. CDC norms are US data, and consented sessions can build the first Indian older-adult functional reference data. Name a physio college as a target partner.
3. Cross-sensor proof. Tune the head-trajectory parameters on KINECAL and test them unchanged on your MediaPipe saree clips. If they hold, the method is clothing- and sensor-agnostic.
4. Email the Sit2Stand authors today and offer your saree data as an extension of their method. A reply is a credibility asset.
5. Calibration that adapts to each body and any clothing: abayas, lungis, sarongs, braces, obesity. That's a Global South market.
6. A sway percentile against KINECAL `sway_metric.csv` (57 people aged 65+, with falls history), shown as context and not called a prediction.

**Response E:**
The real question is what makes a count trustworthy for this person, in this saree, in front of this camera. That answer doesn't come from KINECAL or Sit2Stand. A chair stand is seated head height going up to standing head height and back. A "full stand" means reaching your own upright height, and the 5-second calibration measures exactly those two numbers for the person being tested. So the main threshold is built into the method. Arms crossed works the same way: wrist-to-opposite-shoulder distance divided by the calibrated shoulder width.

That leaves a few unitless numbers: the fraction of the range that counts as "reached", the hysteresis margin, the smoothing cut-off and the minimum dwell time. Fit those on labelled clips of the hard cases. No public dataset has any of these. Data from Kinect, OpenPose or 37-year-olds in track pants tunes you for the wrong problem, and a juror will ask "trained on Kinect for a MediaPipe saree demo?"

Reframe the plan: the pilot clips are the training and validation set. Use 12–15 people with leave-one-subject-out tuning, and report count MAE and exact-match rate per clothing type. Record the demo performer in the demo outfit and room. External data is for citations: Sit2Stand gives the method (re-implemented and cited), and KINECAL sway and the CDC tables are reference distributions. Neither sets a threshold.

"No hardcoded values" means every number falls into one of three classes: a cited protocol constant (30 s, CDC norms), a per-person calibration value, or a fitted value saved as JSON by a script you can re-run.

Finale claim: "counts matched physiotherapist labels on N held-out people, including M in saree or dhoti." Never claim older-adult population accuracy.
