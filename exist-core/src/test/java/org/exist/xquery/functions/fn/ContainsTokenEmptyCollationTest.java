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
package org.exist.xquery.functions.fn;

import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * Verifies that fn:contains-token accepts an empty sequence as the optional $collation
 * argument, per the QT4 XQTS fn-contains-token-80 / 81 / 82 cases that previously
 * failed with XPTY0004.
 */
public class ContainsTokenEmptyCollationTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void emptyCollationEmptySequenceLiteral() throws Exception {
        // contains-token-80: third arg is ()
        assertEquals("true",
                executeStringValue("fn:contains-token('a b c', 'b', ())"));
    }

    @Test
    public void emptyCollationEmptyStringSequence() throws Exception {
        // contains-token-82: third arg comes from a let returning empty sequence
        assertEquals("true",
                executeStringValue("let $c := () return fn:contains-token('a b c', 'b', $c)"));
    }

    @Test
    public void presentCollationStillWorks() throws Exception {
        assertEquals("true",
                executeStringValue("fn:contains-token('a b c', 'B', " +
                        "'http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive')"));
    }

    private String executeStringValue(final String query) throws Exception {
        final Source source = new StringSource(query);
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = brokerPool.getXQueryService();

        try (final DBBroker broker = brokerPool.get(
                Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(brokerPool);
            final CompiledXQuery compiled = xquery.compile(context, source);
            final Sequence result = xquery.execute(broker, compiled, null);
            return result.getStringValue();
        }
    }
}
