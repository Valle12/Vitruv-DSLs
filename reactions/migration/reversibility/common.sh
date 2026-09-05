# Shared paths for the reversibility sweep (Git Bash). Sourced by roundtrip-cell.sh and sweep.sh.
HERE_COMMON="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIG="$(cd "$HERE_COMMON/.." && pwd)"
REPO="$(cd "$MIG/../.." && pwd)"
SNAPSHOTS="$MIG/target/matrix-baselines"
JARS="$MIG/src/test/resources/propagations"
export RESULTS="$MIG/target/matrix-reversibility"
JMOD_URI="file:/$(cygpath -m "$JAVA_HOME")/jmods/java.base.jmod"
MAIN=tools.vitruv.dsls.reactions.migration.Main

# Materializes $SNAPSHOTS/config<n> into <dest>, replacing the portable sentinels the
# way ConfigBaselines.materialize does. Fails if a sentinel survives.
materialize_baseline() {
  local n=$1 dest=$2
  cp -r "$SNAPSHOTS/config$n" "$dest"
  ROOT_URI="file:/$(cygpath -m "$dest")"
  export ROOT_URI JMOD_URI
  grep -rlF -e 'file:/__VSUM_ROOT__' -e 'file:/__JAVA_BASE_JMOD__' "$dest" | while IFS= read -r f; do
    perl -pi -e 's{\Qfile:/__VSUM_ROOT__\E}{$ENV{ROOT_URI}}g; s{\Qfile:/__JAVA_BASE_JMOD__\E}{$ENV{JMOD_URI}}g' "$f"
  done
  if grep -rqF -e '__VSUM_ROOT__' -e '__JAVA_BASE_JMOD__' "$dest"; then
    echo "sentinel left behind in $dest" >&2
    return 3
  fi
}

# migrate <vsum folder> <target config> <preserve policy> <log file>
migrate() {
  local vsum=$1 to=$2 preserve=$3 log=$4
  local args=(--model "$(cygpath -m "$vsum")"
              --propagations "$(cygpath -m "$JARS/umljava-config$to.jar")"
              --strategy explicit --dominant uml --mode ids --source-update none
              --preserve "$preserve" --ask never)
  ( cd "$REPO" && ./mvnw -q -B -o -pl reactions/migration org.codehaus.mojo:exec-maven-plugin:3.6.3:java \
      -Dexec.mainClass="$MAIN" -Dexec.classpathScope=test -Dexec.blockSystemExit=true \
      "-Dexec.args=${args[*]}" ) > "$log" 2>&1
}

# javac_errors <vsum folder> <log file> -> prints the error count
javac_errors() {
  local vsum=$1 log=$2 tmp
  tmp=$(mktemp -d)
  mapfile -t sources < <(find "$vsum/src" -name '*.java' \
      -not -path '*/src/java/*' -not -path '*/src/javax/*' -not -path '*/src/sun/*' -not -path '*/src/jdk/*')
  "$JAVA_HOME/bin/javac" -J-Duser.language=en -J-Duser.country=US -proc:none -d "$tmp" "${sources[@]}" > "$log" 2>&1
  rm -rf "$tmp"
  grep -cE '\.java:[0-9]+: error:' "$log"
}
