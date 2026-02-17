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

import org.junit.runner.RunWith;

/**
 * XSuite that runs passing XQuery tests in parallel.
 * Used by XSuiteParallelTest to verify dynamic pool and parallel execution.
 */
@RunWith(XSuite.class)
@XSuite.XSuiteParallel
@XSuite.XSuiteFiles({
    "src/test/resources/org/exist/test/runner/single-test.xqm",
    "src/test/resources/org/exist/test/runner/no-tests.xqm"
})
public class ParallelPassingSuite {
}
