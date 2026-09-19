#!/bin/bash
# Small pilot (pop=16, gens=10, executions=6) to sanity-check the raft-election
# causalDistance config before committing to a full n=120 campaign.
set -e
cd "$(dirname "$0")"
CP="$(pwd)/target/classes:$(cat /tmp/cp.txt)"

for cfg in \
  config/raft-election-covinst-pilot-baseline.json \
  config/raft-election-covinst-pilot-full.json \
  ; do
  echo "=== starting $cfg at $(date) ==="
  java -cp "$CP" BioConcST.TestDataGeneration "$cfg"
  echo "=== finished $cfg at $(date) ==="
done
echo "PILOT DONE"
