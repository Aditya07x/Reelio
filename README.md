# Reelio — Adaptive Latent State Engine
> 🔗 [View Interactive README](https://aditya07x.github.io/Reelio/)

> An on-device behavioral intelligence system for Android that passively monitors Instagram Reels sessions, models doomscrolling capture probability using a Hidden Markov Model, and surfaces interpretable, personalized insights — all without any data leaving your device.

---

## Table of Contents

- [What It Does](#what-it-does)
- [Architecture Overview](#architecture-overview)
- [Feature Layers](#feature-layers)
- [The ALSE Model (Python)](#the-alse-model-python)
- [UI — Reelio Dashboard](#ui--reelio-dashboard)
- [Micro-Probe Survey System](#micro-probe-survey-system)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Setup & Build](#setup--build)
- [Permissions Required](#permissions-required)
- [Data & Privacy](#data--privacy)
- [Known Limitations](#known-limitations)
- [Roadmap](#roadmap)

---

## What It Does

Reelio runs silently in the background using Android's Accessibility Service API. Every time you open Instagram, it begins capturing over 80 behavioral signals per reel — scroll speed, dwell time, sensor data, ambient light, battery state, audio output, previous app, time-of-day patterns, and more.

When you close Instagram, the data is fed into an on-device Hidden Markov Model (the **Adaptive Latent State Engine**, or ALSE) that classifies each reel as either a **CASUAL** or **DOOM** latent state — i.e., passive browsing vs. compulsive capture. The model learns your personal baseline over time, so its classifications become increasingly accurate to your specific usage patterns.

After the session ends, a check-in notification prompts you to rate your mood, regret, and session intentionality. These self-report scores are fed back into the model as additional features.

All of this is visualized in a React-based WebView dashboard embedded inside the app.

---

## Architecture Overview

```
Instagram App (foreground)
        │
        ▼
InstaAccessibilityService.kt          ← Kotlin accessibility layer
  ├── Per-reel feature extraction      (80+ signals captured live)
  ├── Sensor fusion (accel + light)    (SensorEventListener)
  ├── Session lifecycle management     (Handler + 20s timeout)
  ├── CSV writer → insta_data.csv      (append per reel, Schema v4)
  ├── Post-session DB insert           (Room database)
  └── Python inference call            (Chaquopy bridge)
        │
        ▼
reelio_alse.py                        ← Python model (runs on-device via Chaquopy)
  ├── CSV parser + schema validation
  ├── Feature preprocessing (6 HMM features + contextual priors)
  ├── ReelioCLSE — HMM with 9 architectural pillars
  │     ├── Bayesian personalized baseline
  │     ├── Self-calibrating emission weights (KL-divergence)
  │     ├── Hierarchical temporal memory (3 banks)
  │     ├── Continuous-Time Markov Chain (session gap model)
  │     ├── Survival framing (geometric hazard rate)
  │     ├── Regime change detector
  │     ├── Sparse-data guard (confidence gating)
  │     ├── Contextual state priors (logistic regression)
  │     └── Composite Doom Score heuristic (interpretable UI output)
  └── State persistence → alse_model_state.json
        │
        ▼
MainActivity.kt + WebView             ← JavaScript/React UI
  └── app.jsx                         ← Reelio dashboard (React + Recharts + Lucide)
        ├── Home screen (live session summary)
        └── Dashboard screen
              ├── Cognitive Stability Index (gauge)
              ├── HMM State Dynamics diagram (animated SVG)
              ├── 14-Day Risk Heatmap (tap-to-inspect bars)
              ├── Top 3 Doom Drivers (model-weighted ranking)
              ├── Capture Timeline (area chart, per-reel slider)
              └── Doom Score Anatomy (7-component breakdown)
```

---

## Feature Layers

The service captures signals across 8 layers:

### Layer 1 — Interaction Signals (per reel)
| Signal | Description |
|---|---|
| `DwellTime` | Seconds spent on this reel |
| `AvgScrollSpeed` / `MaxScrollSpeed` | Swipe velocity (events/sec) |
| `ScrollPauseCount` / `ScrollPauseDurationMs` | Mid-reel hesitations |
| `SwipeCompletionRatio` | Ratio of clean swipes vs. aborted swipes |
| `BackScrollCount` | How many times the user scrolled back (rewatch) |
| `Liked` / `Commented` / `Shared` / `Saved` | Engagement flags |
| `LikeLatency` / `CommentLatency` etc. | Time from reel start to interaction |
| `AppExitAttempts` | Rapid exits + re-entries within 20 seconds |
| `ProfileVisits` / `HashtagTaps` | Deep engagement indicators |
| `HasCaption` / `CaptionExpanded` / `HasAudio` / `IsAd` | Content metadata |

### Layer 2 — Physical Context (per session)
| Signal | Description |
|---|---|
| `AccelVariance` / `PostureShiftCount` | Motion from accelerometer |
| `IsStationary` | Low-movement detection |
| `DeviceOrientation` | Portrait vs. Landscape |
| `AmbientLuxStart` / `AmbientLuxEnd` / `IsScreenInDarkRoom` | Light sensor |
| `BatteryStart` / `BatteryDeltaPerSession` / `IsCharging` | Power state |
| `Headphones` / `AudioOutputType` | Audio device (SPEAKER / WIRED / BLUETOOTH) |

### Layer 3 — System Context (per session start)
| Signal | Description |
|---|---|
| `PreviousApp` / `PreviousAppDuration` / `PreviousAppCategory` | What you were doing before |
| `DirectLaunch` | Whether Instagram was opened from the home screen |
| `TimeSinceLastSessionMin` | Gap since last session |
| `DayOfWeek` / `IsHoliday` / `IsWeekend` | Calendar context |
| `ScreenOnCount1hr` / `ScreenOnDuration1hr` | Phone usage in prior hour |
| `NightMode` / `DND` | UI mode and Do Not Disturb state |
| `SessionTriggeredByNotif` | Whether an Instagram notification triggered the session |

### Layer 4 — Within-Session Derived Features (per reel)
| Signal | Description |
|---|---|
| `DwellTimeZscore` / `DwellTimePctile` | Dwell relative to your own session baseline |
| `DwellAcceleration` / `SessionDwellTrend` | Is attention increasing or collapsing over the session? |
| `EarlyVsLateRatio` | Dwell in first vs. second half of session |
| `InteractionRate` / `InteractionBurstiness` / `InteractionDropoff` | Engagement dynamics |
| `LikeStreakLength` | Consecutive likes — proxy for mindless engagement |
| `ScrollIntervalCV` / `ScrollRhythmEntropy` | Variability and randomness of scroll cadence |
| `ScrollBurstDuration` / `InterBurstRestDuration` | Burst-rest cycle |
| `SavedWithoutLike` / `CommentAbandoned` | Behavioral inconsistency signals |

### Layer 5 — Cross-Session Memory (per session start)
| Signal | Description |
|---|---|
| `SessionsToday` / `TotalDwellTodayMin` | Daily usage counters |
| `LongestSessionTodayReels` | Peak session size today |
| `DoomStreakLength` | Consecutive doom-labeled sessions |
| `MorningSessionExists` | First-thing-in-morning usage flag |

### Layer 6 — Circadian & Physiological Proxies
| Signal | Description |
|---|---|
| `CircadianPhase` | Normalized time of day [0.0–1.0] from midnight |
| `SleepProxyScore` | Heuristic: first session before 6am = low sleep |
| `EstimatedSleepDurationH` | Inferred from prior session end time |
| `ConsistencyScore` | Variance of first daily session times over last 7 days |

### Layer 7 — Content Diversity (per session)
| Signal | Description |
|---|---|
| `UniqueAudioCount` | Distinct audio tracks (proxy for content variety) |
| `RepeatContentFlag` / `ContentRepeatRate` | Content repetition detection (placeholder, next phase) |

### Layer 8 — Self-Report Micro-Probes (post-session)
| Signal | Description |
|---|---|
| `PostSessionRating` | How drained/refreshed you felt (1–5) |
| `MoodBefore` / `MoodAfter` / `MoodDelta` | Pre- and post-session mood |
| `RegretScore` | Inverted intentionality score |
| `IntendedAction` | What you intended when opening the app |
| `ActualVsIntendedMatch` | Whether you followed through |

---

## The ALSE Model (Python)

The model lives in `reelio_alse.py` and runs on-device via [Chaquopy](https://chaquo.com/chaquopy/). It has no server dependency.

### 9 Architectural Pillars

**1. Personalized Bayesian Baseline**
HMM emission parameters are initialized from rolling history, not global defaults. Your personal mean and variance per feature define the prior.

**2. Self-Calibrating Emission Weights**
After each session, KL-divergence between the CASUAL and DOOM emission distributions is computed per feature. Features that better separate the two states get higher weight in the inference. The `feature_weights` vector is persisted to `alse_model_state.json` and sent to the UI.

**3. Hierarchical Temporal Memory**
Three memory banks (recent: 5 sessions, medium: 20, long: 50) are maintained. The model interpolates between them based on data density, preventing both short-term overfitting and long-term staleness.

**4. Continuous-Time Markov Chain (CTMC)**
Session gaps are modeled as a CTMC using matrix exponential. The longer the gap since your last session, the more the transition matrix reverts toward the stationary distribution — modeling the realistic "cooling off" effect.

**5. Survival Framing**
Instead of treating each reel independently, the model fits a geometric hazard rate for each state — modeling how likely you are to *stop* scrolling at each reel. DOOM state has a much lower hazard rate (you keep going).

**6. Regime Change Detector**
Tracks the KL-divergence between recent behavior and the stored baseline. If it exceeds a threshold (life event, new phone, holiday), the long-term memory bank is frozen and only recent/medium memory updates — preventing drift corruption.

**7. Sparse-Data Guard**
Composite confidence formula: `model_confidence = C_volume × C_separation × C_stability`, where C_volume = min(n_sessions / 20, 1.0), C_separation measures Casual/Doom state distinguisability, and C_stability measures regime stability. Below 20 sessions, all inferences are blended with the prior in proportion to confidence. The UI shows this value explicitly.

**8. Contextual State Priors**
A lightweight logistic regression is run at session start using 4 physical context features (time of day, charging state, previous app category, ambient light) to set the initial state probability — before any reel-level evidence is incorporated.

**9. Composite Doom Score**
A fully interpretable, model-free heuristic score (0–100) with 7 named components. Used exclusively for the dashboard UI so users can understand *why* a session was flagged, independent of the HMM's latent probability.

| Component | CSV Input | Weight |
|---|---|---|
| Session Length | `CumulativeReels` | 25% |
| Exit Conflict | `AppExitAttempts` | 20% |
| Rapid Re-entry | `TimeSinceLastSessionMin` | 15% |
| Scroll Automaticity | `ScrollRhythmEntropy` | 15% |
| Dwell Collapse | `SessionDwellTrend` | 10% |
| Rewatch Compulsion | `BackScrollCount` | 10% |
| Environment | `IsScreenInDarkRoom + IsCharging + CircadianPhase` | 5% |

### HMM Inference Features (the 6 core inputs)

| Feature | CSV Column | Transformation |
|---|---|---|
| `log_dwell` | `DwellTime` | `log(max(x, 0.001))` |
| `log_speed` | `AvgScrollSpeed` | `log(max(x, 0.001))` |
| `rhythm_dissociation` | `ScrollRhythmEntropy` | raw |
| `rewatch_flag` | `BackScrollCount > 0` | binary |
| `exit_flag` | `AppExitAttempts > 0` | binary |
| `swipe_incomplete` | `1 - SwipeCompletionRatio` | inverted ratio |

---

## UI — Reelio Dashboard

The dashboard is a React 18 app served from `assets/www/` inside a `WebView`. It communicates with the Kotlin layer via a `JavascriptInterface` (`window.Android`) and receives data via `window.injectedJsonData` — a JSON payload injected by `MainActivity` on every page load.

### Home Screen
- Service status indicator with live pulsing dot
- Current session summary: duration, reels, average dwell, capture probability
- Sessions today / total dwell / model confidence
- One-tap navigation to dashboard
- CSV export + data clear controls

### Dashboard Screen
- **Cognitive Stability Index** — arc gauge (0–100 score derived from HMM parameters)
- **State Dynamics Diagram** — animated SVG showing CASUAL ↔ DOOM transition probabilities with a live orbiting dot
- **14-Day Risk Heatmap** — bar chart of daily average doom score. Tap any bar to reveal: exact date, doom %, session count, reel count
- **Top 3 Doom Drivers** — ranked by `component_score × base_weight × model_feature_weight_boost`. Updates live as the model learns which signals matter most for you
- **Capture Timeline** — area chart of per-reel `P(doom)` with a draggable reel cursor
- **Doom Score Anatomy** — collapsible section showing all 7 components with progress bars
- **Behavioral Insight Cards** — 4 auto-generated insight texts from HMM parameters

---

## Micro-Probe Survey System

Reelio presents a 3-step check-in survey after qualifying sessions (>5 reels or >1 exit attempt). The notification fires exactly 20 seconds after your last interaction via a `Handler`-scheduled `Runnable`.

### Survey Steps
1. **Post-Session Rating** — "How do you feel after this session?" (1 = Refreshed, 5 = Drained)
2. **Current Mood** — "Rate your current mood" (1 = Very low, 5 = Great)
3. **Intentionality** — "Did you intend to scroll this long?" (1 = Not at all, 5 = Completely intentional) — inverted to produce `RegretScore`

A separate **Pre-Session Intention Probe** fires (25% probability) when you open Instagram after a gap of >30 minutes, asking what you intended to do. This value is compared against actual session length to compute `ActualVsIntendedMatch`.

Survey results are saved to `SharedPreferences` and picked up at the start of the next session, where they are written as the last columns of the CSV row for that session.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Android | Kotlin, Accessibility Service API, Room Database, Coroutines |
| On-device Python | Chaquopy 15.0 (CPython 3.11 on Android) |
| Python ML | NumPy, pandas, scipy.optimize |
| UI | React 18 (UMD), Recharts, Lucide React, Google Fonts |
| Sensors | Android SensorManager (Accelerometer + Light) |
| Usage Stats | UsageStatsManager (previous app tracking) |
| Storage | CSV (primary), Room SQLite (secondary) |

---

## Project Structure

```
InstagramTracker/
├── app/src/main/
│   ├── java/com/example/instatracker/
│   │   ├── InstaAccessibilityService.kt   ← Core tracking engine
│   │   ├── MainActivity.kt                ← WebView host + JS bridge
│   │   ├── MicroProbeActivity.kt          ← Post-session survey UI
│   │   ├── IntentionProbeActivity.kt      ← Pre-session intention survey
│   │   ├── DatabaseProvider.kt
│   │   └── db/
│   │       ├── AppDatabase.kt
│   │       ├── SessionEntity.kt
│   │       └── SessionDao.kt
│   ├── python/
│   │   └── reelio_alse.py                 ← ALSE model (HMM + heuristics)
│   ├── assets/www/
│   │   ├── index.html
│   │   └── app.jsx                        ← React dashboard
│   └── AndroidManifest.xml
└── README.md
```

---

## Setup & Build

### Prerequisites
- Android Studio Hedgehog or later
- Android SDK 26+
- Chaquopy plugin (already configured in `build.gradle`)
- Python 3.11 packages: `numpy`, `pandas`, `scipy` (declared in `build.gradle` pip block)

### Build Steps

1. Clone the repo:
   ```bash
   git clone https://github.com/yourhandle/reelio.git
   cd reelio
   ```

2. Open in Android Studio → let Gradle sync complete (Chaquopy will download Python and pip packages — this can take a few minutes on first build).

3. Build and install on a physical device (emulators do not support all sensor APIs):
   ```
   Run → Run 'app'
   ```

4. On first launch:
   - Grant **Accessibility Service** permission (the app will prompt you)
   - Grant **Usage Stats** permission (Settings → Apps → Special App Access → Usage Access)
   - Grant **Notification** permission (Android 13+)

5. Open Instagram. The service activates automatically.

> **Note:** The app must be built and run on a physical Android device. Accessibility Services and real sensor data are not available on emulators.

---

## Permissions Required

| Permission | Purpose |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Core tracking — reads UI events from Instagram |
| `PACKAGE_USAGE_STATS` | Previous app detection (which app you came from) |
| `POST_NOTIFICATIONS` | Post-session check-in notifications |
| `RECEIVE_BOOT_COMPLETED` | Re-enable service after device restart |
| `FOREGROUND_SERVICE` | Keep accessibility service alive |
| `WRITE_EXTERNAL_STORAGE` | CSV export (Android < 10) |
| `READ_EXTERNAL_STORAGE` | CSV export (Android < 10) |
| `ACCESS_NETWORK_STATE` | WebView font loading only |
| `REQUEST_INSTALL_PACKAGES` | Chaquopy Python runtime (build-time only) |

No internet permission is used for data transmission. All behavioral data stays on device.

---

## Data & Privacy

- **Zero data leaves your device.** There are no API calls, no analytics SDKs, no crash reporters.
- All CSV data is written to `app-specific external storage` (`getExternalFilesDir(null)`) — accessible only to this app without root.
- The Room database (`insta_data.db`) and model state (`alse_model_state.json`) are stored in internal storage, inaccessible without root.
- You can export the raw CSV via the Export button in the app, or delete all data via the Clear Data button.
- The app does not read, store, or transmit the content of any Instagram posts, comments, captions, usernames, or media.

---

## Known Limitations

- **Accessibility Service detection:** Instagram occasionally updates its UI layout, which can cause the accessibility event tree to change. Reel boundary detection (via `TYPE_WINDOW_CONTENT_CHANGED`) may occasionally double-count or miss a reel transition.
- **Audio track identity:** `UniqueAudioCount` uses accessibility content descriptions as a proxy for track identity, not actual audio fingerprinting. It is an approximation.
- **Repeat content detection:** `RepeatContentFlag` and `ContentRepeatRate` are currently placeholder zeros — full implementation requires content fingerprinting, which is planned.
- **MicroMovementRms:** Currently logged as `0f` — true RMS micro-movement requires integrating raw accelerometer over reel duration, which has a performance overhead tradeoff being evaluated.
- **Calendar heatmap:** Shows real data only once you have sessions across multiple days. Single-day data produces a single bar.
- **Python cold start:** The first inference per session takes ~2–4 seconds due to Chaquopy Python initialization. Subsequent inferences in the same session are near-instant.

---

## Roadmap

**Short term**
- [ ] Weekly summary notification ("Your doom rate dropped 12% this week")
- [ ] Daily usage limit with a soft nudge notification at threshold
- [ ] Settings screen (customizable session timeout, notification toggle, theme)
- [ ] Offline-capable font bundling (remove Google Fonts CDN dependency)

**Medium term**
- [ ] Content fingerprinting for true repeat-content detection
- [ ] Sleep inference from overnight phone inactivity gaps
- [ ] Streak visualization (doom-free days counter)
- [ ] Exportable PDF weekly report

**Long term**
- [ ] Multi-app support (TikTok, YouTube Shorts)
- [ ] Optional encrypted cloud backup of model state only (no raw behavioral data)
- [ ] On-device intervention overlays (gentle friction after doom threshold crossed)

---

## License

MIT License. See `LICENSE` for details.

