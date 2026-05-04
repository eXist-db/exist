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
package org.exist.validation.internal;

import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.exist.TestUtils.ADMIN_DB_PWD;
import static org.exist.TestUtils.ADMIN_DB_USER;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DatabaseResourcesTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void cleanupRunsOnSuccessPath() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Subject admin = pool.getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD);

        final DatabaseResources resources = new DatabaseResources(pool);
        final XQueryContext context = new XQueryContext(pool);

        final AtomicBoolean cleaned = new AtomicBoolean(false);
        context.registerCleanupTask((ctx, predicate) -> cleaned.set(true));

        final Map<String, String> params = new HashMap<>();
        params.put(DatabaseResources.COLLECTION, "/db");
        params.put(DatabaseResources.TARGETNAMESPACE, "http://example.org/no-such-namespace");

        final Sequence result = resources.executeQuery(context, DatabaseResources.FIND_XSD, params, admin);

        assertNotNull("query should succeed (return an empty sequence, not null)", result);
        assertTrue("registered cleanup task must run on the success path", cleaned.get());
    }
}
