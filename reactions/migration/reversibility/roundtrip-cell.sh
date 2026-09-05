#!/usr/bin/env bash
# roundtrip-cell.sh <preserve policy> <A> <B>
#
# M2.3: is a migration reversible? Derives the library with configA, migrates it to configB
# and straight back to configA, and compares the result with the V-SUM it started from.
#
#   baselines/configA  --(umljava-configB.jar)-->  intermediate  --(umljava-configA.jar)-->  returned
#                                                                                              vs
#                                                  the materialized baselines/configA it started from
#
# Both legs use --strategy explicit --dominant uml --mode ids --source-update none --ask never,
# i.e. the settings of the committed configuration matrix. Everything lands in
# target/matrix-reversibility/<policy>/configA-configB/.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; . "$HERE/common.sh"

policy=$1; a=$2; b=$3
cell="config$a-config$b"
out="$RESULTS/$policy/$cell"
work="$RESULTS/work/$policy-$a-$b"
vsum="$work/library-vsum"
rm -rf "$out" "$work"; mkdir -p "$out" "$work"

materialize_baseline "$a" "$vsum" || exit 3

# The V-SUM as it stood before anything was migrated. Same folder path as the returned one,
# so absolute URIs baked into the models compare directly.
cp -r "$vsum" "$out/original"

start=$(date +%s)
migrate "$vsum" "$b" "$policy" "$out/forward.log"; rc_fwd=$?
mid=$(date +%s)
cp -r "$vsum" "$out/intermediate"
migrate "$vsum" "$a" "$policy" "$out/back.log"; rc_back=$?
end=$(date +%s)
cp -r "$vsum" "$out/returned"

javac_original=$(javac_errors "$out/original" "$out/javac-original.log")
javac_returned=$(javac_errors "$out/returned" "$out/javac-returned.log")

# --- did each leg do anything?
g() { grep -oE "$2" "$1" | head -1; }
fwd_nothing=$(grep -q 'Nothing to migrate' "$out/forward.log" && echo true || echo false)
back_nothing=$(grep -q 'Nothing to migrate' "$out/back.log" && echo true || echo false)
fwd_dirty=$(g "$out/forward.log" '[0-9]+ rule\(s\) changed' | grep -oE '^[0-9]+')
back_dirty=$(g "$out/back.log" '[0-9]+ rule\(s\) changed' | grep -oE '^[0-9]+')

# --- the comparison proper: Java text, strict and modulo member order
java_strict=identical; java_ordered=identical; differing=()
mapfile -t names < <(
  for p in "$out/original/src/catalog"/*.java "$out/returned/src/catalog"/*.java; do
    [ -e "$p" ] && basename "$p"
  done | sort -u
)
for n in "${names[@]:-}"; do
  [ -z "$n" ] && continue
  o="$out/original/src/catalog/$n"; r="$out/returned/src/catalog/$n"
  if [ ! -f "$o" ] || [ ! -f "$r" ]; then java_strict=missing; java_ordered=missing; differing+=("$n"); continue; fi
  if ! diff -q <(tr -d '\r' < "$o") <(tr -d '\r' < "$r") > /dev/null; then
    java_strict=content; differing+=("$n")
    if ! diff -q <(tr -d '\r' < "$o" | sed 's/[[:space:]]*$//' | sort) <(tr -d '\r' < "$r" | sed 's/[[:space:]]*$//' | sort) > /dev/null; then
      java_ordered=content
    fi
  fi
done
[ "$java_ordered" = identical ] && [ "$java_strict" = content ] && java_ordered=order

# --- the rule registry: does the returned V-SUM claim to be configA again?
hashes_equal=$(diff -q "$out/original/consistencymetadata/vitruv/rule-hashes.txt" \
                       "$out/returned/consistencymetadata/vitruv/rule-hashes.txt" > /dev/null 2>&1 \
               && echo true || echo false)

{
  echo "policy=$policy"; echo "a=$a"; echo "b=$b"
  echo "exitForward=$rc_fwd"; echo "exitBack=$rc_back"
  echo "secondsForward=$((mid-start))"; echo "secondsBack=$((end-mid))"
  echo "forwardNothingToMigrate=$fwd_nothing"; echo "backNothingToMigrate=$back_nothing"
  echo "forwardDirtyRules=${fwd_dirty:-}"; echo "backDirtyRules=${back_dirty:-}"
  echo "javaStrict=$java_strict"; echo "javaModuloOrder=$java_ordered"
  echo "javaDiffering=${differing[*]:-}"
  echo "ruleHashesEqual=$hashes_equal"
  echo "javacOriginal=$javac_original"; echo "javacReturned=$javac_returned"
  echo "forwardErrorLines=$(grep -c ' ERROR ' "$out/forward.log")"
  echo "backErrorLines=$(grep -c ' ERROR ' "$out/back.log")"
} > "$out/cell.properties"

# The models are compared by compare.py (UML needs xmi:id canonicalisation); keep only what it reads.
rm -rf "$out/intermediate/target" 2>/dev/null
printf '%s %s rc=%s/%s %ss java=%s hashes=%s javac=%s->%s\n' \
  "$(date +%H:%M:%S)" "$policy/$cell" "$rc_fwd" "$rc_back" "$((end-start))" \
  "$java_strict" "$hashes_equal" "$javac_original" "$javac_returned" >> "$RESULTS/sweep-$policy.log"
rm -rf "$work"
[ "$rc_fwd" = 0 ] && [ "$rc_back" = 0 ]
