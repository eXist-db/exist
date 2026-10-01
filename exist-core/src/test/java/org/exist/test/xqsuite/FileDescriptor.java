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

import org.exist.test.runner.AbstractTestRunner;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.FileSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A file of XQSuite or XML tests.
 */
final class FileDescriptor extends AbstractTestDescriptor {
    private final AbstractTestRunner runner;
    private final Map<String, List<XQTestDescriptor>> tests = new LinkedHashMap<>();

    FileDescriptor(final UniqueId id, final AbstractTestRunner runner) {
        super(id, runner.getSourcePath().getFileName().toString(), FileSource.from(runner.getSourcePath().toFile()));
        this.runner = runner;
    }

    AbstractTestRunner runner() {
        return runner;
    }

    /**
     * Adds a test. Several test functions in one file may share a name (for example overloads),
     * and each is a test in its own right, so a repeated name adds a further occurrence.
     */
    synchronized XQTestDescriptor addTest(final String name) {
        final List<XQTestDescriptor> occurrences = tests.computeIfAbsent(name, n -> new ArrayList<>());
        final String segment = occurrences.isEmpty() ? name : name + "#" + (occurrences.size() + 1);
        final XQTestDescriptor test = new XQTestDescriptor(getUniqueId().append("test", segment), name, getSource().orElseThrow());
        occurrences.add(test);
        addChild(test);
        return test;
    }

    /**
     * @return the tests with the given name, in the order they were added
     */
    synchronized List<XQTestDescriptor> findTests(final String name) {
        final List<XQTestDescriptor> occurrences = tests.get(name);
        return occurrences == null ? List.of() : new ArrayList<>(occurrences);
    }

    synchronized List<XQTestDescriptor> tests() {
        final List<XQTestDescriptor> all = new ArrayList<>();
        tests.values().forEach(all::addAll);
        return all;
    }

    @Override
    public Type getType() {
        return Type.CONTAINER;
    }
}
