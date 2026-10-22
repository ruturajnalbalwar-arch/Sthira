# Prior work and datasets for Sthira (researched 22 Oct 2026)

All thresholds in Sthira must be learned from real data or loaded from a cited source file, never hand-picked.

## 1. Similar projects

| Project | What it does | Method | Reported accuracy | Reusable? |
|---|---|---|---|---|
| **Sit2Stand.ai** (Boswell et al. 2023, npj Digital Medicine; github.com/stanfordnmbl/sit2stand-analysis) | Smartphone videos of 5x sit-to-stand, camera at 45° | OpenPose 2D. Reps segmented from the **head's vertical path** (mean of nose and neck y), divided by person height (95th percentile of ankle-to-nose distance), smoothed, then peak detection with prominence = (q99 - q01) / 2 | STS time vs motion capture R = 0.997 (5xSTS time, not 30s counts). Home videos: 32/405 rep-detection errors. Exclusions: 34 wrong angle, 29 long pauses, 25 out of frame, **20 arm use (removed by hand)**, 1 skirt. Mean age 37. | Method yes. **The repo has no licence, so we re-implement and do not copy.** Keypoints and de-identified videos are published. |
| **Ameya MMC** (PLOS Digital Health 2025, PMC12773808) | iPad LiDAR app | Body-height change with thresholds and filters | 228 older adults vs clinician: **30s-STS ICC 0.928**, **TUG ICC 0.962**, 5xSTS ICC 0.995, tandem ICC 0.812, data loss 3.1% | Closed app. We use it as the **accuracy bar and validation protocol** (not a parity claim, since LiDAR is not RGB). |
| MediaPipe pose classification + repetition counter (google-ai-edge/mediapipe) | Rep counting | k-NN on landmark embeddings, EMA smoothing, **hysteresis** counter | Not reported | Apache-2.0. Idea reusable. |
| PoseRAC (MiracleDance/PoseRAC) | Gym rep counting | Transformer on BlazePose landmarks | RepCount-pose MAE 0.236, OBO 0.560 (young gym users) | MIT. Idea only. |
| ai-squat-coach (nimrafaryal; the LinkedIn post) | Squat coach | MediaPipe. Hand-set knee-angle hysteresis (100° / 160°) | Not reported | MIT. Its knee-based rules fail when clothing hides the knees. It lists "a learned scoring model" as future work. |
| vaila tugturn.py | 3D markerless TUG phases | Spatial thresholds | Not reported | AGPL-3.0, so avoid it in a hosted app. |
| UCF TUG subtasks (PMC12762308) | TUG phases from head and shoulders | AlphaPose and MotionBERT | Spearman ρ = 0.479 vs manual (weak) | No code. A warning sign for head-only TUG phases. |
| Camera-placement study (PMC12978916) | Squat and push-up counting, 12 camera setups | Pose estimation | Rep MAE about 1.1, detection rate about 61% | Evidence that **camera placement matters**. |

**Gaps nobody fills:**
- automatic arms-crossed check
- full-stand (vs half-stand) rejection
- validation on older adults in loose clothing
- camera-based sway validated against a force plate in an open source

## 2. Datasets (verified where marked)

| Dataset | Content | Subjects | Relevance | Access and licence |
|---|---|---|---|---|
| **KINECAL** (physionet.org/content/kinecal/1.0.3) ✅ verified | Kinect v2 depth + 25-joint skeleton at 30 fps. Per-frame `.txt`, each joint flagged tracked or inferred. `register.csv` (age, sex, height, group, falls history, clinically-at-risk). `sway_metric.csv` (RMS/mean distance, velocity, frequency, 95% ellipse area; ML/AP; per movement). | 90 people aged 18–92 (57 aged 65+). Lab and informal settings. | STS-5 with arms crossed, TUG, quiet stance, semi-tandem, tandem, one-leg, 3 m walk | **CC0 1.0** ✅. Full ZIP 86.2 GB (180 GB uncompressed). Skeletons are one small file per frame, which is slow on our network. A `sample_set.zip` and `sway_utils.zip` are provided. |
| **Sit2Stand** (stanfordnmbl) | Smartphone home videos (de-identified) + OpenPose keypoints | 405 analysed, aged 18–96 (mean 37) | 5x STS with arms folded, 45° camera, count fixed at 5 | Google Drive. **No licence.** Ask the authors before using it beyond research. |
| HBEDB (PhysioNet, Santos & Duarte) | Force-plate centre of pressure at 100 Hz | 163 people, 76 aged 60+ | Quiet standing, eyes open/closed, firm/foam. Mini-BESTest, falls. | ODC-By, 357 MB |
| LTMM (PhysioNet) | Lower-back accelerometer | 71 people aged 65–87 | TUG **times** and fall history | ODC-By, 20.8 GB |
| Toronto Older Adults Gait Archive | Phone video (faces blurred) + 2D keypoints | 14 people aged 75–99 | Walking only. TUG and Berg scores | Figshare; licence not yet verified |
| SPPB skeletal dataset (ispstiima) | Skeletons from 3 cameras | Healthy and neurodegenerative | Chair stands, balance, gait | No licence |
| NTU RGB+D, Toyota Smarthome, ETRI-3D | RGB-D action datasets | Mostly not elderly (Toyota: 60–80) | Sit down / stand up | Request needed. Non-commercial, and NTU bans derivatives. **Avoid.** |

**Not found anywhere:**
- a public 30-second chair-stand video dataset
- labelled half or incomplete stands
- 4-stage balance video
- TUG video with phase labels
- any saree, dhoti or South Asian clothing pose dataset

These must come from our own consented pilot clips.

## 3. Network constraint (22 Oct)
Downloads on the development machine are slow: PhysioNet takes about 29 s to respond, GitHub about 15 s, and PyPI downloads time out. Any data plan must use small downloads: CSV tables, sample subsets and keypoint archives, not raw video or depth.
