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

Keep [`SchemaVersion.java`](../exist-core/src/main/java/org/exist/util/SchemaVersion.java) constants in sync with `xs:schema/@version` on the paired XSDs — [`SchemaVersionSyncTest`](../exist-core/src/test/java/org/exist/util/SchemaVersionSyncTest.java) fails the build if they drift apart, and [`ci-schema-checks.yml`](../.github/workflows/ci-schema-checks.yml) also triggers on edits to `SchemaVersion.java` itself.

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
