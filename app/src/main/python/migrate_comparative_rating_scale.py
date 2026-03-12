"""
One-time migration for ComparativeRating scale change.

Old stored scale (legacy): 1=best .. 5=worst
New stored scale:          1=worst .. 5=best

This migration flips non-zero comparative values via: new = 6 - old
for values in [1, 5]. It preserves 0 (skipped/no response).

It supports schema-prefixed CSV files (SCHEMA_VERSION=...).
"""

from __future__ import annotations

import argparse
import io
import shutil
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable

import pandas as pd


@dataclass
class FileResult:
    path: Path
    rows: int
    changed_cells: int
    before_nonzero: int
    after_nonzero: int
    backup_path: Path | None
    skipped_reason: str | None = None
    orientation: str | None = None
    supervision_samples: int = 0
    orientation_corr: float | None = None


def _iter_candidates(root: Path, patterns: Iterable[str]) -> list[Path]:
    seen: set[Path] = set()
    out: list[Path] = []
    for pat in patterns:
        for p in root.rglob(pat):
            if not p.is_file() or p.suffix.lower() != ".csv":
                continue
            if ".pre_comparative_scale_fix." in p.name:
                continue
            if p not in seen:
                seen.add(p)
                out.append(p)
    out.sort()
    return out


def _read_text_with_fallback_encodings(path: Path) -> str:
    encodings = ["utf-8", "utf-8-sig", "utf-16", "utf-16-le", "utf-16-be", "latin-1"]
    last_exc: Exception | None = None
    for enc in encodings:
        try:
            return path.read_text(encoding=enc)
        except Exception as exc:  # pragma: no cover - fallback path
            last_exc = exc
            continue
    if last_exc is not None:
        raise last_exc
    raise RuntimeError("No encoding attempts were made")


def _read_csv_with_optional_schema_prefix(path: Path) -> tuple[pd.DataFrame, str | None, bool]:
    raw = _read_text_with_fallback_encodings(path)
    had_trailing_newline = raw.endswith("\n")
    lines = raw.splitlines()
    schema_prefix = None
    body = raw
    if lines and lines[0].startswith("SCHEMA_VERSION="):
        schema_prefix = lines[0]
        body = "\n".join(lines[1:])
    # Be tolerant to occasional malformed export rows while preserving all good rows.
    df = pd.read_csv(io.StringIO(body), engine="python", on_bad_lines="skip")
    return df, schema_prefix, had_trailing_newline


def _write_csv_with_optional_schema_prefix(
    path: Path,
    df: pd.DataFrame,
    schema_prefix: str | None,
    had_trailing_newline: bool,
) -> None:
    csv_body = df.to_csv(index=False)
    out = f"{schema_prefix}\n{csv_body}" if schema_prefix else csv_body
    if not had_trailing_newline and out.endswith("\n"):
        out = out[:-1]
    path.write_text(out, encoding="utf-8")


def _backup_path(path: Path) -> Path:
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    return path.with_name(f"{path.stem}.pre_comparative_scale_fix.{stamp}{path.suffix}")


def _infer_orientation(df: pd.DataFrame, min_samples: int = 5) -> tuple[str, int, float | None]:
    """
    Infer comparative scale orientation from supervised signal relationship.

    We expect RegretScore to increase with session worse-ness.
    - legacy scale (1=best..5=worst): ComparativeRating vs RegretScore tends positive
    - new scale    (1=worst..5=best): ComparativeRating vs RegretScore tends negative
    """
    if "RegretScore" not in df.columns:
        return "no_regret_column", 0, None

    sess_df = df
    if "SessionNum" in df.columns:
        sess_df = df.groupby("SessionNum", sort=False).first().reset_index(drop=True)

    comp = pd.to_numeric(sess_df.get("ComparativeRating"), errors="coerce")
    regret = pd.to_numeric(sess_df.get("RegretScore"), errors="coerce")

    pairs = pd.DataFrame({"comp": comp, "regret": regret}).dropna()
    pairs = pairs[(pairs["comp"] > 0) & pairs["comp"].between(1.0, 5.0, inclusive="both") & (pairs["regret"] > 0)]

    n = int(len(pairs))
    if n < min_samples:
        return "insufficient_supervision", n, None

    corr = pairs["comp"].corr(pairs["regret"], method="spearman")
    if pd.isna(corr):
        return "ambiguous", n, None

    corr_f = float(corr)
    if corr_f >= 0.15:
        return "legacy_needs_flip", n, corr_f
    if corr_f <= -0.15:
        return "already_new_scale", n, corr_f
    return "ambiguous", n, corr_f


def migrate_file(path: Path, apply_changes: bool, force_flip: bool = False) -> FileResult:
    try:
        df, schema_prefix, had_trailing_newline = _read_csv_with_optional_schema_prefix(path)
    except Exception as exc:
        return FileResult(
            path=path,
            rows=0,
            changed_cells=0,
            before_nonzero=0,
            after_nonzero=0,
            backup_path=None,
            skipped_reason=f"read_failed: {exc}",
        )

    if "ComparativeRating" not in df.columns:
        return FileResult(
            path=path,
            rows=len(df),
            changed_cells=0,
            before_nonzero=0,
            after_nonzero=0,
            backup_path=None,
            skipped_reason="no_comparative_column",
        )

    comp_num = pd.to_numeric(df["ComparativeRating"], errors="coerce")
    in_scale = comp_num.between(1.0, 5.0, inclusive="both")
    mask = in_scale & (comp_num > 0)

    before_nonzero = int((comp_num > 0).fillna(False).sum())
    changed_cells = int(mask.fillna(False).sum())

    if changed_cells == 0:
        return FileResult(
            path=path,
            rows=len(df),
            changed_cells=0,
            before_nonzero=before_nonzero,
            after_nonzero=before_nonzero,
            backup_path=None,
            skipped_reason="no_legacy_values_to_flip",
        )

    orientation, n_supervised, corr = _infer_orientation(df)
    if not force_flip:
        if orientation == "already_new_scale":
            return FileResult(
                path=path,
                rows=len(df),
                changed_cells=0,
                before_nonzero=before_nonzero,
                after_nonzero=before_nonzero,
                backup_path=None,
                skipped_reason=f"already_new_scale (n={n_supervised}, corr={corr:.3f})",
                orientation=orientation,
                supervision_samples=n_supervised,
                orientation_corr=corr,
            )
        if orientation != "legacy_needs_flip":
            detail = f"{orientation}"
            if corr is not None:
                detail += f" (n={n_supervised}, corr={corr:.3f})"
            else:
                detail += f" (n={n_supervised})"
            return FileResult(
                path=path,
                rows=len(df),
                changed_cells=0,
                before_nonzero=before_nonzero,
                after_nonzero=before_nonzero,
                backup_path=None,
                skipped_reason=detail,
                orientation=orientation,
                supervision_samples=n_supervised,
                orientation_corr=corr,
            )

    flipped = comp_num.copy()
    flipped.loc[mask] = 6.0 - comp_num.loc[mask]

    # Keep integer-like output when source column was integer-like.
    df_out = df.copy()
    if pd.api.types.is_integer_dtype(df_out["ComparativeRating"]) or (
        pd.api.types.is_object_dtype(df_out["ComparativeRating"])
    ):
        df_out["ComparativeRating"] = flipped.round().astype("Int64")
    else:
        df_out["ComparativeRating"] = flipped

    after_nonzero = int((pd.to_numeric(df_out["ComparativeRating"], errors="coerce") > 0).fillna(False).sum())

    bkp = None
    if apply_changes:
        bkp = _backup_path(path)
        shutil.copy2(path, bkp)
        _write_csv_with_optional_schema_prefix(path, df_out, schema_prefix, had_trailing_newline)

    return FileResult(
        path=path,
        rows=len(df),
        changed_cells=changed_cells,
        before_nonzero=before_nonzero,
        after_nonzero=after_nonzero,
        backup_path=bkp,
        orientation=orientation,
        supervision_samples=n_supervised,
        orientation_corr=corr,
    )


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Migrate ComparativeRating legacy scale to new app scale.")
    p.add_argument("--root", type=str, default=".", help="Root directory to scan recursively")
    p.add_argument(
        "--pattern",
        action="append",
        default=["insta_data*.csv", "instadata*.csv"],
        help="Filename pattern(s) to scan recursively. Can be passed multiple times.",
    )
    p.add_argument(
        "--force-flip",
        action="store_true",
        help="Force-flip comparative values without orientation checks.",
    )
    p.add_argument("--apply", action="store_true", help="Apply changes in place (creates timestamped backups)")
    return p.parse_args()


def main() -> None:
    args = parse_args()
    root = Path(args.root).resolve()

    candidates = _iter_candidates(root, args.pattern)
    if not candidates:
        print("No candidate CSV files found.")
        return

    print(f"Root: {root}")
    print(f"Candidates: {len(candidates)}")
    print(f"Mode: {'APPLY' if args.apply else 'DRY-RUN'}")

    results: list[FileResult] = []
    for p in candidates:
        r = migrate_file(p, apply_changes=args.apply, force_flip=args.force_flip)
        results.append(r)
        if r.skipped_reason:
            print(f"SKIP  {p} [{r.skipped_reason}]")
        else:
            bkp = f" backup={r.backup_path}" if r.backup_path else ""
            orient = f" orientation={r.orientation}" if r.orientation else ""
            print(f"OK    {p} rows={r.rows} changed={r.changed_cells}{orient}{bkp}")

    changed_files = [r for r in results if r.changed_cells > 0 and r.skipped_reason is None]
    total_changed_cells = sum(r.changed_cells for r in changed_files)

    print("\n=== SUMMARY ===")
    print(f"Files scanned: {len(results)}")
    print(f"Files changed: {len(changed_files)}")
    print(f"Cells flipped: {total_changed_cells}")


if __name__ == "__main__":
    main()
