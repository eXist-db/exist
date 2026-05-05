#!/usr/bin/env bash
#
# eXist-db Open Source Native XML Database
# Copyright (C) 2001 The eXist-db Authors
#
# info@exist-db.org
# http://www.exist-db.org
#
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 2.1 of the License, or (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this library; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
#
# Runs the exist-indexes-jmh benchmarks via JMH's Main, using the runtime
# classpath emitted by `mvn package` at target/classpath.txt.
#
# Usage:
#   ./bin/run-bench.sh                                  # all benchmarks, default JMH params
#   ./bin/run-bench.sh NgramWhereClauseBenchmark        # one class
#   ./bin/run-bench.sh NgramWhereClauseBenchmark.shapeA_literal -wi 5 -i 10
#
# Run from the exist-indexes-jmh module directory (or pass MODULE_DIR=... env).
# JAVA_HOME must point at a JDK 21+.

set -euo pipefail

MODULE_DIR="${MODULE_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
TARGET="$MODULE_DIR/target"
CP_FILE="$TARGET/classpath.txt"
MODULE_JAR=$(ls "$TARGET"/exist-indexes-jmh-*.jar 2>/dev/null | grep -v -- '-sources' | head -1 || true)

if [[ ! -f "$CP_FILE" || -z "$MODULE_JAR" ]]; then
    echo "Build the module first:" >&2
    echo "  mvn package -pl exist-indexes-jmh -am -DskipTests \\" >&2
    echo "      -Ddependency-check.skip=true -Ddocker=false" >&2
    exit 1
fi

JAVA="${JAVA_HOME:-}/bin/java"
if [[ ! -x "$JAVA" ]]; then
    JAVA="java"
fi

CP="$MODULE_JAR:$(cat "$CP_FILE")"

exec "$JAVA" -cp "$CP" org.openjdk.jmh.Main "$@"
