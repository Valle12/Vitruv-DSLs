#!/usr/bin/env bash
# sweep.sh <preserve policy> [<workers>] [<A>:<B> ...]
#
# Runs roundtrip-cell.sh over every ordered configuration pair (default: all 81, row-major).
# Cells that already have a cell.properties are skipped, so an interrupted sweep continues.
#
#   ./sweep.sh report 3            # the 81 pairs, three at a time, preservation analysing only
#   ./sweep.sh user 3 1:7 3:9      # just two pairs, preservation applying
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; . "$HERE/common.sh"

policy=${1:?usage: sweep.sh <off|report|user|all> [workers] [A:B ...]}
workers=${2:-1}
shift 2 2>/dev/null || shift $#
cells=("$@")
if [ ${#cells[@]} -eq 0 ]; then
  for a in 1 2 3 4 5 6 7 8 9; do for b in 1 2 3 4 5 6 7 8 9; do cells+=("$a:$b"); done; done
fi

mkdir -p "$RESULTS"
todo=()
for c in "${cells[@]}"; do
  a=${c%%:*}; b=${c##*:}
  [ -f "$RESULTS/$policy/config$a-config$b/cell.properties" ] && continue
  todo+=("$a $b")
done
echo "$(date +%H:%M:%S) sweep $policy: ${#todo[@]} pair(s) with $workers worker(s)" >> "$RESULTS/sweep-$policy.log"
[ ${#todo[@]} -eq 0 ] || printf '%s\n' "${todo[@]}" | xargs -P "$workers" -L 1 bash "$HERE/roundtrip-cell.sh" "$policy"
echo "$(date +%H:%M:%S) sweep $policy: done" >> "$RESULTS/sweep-$policy.log"
