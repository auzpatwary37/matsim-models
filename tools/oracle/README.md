# Opt-in reference oracle (development/testing only)

This directory holds the harness used to generate a **black-box reference** MATSim-format network
from an OSM extract, for comparison against `matsim-models` output.

**This is not part of the build.** Nothing here is invoked by `mvn verify` or CI, and no external
jar is added to `pom.xml` or vendored into the repository. It is a development/testing aid.

## Clean-room rules

- The external converter is run **out of process** and only its **output XML** is read.
- **No external source or bytecode is read, decompiled, or derived from.**
- Reference outputs are written to an **out-of-repo workspace** and must **not** be committed
  (they are large, and are derived artifacts of an external GPL tool over ODbL data).
- The tool name appears only in this opt-in harness and internal developer docs — never in
  public-facing docs, production code, or the build.

## Usage

```bash
tools/oracle/run_reference_oracle.sh <osm-file> <output-network.xml> [config-template]
```

Environment overrides:

| Variable | Default | Purpose |
|---|---|---|
| `ORACLE_JAVA` | `java` | Java launcher for the external tool (it may need a newer JDK than this repo). |
| `ORACLE_HEAP` | unset | If set, passed as `-Xmx<value>`. |
| `ORACLE_PROJECT` | `$HOME/git/pt2matsim` | The external tool's Maven project, used to resolve its classpath offline. |
| `ORACLE_CLASSPATH` | unset | Supply a prebuilt classpath to skip the Maven step. |
| `ORACLE_MAIN` | `org.matsim.pt2matsim.run.Osm2MultimodalNetwork` | Entry point class of the external tool. |
| `ORACLE_CONFIG` | `./osm-converter-template.xml` | Config template; `__OSM_FILE__` / `__OUTPUT_NETWORK__` are substituted per run. |

Example:

```bash
ORACLE_JAVA=/path/to/jdk-25/bin/java \
ORACLE_HEAP=16g \
tools/oracle/run_reference_oracle.sh /tmp/toronto.osm /tmp/ref/toronto-network.xml
```

## Comparing

Use our own readers to parse the produced `network.xml` and diff node/link counts, per-road-class
counts, and direction ratios against our output. Results are recorded in
[`docs/external-reference-comparison.md`](../../docs/external-reference-comparison.md).
