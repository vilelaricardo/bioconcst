#!/bin/bash
# Runs the Williams-balanced interleaved-block replication (see
# sync-claude-codex.md, 2026-09-17 entries (4)/(6)/(8)): 30 blocks, one
# execution of each of the 5 arms per block, in the order recorded in
# config/interleaved-replication/schedule.csv -- so treatment is not
# confounded with time/machine-state drift the way the earlier
# sequential-block batch was.
#
# Usage:
#   ./run-interleaved-replication.sh dry-run   # sanity-check only, no GA runs
#   ./run-interleaved-replication.sh run       # the real 150-execution batch
#
# Per Codex's audit (sync-claude-codex.md, 2026-09-17 (8)): both modes
# require a clean, committed working tree -- the recorded commit hash must
# actually describe the bytecode that runs, not just whatever HEAD happens
# to be while unrelated files are modified. Commit everything the
# replication depends on (src, test, base configs, this script, the
# generator, the generated schedule + cell configs) before running either
# mode.
set -euo pipefail
cd "$(dirname "$0")"

MODE="${1:-}"
if [[ "$MODE" != "dry-run" && "$MODE" != "run" ]]; then
  echo "usage: $0 {dry-run|run}" >&2
  exit 1
fi

SCHEDULE=config/interleaved-replication/schedule.csv
if [[ ! -f "$SCHEDULE" ]]; then
  echo "missing $SCHEDULE -- run generate_williams_schedule.py first" >&2
  exit 1
fi

LOG=interleaved-replication-execution.log

COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
if [[ "$COMMIT" == "unknown" ]]; then
  echo "FAIL: not a git repository -- cannot pin a commit hash for reproducibility" >&2
  exit 1
fi
if ! git diff --quiet HEAD -- 2>/dev/null; then
  echo "FAIL: tracked files differ from HEAD ($COMMIT) -- commit everything the" >&2
  echo "      replication depends on before running, so the recorded commit" >&2
  echo "      hash actually describes the bytecode that runs. See:" >&2
  git diff --stat HEAD -- >&2
  exit 1
fi

echo "commit=$COMMIT (working tree clean, matches HEAD)"
echo "rebuilding from this exact commit (mvn -o -DskipTests package)..."
mvn -o -DskipTests package -q

CP="$(pwd)/target/classes:$(cat /tmp/cp.txt 2>/dev/null || true)"
if [[ ! -f /tmp/cp.txt ]]; then
  echo "computing classpath (mvn dependency:build-classpath)..."
  mvn -o dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -q
  CP="$(pwd)/target/classes:$(cat /tmp/cp.txt)"
fi

JAVA_VERSION="$(java -version 2>&1 | head -1)"
HOSTNAME="$(hostname)"
NPROC="$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo unknown)"

# tail -n1 on Linux uptime output; falls back gracefully if the load-average
# fields aren't present (e.g. some containerized/macOS shells).
load_avg() {
  uptime 2>/dev/null | sed -n 's/.*load average[s]*: *//p' | tr -d ','
}

echo "commit=$COMMIT java=[$JAVA_VERSION] host=$HOSTNAME nproc=$NPROC"

# --- shared validation: schedule shape, uniqueness, config-hash drift,
# and (for "run") that no output from a prior/partial attempt exists yet.
# Exits non-zero on any failure; the caller decides what to do with that.
validate_schedule() {
  local check_no_existing_outputs="$1"

  n_rows=$(tail -n +3 "$SCHEDULE" | wc -l | tr -d ' ')
  echo "schedule rows (excluding seed-comment + header): $n_rows"
  if [[ "$n_rows" -ne 150 ]]; then
    echo "FAIL: expected 150 rows, found $n_rows" >&2
    return 1
  fi

  n_configs=$(tail -n +3 "$SCHEDULE" | tr -d '\r' | cut -d, -f6 | sort -u | wc -l | tr -d ' ')
  n_runnames=$(tail -n +3 "$SCHEDULE" | tr -d '\r' | cut -d, -f7 | sort -u | wc -l | tr -d ' ')
  echo "unique cell_config paths: $n_configs, unique run_names: $n_runnames"
  if [[ "$n_configs" -ne 150 || "$n_runnames" -ne 150 ]]; then
    echo "FAIL: expected 150 unique cell configs and run_names, got $n_configs / $n_runnames" >&2
    return 1
  fi

  missing=0
  hash_mismatches=0
  existing_outputs=0
  declare -A seen_base_hash
  while IFS=, read -r block position arm base_config base_config_hash cell_config run_name; do
    [[ "$block" == "block" ]] && continue
    run_name="${run_name%$'\r'}"
    cell_config="${cell_config%$'\r'}"
    base_config="${base_config%$'\r'}"
    base_config_hash="${base_config_hash%$'\r'}"

    if [[ ! -f "$cell_config" ]]; then
      echo "MISSING cell config: $cell_config" >&2
      missing=$((missing + 1))
      continue
    fi

    if [[ -z "${seen_base_hash[$base_config]:-}" ]]; then
      current_hash="$(shasum -a 256 "$base_config" 2>/dev/null | cut -c1-12)"
      if [[ -z "$current_hash" ]]; then
        current_hash="$(sha256sum "$base_config" | cut -c1-12)"
      fi
      seen_base_hash[$base_config]="$current_hash"
    fi
    if [[ "${seen_base_hash[$base_config]}" != "$base_config_hash" ]]; then
      echo "HASH MISMATCH: $base_config recorded as $base_config_hash, now ${seen_base_hash[$base_config]} -- base config changed since the schedule was generated" >&2
      hash_mismatches=$((hash_mismatches + 1))
    fi

    if [[ "$check_no_existing_outputs" == "1" ]]; then
      for suffix in "-execution0.csv" "-execution0-meta.json" "-execution0-replay.json"; do
        if [[ -f "results/${run_name}${suffix}" ]]; then
          echo "OUTPUT ALREADY EXISTS: results/${run_name}${suffix} -- refusing to start (partial run or accidental resume?)" >&2
          existing_outputs=$((existing_outputs + 1))
        fi
      done
    fi
  done < <(tail -n +3 "$SCHEDULE")

  if [[ "$missing" -gt 0 || "$hash_mismatches" -gt 0 || "$existing_outputs" -gt 0 ]]; then
    echo "FAIL: missing=$missing hash_mismatches=$hash_mismatches existing_outputs=$existing_outputs" >&2
    return 1
  fi
  echo "validation OK: 150 unique/present cell configs, base-config hashes match, $( [[ "$check_no_existing_outputs" == "1" ]] && echo "no pre-existing outputs" || echo "output check skipped" )."
  return 0
}

if [[ "$MODE" == "dry-run" ]]; then
  validate_schedule 0
  echo "(Config content parity across arms -- GA arms identical outside"
  echo " causalDistance/output.runName, random arms identical outside"
  echo " populationSize/output.runName -- is checked by"
  echo " generate_williams_schedule.py's verify_config_parity() every time"
  echo " the schedule is (re)generated, not by this dry-run.)"
  exit 0
fi

# --- real run: re-validate (this time including "no existing outputs"),
# THEN truncate the log and execute in schedule (block) order, one JVM per row ---
validate_schedule 1

: > "$LOG"
{
  echo "commit=$COMMIT java=[$JAVA_VERSION] host=$HOSTNAME nproc=$NPROC schedule=$SCHEDULE"
  echo "$(head -1 "$SCHEDULE")"
} >> "$LOG"

row_num=0
while IFS=, read -r block position arm base_config base_config_hash cell_config run_name; do
  [[ "$block" == "block" ]] && continue
  cell_config="${cell_config%$'\r'}"
  run_name="${run_name%$'\r'}"
  arm="${arm%$'\r'}"
  row_num=$((row_num + 1))
  ts_start="$(date '+%Y-%m-%d %H:%M:%S %Z')"
  load="$(load_avg)"
  echo "=== row $row_num/150: block=$block position=$position arm=$arm run_name=$run_name start=$ts_start load=$load ===" | tee -a "$LOG"
  java -cp "$CP" BioConcST.TestDataGeneration "$cell_config" >> "$LOG" 2>&1
  ts_end="$(date '+%Y-%m-%d %H:%M:%S %Z')"
  echo "=== row $row_num/150 done: run_name=$run_name end=$ts_end ===" | tee -a "$LOG"
done < <(tail -n +3 "$SCHEDULE")

echo "INTERLEAVED_REPLICATION_DONE" | tee -a "$LOG"
