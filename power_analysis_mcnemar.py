#!/usr/bin/env python3
"""
Post-hoc power / prospective sample-size planning for the McNemar-paired
interleaved design, using the discordant-pair rates actually observed in
the two independent replications (2026-09-17 and 2026-09-18) as a
planning effect size for a possible FUTURE replication - not as a
retroactive explanation of the observed p-values, and not as a completed
prospective sample-size justification (see Hoenig and Heisey, 2001,
"The Abuse of Power," for why post-hoc power computed from an observed
effect cannot serve either of those purposes).

Method: EXACT enumeration, not simulation. Under McNemar's null of
marginal homogeneity, the number of discordant pairs D is
Binomial(n_blocks, p10+p01), and conditional on D, the number of blocks
favoring the first arm is Binomial(D, p10/(p10+p01)). Power is the sum,
over every (D, b) outcome, of its exact probability where the exact
two-sided McNemar test (Binomial reference distribution) rejects at
alpha=0.05. This replaces an earlier version of this script that used
Monte Carlo simulation (3000 draws/scenario) AND had a data-entry bug:
the full-vs-random-matched (replication 2) scenario was entered as
discordant counts (9, 4) - which does not reproduce the two arms'
actual marginal totals (23/30 vs 9/30: 23-9=14, and 18-4=14, but
9-4=5 does not) - instead of the correct (18, 4). Caught by Codex's
independent audit (sync-claude-codex.md, 2026-09-18 (11)); this
rewrite fixes both the data and the method (exact instead of simulated)
and was checked to reproduce Codex's own reported values exactly.

Usage: python3 power_analysis_mcnemar.py
"""
from math import comb

ALPHA = 0.05


def mcnemar_exact_pvalue(b, c):
    """Two-sided exact McNemar p-value (binomial reference, min(b,c) tail doubled)."""
    n = b + c
    if n == 0:
        return 1.0
    k = min(b, c)
    tail = sum(comb(n, i) * 0.5 ** n for i in range(0, k + 1))
    p = min(1.0, 2 * tail)
    return p


def power_exact(n_blocks, p10, p01, alpha=ALPHA):
    p_disc = p10 + p01
    if p_disc == 0:
        return 0.0
    p_favor = p10 / p_disc
    power = 0.0
    for d in range(0, n_blocks + 1):
        p_d = comb(n_blocks, d) * (p_disc ** d) * ((1 - p_disc) ** (n_blocks - d))
        if p_d == 0.0:
            continue
        for b in range(0, d + 1):
            c = d - b
            if mcnemar_exact_pvalue(b, c) < alpha:
                p_b_given_d = comb(d, b) * (p_favor ** b) * ((1 - p_favor) ** c)
                power += p_d * p_b_given_d
    return power


# Discordant counts observed in each replication (out of 30 blocks each).
# Verified against the raw per-block success/failure data both replications
# recorded, not re-derived from marginal totals alone.
SCENARIOS = [
    ("full-vs-baseline (replication 1)", 10, 4, 30),
    ("full-vs-baseline (replication 2)", 9, 3, 30),
    ("full-vs-random-matched (replication 1)", 9, 2, 30),
    ("full-vs-random-matched (replication 2)", 18, 4, 30),
]

if __name__ == "__main__":
    for label, favor_count, against_count, n in SCENARIOS:
        p10, p01 = favor_count / n, against_count / n
        observed_p = mcnemar_exact_pvalue(favor_count, against_count)
        print(f"\n{label}: observed p10={p10:.3f} p01={p01:.3f} (exact p={observed_p:.6f} at n=30)")
        for n_blocks in [30, 60, 90, 120, 150]:
            power = power_exact(n_blocks, p10, p01)
            print(f"  n={n_blocks:>3} blocks -> exact power = {power:.4f}")
