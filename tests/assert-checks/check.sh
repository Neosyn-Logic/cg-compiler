#!/usr/bin/env bash
# Assert check: every generated Verilog assert must SAY when it runs. A failing one prints
# "Assertion failed"; a passing one prints "[check] ... first passed" once. Without the second
# line, a run whose asserts never executed looked exactly like one where they all held.
#
#   bash tests/assert-checks/check.sh <cg-language-server.jar>
#
# The generated testbench has no end condition of its own, so each run is bounded by a timeout
# and judged from what it printed. vvp runs with stdin closed: `$stop` would otherwise wait at
# an interactive prompt. Needs iverilog. Exits non-zero on any failure.
set -u
JAR="${1:?usage: check.sh <cg-language-server.jar>}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB="$(cd "$HERE/../../fragments/com.neosyn.ide.libraries/lib/verilog/src/std" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

run() {   # run <top>: generate, elaborate, simulate; print the simulation output
  local top=$1 out="$WORK/$1"
  mkdir -p "$out/src/p"; cp "$HERE/$top.cg" "$out/src/p/"
  java -jar "$JAR" generate "$out/src/p/$top.cg" --output "$out/gen" >"$out/gen.log" 2>&1 \
    || { echo "GENERATE FAILED"; return; }
  local tb; tb=$(find "$out/gen" -name "$top.tb.v" | head -1)
  [ -n "$tb" ] || { echo "NO TESTBENCH"; return; }
  local dirs=(); while IFS= read -r d; do dirs+=(-y "$d" -I "$d"); done < <(find "$out/gen" -type d)
  iverilog -g2012 -o "$out/sim.vvp" -s "${top}_tb" "${dirs[@]}" -y "$LIB/lib" -y "$LIB/mem" \
    -y "$LIB/fifo" "$tb" >"$out/build.log" 2>&1 || { echo "ELABORATION FAILED"; return; }
  (cd "$out" && timeout 20 vvp -n sim.vvp </dev/null 2>&1)
}

fail=0
out=$(run Test_Plus1)
n=$(grep -c '^\[check\] .* first passed at ' <<< "$out")
if [ "$n" -eq 2 ] && ! grep -q 'Assertion failed' <<< "$out"; then
  echo "ok   Test_Plus1: both asserts reported passing, once each"
else
  echo "FAIL Test_Plus1: expected 2 [check] lines and no failure, got $n:"; head -5 <<< "$out" | sed 's/^/    /'; fail=1
fi
out=$(run Test_Plus1Bad)
if grep -q '^Assertion failed: (y == 8'"'"'hb)' <<< "$out" && grep -q '^\[check\] (y == 8'"'"'h4)' <<< "$out"; then
  echo "ok   Test_Plus1Bad: the wrong value fails, the right one before it is reported passing"
else
  echo "FAIL Test_Plus1Bad: expected a [check] then \"Assertion failed\":"; head -5 <<< "$out" | sed 's/^/    /'; fail=1
fi
[ "$fail" -eq 0 ] && echo "ASSERT CHECKS: both cases correct" || echo "ASSERT CHECKS: FAILED"
exit "$fail"
