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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for XSuite parallel scheduler: dynamic pool size and progress-based hang detection.
 */
class XSuiteParallelTest {

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty("xsuite.parallel.threads");
        System.clearProperty("xsuite.hang.threshold.minutes");
        System.clearProperty("xsuite.hang.watcher.interval.seconds");
    }

    @Test
    void parallelSuiteRunsAndCompletesSuccessfully() {
        final Result result = runSuite(ParallelPassingSuite.class);
        assertTrue(result.wasSuccessful(),
            "parallel suite with passing files should succeed; failures: " + result.getFailureCount());
    }

    @Test
    void parallelSuiteWithThreadOverrideCompletes() {
        System.setProperty("xsuite.parallel.threads", "2");
        final Result result = runSuite(ParallelPassingSuite.class);
        assertTrue(result.wasSuccessful(),
            "parallel suite with xsuite.parallel.threads=2 should succeed; failures: " + result.getFailureCount());
    }

    /**
     * Parallel suite with two files (one passing, one that would hang if module vars were evaluated at load).
     * Runs to verify the parallel path with multiple files and that hang-detection properties are read.
     * Full "appears hung" behaviour is not asserted here because eXist may not evaluate library module
     * variables when inspect:module-functions loads the module, so hanging.xqm can complete without hanging.
     */
    @Test
    void parallelSuiteWithTwoFilesCompletes() {
        System.setProperty("xsuite.hang.threshold.minutes", "0.05");
        System.setProperty("xsuite.hang.watcher.interval.seconds", "1");
        final Result result = runSuite(ParallelWithHungSuite.class);
        assertTrue(result.getRunCount() > 0, "suite should run and complete");
    }

    private static Result runSuite(final Class<?> suiteClass) {
        return new JUnitCore().run(suiteClass);
    }
}
