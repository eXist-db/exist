/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.test.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Appends one line per XQuery test failure/error to a log file for CI and grep.
 * Writes to {@code target/surefire-reports/xquery-failures.log} when running under Maven;
 * failures to write are ignored so tests are never affected.
 */
public final class XQueryFailureLog {

    private static final String LOG_NAME = "xquery-failures.log";

    private XQueryFailureLog() {
    }

    /**
     * Append a single line to the XQuery failures log (one line per failure for CI/grep).
     * If the message contains newlines, only the first line is written.
     * Safe to call from any thread; IO errors are swallowed.
     *
     * @param message failure message (e.g. "XQuery failure: file.xq:34 testName"; may contain newlines)
     */
    public static void log(final String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        final String firstLine = message.contains("\n") ? message.substring(0, message.indexOf('\n')) : message;
        try {
            final Path dir = Paths.get(System.getProperty("user.dir", "."), "target", "surefire-reports");
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
            final Path file = dir.resolve(LOG_NAME);
            Files.writeString(file, firstLine + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (final IOException ignored) {
            // do not affect test execution
        }
    }
}
