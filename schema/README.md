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

Keep [`SchemaVersion.java`](../exist-core/src/main/java/org/exist/util/SchemaVersion.java) constants in sync with `xs:schema/@version` on the paired XSDs.

## Validation

**Templates vs schemas** — root [`pom.xml`](../pom.xml) binds `xml-maven-plugin:validate` at the `validate` phase. This also runs on every full build via [`ci-test.yml`](../.github/workflows/ci-test.yml) (`mvn test` runs `validate` first).

[`ci-schema-checks.yml`](../.github/workflows/ci-schema-checks.yml) re-runs `mvn validate` on PRs that touch schemas or canonical templates (fast, path-filtered).

**Version bumps** — CI xpath on the base revision exports `GOVERNANCE_OLD_VERSION_*` env vars; schema paths come from [`governance-schemas.xsl`](governance-schemas.xsl) (pairs read from `pom.xml` validationSets). [`governance-check.xsl`](governance-check.xsl) compares old vs current `xs:schema/@version`.

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
