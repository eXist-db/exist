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
package org.exist.xquery.functions.util;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.ExternalModule;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The number of compiled codes a module keeps for {@code util:eval}, set by {@code eval-cache-size}.
 */
public class EvalCacheSizeTest {

    private static final String LIBRARY_NAMESPACE = "http://exist-db.org/test/eval-cache-size/lib";

    private static final String LIBRARY = """
            module namespace lib = "http://exist-db.org/test/eval-cache-size/lib";
            declare function lib:eval($code as xs:string) {
                util:eval($code, true())
            };
            """;

    /** Evaluates '1', '2', '1' again, then '3'. */
    private static final String MAIN = """
            import module namespace lib = "http://exist-db.org/test/eval-cache-size/lib" at "xmldb:exist:///db/eval-cache-size/lib.xqm";
            string-join((lib:eval('1'), lib:eval('2'), lib:eval('1'), lib:eval('3')) ! string(), ' ')
            """;

    @Nested
    class Disabled {

        @RegisterExtension
        final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(evalCacheSize(0), true, true);

        @Test
        void keepsNone() throws Exception {
            final XQueryContext library = runMain(existEmbeddedServer);
            assertEquals(0, library.getEvalQueryCount());
        }
    }

    @Nested
    class KeepingTwo {

        @RegisterExtension
        final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(evalCacheSize(2), true, true);

        @Test
        void discardsTheLeastRecentlyUsed() throws Exception {
            final XQueryContext library = runMain(existEmbeddedServer);
            assertEquals(2, library.getEvalQueryCount());
            // '2' was used least recently: '1' was used again after it
            assertNull(library.borrowEvalQuery(new StringSource("2")));
            assertNotNull(library.borrowEvalQuery(new StringSource("1")));
            assertNotNull(library.borrowEvalQuery(new StringSource("3")));
        }
    }

    private static Properties evalCacheSize(final int size) {
        final Properties properties = new Properties();
        // an Integer, as the configuration holds it when read from conf.xml
        properties.put(XQueryPool.PROPERTY_EVAL_CACHE_SIZE, size);
        return properties;
    }

    /**
     * Stores the library, runs the main query, and returns the context of the library module it imports.
     */
    private static XQueryContext runMain(final ExistEmbeddedServer existEmbeddedServer)
            throws EXistException, PermissionDeniedException, XPathException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            xquery.execute(broker, "xmldb:store(xmldb:create-collection('/db', 'eval-cache-size'), 'lib.xqm', ``["
                    + LIBRARY + "]``, 'application/xquery')", null);
            final CompiledXQuery compiled = xquery.compile(new XQueryContext(pool), new StringSource(MAIN));
            assertEquals("1 2 1 3", xquery.execute(broker, compiled, null).getStringValue());
            return ((ExternalModule) compiled.getContext().getModules(LIBRARY_NAMESPACE)[0]).getContext();
        }
    }
}
