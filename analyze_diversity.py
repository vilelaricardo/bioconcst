#!/usr/bin/env python3
"""
Analyzes the population-diversity (unique genotype count per generation)
data from the second interleaved replication (2026-09-18), testing the
premature-convergence hypothesis: does full/local-only collapse population
diversity faster than baseline, trading quick early successes for a lower
ceiling once the population can no longer explore towards the rare
celebrate-window target?

Usage: python3 analyze_diversity.py
"""
import json
import csv
import statistics
from collections import defaultdict

RESULTS_DIR = "results"
SCHEDULE = "config/interleaved-replication/schedule.csv"

with open(SCHEDULE) as f:
    lines = f.readlines()
reader = csv.DictReader(lines[1:])
by_arm = defaultdict(list)
for r in reader:
    by_arm[r["arm"]].append(r["run_name"])


def load(run_name):
    meta = json.load(open(f"{RESULTS_DIR}/{run_name}-execution0-meta.json"))
    rows = list(csv.DictReader(open(f"{RESULTS_DIR}/{run_name}-execution0.csv")))
    return {"meta": meta, "rows": rows, "success": len(meta["uncoveredElementKeys"]) == 0}


print("=" * 70)
print("Population diversity (unique genotypes / 16) per generation, by arm")
print("=" * 70)

GA_ARMS = ["baseline", "local-only", "full"]
diversity_by_arm = {}
success_by_arm = {}
for arm in GA_ARMS:
    results = [load(rn) for rn in by_arm[arm]]
    success_by_arm[arm] = sum(1 for r in results if r["success"])
    # mean unique-genotype count per generation across the 30 runs
    ngen = len(results[0]["rows"])
    mean_diversity = []
    for g in range(ngen):
        vals = [int(r["rows"][g]["uniqueGenotypes"]) for r in results]
        mean_diversity.append(round(statistics.mean(vals), 2))
    diversity_by_arm[arm] = mean_diversity
    print(f"\n{arm} (success {success_by_arm[arm]}/30):")
    checkpoints = [0, 4, 9, 14, 19, 24, 27, 29]
    for c in checkpoints:
        print(f"  gen {c+1}: mean unique genotypes = {mean_diversity[c]}/16")

print("\n--- Full trajectories (for plotting) ---")
for arm in GA_ARMS:
    print(f"{arm}: {diversity_by_arm[arm]}")

print("\n--- Correlation check: final diversity vs success ---")
for arm in GA_ARMS:
    results = [load(rn) for rn in by_arm[arm]]
    succ_final_div = [int(r["rows"][-1]["uniqueGenotypes"]) for r in results if r["success"]]
    fail_final_div = [int(r["rows"][-1]["uniqueGenotypes"]) for r in results if not r["success"]]
    if succ_final_div:
        print(f"{arm}: successful runs' final diversity mean = {statistics.mean(succ_final_div):.2f} (n={len(succ_final_div)})")
    if fail_final_div:
        print(f"{arm}: failed runs' final diversity mean = {statistics.mean(fail_final_div):.2f} (n={len(fail_final_div)})")

# Also redo the success/McNemar summary for this second batch for comparison
print("\n" + "=" * 70)
print("Success summary, second interleaved batch (diversity-tracking run)")
print("=" * 70)
ALL_ARMS = ["baseline", "local-only", "full", "random-matched", "random-nominal"]
for arm in ALL_ARMS:
    results = [load(rn) for rn in by_arm[arm]]
    succ = sum(1 for r in results if r["success"])
    evals = [r["meta"]["evaluationCount"] for r in results]
    print(f"{arm}: {succ}/30, mean evaluationCount={statistics.mean(evals):.1f}")

try:
    import numpy as np
    from statsmodels.stats.contingency_tables import mcnemar

    by_block = defaultdict(dict)
    with open(SCHEDULE) as f:
        lines2 = f.readlines()
    reader2 = csv.DictReader(lines2[1:])
    for r in reader2:
        by_block[int(r["block"])][r["arm"]] = r["run_name"]

    def paired_table(arm_a, arm_b):
        yy = yn = ny = nn = 0
        for block in range(30):
            sa = load(by_block[block][arm_a])["success"]
            sb = load(by_block[block][arm_b])["success"]
            if sa and sb: yy += 1
            elif sa and not sb: yn += 1
            elif not sa and sb: ny += 1
            else: nn += 1
        return yy, yn, ny, nn

    print("\n--- McNemar exact, paired by block (second batch) ---")
    for a, b in [("full", "baseline"), ("full", "local-only"), ("local-only", "baseline"),
                 ("full", "random-matched"), ("full", "random-nominal")]:
        yy, yn, ny, nn = paired_table(a, b)
        res = mcnemar([[yy, yn], [ny, nn]], exact=True)
        print(f"  {a} vs {b}: [[{yy},{yn}],[{ny},{nn}]] p={res.pvalue:.4f}")
except ImportError as e:
    print(f"(skipping McNemar: {e})")
