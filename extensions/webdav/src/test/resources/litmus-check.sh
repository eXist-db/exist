#!/bin/bash
# litmus-check.sh — Compare litmus results against a known-failure baseline.
#
# Usage: litmus-check.sh <litmus-output-file> <baseline-file>
#
# Exit codes:
#   0  — all results match baseline (no regressions)
#   1  — regression detected (a previously-passing test now fails)

set -euo pipefail

LITMUS_OUTPUT="${1:?Usage: litmus-check.sh <litmus-output> <baseline>}"
BASELINE="${2:?Usage: litmus-check.sh <litmus-output> <baseline>}"

# Parse actual failures from litmus output.
# litmus lines look like: "17. cond_put_corrupt_token FAIL (...)"
# Note: `grep` returns 1 when zero matches; combined with `set -euo pipefail`
# the pipe would fail and abort the script before assignment, so a perfectly
# green run (0 failures) would crash the check. Appending `|| true` ensures
# ACTUAL_FAILS is simply empty in that case.
ACTUAL_FAILS=$( (grep 'FAIL' "$LITMUS_OUTPUT" \
    | sed -E 's/^.*[0-9]+\. ([a-z_]+)\.* *FAIL.*/\1/' \
    | grep -v '^<' \
    | sort -u) || true)

# Parse expected failures from baseline (skip comments and blank lines).
# Same `|| true` guard as above so a baseline of only comments (no expected
# failures) doesn't crash the pipe.
EXPECTED_FAILS=$( (grep -v '^\s*#' "$BASELINE" | grep -v '^\s*$' | sort -u) || true)

# Find regressions: tests that FAIL now but are NOT in the baseline
REGRESSIONS=""
for test in $ACTUAL_FAILS; do
    if ! echo "$EXPECTED_FAILS" | grep -qx "$test"; then
        REGRESSIONS="${REGRESSIONS}  - ${test}"$'\n'
    fi
done

# Find improvements: tests in the baseline that no longer FAIL
IMPROVEMENTS=""
for test in $EXPECTED_FAILS; do
    if ! echo "$ACTUAL_FAILS" | grep -qx "$test"; then
        IMPROVEMENTS="${IMPROVEMENTS}  - ${test}"$'\n'
    fi
done

# Count totals from summary lines like:
#   <- summary for `basic': of 16 tests run: 16 passed, 0 failed. 100.0%
# `grep ... || true` keeps the script exit-clean when zero matches occur, and
# the `awk` sum produces a numeric 0 rather than an empty string.
TOTAL_PASS=$( (grep -E 'of [0-9]+ tests run' "$LITMUS_OUTPUT" \
    | sed -E 's/.*tests run: ([0-9]+) passed.*/\1/' \
    | awk '{ s += $1 } END { print s+0 }') || echo "?")
TOTAL_FAIL=$( (grep -E 'of [0-9]+ tests run' "$LITMUS_OUTPUT" \
    | sed -E 's/.* ([0-9]+) failed.*/\1/' \
    | awk '{ s += $1 } END { print s+0 }') || echo "?")
EXPECTED_COUNT=$(echo -n "$EXPECTED_FAILS" | grep -c '^[^[:space:]]' || true)

echo "=== Litmus WebDAV Compliance ==="
echo "Passed: ${TOTAL_PASS}"
echo "Failed: ${TOTAL_FAIL}"
echo "Expected failures: ${EXPECTED_COUNT}"
echo ""

if [ -n "$REGRESSIONS" ]; then
    echo "::error::REGRESSION DETECTED — previously-passing tests now fail:"
    echo "$REGRESSIONS"
    echo "Fix these regressions or update the baseline if the failure is expected."
    exit 1
fi

if [ -n "$IMPROVEMENTS" ]; then
    echo "::warning::Tests improved — previously-failing tests now pass:"
    echo "$IMPROVEMENTS"
    echo "Update extensions/webdav/src/test/resources/litmus-baseline.txt to remove these entries."
fi

echo "No regressions detected. All results match baseline."
exit 0
