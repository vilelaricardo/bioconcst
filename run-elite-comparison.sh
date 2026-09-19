#!/bin/bash
set -e
cd "$(dirname "$0")"
CP="$(pwd)/target/classes:$(cat /tmp/cp.txt)"

for cfg in \
  config/two-phase-commit-covinst-elite-baseline.json \
  config/two-phase-commit-covinst-elite-full.json \
  config/raft-election-covinst-rare-elite-baseline.json \
  config/raft-election-covinst-rare-elite-full.json \
  config/paxos-covinst-elite-baseline.json \
  config/paxos-covinst-elite-full.json \
  config/threshold-handshake-covinst-elite-baseline.json \
  config/threshold-handshake-covinst-elite-full.json \
  config/combined-handshake-covinst-elite-baseline.json \
  config/combined-handshake-covinst-elite-full.json \
  config/bully-election-covinst-elite-baseline.json \
  config/bully-election-covinst-elite-full.json \
  config/ricart-agrawala-covinst-elite-baseline.json \
  config/ricart-agrawala-covinst-elite-full.json \
  ; do
  echo "=== starting $cfg at $(date) ==="
  java -Dcoverage.useElitism=true -cp "$CP" BioConcST.TestDataGeneration "$cfg"
  echo "=== finished $cfg at $(date) ==="
done
echo "ELITE COMPARISON DONE"
