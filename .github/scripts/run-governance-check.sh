#!/usr/bin/env bash
set -euo pipefail

WORKSPACE="${1:?workspace required}"
OUT="${2:?context xml path required}"
POM="${WORKSPACE}/pom.xml"
SCHEMA_DIR="${WORKSPACE}/schema"

env_key() {
  printf '%s' "$1" | tr '/.-' '_' | tr '[:upper:]' '[:lower:]'
}

mkdir -p "$(dirname "${OUT}")"

{
  echo '<?xml version="1.0" encoding="UTF-8"?>'
  echo "<g:ctx xmlns:g=\"http://exist-db.org/ns/schema-governance\""
  echo "  workspace=\"${GOVERNANCE_WORKSPACE}\""
  echo "  pom-uri=\"${POM}\">"

  if [[ -n "${GOVERNANCE_SCHEMAS:-}" ]]; then
    IFS=',' read -ra SCHEMA_LIST <<< "${GOVERNANCE_SCHEMAS}"
    for schema in "${SCHEMA_LIST[@]}"; do
      [[ -n "${schema}" ]] || continue
      key="GOVERNANCE_OLD_VERSION_$(env_key "${schema}")"
      ver="${!key-}"
      printf '  <g:old-version path="%s">%s</g:old-version>\n' "${schema}" "${ver}"
    done
  fi

  if [[ -n "${GOVERNANCE_CHANGED:-}" ]]; then
    IFS=',' read -ra PATHS <<< "${GOVERNANCE_CHANGED}"
    for path in "${PATHS[@]}"; do
      [[ -n "${path}" ]] || continue
      printf '  <g:changed path="%s"/>\n' "${path}"
    done
  fi

  echo '</g:ctx>'
} > "${OUT}"

REPORT="${OUT%/*}/governance-report.xml"
xsltproc --path "${SCHEMA_DIR}" --stringparam pom-uri "${POM}" \
  "${SCHEMA_DIR}/governance-check.xsl" "${OUT}" > "${REPORT}"

cat "${REPORT}"
xsltproc "${SCHEMA_DIR}/governance-annotations.xsl" "${REPORT}"
test "$(xmllint --xpath 'string(/*[local-name()="report"]/@status)' "${REPORT}")" = "passed"
