#!/usr/bin/env python3
"""Compare two face images using project embed pipeline."""

from __future__ import annotations

import argparse
import json
import math
import os
import subprocess
import sys
import tempfile


def embed(workspace: str, image: str, face_mode: str, min_det: float):
    with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tmp:
        out_path = tmp.name
    try:
        cmd = [
            sys.executable,
            "scripts/embed_features.py",
            "--kind",
            "face",
            "--image",
            image,
            "--face-mode",
            face_mode,
            "--face-model",
            "buffalo_l",
            "--min-det-score",
            str(min_det),
            "--output",
            out_path,
        ]
        proc = subprocess.run(cmd, cwd=workspace, capture_output=True, text=True)
        if not os.path.exists(out_path):
            raise RuntimeError(
                f"embed failed exit={proc.returncode} stdout={proc.stdout[-400:]} stderr={proc.stderr[-400:]}"
            )
        with open(out_path, encoding="utf-8") as f:
            data = json.load(f)
        if proc.returncode != 0 and not data.get("ok"):
            data["_exitCode"] = proc.returncode
        return data
    finally:
        if os.path.exists(out_path):
            os.unlink(out_path)


def cosine(a, b) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(x * x for x in b))
    return dot / (na * nb)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--image1", required=True)
    parser.add_argument("--image2", required=True)
    parser.add_argument("--workspace", default="E:/nwueyes/ruoyi")
    parser.add_argument("--threshold", type=float, default=0.45)
    parser.add_argument("--min-det-score", type=float, default=0.35)
    args = parser.parse_args()

    results = []
    for mode in ("crop", "detect"):
        e1 = embed(args.workspace, args.image1, mode, args.min_det_score)
        e2 = embed(args.workspace, args.image2, mode, args.min_det_score)
        row = {
            "mode": mode,
            "img1_ok": e1.get("ok"),
            "img2_ok": e2.get("ok"),
            "img1_quality": e1.get("quality"),
            "img2_quality": e2.get("quality"),
        }
        if e1.get("ok") and e2.get("ok"):
            sim = cosine(e1["embedding"], e2["embedding"])
            row["similarity"] = round(sim, 4)
            row["matched"] = sim >= args.threshold
        else:
            row["error1"] = e1.get("error")
            row["error2"] = e2.get("error")
        results.append(row)

    valid = [r for r in results if "similarity" in r]
    best = max(valid, key=lambda r: r["similarity"]) if valid else None
    out = {
        "threshold": args.threshold,
        "image1": args.image1,
        "image2": args.image2,
        "conclusion": (
            "同一人" if best and best["matched"] else "非同一人或置信不足"
        ),
        "best": best,
        "details": results,
    }
    print(json.dumps(out, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
