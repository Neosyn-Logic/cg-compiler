#!/usr/bin/env bash
# Error-reporting check: a source with an error must be rejected with the front end's own
# diagnostic and NOTHING else -- no internal compiler exception printed above or below it, and
# never a bare "No IR files generated" that hides the real errors.
#
#   bash tests/error-reporting/check.sh <cg-language-server.jar>
#
# Each case is `<file>.cg` + the diagnostic it must produce. Exits non-zero on any failure.
set -u
JAR="${1:?usage: check.sh <cg-language-server.jar>}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# file | a diagnostic that must appear
CASES=(
  "IntSum64.cg|pixel cannot be resolved"
  "UndeclaredAssign.cg|sum cannot be resolved"
  "UndeclaredRead.cg|pixel cannot be resolved"
)
# anything from a deeper layer than the front end
FORBIDDEN='Exception|Transform error|Unresolved reference|^[[:space:]]+at |No IR files generated'

fail=0
for case in "${CASES[@]}"; do
  file="${case%%|*}"; want="${case#*|}"; bad=0
  mkdir -p "$WORK/$file/src/t"
  cp "$HERE/$file" "$WORK/$file/src/t/$file"
  out="$(java -jar "$JAR" generate "$WORK/$file/src/t/$file" --output "$WORK/$file/out" 2>&1)"
  rc=$?
  if [ "$rc" -eq 0 ]; then
    echo "FAIL $file: generate succeeded on a source with an error"; fail=1; continue
  fi
  if ! grep -q "$want" <<< "$out"; then
    echo "FAIL $file: the real diagnostic \"$want\" is missing"; bad=1
  fi
  if grep -qE "$FORBIDDEN" <<< "$out"; then
    echo "FAIL $file: an internal error is printed:"
    grep -E "$FORBIDDEN" <<< "$out" | head -3 | sed 's/^/    /'
    bad=1
  fi
  if [ "$bad" -eq 0 ]; then echo "ok   $file"; else fail=1; fi
done
[ "$fail" -eq 0 ] && echo "ERROR REPORTING: all ${#CASES[@]} cases clean" || echo "ERROR REPORTING: FAILED"
exit "$fail"
