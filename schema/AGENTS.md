# Native config schemas

eXist-db's own config-file XSDs (`conf.xsd`, `collection.xconf.xsd`, `descriptor.xsd`,
`controller-config.xsd`, `mime-types.xsd`, plus `users.xsd`/`server.xsd`/`security-manager.xsd`/
`expath-pkg.xsd` and its extensions) live in [`schema/`](./) at the repo root, and are shipped
in every distribution layout as `$EXIST_HOME/schema/` — a sibling of `etc/`, `bin/`, `lib/` (tarball,
zip, Docker image, and the IzPack installer all include it; see `exist-distribution`/`exist-docker`/
`exist-installer`). External tools (eXide, IDE plugins) can resolve a config file's grammar from
this fixed location instead of vendoring their own copy.

- Each XSD's `xs:schema/@version` is an independent semver line — see [`schema/README.md`](README.md)
  for the versioning policy (CI enforces a version bump on any semantic schema edit, via
  `mvn -N xml:transform@schema-governance`, see [`schema/governance.xsl`](governance.xsl)).
- `org.exist.util.SchemaVersion`'s version constants are generated at build time from the XSDs
  themselves (`generate-sources` phase, see `exist-core/pom.xml`'s `schema-version-codegen`
  execution and [`schema/generate-schema-version.xsl`](generate-schema-version.xsl)) — never
  hand-edit `SchemaVersion`'s constants; bump the XSD's `xs:schema/@version` instead and the
  constant follows automatically on the next build.
- The 5 canonical instances (the files `pom.xml`'s `validate-canonical-instances` execution
  validates on every `mvn validate`) are the only ones checked for drift; the ~39 test/sample
  fixture copies scattered across module test resources (e.g. `extensions/*/src/test/resources*/conf.xml`)
  are intentionally hand-trimmed per-module subsets, not literal copies — don't try to regenerate
  them from canonical.
