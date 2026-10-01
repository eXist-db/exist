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

/**
 * Receives the outcome of each test as the XQuery side of an XQSuite or XML test
 * run reports it. Decouples the XQuery callback functions from any particular
 * test framework: the JUnit 4 {@link XSuite} runner and the JUnit Platform engine
 * each provide an implementation.
 */
public interface TestEvents {

    void started(String testName);

    void finished(String testName);

    void ignored(String testName);

    /**
     * An assertion failure or an error raised while running the test.
     */
    void failed(String testName, Throwable reason);

    /**
     * The test's assumptions did not hold, so it is aborted rather than failed.
     */
    void assumptionFailed(String testName, String message);
}
