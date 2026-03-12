import asyncio
import json
import logging
import sys
import traceback
from pathlib import Path
from typing import Optional, List
from uuid import uuid4

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, JSONResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

import numpy as np
import pandas as pd

# ---------------------------------------------------------------------------
_THIS_DIR = Path(__file__).resolve().parent
_PROJECT_ROOT = _THIS_DIR.parent.parent
_PYTHON_SRC = _PROJECT_ROOT / "app" / "src" / "main" / "python"

if str(_PYTHON_SRC) not in sys.path:
    sys.path.insert(0, str(_PYTHON_SRC))

from synthetic_validation_harness import (
    CalibrationProfile,
    generate_synthetic_dataset,
)
from reelio_alse import (
    EXPECTED_SCHEMA_VERSION,
    DOOM_PROBABILITY_THRESHOLD,
    COMPONENT_NAMES,
    run_dashboard_payload,
    preprocess_session,
    UserBaseline,
    ReelioCLSE,
    DoomScorer,
    RegimeDetector,
    RegretValidator,
    effective_session_reel_count,
    dedupe_session_rows,
    normalize_prestate_risk,
    normalize_comparative_rating,
    compute_supervised_doom_label,
)

# ---------------------------------------------------------------------------
app = FastAPI(title="Reélio Simulation Playground API")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_credentials=True, allow_methods=["*"], allow_headers=["*"])
app.mount("/static", StaticFiles(directory=_THIS_DIR / "static"), name="static")

# Session store — keyed by UUID to avoid concurrency issues
_session_store: dict[str, dict] = {}

SURVEY_COLUMNS = [
    "PostSessionRating", "IntendedAction", "ActualVsIntendedMatch",
    "RegretScore", "MoodBefore", "MoodAfter", "MoodDelta",
    "PreviousContext", "DelayedRegretScore", "ComparativeRating",
]


# ===========================================================================
# Request Models
# ===========================================================================
class SimulationRequest(BaseModel):
    scenario: str
    sessions: int = Field(default=60, ge=3, le=500)
    seed: int = Field(default=42)

    # Session shape — real-data defaults
    session_reels_mu: float = Field(default=22.0, ge=2.0, le=200.0)
    session_reels_sigma: float = Field(default=8.0, ge=0.5, le=50.0)
    dwell_mu: float = Field(default=7.0, ge=0.3, le=30.0)
    dwell_sigma: float = Field(default=6.8, ge=0.1, le=20.0)
    speed_mu: float = Field(default=10.4, ge=0.1, le=30.0)
    speed_sigma: float = Field(default=8.0, ge=0.1, le=20.0)
    gap_q10: float = Field(default=6.0, ge=0.0, le=120.0)
    gap_q50: float = Field(default=35.0, ge=1.0, le=600.0)
    gap_q90: float = Field(default=240.0, ge=10.0, le=2880.0)
    exit_rate_base: float = Field(default=0.06, ge=0.0, le=1.0)
    rewatch_rate_base: float = Field(default=0.08, ge=0.0, le=1.0)

    # Survey — per-field fill rates (replace single survey_fill_pct)
    survey_fill_pct: float = Field(default=40.0, ge=0.0, le=100.0, description="Global fallback fill %")
    mood_before_fill_rate: float = Field(default=60.0, ge=0.0, le=100.0)
    post_session_rating_fill_rate: float = Field(default=40.0, ge=0.0, le=100.0)
    regret_score_fill_rate: float = Field(default=40.0, ge=0.0, le=100.0)
    mood_after_fill_rate: float = Field(default=40.0, ge=0.0, le=100.0)
    comparative_rating_fill_rate: float = Field(default=13.0, ge=0.0, le=100.0)
    actual_vs_intended_fill_rate: float = Field(default=13.0, ge=0.0, le=100.0)
    delayed_regret_fill_rate: float = Field(default=0.0, ge=0.0, le=100.0)
    override_regret: Optional[int] = None
    override_mood_before: Optional[int] = None
    override_comparative: Optional[int] = None
    intended_action_categories: List[str] = Field(
        default=["Bored / Nothing to do", "Quick break (intentional)", ""]
    )
    previous_context_categories: List[str] = Field(
        default=["Relaxing", "Boredom", ""]
    )

    # Realism — session structure
    binge_probability: float = Field(default=5.0, ge=0.0, le=100.0)
    burst_session_rate: float = Field(default=15.0, ge=0.0, le=100.0)
    interruption_rate: float = Field(default=8.0, ge=0.0, le=100.0)
    dwell_spike_rate: float = Field(default=5.0, ge=0.0, le=100.0)
    weekend_multiplier: float = Field(default=1.0, ge=1.0, le=3.0)

    # Realism — survey
    survey_fatigue_decay: float = Field(default=0.0, ge=0.0, le=1.0)
    correlated_survey_skip: bool = False
    dishonesty_rate: float = Field(default=0.0, ge=0.0, le=100.0)

    # Data quality bugs
    simulate_flood_bug: bool = False
    flood_probability: float = Field(default=3.0, ge=0.0, le=100.0)
    flood_repeat_count: int = Field(default=100, ge=2, le=1000)
    simulate_double_write: bool = False
    double_write_completion_factor: float = Field(default=0.7, ge=0.1, le=1.0)

    # Sensor realism
    lux_mode: str = Field(default="bimodal")  # bimodal | dark | bright | uniform
    is_charging_rate: float = Field(default=46.0, ge=0.0, le=100.0)
    device_orientation_portrait_rate: float = Field(default=95.0, ge=0.0, le=100.0)

    # Content realism
    ad_rate: float = Field(default=12.0, ge=0.0, le=100.0)
    ad_skip_latency_mu: float = Field(default=9214.0, ge=100.0, le=60000.0)
    ad_skip_latency_sigma: float = Field(default=8000.0, ge=100.0, le=30000.0)

    # Behavioral feedback loops
    content_narrowing_rate: float = Field(default=0.0, ge=0.0, le=1.0)
    mood_carryover_strength: float = Field(default=0.0, ge=0.0, le=1.0)
    sleep_debt_enabled: bool = False
    exit_conflict_shape: str = Field(default="flat")  # flat | early_spike | doom_collapse
    notif_trigger_rate: float = Field(default=0.0, ge=0.0, le=100.0)

    # Personality archetype
    personality: str = Field(default="neutral")  # neutral | binge_prone | avoidant | hunter | zombie | night_owl


class RepredictRequest(BaseModel):
    csv_text: str
    scenario: str = "custom"


# ===========================================================================
# Personality archetype presets
# ===========================================================================
PERSONALITY_PRESETS = {
    "binge_prone":  {"binge_probability": 25.0, "burst_session_rate": 30.0, "dwell_spike_rate": 12.0, "exit_conflict_shape": "doom_collapse"},
    "avoidant":     {"interruption_rate": 25.0, "dishonesty_rate": 20.0, "mood_carryover_strength": 0.5},
    "hunter":       {"ad_rate": 5.0, "exit_conflict_shape": "early_spike", "content_narrowing_rate": 0.6},
    "zombie":       {"dwell_mu": 4.0, "speed_mu": 14.0, "exit_rate_base": 0.01, "rewatch_rate_base": 0.02, "binge_probability": 15.0},
    "night_owl":    {"lux_mode": "dark", "sleep_debt_enabled": True, "mood_carryover_strength": 0.3},
}


# ===========================================================================
# Composable realism functions
# ===========================================================================

def _apply_binge_sessions(df, req, rng):
    """Randomly extend some sessions to 80-200 reels."""
    if req.binge_probability <= 0:
        return df
    session_nums = sorted(df["SessionNum"].unique())
    n_binge = max(0, int(len(session_nums) * req.binge_probability / 100.0))
    if n_binge == 0:
        return df
    binge_sessions = set(rng.choice(session_nums, size=n_binge, replace=False))
    for sn in binge_sessions:
        mask = df["SessionNum"] == sn
        existing = df.loc[mask]
        if len(existing) < 5:
            continue
        target = int(rng.integers(80, 200))
        extra = target - len(existing)
        if extra > 0:
            template = existing.iloc[-1:].copy()
            extras = pd.concat([template] * extra, ignore_index=True)
            for i in range(len(extras)):
                extras.iloc[i, extras.columns.get_loc("ReelIndex")] = len(existing) + i + 1
                extras.iloc[i, extras.columns.get_loc("CumulativeReels")] = len(existing) + i + 1
                extras.iloc[i, extras.columns.get_loc("DwellTime")] = float(np.clip(rng.lognormal(0.5, 0.8), 0.3, 12.0))
                extras.iloc[i, extras.columns.get_loc("AvgScrollSpeed")] = float(np.clip(rng.normal(8.0, 3.0), 1.0, 25.0))
            df = pd.concat([df, extras], ignore_index=True)
    return df


def _apply_interruptions(df, req, rng):
    """Truncate some sessions to 2-5 reels."""
    if req.interruption_rate <= 0:
        return df
    session_nums = sorted(df["SessionNum"].unique())
    n = max(0, int(len(session_nums) * req.interruption_rate / 100.0))
    if n == 0:
        return df
    for sn in rng.choice(session_nums, size=n, replace=False):
        indices = df.index[df["SessionNum"] == sn]
        keep = int(rng.integers(2, 6))
        if len(indices) > keep:
            df = df.drop(indices[keep:])
    return df


def _apply_burst_gaps(df, req, rng):
    """Make some inter-session gaps very short (1-8 min)."""
    if req.burst_session_rate <= 0 or "TimeSinceLastSessionMin" not in df.columns:
        return df
    session_nums = sorted(df["SessionNum"].unique())
    n = max(0, int(len(session_nums) * req.burst_session_rate / 100.0))
    if n == 0:
        return df
    for sn in rng.choice(session_nums, size=n, replace=False):
        df.loc[df["SessionNum"] == sn, "TimeSinceLastSessionMin"] = round(float(rng.uniform(1.0, 8.0)), 2)
    return df


def _apply_dwell_spikes(df, req, rng):
    """Random 3-8× dwell on some reels."""
    if req.dwell_spike_rate <= 0:
        return df
    spike_mask = rng.random(len(df)) < (req.dwell_spike_rate / 100.0)
    if spike_mask.any():
        col = df.columns.get_loc("DwellTime")
        for idx in df.index[spike_mask]:
            loc = df.index.get_loc(idx)
            df.iat[loc, col] = round(float(df.at[idx, "DwellTime"]) * rng.uniform(3.0, 8.0), 3)
    return df


def _apply_ad_rows(df, req, rng):
    """Set IsAd=1 at the specified ad_rate%, with realistic skip latency."""
    if "IsAd" not in df.columns:
        return df
    ad_mask = rng.random(len(df)) < (req.ad_rate / 100.0)
    df.loc[ad_mask, "IsAd"] = 1
    df.loc[~ad_mask, "IsAd"] = 0
    # Set skip latency for ad rows
    if "AdSkipLatencyMs" in df.columns:
        ad_indices = df.index[df["IsAd"] == 1]
        for idx in ad_indices:
            df.at[idx, "AdSkipLatencyMs"] = int(np.clip(rng.normal(req.ad_skip_latency_mu, req.ad_skip_latency_sigma), 200, 60000))
        df.loc[df["IsAd"] == 0, "AdSkipLatencyMs"] = -1
    return df


def _apply_lux_distribution(df, req, rng):
    """Replace flat lux with bimodal/dark/bright/uniform distribution."""
    if "AmbientLuxStart" not in df.columns:
        return df
    n = len(df)
    if req.lux_mode == "bimodal":
        # 60% dark room (5-30 lux), 40% daylight (200-5000 lux)
        mode = rng.random(n) < 0.6
        lux = np.where(mode,
                       np.clip(rng.normal(15, 8, n), 1, 30),
                       np.clip(rng.normal(1200, 1500, n), 200, 5000))
    elif req.lux_mode == "dark":
        lux = np.clip(rng.normal(10, 5, n), 1, 30)
    elif req.lux_mode == "bright":
        lux = np.clip(rng.normal(2000, 1500, n), 200, 10000)
    else:  # uniform
        lux = rng.uniform(1, 5000, n)
    df["AmbientLuxStart"] = np.round(lux, 2)
    df["AmbientLuxEnd"] = np.round(lux + rng.normal(0, 10, n), 2).clip(0)
    df["LuxDelta"] = np.round(df["AmbientLuxEnd"] - df["AmbientLuxStart"], 3)
    df["IsScreenInDarkRoom"] = (df["AmbientLuxStart"] < 15).astype(int)
    return df


def _apply_speed_outliers(df, req, rng):
    """Inject occasional extreme scroll speed artifacts."""
    outlier_rate = 0.002  # 0.2% of rows
    mask = rng.random(len(df)) < outlier_rate
    if mask.any() and "AvgScrollSpeed" in df.columns:
        df.loc[mask, "AvgScrollSpeed"] = rng.uniform(100, 1000, mask.sum()).round(2)
    return df


def _apply_posture_session_constant(df, req, rng):
    """Make PostureShiftCount constant within a session (not random per-reel)."""
    if "PostureShiftCount" not in df.columns:
        return df
    for sn in df["SessionNum"].unique():
        mask = df["SessionNum"] == sn
        session_posture = int(rng.poisson(2))
        df.loc[mask, "PostureShiftCount"] = session_posture
    return df


def _apply_survey_fills(df, req, rng):
    """Per-field survey fill rates with fatigue decay and correlated skipping."""
    session_nums = sorted(df["SessionNum"].unique())
    n_sessions = len(session_nums)

    # Per-field fill rate map
    field_rates = {
        "MoodBefore":          req.mood_before_fill_rate / 100.0,
        "PostSessionRating":   req.post_session_rating_fill_rate / 100.0,
        "RegretScore":         req.regret_score_fill_rate / 100.0,
        "MoodAfter":           req.mood_after_fill_rate / 100.0,
        "ComparativeRating":   req.comparative_rating_fill_rate / 100.0,
        "ActualVsIntendedMatch": req.actual_vs_intended_fill_rate / 100.0,
        "DelayedRegretScore":  req.delayed_regret_fill_rate / 100.0,
        "MoodDelta":           req.mood_after_fill_rate / 100.0,  # same as MoodAfter
        "IntendedAction":      req.mood_before_fill_rate / 100.0,  # same as MoodBefore
        "PreviousContext":     req.mood_before_fill_rate / 100.0,
    }

    for i, sn in enumerate(session_nums):
        mask = df["SessionNum"] == sn
        session_data = df.loc[mask]

        # Fatigue decay factor
        t = i / max(1, n_sessions - 1)
        fatigue = 1.0
        if req.survey_fatigue_decay > 0:
            fatigue = 0.3 + 0.7 * np.exp(-req.survey_fatigue_decay * t * 3.0)

        # Correlated skip factor
        corr_factor = 1.0
        if req.correlated_survey_skip and len(session_data) > 0:
            avg_dwell = session_data["DwellTime"].mean()
            if avg_dwell < 2.0:
                corr_factor = 0.4
            elif avg_dwell < 3.0:
                corr_factor = 0.7

        for col, base_rate in field_rates.items():
            if col not in df.columns:
                continue
            fill_prob = base_rate * fatigue * corr_factor
            if rng.random() > fill_prob:
                # Blank this field for this session
                if df[col].dtype in ("int64", "float64", "int32", "float32"):
                    df.loc[mask, col] = 0
                else:
                    df.loc[mask, col] = ""

    # Fixed overrides
    if req.override_regret is not None:
        df["RegretScore"] = req.override_regret
    if req.override_mood_before is not None:
        df["MoodBefore"] = req.override_mood_before
    if req.override_comparative is not None:
        df["ComparativeRating"] = req.override_comparative

    return df


def _apply_survey_dishonesty(df, req, rng):
    """Invert survey responses for some sessions."""
    if req.dishonesty_rate <= 0:
        return df
    session_nums = sorted(df["SessionNum"].unique())
    n = max(0, int(len(session_nums) * req.dishonesty_rate / 100.0))
    if n == 0:
        return df
    for sn in rng.choice(session_nums, size=n, replace=False):
        mask = df["SessionNum"] == sn
        for col in ["RegretScore", "ComparativeRating", "PostSessionRating"]:
            if col in df.columns:
                val = df.loc[mask, col].iloc[0]
                if isinstance(val, (int, float)) and val > 0:
                    df.loc[mask, col] = 6 - int(val)
    return df


def _apply_data_corruption(df, req, rng):
    """Simulate end-of-session flood bug and double-write corruption."""
    if req.simulate_flood_bug:
        session_nums = sorted(df["SessionNum"].unique())
        n_flood = max(0, int(len(session_nums) * req.flood_probability / 100.0))
        if n_flood > 0:
            for sn in rng.choice(session_nums, size=n_flood, replace=False):
                mask = df["SessionNum"] == sn
                last_row = df.loc[mask].iloc[-1:].copy()
                floods = pd.concat([last_row] * req.flood_repeat_count, ignore_index=True)
                df = pd.concat([df, floods], ignore_index=True)

    if req.simulate_double_write:
        session_nums = sorted(df["SessionNum"].unique())
        for sn in session_nums:
            mask = df["SessionNum"] == sn
            indices = df.index[mask]
            if len(indices) < 3:
                continue
            # Duplicate ~20% of reel boundaries
            n_dup = max(1, int(len(indices) * 0.2))
            dup_indices = rng.choice(indices, size=n_dup, replace=False)
            dups = df.loc[dup_indices].copy()
            # First write has shorter dwell
            dups["DwellTime"] = (dups["DwellTime"] * req.double_write_completion_factor).round(3)
            df = pd.concat([df, dups], ignore_index=True)
    return df


def _apply_personality_archetype(df, req, rng):
    """Apply personality archetype overrides. The archetype modifies req-level
    params which are already applied by previous functions. This function handles
    any remaining per-row adjustments."""
    if req.personality == "neutral" or req.personality not in PERSONALITY_PRESETS:
        return df
    # Most personality effects are wired through the SimulationRequest params
    # which feed into the per-function logic. This function handles row-level
    # adjustments specific to the archetype.
    if req.personality == "zombie":
        # Zombie: flatten entropy (automaton-like), reduce exit attempts
        if "ScrollRhythmEntropy" in df.columns:
            df["ScrollRhythmEntropy"] = df["ScrollRhythmEntropy"].clip(upper=1.5)
        if "AppExitAttempts" in df.columns:
            df["AppExitAttempts"] = (df["AppExitAttempts"] * 0.3).astype(int)
    elif req.personality == "night_owl":
        # Night owl: skew CircadianPhase toward late-night
        if "CircadianPhase" in df.columns:
            df["CircadianPhase"] = df["CircadianPhase"].apply(
                lambda x: (x + 0.3) % 1.0 if rng.random() < 0.6 else x
            )
    return df


def _apply_feedback_loops(df, req, rng):
    """Content narrowing, mood carryover, sleep debt."""
    session_nums = sorted(df["SessionNum"].unique())

    # Mood carryover
    if req.mood_carryover_strength > 0 and "MoodBefore" in df.columns and "MoodAfter" in df.columns:
        prev_mood_after = None
        for sn in session_nums:
            mask = df["SessionNum"] == sn
            if prev_mood_after is not None:
                current = df.loc[mask, "MoodBefore"].iloc[0]
                if current > 0 and prev_mood_after > 0:
                    blended = int(round(current * (1 - req.mood_carryover_strength) + prev_mood_after * req.mood_carryover_strength))
                    df.loc[mask, "MoodBefore"] = max(1, min(10, blended))
            mood_after_val = df.loc[mask, "MoodAfter"].iloc[0]
            if isinstance(mood_after_val, (int, float)) and mood_after_val > 0:
                prev_mood_after = mood_after_val

    # Content narrowing — reduce entropy over time
    if req.content_narrowing_rate > 0 and "ScrollRhythmEntropy" in df.columns:
        for i, sn in enumerate(session_nums):
            mask = df["SessionNum"] == sn
            decay = 1.0 - req.content_narrowing_rate * (i / max(1, len(session_nums) - 1))
            df.loc[mask, "ScrollRhythmEntropy"] *= max(0.1, decay)

    # Sleep debt — reduce ConsistencyScore and increase fatigue over time
    if req.sleep_debt_enabled:
        for i, sn in enumerate(session_nums):
            mask = df["SessionNum"] == sn
            debt = min(1.0, i * 0.015)
            if "DwellTime" in df.columns:
                df.loc[mask, "DwellTime"] *= max(0.6, 1.0 - debt * 0.3)

    return df


def _apply_realism(df: pd.DataFrame, req: SimulationRequest) -> pd.DataFrame:
    """Apply all realism transformations in composable sequence."""
    # Wire personality archetype params into request before main pipeline
    if req.personality in PERSONALITY_PRESETS:
        for k, v in PERSONALITY_PRESETS[req.personality].items():
            if hasattr(req, k):
                setattr(req, k, v)

    df = df.copy()
    rng = np.random.default_rng(req.seed + 1337)
    for fn in [
        _apply_binge_sessions, _apply_interruptions, _apply_burst_gaps,
        _apply_dwell_spikes, _apply_ad_rows, _apply_lux_distribution,
        _apply_speed_outliers, _apply_posture_session_constant,
        _apply_survey_fills, _apply_survey_dishonesty,
        _apply_feedback_loops, _apply_personality_archetype,
        _apply_data_corruption,
    ]:
        df = fn(df, req, rng)
    return df.sort_values(["SessionNum", "ReelIndex"]).reset_index(drop=True)


# ===========================================================================
# Scenario checks (replaces if/elif chain)
# ===========================================================================
SCENARIO_CHECKS = {
    "improving": [
        ("trend_direction_expected", lambda delta, *_: delta <= -0.05),
        ("last_window_healthier",    lambda delta, last, first, *_: last < first),
    ],
    "worsening": [
        ("trend_direction_expected", lambda delta, *_: delta >= 0.05),
        ("last_window_worse",        lambda delta, last, first, *_: last > first),
    ],
    "stable_casual": [
        ("trend_direction_expected", lambda delta, *_: abs(delta) <= 0.12),
    ],
    "late_night_clusters": [
        ("trend_direction_expected", lambda *_: True),
    ],
    # New stress-test scenarios
    "ad_heavy": [
        ("trend_direction_expected", lambda *_: True),
    ],
    "survey_sparse": [
        ("trend_direction_expected", lambda *_: True),
    ],
    "false_positive_trap": [
        ("trend_direction_expected", lambda delta, *_: abs(delta) <= 0.15),
    ],
    "exit_conflict_extreme": [
        ("trend_direction_expected", lambda delta, *_: delta >= 0.0),
    ],
    "regime_shift": [
        ("trend_direction_expected", lambda *_: True),
    ],
    "cold_start_race": [
        ("trend_direction_expected", lambda *_: True),
    ],
    "flood_corrupted": [
        ("trend_direction_expected", lambda *_: True),
    ],
}


def _build_checks(scenario, st, first_mean, last_mean, evaluation):
    delta = last_mean - first_mean
    base = {
        "confidence_reaches_minimum": float(evaluation.get("model_confidence", 0)) >= 0.25,
        "non_trivial_scoring": float(np.std(st)) >= 0.02 if st else False,
        "threshold_crossings_observed": any(x >= DOOM_PROBABILITY_THRESHOLD for x in st),
    }
    for name, fn in SCENARIO_CHECKS.get(scenario, []):
        base[name] = fn(delta, last_mean, first_mean, st, evaluation)
    return base


# ===========================================================================
# Data quality computation
# ===========================================================================
def _compute_data_quality(df: pd.DataFrame) -> dict:
    """Compute data quality stats for the pipeline visualization."""
    session_nums = df["SessionNum"].unique()
    flood_sessions = 0
    double_write_sessions = 0
    total_rows = len(df)

    for sn in session_nums:
        sess = df[df["SessionNum"] == sn]
        if "CumulativeReels" in sess.columns:
            max_repeat = sess["CumulativeReels"].value_counts().max()
            if max_repeat > 10:
                flood_sessions += 1
            elif max_repeat == 2:
                double_write_sessions += 1

    # Survey coverage
    survey_coverage = {}
    for col, label in [("RegretScore", "regret"), ("MoodBefore", "mood"), ("ComparativeRating", "comparative")]:
        if col in df.columns:
            # Count sessions where the first row has a non-zero value
            filled = sum(1 for sn in session_nums if df.loc[df["SessionNum"] == sn, col].iloc[0] > 0)
            survey_coverage[label] = round(filled / max(1, len(session_nums)), 3)
        else:
            survey_coverage[label] = 0.0

    # Ad stats
    ad_fraction = float((df["IsAd"] == 1).mean()) if "IsAd" in df.columns else 0.0
    ad_rows = df[df.get("IsAd", pd.Series()) == 1] if "IsAd" in df.columns else pd.DataFrame()
    mean_skip_ms = float(ad_rows["AdSkipLatencyMs"].mean()) if len(ad_rows) > 0 and "AdSkipLatencyMs" in ad_rows.columns else 0.0

    return {
        "flood_sessions": flood_sessions,
        "double_write_sessions": double_write_sessions,
        "rows_before_dedupe": total_rows,
        "effective_data_fraction": 1.0,  # updated after dedupe
        "ad_row_fraction": round(ad_fraction, 3),
        "mean_skip_latency_ms": round(mean_skip_ms, 1),
        "survey_coverage": survey_coverage,
    }


# ===========================================================================
# Full ALSE pipeline runner — real ReelioCLSE.process_session()
# ===========================================================================
def _run_pipeline_incremental(csv_plain_text: str):
    df = pd.read_csv(pd.io.common.StringIO(csv_plain_text))
    df.columns = df.columns.str.strip()

    model = ReelioCLSE()
    baseline = UserBaseline()
    regime = RegimeDetector()
    scorer = DoomScorer()
    regret_val = RegretValidator()

    session_keys = sorted(df["SessionNum"].unique())
    prev_gamma = None

    # Compute data quality before dedupe
    dq = _compute_data_quality(df)

    for i, sn in enumerate(session_keys):
        sess_df = df[df["SessionNum"] == sn].copy()
        rows_before = len(sess_df)
        sess_df = dedupe_session_rows(sess_df, session_id=str(sn))
        rows_after = len(sess_df)
        sess_df = preprocess_session(sess_df)

        n_reels = effective_session_reel_count(sess_df)

        gap_min = float(pd.to_numeric(
            sess_df["TimeSinceLastSessionMin"].iloc[0], errors="coerce") or 0.0
        ) if "TimeSinceLastSessionMin" in sess_df.columns else 0.0
        gap_hr = gap_min / 60.0

        mean_dwell = float(sess_df["DwellTime"].mean())
        mean_speed = float(sess_df["AvgScrollSpeed"].mean()) if "AvgScrollSpeed" in sess_df.columns else 0.0
        log_dwell = float(sess_df["log_dwell"].mean())
        log_speed = float(sess_df["log_speed"].mean())
        exit_rate = float(sess_df["AppExitAttempts"].sum()) / max(1, n_reels)
        rewatch_rate = float(sess_df["BackScrollCount"].sum()) / max(1, n_reels)
        entropy = float(sess_df["ScrollRhythmEntropy"].mean()) if "ScrollRhythmEntropy" in sess_df.columns else 0.0
        trend = float(sess_df["SessionDwellTrend"].iloc[-1]) if "SessionDwellTrend" in sess_df.columns else 0.0
        supervised_doom = float(sess_df["supervised_doom"].iloc[0]) if "supervised_doom" in sess_df.columns else 0.0
        fatigue_risk = float(sess_df["fatigue_risk"].iloc[0]) if "fatigue_risk" in sess_df.columns else 0.0

        # Ad stats for this session
        is_ad_col = sess_df["IsAd"] if "IsAd" in sess_df.columns else pd.Series([0] * len(sess_df))
        ad_fraction = float((is_ad_col == 1).mean())
        ad_rows_sess = sess_df[is_ad_col == 1]
        ad_mean_dwell = float(ad_rows_sess["DwellTime"].mean()) if len(ad_rows_sess) > 0 else 0.0
        organic_rows = sess_df[is_ad_col != 1]
        organic_mean_dwell = float(organic_rows["DwellTime"].mean()) if len(organic_rows) > 0 else mean_dwell
        avg_skip_ms = float(ad_rows_sess["AdSkipLatencyMs"].mean()) if len(ad_rows_sess) > 0 and "AdSkipLatencyMs" in ad_rows_sess.columns else 0.0

        try:
            start_time = str(sess_df["StartTime"].iloc[0])
        except:
            start_time = ""

        prev_S_t = 0.5 if prev_gamma is None else 0.5
        doom_result = scorer.score(sess_df, baseline, gap_min, prev_S_t)

        phase = float(sess_df["CircadianPhase"].iloc[0]) if "CircadianPhase" in sess_df.columns else 0.5
        mood_before_raw = float(sess_df["MoodBefore"].iloc[0]) if "MoodBefore" in sess_df.columns else 0
        mood_risk = normalize_prestate_risk(mood_before_raw)
        intended = str(sess_df["IntendedAction"].iloc[0]) if "IntendedAction" in sess_df.columns else ""
        prev_ctx = str(sess_df["PreviousContext"].iloc[0]) if "PreviousContext" in sess_df.columns else ""
        stress_flag = 1.0 if any([
            intended == "Stressed / Avoidance", intended == "Bored / Nothing to do",
            intended == "Procrastinating something", prev_ctx == "Work / Study", prev_ctx == "Boredom"
        ]) else 0.0

        A_gap = model._a_gap(gap_hr)

        gamma, blended_prob = model.process_session(sess_df, baseline, regime, prev_gamma)
        if gamma is None:
            yield {
                "session": i + 1, "total_sessions": len(session_keys),
                "session_num": int(sn), "start_time": start_time,
                "skipped": True, "reason": f"Session {sn}: {n_reels} reel(s), skipped",
            }
            continue

        S_t = float(blended_prob)
        raw_mean_doom = float(np.mean(gamma[:, 1]))

        emission_params = {
            "mu_dwell_casual": round(float(model.mu[0, 0]), 4),
            "mu_dwell_doom":   round(float(model.mu[0, 1]), 4),
            "mu_speed_casual": round(float(model.mu[1, 0]), 4),
            "mu_speed_doom":   round(float(model.mu[1, 1]), 4),
            "sigma_dwell_casual": round(float(model.sigma[0, 0]), 4),
            "sigma_dwell_doom":   round(float(model.sigma[0, 1]), 4),
            "sigma_speed_casual": round(float(model.sigma[1, 0]), 4),
            "sigma_speed_doom":   round(float(model.sigma[1, 1]), 4),
            "p_rewatch_casual": round(float(model.p_bern[3, 0]), 4),
            "p_rewatch_doom":   round(float(model.p_bern[3, 1]), 4),
            "p_exit_casual":    round(float(model.p_bern[4, 0]), 4),
            "p_exit_doom":      round(float(model.p_bern[4, 1]), 4),
            "rho_dwell_speed_casual": round(float(model.rho_dwell_speed[0]), 4),
            "rho_dwell_speed_doom":   round(float(model.rho_dwell_speed[1]), 4),
        }

        transition = {
            "A_casual_casual": round(float(model.A[0, 0]), 4),
            "A_casual_doom":   round(float(model.A[0, 1]), 4),
            "A_doom_casual":   round(float(model.A[1, 0]), 4),
            "A_doom_doom":     round(float(model.A[1, 1]), 4),
        }

        ctmc = {
            "q_01_pull": round(float(model.q_01), 4),
            "q_10_escape": round(float(model.q_10), 4),
            "A_gap": [[round(float(x), 4) for x in row] for row in A_gap.tolist()],
        }

        contextual_pi = {
            "pi_casual": round(float(model.pi[0]), 4),
            "pi_doom":   round(float(model.pi[1]), 4),
        }

        hazard = {
            "h_doom":   round(float(model.h[0]), 4),
            "h_casual": round(float(model.h[1]), 4),
        }

        feature_names = ["log_dwell", "log_speed", "rhythm_dissociation",
                         "rewatch_flag", "exit_flag", "swipe_incomplete"]
        feature_weights = {
            feature_names[k]: round(float(model.feature_weights[k]), 4)
            for k in range(model.num_features)
        }

        gamma_summary = {
            "mean_doom": round(float(np.mean(gamma[:, 1])), 4),
            "max_doom":  round(float(np.max(gamma[:, 1])), 4),
            "min_doom":  round(float(np.min(gamma[:, 1])), 4),
            "first_reel_doom": round(float(gamma[0, 1]), 4),
            "last_reel_doom":  round(float(gamma[-1, 1]), 4),
            "doom_trajectory": [round(float(gamma[t, 1]), 3)
                                for t in range(0, len(gamma), max(1, len(gamma)//10))],
        }

        label_conf = model.last_label_conf
        supervised_layer = {
            "supervised_doom": round(supervised_doom, 4),
            "label_confidence": round(float(label_conf), 4),
            "running_disagreement": round(float(model.running_disagreement), 4),
            "labeled_sessions_total": int(model.labeled_sessions),
        }

        regime_alert = regime.regime_alert
        regime_state = {
            "alert": regime_alert,
            "alert_duration": int(regime.alert_duration),
            "doom_history_len": len(regime.doom_history),
        }

        conf_breakdown = model.compute_model_confidence_breakdown()

        components = {}
        for k, v in doom_result.get("components", {}).items():
            try:
                components[k] = round(float(v), 4)
            except:
                components[k] = 0.0

        baseline_state = {
            "dwell_mu": round(float(baseline.dwell_mu_personal), 4),
            "dwell_sig": round(float(baseline.dwell_sig_personal), 4),
            "speed_mu": round(float(baseline.speed_mu_personal), 4),
            "speed_sig": round(float(baseline.speed_sig_personal), 4),
            "session_len_mu": round(float(baseline.session_len_mu), 2),
            "session_len_sig": round(float(baseline.session_len_sig), 2),
            "exit_rate_baseline": round(float(baseline.exit_rate_baseline), 4),
            "rewatch_rate_base": round(float(baseline.rewatch_rate_base), 4),
            "n_sessions_seen": int(baseline.n_sessions_seen),
        }

        regret_score_raw = float(sess_df["RegretScore"].iloc[0]) if "RegretScore" in sess_df.columns else 0.0
        if regret_score_raw > 0:
            regret_val.add_observation(S_t, regret_score_raw)
        calibration = regret_val.get_calibration_quality()

        yield {
            "session": i + 1, "total_sessions": len(session_keys),
            "session_num": int(sn), "start_time": start_time, "n_reels": n_reels,

            "features": {
                "n_reels": n_reels, "mean_dwell": round(mean_dwell, 3),
                "log_dwell": round(log_dwell, 4), "mean_speed": round(mean_speed, 3),
                "log_speed": round(log_speed, 4), "exit_rate": round(exit_rate, 4),
                "rewatch_rate": round(rewatch_rate, 4), "entropy": round(entropy, 4),
                "trend": round(trend, 4), "gap_min": round(gap_min, 1),
                "fatigue_risk": round(fatigue_risk, 3), "mood_risk": round(mood_risk, 3),
                "stress_flag": stress_flag,
                # Ad-aware features
                "ad_fraction": round(ad_fraction, 3),
                "avg_skip_latency_ms": round(avg_skip_ms, 0),
                "ad_mean_dwell": round(ad_mean_dwell, 3),
                "organic_mean_dwell": round(organic_mean_dwell, 3),
            },

            "doom_scorer": {
                "doom_score": round(float(doom_result["doom_score"]), 4),
                "label": doom_result["label"],
                "components": components,
            },

            "baseline": baseline_state,
            "emission": emission_params,
            "transition": transition,
            "ctmc": ctmc,
            "hazard": hazard,
            "regime": regime_state,
            "confidence": {k: round(float(v), 4) if isinstance(v, float) else v
                          for k, v in conf_breakdown.items()},
            "contextual_pi": contextual_pi,
            "feature_weights": feature_weights,
            "gamma": gamma_summary,
            "supervised": supervised_layer,
            "regret_calibration": {
                k: round(float(v), 4) if isinstance(v, float) else v
                for k, v in calibration.items()
            },

            # Data quality for this session
            "data_quality": {
                "rows_before_dedupe": rows_before,
                "rows_after_dedupe": rows_after,
                "deduped": rows_before != rows_after,
            },

            "S_t": round(S_t, 4),
            "raw_hmm_doom": round(raw_mean_doom, 4),
        }

        prev_gamma = gamma


# ===========================================================================
# Helper: build profile from request
# ===========================================================================
def _build_profile(req: SimulationRequest) -> CalibrationProfile:
    return CalibrationProfile(
        session_reels_mu=req.session_reels_mu, session_reels_sigma=req.session_reels_sigma,
        dwell_mu=req.dwell_mu, dwell_sigma=req.dwell_sigma,
        speed_mu=req.speed_mu, speed_sigma=req.speed_sigma,
        gap_q10=req.gap_q10, gap_q50=req.gap_q50, gap_q90=req.gap_q90,
        exit_rate_base=req.exit_rate_base, rewatch_rate_base=req.rewatch_rate_base,
    )


# ===========================================================================
# Routes
# ===========================================================================
@app.get("/", response_class=HTMLResponse)
async def read_index():
    return (_THIS_DIR / "templates" / "index.html").read_text(encoding="utf-8")


@app.get("/api/health")
async def health():
    return {"status": "ok"}


@app.post("/api/simulate")
async def run_simulation(req: SimulationRequest) -> JSONResponse:
    try:
        profile = _build_profile(req)
        df, metadata = generate_synthetic_dataset(
            scenario=req.scenario, n_sessions=req.sessions, profile=profile, seed=req.seed,
        )
        df = _apply_realism(df, req)
        csv_plain = df.to_csv(index=False)
        csv_schema = f"SCHEMA_VERSION={EXPECTED_SCHEMA_VERSION}\n" + csv_plain

        sid = str(uuid4())[:8]
        _session_store[sid] = {"csv_plain": csv_plain, "csv_schema": csv_schema, "scenario": req.scenario}

        return JSONResponse(content={
            "status": "generated", "metadata": metadata, "session_id": sid,
            "total_rows": len(df), "total_sessions": int(df["SessionNum"].nunique()),
            "data_quality": _compute_data_quality(df),
        })
    except Exception:
        logging.exception("Simulation error")
        raise HTTPException(status_code=500, detail=traceback.format_exc())


@app.post("/api/simulate-stream")
async def simulate_stream(req: SimulationRequest):
    """SSE endpoint: generate data + stream FULL ALSE pipeline session by session."""

    async def event_generator():
        try:
            yield f"data: {json.dumps({'type': 'status', 'message': 'Generating synthetic data...'})}\n\n"
            await asyncio.sleep(0.05)

            profile = _build_profile(req)
            df, metadata = generate_synthetic_dataset(
                scenario=req.scenario, n_sessions=req.sessions, profile=profile, seed=req.seed,
            )
            df = _apply_realism(df, req)
            csv_plain = df.to_csv(index=False)
            csv_schema = f"SCHEMA_VERSION={EXPECTED_SCHEMA_VERSION}\n" + csv_plain

            sid = str(uuid4())[:8]
            _session_store[sid] = {"csv_plain": csv_plain, "csv_schema": csv_schema, "scenario": req.scenario}

            dq = _compute_data_quality(df)
            yield f"data: {json.dumps({'type': 'generated', 'session_id': sid, 'total_rows': len(df), 'total_sessions': int(df['SessionNum'].nunique()), 'metadata': metadata, 'data_quality': dq})}\n\n"
            await asyncio.sleep(0.05)

            for event in _run_pipeline_incremental(csv_plain):
                yield f"data: {json.dumps({'type': 'session', **event})}\n\n"
                await asyncio.sleep(0.01)

            yield f"data: {json.dumps({'type': 'status', 'message': 'Running reference pipeline...'})}\n\n"
            await asyncio.sleep(0.05)

            payload_raw = run_dashboard_payload(csv_schema)
            payload = json.loads(payload_raw)
            sessions = payload.get("sessions", [])
            st = [float(s.get("S_t", 0.0)) for s in sessions]
            reels_per = df.groupby("SessionNum")["ReelIndex"].max().sort_index().tolist()

            window = max(3, min(10, len(st) // 5)) if len(st) >= 5 else len(st)
            first_mean = float(np.mean(st[:window])) if st else 0.0
            last_mean = float(np.mean(st[-window:])) if st else 0.0

            final = {
                "type": "complete",
                "session_id": sid,
                "chart_data": {
                    "sessions": list(range(1, len(st) + 1)),
                    "s_t": st, "reels_per_session": reels_per,
                },
                "evaluation": {
                    "sessions_scored": len(sessions),
                    "mean_first_window": round(first_mean, 3),
                    "mean_last_window": round(last_mean, 3),
                    "delta_last_minus_first": round(last_mean - first_mean, 3),
                    "model_confidence": float(payload.get("model_confidence", 0.0)),
                    "checks": _build_checks(req.scenario, st, first_mean, last_mean, payload),
                },
                "data_quality": dq,
            }
            yield f"data: {json.dumps(final)}\n\n"

        except Exception:
            yield f"data: {json.dumps({'type': 'error', 'message': traceback.format_exc()})}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")


@app.get("/api/csv/{session_id}")
async def get_csv(session_id: str):
    entry = _session_store.get(session_id)
    if not entry:
        raise HTTPException(status_code=404, detail="Run a simulation first.")
    df = pd.read_csv(pd.io.common.StringIO(entry["csv_plain"])).fillna(0)
    return JSONResponse(content={
        "columns": list(df.columns),
        "rows": df.head(500).values.tolist(),
        "total_rows": len(df),
        "truncated": len(df) > 500,
    })


@app.post("/api/repredict")
async def repredict(req: RepredictRequest) -> JSONResponse:
    try:
        csv_schema = f"SCHEMA_VERSION={EXPECTED_SCHEMA_VERSION}\n" + req.csv_text
        payload_raw = run_dashboard_payload(csv_schema)
        payload = json.loads(payload_raw)
        sessions = payload.get("sessions", [])
        st = [float(s.get("S_t", 0.0)) for s in sessions]
        df = pd.read_csv(pd.io.common.StringIO(req.csv_text))
        reels_per = df.groupby("SessionNum")["ReelIndex"].max().sort_index().tolist()

        dq = _compute_data_quality(df)

        return JSONResponse(content={
            "chart_data": {"sessions": list(range(1, len(st) + 1)), "s_t": st, "reels_per_session": reels_per},
            "sessions_scored": len(sessions),
            "model_confidence": float(payload.get("model_confidence", 0.0)),
            "data_quality": dq,
        })
    except Exception:
        logging.exception("Repredict error")
        raise HTTPException(status_code=500, detail=traceback.format_exc())


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app:app", host="127.0.0.1", port=8000, reload=True)
