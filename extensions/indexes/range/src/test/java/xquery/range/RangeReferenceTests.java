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
package xquery.range;

import org.exist.test.xqsuite.XQSuite;

/**
 * The reference checks of the range index: its answers against those of fn:matches without an index. They run on their
 * own, not beside {@link RangeTests}: their thousands of queries are a load under which the field tests of the
 * concurrent RangeTests files found nothing on CI. The tests that do not pass yet are listed in
 * {@code src/test/xquery/reference-known-failures.lst}.
 */
@XQSuite(value = {
    "src/test/xquery/range-reference"
}, knownFailures = "src/test/xquery/reference-known-failures.lst")
public class RangeReferenceTests {
}
