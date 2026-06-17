#!/usr/bin/env bash
# Pure git plumbing for schema governance — everything else (pairing,
# version comparison, the GitHub annotations, failing the build) happens in
# a single Saxon XSLT 2.0 transform (schema/governance.xsl) driven by
# `mvn xml:transform@schema-governance`. This script's only job is to put
# the git state that transform needs onto disk as plain files/XML, so the
# stylesheet never has to shell out itself.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

WORKSPACE="${1:?workspace required}"
OUT="${2:-${WORKSPACE}/target/governance}"
mkdir -p "${OUT}/base"

if [[ -n "${GITHUB_BASE_REF:-}" ]]; then
  git fetch --depth=1 origin "${GITHUB_BASE_REF}" 2>/dev/null || true
  BASE="$(git merge-base HEAD "origin/${GITHUB_BASE_REF}")"
elif [[ -n "${GITHUB_EVENT_BEFORE:-}" && "${GITHUB_EVENT_BEFORE}" != "0000000000000000000000000000000000000000" ]]; then
  BASE="${GITHUB_EVENT_BEFORE}"
else
  BASE="$(git rev-parse HEAD~1 2>/dev/null || git rev-parse HEAD)"
fi

# Every tracked schema/*.xsd's content as it existed at BASE, one file per
# schema named after its basename. A missing file at $OUT/base/<name> means
# "didn't exist at BASE" (new schema) — governance.xsl checks for that with
# doc-available() rather than this script trying to distinguish "new file"
# from "tool failure" itself.
git ls-tree -r --name-only HEAD -- schema | grep '\.xsd$' | while read -r f; do
  out="${OUT}/base/$(basename "${f}")"
  git show "${BASE}:${f}" > "${out}" 2>/dev/null || rm -f "${out}"
done

# One path per line; governance.xsl reads this with unparsed-text() + tokenize(),
# so no XML-escaping of path characters is needed anywhere in this pipeline.
git diff --name-only "${BASE}" -- \
  schema \
  exist-distribution/src/main/config \
  exist-jetty-config/src/main/resources/webapp/WEB-INF/controller-config.xml \
  exist-core/src/main/resources/org/exist/util/mime-types.xml \
  exist-core/src/main/java/org/exist/util/SchemaVersion.java \
  > "${OUT}/changed.txt" 2>/dev/null || true

cat > "${OUT}/context.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<g:ctx xmlns:g="http://exist-db.org/ns/schema-governance"
       workspace="${WORKSPACE}"
       base-dir="${OUT}/base"
       base-ref="${BASE}"
       changed-file="${OUT}/changed.txt"/>
EOF

echo "Governance context: ${OUT}/context.xml (base ${BASE})"
