#!/bin/bash
# Generic runner for a balanced block schedule (see
# generate_williams_schedule.py / generate_two_arm_schedule.py and
# sync-claude-codex.md, 2026-09-17/18 entries): executes the rows of a
# schedule.csv in order, one JVM per row, requiring a clean committed
# working tree so the recorded commit hash actually describes the bytecode
# that ran.
#
# Usage:
#   ./run-block-schedule.sh <schedule-dir> dry-run   # sanity-check only
#   ./run-block-schedule.sh <schedule-dir> run        # the real batch
#
# <schedule-dir> is a directory containing schedule.csv (row count is read
# from the file itself, not hardcoded) and cell-configs/.
set -euo pipefail
cd "$(dirname "$0")"

SCHEDULE_DIR="${1:-}"
MODE="${2:-}"
if [[ -z "$SCHEDULE_DIR" || ( "$MODE" != "dry-run" && "$MODE" != "run" ) ]]; then
  echo "usage: $0 <schedule-dir> {dry-run|run}" >&2
  exit 1
fi

SCHEDULE="$SCHEDULE_DIR/schedule.csv"
if [[ ! -f "$SCHEDULE" ]]; then
  echo "missing $SCHEDULE" >&2
  exit 1
fi

EXPECTED_ROWS=$(tail -n +3 "$SCHEDULE" | wc -l | tr -d ' ')
LOG="$(basename "$SCHEDULE_DIR")-execution.log"
DONE_MARKER="$(basename "$SCHEDULE_DIR" | tr '[:lower:]-' '[:upper:]_')_DONE"

COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
if [[ "$COMMIT" == "unknown" ]]; then
  echo "FAIL: not a git repository -- cannot pin a commit hash for reproducibility" >&2
  exit 1
fi
if ! git diff --quiet HEAD -- 2>/dev/null; then
  echo "FAIL: tracked files differ from HEAD ($COMMIT) -- commit everything the" >&2
  echo "      run depends on first, so the recorded commit hash actually" >&2
  echo "      describes the bytecode that runs. See:" >&2
  git diff --stat HEAD -- >&2
  exit 1
fi

echo "commit=$COMMIT (working tree clean, matches HEAD)"
echo "rebuilding from this exact commit (mvn -o -DskipTests compile)..."
mvn -o -DskipTests compile -q

CP="$(pwd)/target/classes:$(cat /tmp/cp.txt 2>/dev/null || true)"
if [[ ! -f /tmp/cp.txt ]]; then
  echo "computing classpath (mvn dependency:build-classpath)..."
  mvn -o dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -q
  CP="$(pwd)/target/classes:$(cat /tmp/cp.txt)"
fi

JAVA_VERSION="$(java -version 2>&1 | head -1)"
HOSTNAME="$(hostname)"
NPROC="$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo unknown)"

load_avg() {
  uptime 2>/dev/null | sed -n 's/.*load average[s]*: *//p' | tr -d ','
}

echo "commit=$COMMIT java=[$JAVA_VERSION] host=$HOSTNAME nproc=$NPROC schedule=$SCHEDULE rows=$EXPECTED_ROWS"

validate_schedule() {
  local check_no_existing_outputs="$1"

  n_configs=$(tail -n +3 "$SCHEDULE" | tr -d '\r' | cut -d, -f6 | sort -u | wc -l | tr -d ' ')
  n_runnames=$(tail -n +3 "$SCHEDULE" | tr -d '\r' | cut -d, -f7 | sort -u | wc -l | tr -d ' ')
  echo "unique cell_config paths: $n_configs, unique run_names: $n_runnames (expected $EXPECTED_ROWS)"
  if [[ "$n_configs" -ne "$EXPECTED_ROWS" || "$n_runnames" -ne "$EXPECTED_ROWS" ]]; then
    echo "FAIL: expected $EXPECTED_ROWS unique cell configs and run_names, got $n_configs / $n_runnames" >&2
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
      echo "HASH MISMATCH: $base_config recorded as $base_config_hash, now ${seen_base_hash[$base_config]}" >&2
      hash_mismatches=$((hash_mismatches + 1))
    fi

    if [[ "$check_no_existing_outputs" == "1" ]]; then
      for suffix in "-execution0.csv" "-execution0-meta.json" "-execution0-replay.json"; do
        if [[ -f "results/${run_name}${suffix}" ]]; then
          echo "OUTPUT ALREADY EXISTS: results/${run_name}${suffix} -- refusing to start" >&2
          existing_outputs=$((existing_outputs + 1))
        fi
      done
    fi
  done < <(tail -n +3 "$SCHEDULE")

  if [[ "$missing" -gt 0 || "$hash_mismatches" -gt 0 || "$existing_outputs" -gt 0 ]]; then
    echo "FAIL: missing=$missing hash_mismatches=$hash_mismatches existing_outputs=$existing_outputs" >&2
    return 1
  fi
  echo "validation OK: $EXPECTED_ROWS unique/present cell configs, base-config hashes match, $( [[ "$check_no_existing_outputs" == "1" ]] && echo "no pre-existing outputs" || echo "output check skipped" )."
  return 0
}

if [[ "$MODE" == "dry-run" ]]; then
  validate_schedule 0
  exit 0
fi

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
  echo "=== row $row_num/$EXPECTED_ROWS: block=$block position=$position arm=$arm run_name=$run_name start=$ts_start load=$load ===" | tee -a "$LOG"
  java -cp "$CP" BioConcST.TestDataGeneration "$cell_config" >> "$LOG" 2>&1
  ts_end="$(date '+%Y-%m-%d %H:%M:%S %Z')"
  echo "=== row $row_num/$EXPECTED_ROWS done: run_name=$run_name end=$ts_end ===" | tee -a "$LOG"
done < <(tail -n +3 "$SCHEDULE")

echo "$DONE_MARKER" | tee -a "$LOG"
