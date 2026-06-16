#!/usr/bin/env bash
# Resolve diff base, export old @version values (xpath) for schemas resolved from pom.xml via XSL.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

WORKSPACE="${1:?workspace required}"
POM="${WORKSPACE}/pom.xml"
SCHEMA_DIR="${WORKSPACE}/schema"
TMP="${2:-$(mktemp -d)}"
mkdir -p "${TMP}"

if [[ -n "${GITHUB_BASE_REF:-}" ]]; then
  git fetch --depth=1 origin "${GITHUB_BASE_REF}" 2>/dev/null || true
  BASE="$(git merge-base HEAD "origin/${GITHUB_BASE_REF}")"
elif [[ -n "${GITHUB_EVENT_BEFORE:-}" && "${GITHUB_EVENT_BEFORE}" != "0000000000000000000000000000000000000000" ]]; then
  BASE="${GITHUB_EVENT_BEFORE}"
else
  BASE="$(git rev-parse HEAD~1 2>/dev/null || git rev-parse HEAD)"
fi

schema_version_at() {
  git show "${BASE}:$1" 2>/dev/null \
    | xmllint --xpath 'string(/*[local-name()="schema"]/@version)' - 2>/dev/null \
    | xargs echo || true
}

env_key() {
  printf '%s' "$1" | tr '/.-' '_' | tr '[:upper:]' '[:lower:]'
}

{
  echo "GOVERNANCE_WORKSPACE=${WORKSPACE}"
  echo "GOVERNANCE_BASE_REF=${BASE}"
} >> "${GITHUB_ENV}"

CHANGED=()
while IFS= read -r path; do
  [[ -n "${path}" ]] || continue
  case "${path}" in
    *.md|*.xsl) continue ;;
    schema/governance-*|schema/normalize-*) continue ;;
  esac
  CHANGED+=("${path}")
done < <(git diff --name-only "${BASE}" -- \
  schema \
  exist-distribution/src/main/config \
  exist-jetty-config/src/main/resources/webapp/WEB-INF/controller-config.xml \
  exist-core/src/main/resources/org/exist/util/mime-types.xml \
  2>/dev/null || true)

CHANGED_XML="${TMP}/changed.xml"
{
  echo '<?xml version="1.0" encoding="UTF-8"?>'
  echo '<g:changed xmlns:g="http://exist-db.org/ns/schema-governance">'
  for path in "${CHANGED[@]:-}"; do
    printf '  <g:path>%s</g:path>\n' "${path}"
  done
  echo '</g:changed>'
} > "${CHANGED_XML}"

SCHEMAS=()
while IFS= read -r schema; do
  [[ -n "${schema}" ]] && SCHEMAS+=("${schema}")
done < <(xsltproc --path "${SCHEMA_DIR}" --stringparam pom-uri "${POM}" \
  "${SCHEMA_DIR}/governance-schemas.xsl" "${CHANGED_XML}")

for schema in "${SCHEMAS[@]:-}"; do
  ver="$(schema_version_at "${schema}")"
  echo "GOVERNANCE_OLD_VERSION_$(env_key "${schema}")=${ver}" >> "${GITHUB_ENV}"
done

IFS=','; echo "GOVERNANCE_SCHEMAS=${SCHEMAS[*]:-}" >> "${GITHUB_ENV}"
IFS=','; echo "GOVERNANCE_CHANGED=${CHANGED[*]:-}" >> "${GITHUB_ENV}"

echo "Governance base: ${BASE}"
echo "Changed: ${CHANGED[*]:-(none)}"
echo "Schemas: ${SCHEMAS[*]:-(none)}"
