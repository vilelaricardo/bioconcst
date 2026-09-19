#!/bin/bash
set -e
cd "$(dirname "$0")"
CP="$(pwd)/target/classes:$(cat /tmp/cp.txt)"

for cfg in \
  config/raft-election-covinst-rare-baseline.json \
  config/raft-election-covinst-rare-full.json \
  ; do
  echo "=== starting $cfg at $(date) ==="
  java -cp "$CP" BioConcST.TestDataGeneration "$cfg"
  echo "=== finished $cfg at $(date) ==="
done
echo "PILOT DONE"
