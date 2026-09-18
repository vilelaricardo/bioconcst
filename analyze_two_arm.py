#!/usr/bin/env python3
"""
Analyzes the two-arm (full vs. random-huge) balanced experiment
(2026-09-18): does giving random sampling ~5x the GA's real evaluation
budget close most of the gap with full causalDistance redirection?
Directly tests Ricardo's point that cumulative coverage at a fixed budget
conflates search quality with budget sufficiency.

Usage: python3 analyze_two_arm.py
"""
import json
import csv
import statistics
from collections import defaultdict

RESULTS_DIR = "results"
SCHEDULE = "config/two-arm-random-huge/schedule.csv"

with open(SCHEDULE) as f:
    lines = f.readlines()
reader = csv.DictReader(lines[1:])
by_arm = defaultdict(list)
by_block = defaultdict(dict)
for r in reader:
    by_arm[r["arm"]].append(r["run_name"])
    by_block[int(r["block"])][r["arm"]] = r["run_name"]


def load(run_name):
    meta = json.load(open(f"{RESULTS_DIR}/{run_name}-execution0-meta.json"))
    return {"meta": meta, "success": len(meta["uncoveredElementKeys"]) == 0}


print("=" * 70)
print("Two-arm experiment: full causalDistance vs. random at ~5x budget")
print("=" * 70)

for arm in ["full", "random-huge"]:
    results = [load(rn) for rn in by_arm[arm]]
    succ = sum(1 for r in results if r["success"])
    evals = [r["meta"]["evaluationCount"] for r in results]
    print(f"\n{arm}: {succ}/30 success, mean evaluationCount={statistics.mean(evals):.1f}")

yy = yn = ny = nn = 0
for block in range(30):
    sa = load(by_block[block]["full"])["success"]
    sb = load(by_block[block]["random-huge"])["success"]
    if sa and sb: yy += 1
    elif sa and not sb: yn += 1
    elif not sa and sb: ny += 1
    else: nn += 1

print(f"\nPaired table (full-only={yn}, random-huge-only={ny}, both={yy}, neither={nn})")

try:
    from statsmodels.stats.contingency_tables import mcnemar
    res = mcnemar([[yy, yn], [ny, nn]], exact=True)
    print(f"McNemar exact p={res.pvalue:.4f}")
except ImportError:
    from scipy.stats import binomtest
    discordant = yn + ny
    if discordant > 0:
        p = binomtest(min(yn, ny), discordant, 0.5).pvalue
        print(f"Exact sign-test p={p:.4f}")
    else:
        print("No discordant pairs")

print("\nInterpretation guide:")
print("- If random-huge closes most of the gap with full (similar success %,")
print("  p not significant), that supports Ricardo's point: the earlier")
print("  matched-budget comparisons partly reflected budget, not just method.")
print("- If full still clearly leads even at ~5x random's budget, that argues")
print("  the mechanism's guidance has real value beyond just running longer.")
