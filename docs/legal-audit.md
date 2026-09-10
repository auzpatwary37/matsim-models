# Legal / IP Hygiene Audit Note

**Date:** 2026-09-09 · **Scope:** `auzpatwary37/matsim-models` (branch `chore/legal-ip-hygiene`)

This is a licensing/attribution **hygiene** pass. It intentionally does **not** make
definitive legal conclusions (such as "fully compliant"). Where an upstream license could not
be verified, it is flagged.

## Method

- Inventoried all files under `src/main/resources`, `src/test/resources`, plus root
  metadata (`pom.xml`, `README.md`, `checkstyle.xml`, `.github/workflows/ci.yml`).
- Compared each MATSim-derived XSD/DTD against the reference copies shipped in the local
  MATSim artifact `org.matsim:matsim:2027.0-2026w25` (jar `dtd/` directory) to determine
  provenance and what was changed.
- Traced every `*.xsd`/`*.dtd` reference in production Java to determine runtime reachability.
- Scanned all Java sources for copied MATSim implementation (package declarations, imports,
  large similar blocks, copied Javadoc, class-hint strings).
- Checked git history for the actual copyright owner(s).

## Every third-party bundled file, and its disposition

### A. MATSim schema / DTD specification material (adapted, **not** re-licensed as Apache-2.0)

| File | Source | Disposition |
|------|--------|-------------|
| `src/main/resources/schemas/v2/matsimCommon.xsd` | `matsim.org/files/dtd/matsimCommon.xsd` | Adapted from published MATSim schema. **Packaged** because the opt-in reader path needs it at runtime. Header corrected; upstream editor attribution restored (Dominik Grether). Separate-licensing documented. Off-by-default; test oracle also uses a test-scope copy. |
| `src/main/resources/schemas/v2/vehicleDefinitionsEnumTypes.xsd` | `matsim.org/files/dtd/vehicleDefinitionsEnumTypes.xsd` | Same as above (included by `vehicleDefinitions_v2.0.xsd`). Attribution restored (Dominik Grether). |
| `src/main/resources/schemas/v2/vehicleDefinitions_v2.0.xsd` | `matsim.org/files/dtd/vehicleDefinitions_v2.0.xsd` | **Primary production-referenced schema** (`new VehiclesXmlReader(true)` loads this). Absolute `xs:include` URLs rewritten to relative for offline classpath resolution. Attribution restored (Kai Martins-Turner). |
| `src/test/resources/matsim-spec/matsimCommon.xsd` | `matsim.org/files/dtd/matsimCommon.xsd` | Test-scope copy (identical to main copy). Interoperability oracle only. Header corrected. |
| `src/test/resources/matsim-spec/vehicleDefinitionsEnumTypes.xsd` | `matsim.org/files/dtd/vehicleDefinitionsEnumTypes.xsd` | Test-scope copy. Header corrected. |
| `src/test/resources/matsim-spec/vehicleDefinitions_v2.0.xsd` | `matsim.org/files/dtd/vehicleDefinitions_v2.0.xsd` | Test-scope copy (retains absolute `xs:include` URLs; resolved locally by the test). Header corrected. |
| `src/test/resources/matsim-spec/transitSchedule_v2.dtd` | `matsim.org/files/dtd/transitSchedule_v2.dtd` | Test-scope DTD (leading `<?xml?>` decl removed). Interoperability oracle only. Header corrected; attributed to MATSim contributors. |

**Upstream license facts (verified):** MATSim *program code* is GPL v2; MATSim *input/output
data files* are CC-BY 4.0. The individual DTD/XSD *specification files* carry **no in-file
license statement**, and that license is **not verifiable** from the files or the published
copy, so no license is asserted for them (the in-tree headers no longer cite GPL/CC-BY).
These files were previously described as "retrieved verbatim"; comparison shows they were
**adapted** (provenance header added, upstream editor credits and `<xs:annotation>` docs
removed, includes rewritten). The misleading "verbatim"/"test scope (but in main)" header
text was corrected in all copies, and the upstream editor attribution was **restored**.

**Why they remain in `src/main/resources`:** only `VehiclesXmlReader` (opt-in
`validateSchema=true`) reaches a published MATSim schema at runtime, via
`schemas/v2/vehicleDefinitions_v2.0.xsd` and its includes. Every other production reader uses
a repo-authored `xs:anyType` stub. Removing the `v2` files would break that opt-in feature, so
they are kept and instead carved out of the Apache-2.0 statement and documented.

### B. Hand-authored interoperability fixtures (original, **not** MATSim code)

| File | Disposition |
|------|-------------|
| `src/test/resources/matsim-spec/transitSchedule-current-v2.xml` | Written by hand from the transitSchedule v2 DTD content model. Apache-2.0 (original). |
| `src/test/resources/matsim-spec/vehicleDefinitions-current-v2.xml` | Written by hand from the vehicleDefinitions v2.0 schema. Apache-2.0 (original). |

### C. Repo-authored resources (original, Apache-2.0)

- `src/main/resources/schemas/{config,events,facilities,households,lanes,network,population,transitSchedule,vehicles}.xsd` — permissive 4–14-line root-element `xs:anyType` stubs. Two of them declare a MATSim **namespace URI** (`http://www.matsim.org/schema/...`) as an identifier for wire compatibility; a namespace URI is a name, not copyrighted material, and poses no licensing issue.
- `src/test/resources/fixtures/*.xml` and `src/test/resources/fixtures/*.osm` — hand-authored synthetic fixtures (`generator="test"`). **No real OpenStreetMap data is bundled**, so the test OSM fixtures carry no ODbL obligations.

### D. Maven binary dependencies (declared in `pom.xml`)

`jackson-dataformat-xml` 2.17.1 (Apache-2.0), `jackson-datatype-jsr310` 2.17.1 (Apache-2.0),
`proj4j` 1.4.3 (EPL-1.0), `proj4j-epsg` 1.4.3 (EPL-1.0, bundles IANA/EPSG registration data of
a third party), `junit-jupiter` 5.10.3 (EPL-2.0, test), `org.openstreetmap.pbf:osmpbf` 1.6.0
(LGPL-3.0, added by the OSM network import commit on `main`; runtime PBF parser, combined with
but not re-licensed under this Apache-2.0 library). Details and the EPSG/LGPL notes are in
`THIRD_PARTY_NOTICES.md`.

### E. Build tooling (not redistributed in the JAR)

GitHub Actions `actions/checkout@v4`, `actions/setup-java@v4`; standard Maven plugins. Listed
for completeness; not part of the distributed artifact.

## Code-copy scan (MATSim implementation source)

- **No `package org.matsim`** declarations anywhere; all production packages are
  `com.citymodeler.matsim.models.*`.
- **No `import org.matsim.*`** in any source file.
- The only `org.matsim.*` occurrences in production code are two **string constants** in
  `XmlSupport.java` — the wire-format class-hint strings written into the `disallowedNextLinks`
  attribute so MATSim can rehydrate the value. Referencing wire-format names / class-hint
  strings is interoperability metadata, not copied implementation.
- No large blocks substantially similar to MATSim source, no copied Javadoc/@author, and no
  MATSim-attributed code. The `maven-enforcer-plugin` rule `enforce-no-matsim-imports`
  guards against reintroducing `org.matsim` source files.

**Conclusion:** the Java implementation is independent; only format identifiers and class-hint
strings reference MATSim. No actual source copying detected.

## Copyright holder

- **Ownership:** the copyright owner of the original software is **Ashraf Uz Zaman Patwary**
  (sole developer; all commits are by `auzpatwary37`, first commit 2026-04-25). LICENSE and
  README state "Copyright 2026 Ashraf Uz Zaman Patwary".
- **Historical metadata:** the README copyright line previously named "Urban Systems
  Technologies"; it now names Ashraf Uz Zaman Patwary. "Urban Systems Technologies" remains
  in the POM `<developers>` block only as descriptive organization branding, and the POM
  developer `<name>` is "Ashraf Uz Zaman Patwary".

## What was changed in this pass

1. Added root `LICENSE` (Apache 2.0, verbatim).
2. Added `THIRD_PARTY_NOTICES.md` (separated by origin; flags unverifiable items).
3. Corrected the 7 MATSim-derived schema/DTD headers: honest provenance, **restored** upstream
   editor attribution, explicit "NOT Apache-2.0" carve-out.
4. README: independence disclaimer, scoped licensing, precise compatibility matrix (replaces
   the blanket "MATSim 2025.0" claim), new OpenStreetMap/ODbL section, copyright set to the
   confirmed owner (Ashraf Uz Zaman Patwary), rebranded display name "CityModeler MATSim I/O".
5. POM display metadata: `<name>`/`<description>` rebranded and scoped; `groupId`/`artifactId`/
   `version` unchanged for artifact compatibility.
6. Aligned `docs/compatibility.md` and `docs/integration-guide.md` with the precise
   compatibility wording.

## Verification

- Full test suite run after changes: **397 tests, 0 failures, 0 errors** (run on the current
  `main` base, which includes the OSM network import; the pre-OSM baseline was 336 passing).
- All 6 edited XSDs confirmed well-formed (`xmllint`); the DTD is exercised by
  `TransitScheduleSpecValidationTest` (SAX DTD validation).
- No production schema files were removed or relocated; the opt-in
  `VehiclesXmlReader(true)` runtime validation path is unaffected (comments only).

## Outstanding items

1. **Individual MATSim DTD/XSD license** — not stated in the files and not independently
   verified; no license is asserted for them. Bundling is a deliberate choice (the opt-in
   `VehiclesXmlReader(true)` path depends on them); flagged for future confirmation.
2. **Historical planning doc** — `docs/xml-compatibility-test-plan.md` still refers to a
  "MATSim 2025.0" fixture set. It is a dated internal plan (superseded); left unchanged to
  avoid churn, but noted here.
