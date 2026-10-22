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
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.Util;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stored query whose library module evaluates code with {@code util:eval($code, true())}, which keeps the compiled
 * code in the XQuery pool, and the code calls back into the library. Compiled queries are borrowed from and
 * returned to the pool, as REST and XML-RPC do.
 */
public class EvalCachedQueryTest {

    private static final String COLLECTION = "/db/eval-cached-query";
    private static final XmldbURI MAIN = XmldbURI.create(COLLECTION + "/main.xq");
    private static final XmldbURI MAIN_WITHOUT_IMPORT = XmldbURI.create(COLLECTION + "/main-without-import.xq");
    private static final int THREADS = 8;
    private static final int ITERATIONS = 100;

    private static final String LIBRARY = """
            xquery version "3.1";
            module namespace lib = "http://exist-db.org/test/eval-cached-query/lib";

            declare variable $lib:one := 1;

            declare function lib:merge($maps as map(*)*) as xs:integer {
                let $count := count($maps)
                let $sizes := for $map in $maps return map:size($map)
                return ($count + sum($sizes)) * $lib:one
            };

            declare function lib:eval($code as xs:string) {
                util:eval($code, true())
            };
            """;

    private static final String CODE = """
            import module namespace lib = 'http://exist-db.org/test/eval-cached-query/lib' at 'xmldb:exist:///db/eval-cached-query/lib.xqm';
            sum(for $i in 1 to 50 return lib:merge((map { 'a': $i }, map { 'b': 2, 'c': 3 })))
            """;

    /**
     * The same code, calling the library through the context of the library function that evaluates it, without
     * importing the library itself.
     */
    private static final String CODE_WITHOUT_IMPORT = """
            sum(for $i in 1 to 50 return lib:merge((map { 'a': $i }, map { 'b': 2, 'c': 3 })))
            """;

    /** 50 × (2 maps + 3 entries) */
    private static final int EXPECTED = 250;

    @RegisterExtension
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeEach
    public void setUp() throws EXistException, PermissionDeniedException, XPathException {
        storeLibrary();
        execute("xmldb:store('" + COLLECTION + "', 'main.xq', ``[" + """
                import module namespace lib = "http://exist-db.org/test/eval-cached-query/lib" at "xmldb:exist:///db/eval-cached-query/lib.xqm";
                lib:eval("%s")
                """.formatted(CODE) + "]``, 'application/xquery')");
        execute("xmldb:store('" + COLLECTION + "', 'main-without-import.xq', ``[" + """
                import module namespace lib = "http://exist-db.org/test/eval-cached-query/lib" at "xmldb:exist:///db/eval-cached-query/lib.xqm";
                lib:eval("%s")
                """.formatted(CODE_WITHOUT_IMPORT) + "]``, 'application/xquery')");
    }

    @Test
    void concurrentCallsOfAStoredQuery() throws Exception {
        runConcurrently(MAIN);
    }

    private void runConcurrently(final XmldbURI main) throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            final CountDownLatch start = new CountDownLatch(1);
            final List<Future<Void>> threads = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                threads.add(executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < ITERATIONS; j++) {
                        assertEquals(EXPECTED, runMain(main));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (final Future<Void> thread : threads) {
                thread.get(5, TimeUnit.MINUTES);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCallsOfAStoredQueryWithoutImport() throws Exception {
        runConcurrently(MAIN_WITHOUT_IMPORT);
    }

    @Test
    void afterTheLibraryIsStoredAgain() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(EXPECTED, runMain(MAIN));
        }
        storeLibrary();
        for (int i = 0; i < 3; i++) {
            assertEquals(EXPECTED, runMain(MAIN));
        }
    }

    @Test
    void afterTheLibraryIsStoredAgainWithoutImport() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(EXPECTED, runMain(MAIN_WITHOUT_IMPORT));
        }
        storeLibrary();
        for (int i = 0; i < 3; i++) {
            assertEquals(EXPECTED, runMain(MAIN_WITHOUT_IMPORT));
        }
    }

    private void storeLibrary() throws EXistException, PermissionDeniedException, XPathException {
        execute("xmldb:store(xmldb:create-collection('/db', 'eval-cached-query'), 'lib.xqm', ``[" + LIBRARY + "]``, 'application/xquery')");
    }

    /**
     * Runs the stored main query, borrowing its compiled form from the XQuery pool and returning it.
     */
    private int runMain(final XmldbURI main) throws EXistException, PermissionDeniedException, XPathException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final LockedDocument mainDocument = broker.getXMLResource(main, LockMode.READ_LOCK)) {
            final Source source = new DBSource(pool, (BinaryDocument) mainDocument.getDocument(), true);
            return Util.withCompiledQuery(broker, source, compiled -> {
                final Sequence result = pool.getXQueryService().execute(broker, compiled, null);
                return result.itemAt(0).toJavaObject(Integer.class);
            });
        }
    }

    private Sequence execute(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            return pool.getXQueryService().execute(broker, query, null);
        }
    }
}
