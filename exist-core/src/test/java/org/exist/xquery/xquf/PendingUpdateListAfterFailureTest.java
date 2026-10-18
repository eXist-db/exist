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
package org.exist.xquery.xquf;

import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A context that is not reset after execution -- as with util:eval's cached path -- must not keep
 * the pending updates of an evaluation that failed, or its next execution would apply them.
 */
public class PendingUpdateListAfterFailureTest {

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    @Test
    public void aFailedEvaluationLeavesNoPendingUpdates() throws Exception {
        final BrokerPool pool = server.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            final XQuery xquery = pool.getXQueryService();
            final XQueryContext context = new XQueryContext(pool);
            final CompiledXQuery compiled = xquery.compile(context,
                    new StringSource("(insert node <b/> into <a/>, error())"));
            try {
                xquery.execute(broker, compiled, null, null, false);
                fail("the query calls fn:error()");
            } catch (final XPathException e) {
                // expected
            }
            assertTrue("pending updates must be discarded when the evaluation fails",
                    context.getPendingUpdateList().isEmpty());
        }
    }
}
