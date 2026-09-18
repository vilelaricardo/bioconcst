#!/usr/bin/env python3
"""
Generates a Williams-balanced execution schedule for the RQ6/RQ7
interleaved-block replication (see sync-claude-codex.md, 2026-09-17
entries (4)/(6)): 5 arms (baseline/local-only/full/random-matched/
random-nominal), each block runs exactly one execution of each arm, in
an order that balances both position (each arm appears equally often in
each of the 5 slots) and immediate carryover (each arm follows each
other arm equally often) -- so a residual time/machine-state drift over
the run cannot be mistaken for a treatment effect.

Construction (Williams 1949, doubled for odd n=5 treatments): a single
Latin square built from the cyclic shifts of the "fold-in" base sequence
[0,1,n-1,2,n-2,...] balances carryover for EVEN n only. For odd n, the
square's own row-reversals are appended, giving 2n=10 sequences that
together balance every ordered adjacent pair exactly twice. Codex's
requested design repeats this 10-sequence set 3 times (30 blocks total),
so each arm appears exactly 6 times per position and every ordered
carryover pair appears exactly 6 times overall.

Usage:
    python3 generate_williams_schedule.py [--seed N]

Writes:
    config/interleaved-replication/schedule.csv
    config/interleaved-replication/cell-configs/block<B>-pos<P>-<arm>.json
"""
import argparse
import csv
import hashlib
import json
import os
import random

REPO_ROOT = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(REPO_ROOT, "config", "interleaved-replication")
CELL_CONFIG_DIR = os.path.join(OUT_DIR, "cell-configs")

# Maps abstract Williams-design labels (0..4) to the real arm configs.
# The mapping itself is drawn once at random (see main()) -- these are
# just the five source configs it draws from.
BASE_CONFIGS = {
    "baseline": "config/quorum-handshake-covinst-rq6-baseline-final-n30.json",
    "local-only": "config/quorum-handshake-covinst-rq6-local-only-final-n30.json",
    "full": "config/quorum-handshake-covinst-rq6-full-final-n30.json",
    "random-matched": "config/quorum-handshake-covinst-rq7-random-matched-final-n30.json",
    "random-nominal": "config/quorum-handshake-covinst-rq7-random-nominal-final-n30.json",
}


def williams_10_sequences(n=5):
    """Returns the 2n Williams-balanced sequences (list of n-permutations
    of range(n)) for odd n. See module docstring for the construction."""
    assert n % 2 == 1, "this construction's doubling step is for odd n only"
    base = [0, 1, n - 1]
    lo, hi = 2, n - 2
    while lo <= hi:
        base.append(lo)
        if hi != lo:
            base.append(hi)
        lo += 1
        hi -= 1
    assert sorted(base) == list(range(n)), f"base fold-in sequence is not a permutation: {base}"

    rows = [[(v + i) % n for v in base] for i in range(n)]
    mirrored = [list(reversed(r)) for r in rows]
    return rows + mirrored


def verify_balance(sequences, n=5, repeats=1):
    """Sanity-checks the two properties the replication design needs:
    each treatment appears equally often per position, and each ordered
    adjacent pair appears equally often. Raises AssertionError on any
    violation -- this is the "dry-run" check Codex asked to audit before
    the 150-execution batch."""
    all_seqs = sequences * repeats
    total = len(all_seqs)

    position_counts = [[0] * n for _ in range(n)]
    for seq in all_seqs:
        for pos, arm in enumerate(seq):
            position_counts[pos][arm] += 1
    expected_per_cell = total // n
    for pos in range(n):
        for arm in range(n):
            got = position_counts[pos][arm]
            assert got == expected_per_cell, (
                f"position {pos}, arm {arm}: expected {expected_per_cell} occurrences, got {got}"
            )

    pair_counts = {}
    for seq in all_seqs:
        for a, b in zip(seq, seq[1:]):
            pair_counts[(a, b)] = pair_counts.get((a, b), 0) + 1
    expected_pairs = total * (n - 1) // (n * (n - 1))
    for a in range(n):
        for b in range(n):
            if a == b:
                continue
            got = pair_counts.get((a, b), 0)
            assert got == expected_pairs, (
                f"ordered pair {a}->{b}: expected {expected_pairs} occurrences, got {got}"
            )
    return {"position_counts": position_counts, "pair_counts": pair_counts,
             "expected_per_cell": expected_per_cell, "expected_pairs": expected_pairs}


def config_hash(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()[:12]


def verify_config_parity():
    """Re-checks, automatically, the same parity Codex verified by hand in
    sync-claude-codex.md (2026-09-17, entry (4)): the three GA arm configs
    must be identical outside coverageInst.causalDistance and output.
    runName, and the two random-arm configs must be identical outside
    ga.populationSize and output.runName. This guards against silent drift
    if a base config is ever hand-edited after that manual check -- the
    schedule must not be generated (and no cell configs written) if it no
    longer holds."""
    def load(name):
        with open(os.path.join(REPO_ROOT, BASE_CONFIGS[name])) as f:
            return json.load(f)

    def strip(cfg, *paths):
        cfg = json.loads(json.dumps(cfg))  # deep copy
        for path in paths:
            node = cfg
            for key in path[:-1]:
                node = node.get(key, {})
            node.pop(path[-1], None)
        return cfg

    ga_arms = ["baseline", "local-only", "full"]
    ga_stripped = [
        strip(load(a), ("benchmark", "coverageInst", "causalDistance"), ("output", "runName"))
        for a in ga_arms
    ]
    assert all(c == ga_stripped[0] for c in ga_stripped), (
        "GA arm configs differ outside causalDistance/output.runName -- aborting, "
        "see verify_config_parity() docstring"
    )

    random_arms = ["random-matched", "random-nominal"]
    random_stripped = [
        strip(load(a), ("ga", "populationSize"), ("output", "runName"))
        for a in random_arms
    ]
    assert random_stripped[0] == random_stripped[1], (
        "random-arm configs differ outside populationSize/output.runName -- aborting, "
        "see verify_config_parity() docstring"
    )


def make_cell_config(base_config_path, block, position, arm_name):
    with open(base_config_path) as f:
        cfg = json.load(f)
    cfg["ga"]["executions"] = 1
    run_name = f"interleaved-block{block:02d}-pos{position}-{arm_name}"
    cfg["output"]["runName"] = run_name
    return cfg, run_name


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", type=int, default=None,
                         help="RNG seed for the arm-label mapping and block-order shuffle; omit for a fresh random draw (still recorded in schedule.csv's header comment)")
    args = parser.parse_args()

    seed = args.seed if args.seed is not None else random.SystemRandom().randint(0, 2**31 - 1)
    rng = random.Random(seed)

    verify_config_parity()  # fails loudly before writing anything if a base config has drifted

    n = 5
    sequences = williams_10_sequences(n)
    verify_balance(sequences, n=n, repeats=3)  # fails loudly before writing anything if the math is wrong

    # Draw the abstract-label (0..4) -> real-arm mapping once.
    arm_names = list(BASE_CONFIGS.keys())
    shuffled_arms = arm_names[:]
    rng.shuffle(shuffled_arms)
    label_to_arm = dict(enumerate(shuffled_arms))

    # 30 blocks = the 10 sequences repeated 3 times, then the block ORDER shuffled.
    blocks = sequences * 3
    block_order = list(range(len(blocks)))
    rng.shuffle(block_order)

    os.makedirs(CELL_CONFIG_DIR, exist_ok=True)
    schedule_rows = []
    base_config_hashes = {name: config_hash(os.path.join(REPO_ROOT, path)) for name, path in BASE_CONFIGS.items()}

    for block_idx, seq_idx in enumerate(block_order):
        sequence = blocks[seq_idx]
        for position, label in enumerate(sequence):
            arm_name = label_to_arm[label]
            base_path = os.path.join(REPO_ROOT, BASE_CONFIGS[arm_name])
            cfg, run_name = make_cell_config(base_path, block_idx, position, arm_name)
            cell_config_path = os.path.join(CELL_CONFIG_DIR, f"{run_name}.json")
            with open(cell_config_path, "w") as f:
                json.dump(cfg, f, indent=2)
                f.write("\n")
            schedule_rows.append({
                "block": block_idx,
                "position": position,
                "arm": arm_name,
                "base_config": BASE_CONFIGS[arm_name],
                "base_config_hash": base_config_hashes[arm_name],
                "cell_config": os.path.relpath(cell_config_path, REPO_ROOT),
                "run_name": run_name,
            })

    schedule_path = os.path.join(OUT_DIR, "schedule.csv")
    with open(schedule_path, "w", newline="") as f:
        f.write(f"# seed={seed} arm_mapping={label_to_arm}\n")
        writer = csv.DictWriter(f, fieldnames=["block", "position", "arm", "base_config",
                                                "base_config_hash", "cell_config", "run_name"],
                                 lineterminator="\n")
        writer.writeheader()
        for row in schedule_rows:
            writer.writerow(row)

    print(f"seed={seed}")
    print(f"arm mapping: {label_to_arm}")
    print(f"wrote {len(schedule_rows)} schedule rows to {schedule_path}")
    print(f"wrote {len(schedule_rows)} cell configs to {CELL_CONFIG_DIR}")

    run_names = [r["run_name"] for r in schedule_rows]
    assert len(set(run_names)) == len(run_names), "duplicate run_name detected -- output would be overwritten"
    print("all run_names unique: OK")


if __name__ == "__main__":
    main()
