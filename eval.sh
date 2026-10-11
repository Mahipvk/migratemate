#!/bin/bash
# Runs every sample in samples/ in all three modes and prints a comparison table.
#   recipes = OpenRewrite only (no AI)        -> the baseline
#   ai      = version bump + AI only
#   full    = OpenRewrite + AI (MigrateMate's approach)
set -e
cd "$(dirname "$0")"

for project in samples/*/; do
  for mode in recipes ai full; do
    ./upgrade.sh "$project" "$mode" || echo "run failed: $project $mode"
  done
done

echo
echo "=== Eval results (eval/results.csv) ==="
column -s, -t < eval/results.csv
