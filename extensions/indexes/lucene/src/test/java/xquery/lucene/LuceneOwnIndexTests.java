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

import org.exist.test.xqsuite.XQSuite;

/**
 * ft-query-own-index.xqm in a database of its own, where only its collections are indexed: whether ft:query on a
 * node without an index of its own depended on what else the database indexed shows only there. The file also runs in
 * {@link LuceneTests}.
 */
@XQSuite({
    "src/test/xquery/lucene/ft-query-own-index.xqm"
})
public class LuceneOwnIndexTests {
}
