#!/bin/bash
# Reproduces the C-vs-D replay table for RIP's R2-saturation scenario
# (Codex sync entry 34/36): genotype A=(15,1,1,1) has R2 saturate right at
# the boundary (candidate=16); genotype B=(15,15,1,1) has R2 saturate far
# beyond it (candidate=30). Both configs are identical except D adds a
# cross-process source (local+cross combined) to R2/R3/R4's finite-send
# edge; C keeps only the local source. Expected pattern: C ties A and B on
# every edge downstream of R2 (it only ever sees the propagated 16); D
# ranks A as closer to recoverable than B on those same edges, because it
# recovers R2's own real margin via the recursive chain. No GA involved -
# single fixed-genotype evaluations only.
set -e
cd "$(dirname "$0")"
CP="$(pwd)/target/classes:$(cat /tmp/cp.txt)"

for cfg in config/rip-covinst-c-local.json config/rip-covinst-d-cross.json; do
  for genotype in "A 15 1 1 1" "B 15 15 1 1"; do
    read -r label w1 w2 w3 w4 <<< "$genotype"
    echo "=== $cfg genotype $label=($w1,$w2,$w3,$w4) ==="
    java -cp "$CP" BioConcST.DebugSingleEvaluation "$cfg" "$w1" "$w2" "$w3" "$w4" \
      | grep -E "EDGE_DEBUG|distance="
  done
done
