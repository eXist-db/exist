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
package org.exist.xquery.modules.vector;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code vector:embed} depends on the context item only through its arguments. ft:query-vector treats a
 * query vector that depends on the context item as differing per candidate, and then searches, and embeds,
 * once per candidate node. Compiling is enough to check this: no embedding model is loaded.
 */
public class EmbedDependenciesTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    void literalTextDoesNotDependOnContextItem() throws EXistException, PermissionDeniedException, XPathException {
        assertFalse(dependsOnContextItem("vector:embed('famine relief', 'all-MiniLM-L6-v2')"),
                "an embedding of literal text must not depend on the context item");
    }

    @Test
    void textFromContextItemDependsOnContextItem() throws EXistException, PermissionDeniedException, XPathException {
        assertTrue(dependsOnContextItem("vector:embed(string(.), 'all-MiniLM-L6-v2')"),
                "an embedding of the context item's text must depend on the context item");
    }

    private static boolean dependsOnContextItem(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        // compiling needs a broker held by the current thread
        try (final DBBroker ignored = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final XQueryContext context = new XQueryContext(pool);
            final CompiledXQuery compiled = xquery.compile(context, query);
            return Dependency.dependsOn((Expression) compiled, Dependency.CONTEXT_ITEM);
        }
    }
}
