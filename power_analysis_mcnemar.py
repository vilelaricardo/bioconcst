#!/usr/bin/env python3
"""
Post-hoc power / prospective sample-size planning for the McNemar-paired
interleaved design, using the discordant-pair rates actually observed in
the two independent replications (2026-09-17 and 2026-09-18) as the
planning effect size. This is the "prospective sample-size justification"
flagged as missing in main.tex's Statistical Conclusion Validity threat
and in sync-claude-codex.md's discussion of the two replications.

Method: direct Monte Carlo simulation against the *exact* McNemar test
(statsmodels.stats.contingency_tables.mcnemar, exact=True) rather than the
closed-form normal-approximation sample-size formula (Miettinen 1968),
which this script's own sanity check found to be modestly optimistic at
these sample sizes (predicted 89 blocks for 80% power on one scenario;
simulation showed that N only gave ~76-78% power against the exact test).

Usage: python3 power_analysis_mcnemar.py
"""
import numpy as np
from statsmodels.stats.contingency_tables import mcnemar

rng = np.random.default_rng(7)


def simulate_power(n_blocks, p10, p01, n_sims=3000, alpha=0.05):
    p_concord = 1 - p10 - p01
    rejections = 0
    for _ in range(n_sims):
        outcomes = rng.choice(["10", "01", "c"], size=n_blocks, p=[p10, p01, p_concord])
        b = int(np.sum(outcomes == "10"))
        c = int(np.sum(outcomes == "01"))
        res = mcnemar([[0, b], [c, 0]], exact=True)
        if res.pvalue < alpha:
            rejections += 1
    return rejections / n_sims


# Discordant counts observed in each replication (out of 30 blocks each).
SCENARIOS = [
    ("full-vs-baseline (replication 1)", 10, 4, 30),
    ("full-vs-baseline (replication 2)", 9, 3, 30),
    ("full-vs-random-matched (replication 1)", 9, 2, 30),
    ("full-vs-random-matched (replication 2)", 9, 4, 30),
]

if __name__ == "__main__":
    for label, favor_count, against_count, n in SCENARIOS:
        p10, p01 = favor_count / n, against_count / n
        print(f"\n{label}: observed p10={p10:.3f} p01={p01:.3f}")
        for n_blocks in [30, 60, 90, 120, 150]:
            power = simulate_power(n_blocks, p10, p01)
            print(f"  n={n_blocks:>3} blocks -> simulated power = {power:.3f}")
