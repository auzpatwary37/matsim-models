#!/usr/bin/env bash
#
# Opt-in black-box reference-oracle harness.
#
# Runs an EXTERNAL MATSim-format OSM->network converter as an out-of-process black box on an OSM
# extract, producing a reference network.xml to compare against our own output. This is a
# DEVELOPMENT/TESTING aid only:
#
#   * It is NOT part of the Maven build and is never invoked by `mvn verify` or CI.
#   * It adds no dependency to pom.xml and no external jar is vendored into the repository.
#   * It calls the external tool by its command-line class name only; no external source or
#     bytecode is read. Clean-room rules still apply to everything it produces.
#
# Usage:
#   tools/oracle/run_reference_oracle.sh <osm-file> <output-network.xml> [config-template]
#
# Environment:
#   ORACLE_JAVA        Java launcher to use (default: java). The external tool may need a newer JDK
#                      than this repo builds with; set this if so.
#   ORACLE_CLASSPATH   Classpath containing the external tool and its deps. If unset, the script
#                      tries to build one with Maven from ORACLE_PROJECT (offline).
#   ORACLE_PROJECT     Path to the external tool's Maven project (default: $HOME/git/pt2matsim).
#   ORACLE_MAIN        Main class (default: org.matsim.pt2matsim.run.Osm2MultimodalNetwork).
#   ORACLE_CONFIG      Config template to copy/adapt (default: this directory's
#                      osm-converter-template.xml).
#
# Notes:
#   * The generated config rewrites osmFile/outputNetworkFile to the requested paths.
#   * Reference outputs are meant to live in an out-of-repo workspace; do not commit them.
#
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "usage: $0 <osm-file> <output-network.xml> [config-template]" >&2
  exit 2
fi

OSM_FILE="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
OUT_FILE="$2"
mkdir -p "$(dirname "$OUT_FILE")"
OUT_FILE="$(cd "$(dirname "$OUT_FILE")" && pwd)/$(basename "$OUT_FILE")"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_TEMPLATE="${3:-$SCRIPT_DIR/osm-converter-template.xml}"

ORACLE_JAVA="${ORACLE_JAVA:-java}"
ORACLE_PROJECT="${ORACLE_PROJECT:-$HOME/git/pt2matsim}"
ORACLE_MAIN="${ORACLE_MAIN:-org.matsim.pt2matsim.run.Osm2MultimodalNetwork}"

if [ ! -f "$CONFIG_TEMPLATE" ]; then
  echo "error: config template not found: $CONFIG_TEMPLATE" >&2
  exit 2
fi

# Resolve the external classpath without touching this repo's pom.
if [ -z "${ORACLE_CLASSPATH:-}" ]; then
  CP_FILE="$(mktemp)"
  echo "info: building external classpath from $ORACLE_PROJECT (offline)..." >&2
  ( cd "$ORACLE_PROJECT" && mvn -o -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE" ) >&2
  ORACLE_CLASSPATH="$(cat "$CP_FILE")"
  rm -f "$CP_FILE"
fi

# Materialize a per-run config with the requested paths.
RUN_CONFIG="$(mktemp --suffix=.xml)"
sed \
  -e "s#__OSM_FILE__#$OSM_FILE#g" \
  -e "s#__OUTPUT_NETWORK__#$OUT_FILE#g" \
  "$CONFIG_TEMPLATE" > "$RUN_CONFIG"

echo "info: running external oracle" >&2
echo "      main : $ORACLE_MAIN" >&2
echo "      osm  : $OSM_FILE" >&2
echo "      out  : $OUT_FILE" >&2

"$ORACLE_JAVA" ${ORACLE_HEAP:+-Xmx$ORACLE_HEAP} -cp "$ORACLE_CLASSPATH" "$ORACLE_MAIN" "$RUN_CONFIG"
rm -f "$RUN_CONFIG"

if [ ! -f "$OUT_FILE" ]; then
  echo "error: oracle did not produce $OUT_FILE" >&2
  exit 1
fi
echo "info: reference network written to $OUT_FILE" >&2
