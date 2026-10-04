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

import org.exist.test.xqsuite.SuiteRun.FileTime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SuiteRunTimeSummaryTest {

    @Test
    void summaryNamesTheFileCountTheTotalAndTheSlowestFiles() {
        final List<FileTime> times = List.of(new FileTime("a.xqm", 100), new FileTime("b.xqm", 1300), new FileTime("c.xqm", 1200), new FileTime("d.xqm", 5));
        assertEquals("XQSuite LuceneTests: 4 files, 2605 ms in all, slowest: b.xqm 1300 ms, c.xqm 1200 ms",
                SuiteRun.timeSummary("LuceneTests", times, 2));
    }

    @Test
    void singleFileIsNotPlural() {
        assertEquals("XQSuite SingleTest: 1 file, 7 ms in all, slowest: only.xqm 7 ms",
                SuiteRun.timeSummary("SingleTest", List.of(new FileTime("only.xqm", 7)), 3));
    }

    @Test
    void filesOfEqualTimeComeInNameOrder() {
        assertEquals("XQSuite S: 2 files, 20 ms in all, slowest: a.xqm 10 ms, b.xqm 10 ms",
                SuiteRun.timeSummary("S", List.of(new FileTime("b.xqm", 10), new FileTime("a.xqm", 10)), 3));
    }
}
