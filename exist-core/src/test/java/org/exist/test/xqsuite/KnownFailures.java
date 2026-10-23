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
package org.exist.test.xqsuite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The tests of a suite that are known to fail, read from the file named by {@link XQSuite#knownFailures()}.
 * <p>
 * The file lists one test per line by its report name, the file followed by the test ({@code ft-match.xql: field-retrieve});
 * {@code #} starts a comment. A listed test that fails is reported as aborted instead of failed, so that the suite
 * passes until something else fails. A listed test that runs and does not fail is a stale entry, reported when the
 * suite ends, so that the list shrinks as the failures are fixed. A name that occurs more than once in a file (a test
 * with several {@code %test:args}) is stale only if none of its occurrences failed. A listed test that did not run
 * (part of a suite selected in an IDE) is not reported.
 */
final class KnownFailures {
    static final KnownFailures NONE = new KnownFailures(null, Set.of());

    private final Path source;
    private final Set<String> names;
    private final Set<String> ran = ConcurrentHashMap.newKeySet();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();

    private KnownFailures(final Path source, final Set<String> names) {
        this.source = source;
        this.names = names;
    }

    /**
     * @param suiteClass a class annotated with {@link XQSuite}
     * @return the known failures of the suite, or {@link #NONE} if it names no file
     * @throws IOException if the file cannot be read
     */
    static KnownFailures of(final Class<?> suiteClass) throws IOException {
        final String file = suiteClass.getAnnotation(XQSuite.class).knownFailures();
        if (file.isEmpty()) {
            return NONE;
        }
        final Path path = Path.of(file);
        final Set<String> names = new LinkedHashSet<>();
        for (final String line : Files.readAllLines(path)) {
            final int comment = line.indexOf('#');
            final String name = (comment >= 0 ? line.substring(0, comment) : line).trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return new KnownFailures(path, names);
    }

    /**
     * Records that a test finished.
     *
     * @param testName the report name of the test
     * @param hasFailed whether it failed
     * @return true if it failed and is listed, so that its failure is expected
     */
    boolean finished(final String testName, final boolean hasFailed) {
        if (!names.contains(testName)) {
            return false;
        }
        ran.add(testName);
        if (hasFailed) {
            failed.add(testName);
        }
        return hasFailed;
    }

    Path source() {
        return source;
    }

    /**
     * @return the listed tests that ran and did not fail, in the order of the file
     */
    List<String> stale() {
        return names.stream().filter(name -> ran.contains(name) && !failed.contains(name)).toList();
    }
}
