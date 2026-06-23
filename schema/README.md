# eXist-db native XML schemas

XSD schemas for eXist-db configuration and descriptor files.

## Versioning policy

Each native schema declares an independent semver on `<xs:schema version="…">`. Schema version is **not** tied to the eXist-db product release.

| Change type | Version bump |
|-------------|--------------|
| Breaking change for existing instance documents | **MAJOR** |
| Backward-compatible addition (new optional element/attribute) | **MINOR** |
| Documentation or non-semantics XSD-only change | **PATCH** (optional) |

CI fails if a schema or canonical template changes without bumping the paired `xs:schema/@version`.

Canonical templates may declare an optional **`schemaVersion`** attribute on the root element. When present, its value should match the paired `xs:schema/@version` (native schema semver — not the eXist product release, and not expath package `@version`). Legacy documents without the attribute remain valid; runtime code logs a debug message when it is missing and warns when it differs from the version this build expects.

| Schema `@version` | `schemaVersion` on template |
|-------------------|----------------------------|
| `conf.xsd` | `<exist schemaVersion="…">` |
| `collection.xconf.xsd` | `<collection schemaVersion="…">` |
| `descriptor.xsd` | `<xquery-app schemaVersion="…">` |
| `mime-types.xsd` | `<mime-types schemaVersion="…">` |
| `controller-config.xsd` | `<configuration schemaVersion="…">` |

All five schemas `xs:include` the `schemaVersionType` simple type from [`schema-version-type.xsd`](schema-version-type.xsd) rather than each declaring their own copy (it has no `targetNamespace`, so it is pulled in as a chameleon component and inherits each includer's namespace).

[`SchemaVersion.java`](../exist-core/src/main/java/org/exist/util/SchemaVersion.java)'s version constants are generated at build time from `xs:schema/@version` on the paired XSDs (see [`generate-schema-version.xsl`](generate-schema-version.xsl), wired as `exist-core/pom.xml`'s `schema-version-codegen` execution) — never hand-edit them. [`SchemaVersionSyncTest`](../exist-core/src/test/java/org/exist/util/SchemaVersionSyncTest.java) is a tautology now that the constants can't drift by construction, kept as a guard against the codegen wiring itself silently breaking. [`ci-schema-checks.yml`](../.github/workflows/ci-schema-checks.yml) still triggers on edits to `SchemaVersion.java` itself.

## Validation

**Templates vs schemas** — root [`pom.xml`](../pom.xml) binds `xml-maven-plugin:validate` at the `validate` phase. This also runs on every full build via [`ci-test.yml`](../.github/workflows/ci-test.yml) (`mvn test` runs `validate` first).

[`ci-schema-checks.yml`](../.github/workflows/ci-schema-checks.yml) re-runs `mvn validate` on PRs that touch schemas or canonical templates (fast, path-filtered).

**Schemas vs the W3C meta-schema** — the same `validate-canonical-instances` execution also validates every `schema/**/*.xsd` as an instance document against the W3C XSD 1.1 meta-schema, catching malformed schema authoring (e.g. a misplaced `xs:assert`, an unresolvable `xsi:type` in `xs:appinfo`) before it ships. Resolution is fully offline: `catalogHandling` is set to `strict` and a `catalogs` entry points at [`catalog.xml`](../exist-jetty-config/src/main/resources/webapp/WEB-INF/catalog.xml), so the meta-schema's own `xs:import` of the `xml:` namespace never reaches the network — a live fetch would fail the build rather than silently succeeding. Idea borrowed from [#5541](https://github.com/eXist-db/exist/issues/5541), where the same catalog trick lets a user validate the well-formedness of their own XSD against the meta-schema.

The meta-schema files are vendored, not generated:

| File | Source | Fetched |
|------|--------|---------|
| `entities/XMLSchema.xsd` | <https://www.w3.org/2009/XMLSchema/XMLSchema.xsd> (XSD 1.1 structures schema — supersedes the older 2001/2004 XSD 1.0 revision previously bundled here, which had no `xs:assert`/`vc:` support and was otherwise unused) | 2026-06-20 |
| `entities/XMLSchema.dtd`, `entities/datatypes.dtd` | <https://www.w3.org/2009/XMLSchema/XMLSchema.dtd>, `.../datatypes.dtd` — internal-subset companions `XMLSchema.xsd`'s `DOCTYPE`/parameter entities pull in | 2026-06-20 |
| `entities/xml.xsd` | <https://www.w3.org/2001/xml.xsd> — the `xml:` namespace schema `XMLSchema.xsd` itself imports | 2026-06-20 |

Published under the [W3C Document License](https://www.w3.org/Consortium/Legal/2015/doc-license) (permissive, redistribution allowed). Each file is byte-identical to its upstream source — kept that way deliberately so future re-syncs are a clean diff against W3C's copy, with no local patching. Duplicated under `exist-core/src/test/resources/org/exist/validation/entities/` to mirror that module's own test catalog, matching this repo's existing pattern for the other bundled DTDs/XSDs.

**Version bumps** — a single Saxon XSLT 2.0 transform, [`governance.xsl`](governance.xsl), does the whole check in one pass: it reads the schema/template pairs straight from `pom.xml`'s `validate-canonical-instances` validationSets, reads changed paths and BASE-revision copies of each XSD via [`unparsed-text()`](https://www.w3.org/TR/xpath-functions-30/#func-unparsed-text)/[`document()`](https://www.w3.org/TR/xslt-30/#document)/[`doc-available()`](https://www.w3.org/TR/xpath-functions-30/#func-doc-available), and fails the build directly with `xsl:message terminate="yes"` (which also prints the GitHub Actions `::error::` annotations) when a paired schema/template changed without its `xs:schema/@version` moving. [`.github/scripts/prepare-governance-context.sh`](../.github/scripts/prepare-governance-context.sh) is pure git plumbing — it resolves the diff base, dumps each schema's BASE-revision content to disk, and writes a small `context.xml` — everything else is XSLT, run via `mvn -N xml:transform@schema-governance`.

That execution is bound to `phase=none` in [`pom.xml`](../pom.xml), so it never runs on an ordinary `mvn install`/`mvn test`/`mvn validate` — only [`ci-schema-checks.yml`](../.github/workflows/ci-schema-checks.yml) invokes it directly, after running the shim script. Saxon-HE (XSLT 2.0/3.0) is already a `xml-maven-plugin` dependency via `exist-parent/pom.xml`'s `pluginManagement` — no extra CI dependency installation needed (no more `xmllint`/`xsltproc`).

## Canonical templates

| Schema | Template |
|--------|----------|
| [`conf.xsd`](conf.xsd) | [`exist-distribution/src/main/config/conf.xml`](../exist-distribution/src/main/config/conf.xml) |
| [`collection.xconf.xsd`](collection.xconf.xsd) | [`collection.xconf.init`](../exist-distribution/src/main/config/collection.xconf.init) |
| [`descriptor.xsd`](descriptor.xsd) | [`descriptor.xml`](../exist-distribution/src/main/config/descriptor.xml) |
| [`controller-config.xsd`](controller-config.xsd) | [`controller-config.xml`](../exist-jetty-config/src/main/resources/webapp/WEB-INF/controller-config.xml) |
| [`mime-types.xsd`](mime-types.xsd) | [`mime-types.xml`](../exist-core/src/main/resources/org/exist/util/mime-types.xml) |

Other schemas (`users.xsd`, `server.xsd`, `expath-pkg.xsd`, …) apply to runtime or package files, not shipped templates.

## Distribution

Schemas ship at `$EXIST_HOME/schema/` ([#6189](https://github.com/eXist-db/exist/issues/6189)).

---

## Test fixture codegen

Hand-rolling separate `conf.xml` / `controller-config.xml` copies for every test module means
they drift. Two shared base stylesheets in this directory generate the module-specific test
fixtures from the canonical templates at build time:

| Base stylesheet | Canonical source | Generated file |
|-----------------|-----------------|----------------|
| [`generate-conf-fixture.xsl`](generate-conf-fixture.xsl) | `exist-distribution/src/main/config/conf.xml` | `conf.xml` |
| [`generate-controller-config-fixture.xsl`](generate-controller-config-fixture.xsl) | `exist-jetty-config/…/standalone-webapp/WEB-INF/controller-config.xml` | `controller-config.xml` |

### How it works

Each test module that needs a custom `conf.xml` has a thin `src/test/resources-filtered/conf-fixture.xsl`
that imports the base stylesheet and redeclares whichever `xsl:param` defaults need changing.
XSLT import precedence means the per-fixture param value wins over the base default without
requiring any template overrides.  `xml-maven-plugin` runs the transformation in the
`generate-test-resources` phase; the output lands in `target/generated-test-resources/` and
is listed as a filtered `testResource` so Maven's `${...}` token substitution still applies.

### Param reference — `generate-conf-fixture.xsl`

| Param | Type | Default | Purpose |
|-------|------|---------|---------|
| `keep-modules` | `xs:string*` | `()` | `@uri` values of builtin XQuery modules to keep |
| `keep-indexes` | `xs:string*` | `()` | `@id` values of indexer modules to keep |
| `data-path` | `xs:string` | `${basedir}/target/test-data` | Rewrites `db-connection/@files` and `recovery/@journal-dir` |
| `catalog-uri` | `xs:string?` | `()` | Overrides `validation/catalog/@uri`; empty = keep canonical's |
| `content-file-pool-size` | `xs:string?` | `()` | Overrides content file pool size; empty = keep canonical's |
| `extra-triggers` | `element()*` | `()` | Appended to `db-connection/startup/triggers` |
| `extra-index-modules` | `element()*` | `()` | Appended to `indexer/modules` |
| `extra-modules` | `element()*` | `()` | Appended to `xquery/builtin-modules` |

The base stylesheet also strips `RestXqStartupTrigger` and `AutoDeploymentTrigger` from
canonical (they assume a full webapp deployment); restore them per-fixture via
`$extra-triggers` if a test needs them.

### Param reference — `generate-controller-config-fixture.xsl`

| Param | Type | Default | Purpose |
|-------|------|---------|---------|
| `keep-forwards` | `xs:string*` | all 4 | `forward/@pattern` values to keep |
| `rest-forward-pattern` | `xs:string?` | `()` | Override `@pattern` on the `/rest` forward; empty = keep as-is |
| `root-elements` | `element()*` | `()` | Replace the entire `<root>` group; empty = keep template's roots |

### `keep-*` vs `extra-*` — critical distinction

`keep-modules` and `keep-indexes` operate on **live elements only**.  If a `<module>` entry
is commented out in the canonical template, it is invisible to the XPath match and cannot be
"kept" this way.  Use the corresponding `extra-*` param instead:

```xml
<!-- WRONG: spatial-index is commented out in canonical — keep-indexes silently produces nothing -->
<xsl:param name="keep-indexes" as="xs:string*" select="('sort-index', 'spatial-index')"/>

<!-- CORRECT: inject the live element directly -->
<xsl:param name="extra-index-modules" as="element()*">
    <module xmlns="" id="spatial-index" file="spatial.dbx"
            class="org.exist.indexing.spatial.GMLHSQLIndex"/>
</xsl:param>
```

Modules that are commented out in canonical by default: `spatial-index`, `xqsuite`, `vector`.

The same rule applies to `$extra-modules` for **builtin modules with child `<parameter>`
elements** — if the canonical entry is self-closing and your fixture needs parameter children,
keep `$keep-modules` empty for that module (so the bare canonical entry is not also kept)
and supply the parameterised element via `$extra-modules`:

```xml
<!-- SQL module needs pool parameters not present in the canonical self-closing entry -->
<xsl:param name="extra-modules" as="element()*">
    <module xmlns="" uri="http://exist-db.org/xquery/sql" class="org.exist.xquery.modules.sql.SQLModule">
        <parameter name="pool.1.name" value="pool-1"/>
        …
    </module>
</xsl:param>
```

### Maven token escaping in element literals

The generated `conf.xml` goes through Maven's testResource filtering, so `${basedir}` in
string parameters expands correctly.  However, if you write a `${…}` token inside a
**literal-result-element attribute that is also an XSLT AVT** (`{…}`), you must double the
curly braces so XSLT does not try to evaluate the inner braces as an XPath expression:

```xml
<!-- WRONG: XSLT tries to evaluate ${project.build.testOutputDirectory} as XPath -->
<parameter name="dir" value="${project.build.testOutputDirectory}/functx"/>

<!-- CORRECT: doubled braces → XSLT emits ${...} as literal text → Maven expands it -->
<parameter name="dir" value="${{project.build.testOutputDirectory}}/functx"/>
```

Plain attribute content (not inside `{…}`) passes through unchanged and needs no escaping.

### Adding a fixture for a new module

1. Create `src/test/resources-filtered/conf-fixture.xsl` importing the base stylesheet.
   Count the directory levels from that file to the repo root to get the correct relative path:
   - Depth 2 (e.g. `exist-ant/src/test/resources-filtered/…`): `../../../../schema/generate-conf-fixture.xsl`
   - Depth 3 (e.g. `extensions/lucene/src/test/resources-filtered/…`): `../../../../../schema/…`
   - Depth 4 (e.g. `extensions/modules/sql/…`): `../../../../../../schema/…`

2. Override only the params that differ from the base defaults; leave everything else out.

3. In `pom.xml` (or automatically via the `conf-fixture-codegen` root profile described
   below), wire `xml-maven-plugin` to run the transformation.

4. Add `target/generated-test-resources` as a filtered `testResource` and exclude
   `**/*-fixture.xsl` from `src/test/resources-filtered` so the stylesheet itself is not
   copied to `target/test-classes`.

### Parent POM profile (`conf-fixture-codegen`)

The root `pom.xml` contains a `conf-fixture-codegen` profile activated automatically
whenever `src/test/resources-filtered/conf-fixture.xsl` is present in a module.  The profile
runs the standard single-conf-xml transformation (canonical `conf.xml` → fixture → output in
`target/generated-test-resources/conf.xml`).

Most modules can remove their individual `xml-maven-plugin` `conf-fixture-codegen` execution
from `pom.xml` entirely and rely on the profile; only modules with **multiple fixtures** (e.g.
`exist-core`'s four per-package `conf-fixture.xsl` files) or **non-standard output paths**
(e.g. `expathrepo`) keep their own explicit execution alongside the profile.

### IDE support (`schema/catalog.xml`)

[`schema/catalog.xml`](catalog.xml) provides OASIS catalog entries mapping stable URNs to
the two base stylesheets.  Registering this catalog in your IDE lets it resolve `xsl:import`
references in per-fixture stylesheets without needing the correct relative-path depth:

```xml
<xsl:import href="urn:exist-db:codegen:generate-conf-fixture"/>
```

The Maven build does **not** use these URN aliases — the `xsl:import href` in source fixtures
still uses depth-relative paths, which are correct and working.  The catalog is for IDE
tooling only.

- **oXygen**: Preferences → XML → XML Catalogs → Add → browse to `schema/catalog.xml`
- **IntelliJ**: Settings → Languages & Frameworks → Schemas and DTDs → User Catalogs → add the catalog
