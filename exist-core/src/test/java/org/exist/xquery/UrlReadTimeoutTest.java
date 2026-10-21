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
package org.exist.xquery;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.source.UrlConnections;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.SilentServer;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading from a URL outside of {@link org.exist.source.URLSource} must not make the query wait
 * forever on a silent remote host, see <a href="https://github.com/eXist-db/exist/issues/6902">#6902</a>.
 * <p>
 * The remote host is a local socket that accepts connections and never answers. Before the fix
 * every query below hung forever, the class level timeout turns that into a failure.
 */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class UrlReadTimeoutTest {

    private static final int READ_TIMEOUT = 500;
    private static final long MAX_DURATION = 10_000;

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @RegisterExtension
    final SilentServer silentServer = new SilentServer();

    private static final XmldbURI TEST_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("test-url-read-timeout");

    static Stream<Arguments> queries() {
        return Stream.of(
                Arguments.of("xmldb:store from a URL", """
                        xmldb:store("%1$s", "x.xml", xs:anyURI("%2$s/x.xml"))
                        """),
                Arguments.of("util:eval-with-context variable source", """
                        util:eval-with-context("$v",
                            <static-context><variable name="v" source="%2$s/v.xml"/></static-context>, false())
                        """),
                Arguments.of("validation:jaxv-report with a URL grammar", """
                        validation:jaxv-report(<a/>, xs:anyURI("%2$s/s.xsd"))
                        """),
                Arguments.of("validation:pre-parse-grammar with a URL", """
                        validation:pre-parse-grammar(xs:anyURI("%2$s/s.xsd"))
                        """),
                Arguments.of("transform:transform with a URL stylesheet", """
                        transform:transform(<a/>, xs:anyURI("%2$s/s.xsl"), ())
                        """),
                Arguments.of("XInclude of an external href", """
                        let $doc := xmldb:store("%1$s", "xi.xml",
                            <doc xmlns:xi="http://www.w3.org/2001/XInclude"><xi:include href="%2$s/inc.xml"/></doc>)
                        return util:expand(doc($doc))
                        """),
                Arguments.of("XInclude of an external href as text", """
                        let $doc := xmldb:store("%1$s", "xi-text.xml",
                            <doc xmlns:xi="http://www.w3.org/2001/XInclude"><xi:include href="%2$s/inc.txt" parse="text"/></doc>)
                        return util:expand(doc($doc))
                        """)
        );
    }

    @BeforeAll
    static void setup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final Collection testCollection = broker.getOrCreateCollection(transaction, TEST_COLLECTION);
            broker.saveCollection(transaction, testCollection);
            transaction.commit();
        }
    }

    @AfterAll
    static void teardown() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            try (final Collection testCollection = broker.openCollection(TEST_COLLECTION, Lock.LockMode.WRITE_LOCK)) {
                if (testCollection != null) {
                    broker.removeCollection(transaction, testCollection);
                }
            }
            transaction.commit();
        }
    }

    @BeforeEach
    void shortReadTimeout() {
        existEmbeddedServer.getBrokerPool().getConfiguration().setProperty(XQueryContext.PROPERTY_URL_READ_TIMEOUT, READ_TIMEOUT);
    }

    @AfterEach
    void resetReadTimeout() {
        existEmbeddedServer.getBrokerPool().getConfiguration().setProperty(XQueryContext.PROPERTY_URL_READ_TIMEOUT, UrlConnections.DEFAULT_READ_TIMEOUT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("queries")
    void readFromSilentHostIsBounded(final String description, final String query) throws EXistException, PermissionDeniedException {
        final String base = silentServer.url("");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final long start = System.currentTimeMillis();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            pool.getXQueryService().execute(broker, query.formatted(TEST_COLLECTION, base), null);
        } catch (final XPathException e) {
            // failing is fine, hanging is not
        }
        final long duration = System.currentTimeMillis() - start;

        assertTrue(silentServer.connectionCount() > 0, "a connection to the silent host expected");
        assertTrue(duration < MAX_DURATION, "took " + duration + " ms");
    }
}
