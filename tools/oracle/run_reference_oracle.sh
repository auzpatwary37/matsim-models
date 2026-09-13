#!/usr/bin/env bash
#
# Opt-in black-box reference-oracle harness.
#
# Runs an external MATSim-format OSM->network converter OUT OF PROCESS as a black box on an OSM
# extract, producing a reference network.xml to compare against our own output. Development/testing
# aid only:
#
#   * NOT part of the Maven build; never invoked by `mvn verify` or CI.
#   * Adds no dependency to pom.xml; no external jar, source, or config is vendored into the repo.
#   * The external tool is asked to GENERATE its own default config at run time; this script then
#     edits only the few values our comparison needs. No external config sample is copied in.
#
# External tool name (used only here and in internal developer docs, by design — transparency):
#   pt2MATSim (GPL), run via its published command-line entry points:
#     - CreateDefaultOsmConfig      (generates default config)
#     - Osm2MultimodalNetwork       (runs the conversion)
#   Both are called by class name at the command line only; no source or bytecode is read.
#
# Usage:
#   tools/oracle/run_reference_oracle.sh <osm-file> <output-network.xml>
#
# Environment:
#   ORACLE_JAVA        Java launcher (default: java). May need a newer JDK than this repo.
#   ORACLE_HEAP        If set, passed as -Xmx<value>.
#   ORACLE_CLASSPATH   Classpath for the external tool. If unset, built with Maven from ORACLE_PROJECT.
#   ORACLE_PROJECT     External tool Maven project (default: $HOME/git/pt2matsim).
#   ORACLE_MAIN        Conversion entry point (default: org.matsim.pt2matsim.run.Osm2MultimodalNetwork).
#   ORACLE_DEFAULT_CONFIG_MAIN  Config-generator entry point
#                      (default: org.matsim.pt2matsim.run.CreateDefaultOsmConfig).
#
# Reference outputs belong in an out-of-repo workspace; do not commit them.
#
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "usage: $0 <osm-file> <output-network.xml>" >&2
  exit 2
fi

OSM_FILE="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
OUT_FILE="$2"
mkdir -p "$(dirname "$OUT_FILE")"
OUT_FILE="$(cd "$(dirname "$OUT_FILE")" && pwd)/$(basename "$OUT_FILE")"

ORACLE_JAVA="${ORACLE_JAVA:-java}"
ORACLE_PROJECT="${ORACLE_PROJECT:-$HOME/git/pt2matsim}"
ORACLE_MAIN="${ORACLE_MAIN:-org.matsim.pt2matsim.run.Osm2MultimodalNetwork}"
ORACLE_DEFAULT_CONFIG_MAIN="${ORACLE_DEFAULT_CONFIG_MAIN:-org.matsim.pt2matsim.run.CreateDefaultOsmConfig}"

if [ -z "${ORACLE_CLASSPATH:-}" ]; then
  CP_FILE="$(mktemp)"
  echo "info: building external classpath from $ORACLE_PROJECT (offline)..." >&2
  ( cd "$ORACLE_PROJECT" && mvn -o -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE" ) >&2
  ORACLE_CLASSPATH="$(cat "$CP_FILE")"
  rm -f "$CP_FILE"
fi

JAVA_OPTS=()
if [ -n "${ORACLE_HEAP:-}" ]; then
  JAVA_OPTS+=("-Xmx${ORACLE_HEAP}")
fi

# 1) Ask the external tool to generate its own default config. We vendor none.
RUN_CONFIG="$(mktemp --suffix=.xml)"
"$ORACLE_JAVA" "${JAVA_OPTS[@]}" -cp "$ORACLE_CLASSPATH" "$ORACLE_DEFAULT_CONFIG_MAIN" "$RUN_CONFIG" >/dev/null 2>&1

# 2) Apply only the values our comparison needs. Everything else stays at the tool's own defaults.
#    These three are our explicit comparison choices; see tools/oracle/README.md for provenance.
sed -i \
  -e "s#<param name=\"osmFile\" value=\"null\" />#<param name=\"osmFile\" value=\"$OSM_FILE\" />#" \
  -e "s#<param name=\"outputNetworkFile\" value=\"null\" />#<param name=\"outputNetworkFile\" value=\"$OUT_FILE\" />#" \
  -e "s#<param name=\"outputCoordinateSystem\" value=\"null\" />#<param name=\"outputCoordinateSystem\" value=\"EPSG:3857\" />#" \
  "$RUN_CONFIG"

echo "info: running external oracle" >&2
echo "      main : $ORACLE_MAIN" >&2
echo "      osm  : $OSM_FILE" >&2
echo "      out  : $OUT_FILE" >&2

# 3) Run the conversion.
"$ORACLE_JAVA" "${JAVA_OPTS[@]}" -cp "$ORACLE_CLASSPATH" "$ORACLE_MAIN" "$RUN_CONFIG"
rm -f "$RUN_CONFIG"

if [ ! -f "$OUT_FILE" ]; then
  echo "error: oracle did not produce $OUT_FILE" >&2
  exit 1
fi
echo "info: reference network written to $OUT_FILE" >&2
