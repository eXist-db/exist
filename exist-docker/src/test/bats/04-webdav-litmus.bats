#!/usr/bin/env bats

# WebDAV compliance tests using the litmus test suite
# (https://github.com/notroj/litmus). Runs against the WebDAV endpoint
# of the exist-ci container started by ci-container.yml.
#
# litmus is installed from apt (Ubuntu packages a stable release as the
# 'litmus' package) rather than built from upstream master, which keeps
# the test decoupled from upstream-build instability and avoids the
# autoreconf + neon-submodule + autogen.sh dance.
#
# To run locally on macOS, install litmus via MacPorts (`sudo port
# install litmus`) or build from source; on Linux, `sudo apt-get install
# litmus`.

WEBDAV_URL=http://localhost:8080/exist/webdav/db/
ADMIN_USER=admin
ADMIN_PASSWORD=""

LITMUS_RESULTS=/tmp/litmus-results.txt
BASELINE=extensions/webdav/src/test/resources/litmus-baseline.txt
CHECK_SCRIPT=extensions/webdav/src/test/resources/litmus-check.sh

setup_file() {
    if ! command -v litmus >/dev/null 2>&1; then
        if [ "$(uname)" = "Linux" ] && command -v apt-get >/dev/null 2>&1; then
            sudo apt-get install -y -qq litmus >/dev/null
        else
            echo "litmus binary not in PATH and apt unavailable; install litmus locally to run this test." >&2
            return 1
        fi
    fi
}

@test "WebDAV PROPFIND responds with multistatus (207)" {
    status_code=$(curl -s -o /dev/null -w '%{http_code}' \
        -u "${ADMIN_USER}:${ADMIN_PASSWORD}" \
        -X PROPFIND "${WEBDAV_URL}")
    [ "$status_code" = "207" ]
}

@test "litmus basic suite passes" {
    run litmus "${WEBDAV_URL}" "${ADMIN_USER}" "${ADMIN_PASSWORD}"
    # litmus exits 0 even with failures; capture output for the
    # next test to evaluate via litmus-check.sh.
    echo "$output" > "${LITMUS_RESULTS}"
    [ "$status" -eq 0 ] || [ "$status" -eq 1 ]
    # Quick sanity: the basic suite must complete.
    echo "$output" | grep -q "summary for \`basic'"
}

@test "litmus results match expected-failure baseline" {
    [ -f "${LITMUS_RESULTS}" ] || skip "litmus did not run"
    [ -f "${BASELINE}" ] || skip "baseline file missing"
    [ -f "${CHECK_SCRIPT}" ] || skip "check script missing"
    run bash "${CHECK_SCRIPT}" "${LITMUS_RESULTS}" "${BASELINE}"
    if [ "$status" -ne 0 ]; then
        echo "$output" >&2
    fi
    [ "$status" -eq 0 ]
}
