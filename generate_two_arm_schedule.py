#!/usr/bin/env python3
"""
Generates a balanced 2-arm interleaved schedule: full causalDistance vs. a
random-sampling control given ~10x the evaluation budget the GA arms
actually spend (3800 vs ~381). Tests Ricardo's point directly (2026-09-18,
sync-claude-codex.md): cumulative coverage at a fixed budget is monotonic
and bounded, so ANY search approaches full coverage given enough budget --
"baseline catches up given more generations" or "random catches up given
1 billion samples" doesn't by itself show the methods are equally good.
This gives random a substantially larger (not just matched) budget and
asks whether it closes most of the gap with full redirection.

For n=2 arms a single Williams square already balances carryover (the
doubling trick in generate_williams_schedule.py is only needed for odd n),
so this is just the two sequences [0,1] and [1,0], each used 15 times,
with the 30 resulting blocks then shuffled in order.

Usage:
    python3 generate_two_arm_schedule.py [--seed N]

Writes:
    config/two-arm-random-huge/schedule.csv
    config/two-arm-random-huge/cell-configs/block<B>-pos<P>-<arm>.json
"""
import argparse
import csv
import hashlib
import json
import os
import random

REPO_ROOT = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(REPO_ROOT, "config", "two-arm-random-huge")
CELL_CONFIG_DIR = os.path.join(OUT_DIR, "cell-configs")

BASE_CONFIGS = {
    "full": "config/quorum-handshake-covinst-rq6-full-final-n30.json",
    "random-huge": "config/quorum-handshake-covinst-rq-random-huge-final-n30.json",
}


def config_hash(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()[:12]


def make_cell_config(base_config_path, block, position, arm_name):
    with open(base_config_path) as f:
        cfg = json.load(f)
    cfg["ga"]["executions"] = 1
    run_name = f"twoarm-block{block:02d}-pos{position}-{arm_name}"
    cfg["output"]["runName"] = run_name
    return cfg, run_name


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", type=int, default=None)
    args = parser.parse_args()
    seed = args.seed if args.seed is not None else random.SystemRandom().randint(0, 2**31 - 1)
    rng = random.Random(seed)

    arm_names = list(BASE_CONFIGS.keys())  # [full, random-huge]
    sequences = [[0, 1], [1, 0]]  # AB, BA - balances position and adjacency for n=2

    blocks = sequences * 15  # 30 blocks total, 15 of each order
    block_order = list(range(len(blocks)))
    rng.shuffle(block_order)

    os.makedirs(CELL_CONFIG_DIR, exist_ok=True)
    schedule_rows = []
    base_config_hashes = {name: config_hash(os.path.join(REPO_ROOT, path)) for name, path in BASE_CONFIGS.items()}

    for block_idx, seq_idx in enumerate(block_order):
        sequence = blocks[seq_idx]
        for position, label in enumerate(sequence):
            arm_name = arm_names[label]
            base_path = os.path.join(REPO_ROOT, BASE_CONFIGS[arm_name])
            cfg, run_name = make_cell_config(base_path, block_idx, position, arm_name)
            cell_config_path = os.path.join(CELL_CONFIG_DIR, f"{run_name}.json")
            with open(cell_config_path, "w") as f:
                json.dump(cfg, f, indent=2)
                f.write("\n")
            schedule_rows.append({
                "block": block_idx, "position": position, "arm": arm_name,
                "base_config": BASE_CONFIGS[arm_name], "base_config_hash": base_config_hashes[arm_name],
                "cell_config": os.path.relpath(cell_config_path, REPO_ROOT), "run_name": run_name,
            })

    # balance check: each arm exactly 15x per position (0 and 1)
    from collections import Counter
    counts = Counter((r["position"], r["arm"]) for r in schedule_rows)
    for pos in (0, 1):
        for arm in arm_names:
            assert counts[(pos, arm)] == 15, f"imbalance at position {pos}, arm {arm}: {counts[(pos, arm)]}"

    schedule_path = os.path.join(OUT_DIR, "schedule.csv")
    with open(schedule_path, "w", newline="") as f:
        f.write(f"# seed={seed}\n")
        writer = csv.DictWriter(f, fieldnames=["block", "position", "arm", "base_config",
                                                "base_config_hash", "cell_config", "run_name"],
                                 lineterminator="\n")
        writer.writeheader()
        for row in schedule_rows:
            writer.writerow(row)

    print(f"seed={seed}")
    print(f"wrote {len(schedule_rows)} schedule rows to {schedule_path}")
    run_names = [r["run_name"] for r in schedule_rows]
    assert len(set(run_names)) == len(run_names), "duplicate run_name detected"
    print("balance OK, all run_names unique")


if __name__ == "__main__":
    main()
