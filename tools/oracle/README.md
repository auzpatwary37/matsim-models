# Opt-in reference oracle (development/testing only)

Harness for generating a **black-box reference** MATSim-format network from an OSM extract, to
compare against `matsim-models` output.

**Not part of the build.** Nothing here is invoked by `mvn verify` or CI, and no external jar,
source, or configuration is vendored into the repository. It is a development/testing aid.

## External tool

The external tool is **pt2MATSim** (GPL). It is named here and in internal developer docs on
purpose — transparency matters; the goal is clean provenance, not obscuring which tool is used.
It appears nowhere in production code or public-facing docs, and no part of it is a dependency.

## Clean-room model

- The external tool is invoked **out of process** via its published command-line entry points; only
  its **output XML** is read. **No external source or bytecode is read, decompiled, or derived
  from.**
- The harness contains only independently authored **invocation** and a few **overrides**; the
  external tool **generates its own default configuration at run time** (via its published
  `CreateDefaultOsmConfig` entry point). No external config example or comment text is copied into
  this repository.
- Reference outputs are written to an **out-of-repo workspace** and must **not** be committed (large,
  and derived artifacts of an external GPL tool over ODbL data).

## Usage

```bash
tools/oracle/run_reference_oracle.sh <osm-file> <output-network.xml>
```

Environment overrides:

| Variable | Default | Purpose |
|---|---|---|
| `ORACLE_JAVA` | `java` | Java launcher for the external tool (it may need a newer JDK than this repo). |
| `ORACLE_HEAP` | unset | If set, passed as `-Xmx<value>`. |
| `ORACLE_PROJECT` | `$HOME/git/pt2matsim` | The external tool's Maven project, used to resolve its classpath offline. |
| `ORACLE_CLASSPATH` | unset | Supply a prebuilt classpath to skip the Maven step. |
| `ORACLE_MAIN` | `org.matsim.pt2matsim.run.Osm2MultimodalNetwork` | Conversion entry point. |
| `ORACLE_DEFAULT_CONFIG_MAIN` | `org.matsim.pt2matsim.run.CreateDefaultOsmConfig` | Config-generator entry point. |

## Provenance of the values we set

The external tool generates its own full default config at run time; the harness mutates only three
values, and leaves every other parameter at the tool's own generated default:

| Parameter | Value we set | Basis |
|---|---|---|
| `osmFile` | the input OSM path | Required input for the run (obvious). |
| `outputNetworkFile` | the requested output path | Required output for the run (obvious). |
| `outputCoordinateSystem` | `EPSG:3857` | **Our explicit comparison choice**, so both pipelines emit in the same projected CRS as our own build. |

No other parameter is set by us. In particular, `keepPaths`, `maxLinkLength`, the
`routableSubnetwork` list, and the `wayDefaultParams` road-class table are **the tool's own
generated defaults**, not values copied from any sample: they are produced at run time by the tool's
documented config generator. This is why the harness runs the generator first instead of shipping a
config file.

## Comparing

Parse the produced `network.xml` with our own readers and diff node/link counts, per-road-class
counts, and direction ratios against our output. Results are recorded in
[`docs/pt2matsim-blackbox-comparison.md`](../../docs/pt2matsim-blackbox-comparison.md).
