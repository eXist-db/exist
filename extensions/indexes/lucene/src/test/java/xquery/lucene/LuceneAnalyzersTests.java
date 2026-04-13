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
package xquery.lucene;

import org.exist.test.runner.XSuite;
import org.junit.runner.RunWith;

/**
 * Runs only the analyzers XQSuite tests in isolation.
 * Use for fast TDD feedback when working on analyzers or when the full suite
 * shows flaky query-parser failures due to test ordering:
 *
 * mvn test -Dtest=LuceneAnalyzersTests -pl extensions/indexes/lucene -DfailIfNoTests=false
 */
@RunWith(XSuite.class)
@XSuite.XSuiteFiles({
    "src/test/xquery/lucene/analyzers-diacritics.xql"
})
public class LuceneAnalyzersTests {
}
