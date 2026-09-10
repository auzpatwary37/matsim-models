# Third-Party Notices

This file documents the non-original materials bundled with `matsim-models`, the
source and applicable license of each, and how each is used. It is part of the
**licensing/attribution hygiene** of the project. It is not legal advice: where the
upstream license of a specific file could not be verified, that fact is called out
explicitly instead of assuming a license.

Scope of this file:

- The **original software written in this repository** is licensed under the Apache
  License 2.0 (see `LICENSE`). That license covers the software authored here, **not**
  the third-party specification materials, OpenStreetMap data, or binary Maven
  dependencies described below.
- Each section is separated by origin so a reader can apply the correct license to the
  correct material.

---

## 1. Original `matsim-models` software (Apache License 2.0)

The original software in this repository is Copyright 2026 Ashraf Uz Zaman Patwary and is
licensed under the Apache License 2.0 (full text in `LICENSE`).

- **What:** All Java sources under `src/main/java` and `src/test/java`
  (packages `com.citymodeler.matsim.models.*`), the repository-authored resources
  (the permissive root-element XSD stubs in `src/main/resources/schemas/`, the
  hand-authored XML/OSM test fixtures, and the documentation).

These original sources are implemented independently. They reference MATSim **wire-format
names, XML element/attribute names, and class-hint strings** for interoperability, but do
not `import` or copy MATSim implementation source (see the audit note, `docs/legal-audit.md`).

---

## 2. MATSim schema / DTD / specification materials (vendored, separately licensed)

The following files are **not** original to this repository. They are **adapted from the
publicly published MATSim wire-format schemas** hosted at `https://matsim.org/files/dtd/`
(and mirrored in `matsim-org/matsim`). They describe the MATSim file formats; they are
**data-format specification material, not program code**.

**Important:** An XML schema or DTD that merely describes a file format is **not** thereby
re-licensed. These files originate in the MATSim project (whose Java program code is
distributed under the GNU General Public License v2, and whose input/output data files are
licensed under CC-BY 4.0); the DTD/XSD *specification files* copied here are **neither
program code nor data files**, and the license that governs each individual one is **not
stated inside the file** and has **not been independently verified**. We therefore assert no
license — not GPL, CC-BY, Apache, or any other — for these files. Consequently:

- These files are **not** asserted to be Apache 2.0.
- They are **not** redistributed under the blanket "this project is Apache 2.0" statement.
- Upstream editor/attribution credits present in the published files are **preserved in the
  corrected file headers** (see the per-file notes below and the headers committed in-tree).
- Their use is limited to **offline interoperability validation** (see "Usage" per file).

| File | Upstream source (matsim.org) | Adaptations made in-repo | Reachable from |
|------|------------------------------|--------------------------|----------------|
| `src/main/resources/schemas/v2/matsimCommon.xsd` | `…/files/dtd/matsimCommon.xsd` | Added provenance/attribution header; rewrote relative/absolute includes for offline classpath resolution. Upstream editor credit (Dominik Grether, VSP, Berlin Institute of Technology) restored in header. | Production (opt-in) **and** tests |
| `src/main/resources/schemas/v2/vehicleDefinitionsEnumTypes.xsd` | `…/files/dtd/vehicleDefinitionsEnumTypes.xsd` | Added provenance/attribution header. Upstream editor credit (Dominik Grether, VSP, Berlin Institute of Technology) restored. | Production (opt-in) **and** tests |
| `src/main/resources/schemas/v2/vehicleDefinitions_v2.0.xsd` | `…/files/dtd/vehicleDefinitions_v2.0.xsd` | Added provenance/attribution header; converted absolute `xs:include` URLs to relative for offline resolution. Upstream editor credit (Kai Martins-Turner, VSP, Berlin Institute of Technology) restored. | Production (opt-in) **and** tests |
| `src/test/resources/matsim-spec/matsimCommon.xsd` | `…/files/dtd/matsimCommon.xsd` | Same adaptations as the main-resources copy. | Tests only |
| `src/test/resources/matsim-spec/vehicleDefinitionsEnumTypes.xsd` | `…/files/dtd/vehicleDefinitionsEnumTypes.xsd` | Same adaptations as the main-resources copy. | Tests only |
| `src/test/resources/matsim-spec/vehicleDefinitions_v2.0.xsd` | `…/files/dtd/vehicleDefinitions_v2.0.xsd` | Provenance/attribution header; retains absolute `xs:include` URLs (resolved to the local `matsim-spec/` files by the test's resource resolver). | Tests only |
| `src/test/resources/matsim-spec/transitSchedule_v2.dtd` | `…/files/dtd/transitSchedule_v2.dtd` | Added provenance/attribution header; removed the leading `<?xml …?>` declaration. Attributed to the MATSim contributors. | Tests only |

**Hand-authored interoperability fixtures** (original to this repository, **not** MATSim code):

| File | Nature |
|------|--------|
| `src/test/resources/matsim-spec/transitSchedule-current-v2.xml` | Hand-written from the transitSchedule v2 DTD content model; used as a test oracle. No MATSim/Pt2MATSim code. |
| `src/test/resources/matsim-spec/vehicleDefinitions-current-v2.xml` | Hand-written from the vehicleDefinitions v2.0 schema; used as a test oracle. No MATSim/Pt2MATSim code. |

**Usage / why they are (or are not) packaged:**

- The `src/main/resources/schemas/v2/*` files are **packaged in the distributed JAR**
  because the optional runtime validation path — `new VehiclesXmlReader(true)` — loads
  `vehicleDefinitions_v2.0.xsd` (and its `xs:include`d companions) at runtime. This is the
  only production reader whose opt-in validation uses a published MATSim schema; every other
  production reader uses a permissive repo-authored `xs:anyType` stub.
- The `src/test/resources/matsim-spec/*` files are **test-scope only** and serve as
  external interoperability oracles (`TransitScheduleSpecValidationTest`,
  `MatsimVehicleSpecValidationTest`).

> **Flagged for upstream verification:** The precise license of the individual MATSim DTD/XSD
> specification files (as opposed to MATSim's Java program code) is not stated in the files
> and was not independently confirmable. Bundling files that originate in a GPL-licensed
> project inside an Apache 2.0 JAR may carry obligations that depend on that (unverified)
> license. Mitigations applied here: (a) the schemas are **off by default** (validation is
> opt-in and test-oracle only for all other formats), (b) upstream attribution is restored in
> the file headers, and (c) they are explicitly carved out of the Apache 2.0 statement in
> this file and the README. If you need a strictly Apache-2.0-only distribution, consider
> fetching these schemas on demand rather than shipping them. This is noted as an
> outstanding licensing question, **not** a compliance assertion.

---

## 3. OpenStreetMap / ODbL

- **No OpenStreetMap data is bundled** in this repository. The OSM files under
  `src/test/resources/fixtures/*.osm` are **hand-authored synthetic test fixtures**
  (`generator="test"`); they are not extracts from the OpenStreetMap database and carry no
  ODbL obligations of their own.
- The library provides OSM parsing/conversion routines. When those routines are used on
  **real OpenStreetMap data**, the following apply independently of this repository's license:
  - Imported OSM data remains subject to the **Open Database License (ODbL)**.
  - **Attribution to OpenStreetMap contributors** must be preserved where required
    (e.g. `© OpenStreetMap contributors`).
  - **Generated/derived datasets** produced from OSM data (networks, facility databases, etc.)
    may carry **ODbL share-alike** obligations that are independent of this software's
    Apache 2.0 license.
- The Apache 2.0 license granted by this repository applies to the **software only**; it does
  **not** extend to OSM data, OSM-derived databases, MATSim schemas/DTDs, or any other
  third-party material.

---

## 4. Maven (binary) dependencies

Declared in `pom.xml`. These are third-party libraries used at runtime/build; per Apache 2.0
§4(d)/(e) their own notices are retained (typically in each artifact's own `META-INF`).
Distributing a compatible library alongside these is permitted by their respective licenses,
subject to their terms.

| Dependency | Version | Scope | License |
|------------|---------|-------|---------|
| `com.fasterxml.jackson.dataformat:jackson-dataformat-xml` | 2.17.1 | compile | Apache License 2.0 |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | 2.17.1 | compile | Apache License 2.0 |
| `org.locationtech.proj4j:proj4j` | 1.4.3 | compile | Eclipse Public License 1.0 |
| `org.locationtech.proj4j:proj4j-epsg` | 1.4.3 | compile | Eclipse Public License 1.0 |
| `org.junit.jupiter:junit-jupiter` | 5.10.3 | test | Eclipse Public License 2.0 |
| `org.openstreetmap.pbf:osmpbf` | 1.6.0 | compile | GNU Lesser General Public License, version 3.0 (LGPL-3.0) |

Notes:

- `jackson-dataformat-xml` and `jackson-datatype-jsr310` pull in a number of Jackson
  transitive modules (all Apache 2.0).
- `proj4j-epsg` additionally bundles **EPSG coordinate-system registration data**, whose own
  rights belong to IANA/EPSG and may carry separate terms; this is **noted** as data of a
  third party even though it arrives via a permissively-licensed jar.
- `osmpbf` (OSM-binary) is **LGPL-3.0** and is a runtime dependency used to parse OSM PBF
  data. LGPL-3.0 permits combining it with this Apache-2.0 library, but it remains a
  separately-licensed component (it is **not** re-licensed under Apache 2.0); users who
  re-link or replace it must be able to do so per LGPL terms.

---

## 5. Build tooling (not redistributed in the JAR)

- **GitHub Actions** used by the CI workflow: `actions/checkout@v4`,
  `actions/setup-java@v4`. Referenced by pinned version in
  `.github/workflows/ci.yml`; executed at build time and not shipped in the artifact.
- **Maven plugins** (compiler, surefire, enforcer, checkstyle, SpotBugs, JaCoCo, source,
  javadoc, deploy): build-time tooling, governed by their own licenses (predominantly Apache
  2.0), not redistributed by this project.

These are listed for completeness; they do not form part of the distributed software.

---

## Outstanding items

1. **Individual MATSim DTD/XSD license** — the individual DTD/XSD specification files carry
   no in-file license and that license has not been independently verified; no license is
   asserted for them. Keeping them bundled is a deliberate choice (the opt-in
   `VehiclesXmlReader(true)` validation path depends on them). If a strictly Apache-2.0-only
    distribution is ever required, fetch them on demand or drop that opt-in check instead.

*This document is a licensing/attribution hygiene improvement. It deliberately avoids
definitive legal conclusions (such as "fully compliant") and flags every item whose upstream
terms could not be verified.*
