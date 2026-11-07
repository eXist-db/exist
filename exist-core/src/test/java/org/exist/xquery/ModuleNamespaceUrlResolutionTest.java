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
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A module that cannot be resolved locally must not make the query wait on a remote host,
 * see <a href="https://github.com/eXist-db/exist/issues/6898">#6898</a>.
 * <p>
 * The remote host is a local socket that accepts connections and never answers. Before the fix
 * every query below hung forever, the class level timeout turns that into a failure.
 */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class ModuleNamespaceUrlResolutionTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final XmldbURI TEST_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("test-module-namespace-url");
    private static final String LOCAL_MODULE_NAMESPACE = "xmldb:exist://" + TEST_COLLECTION.append("local.xqm");
    private static final String LOCAL_MODULE = """
            xquery version "3.1";
            module namespace local-ns = "%s";

            declare function local-ns:hello() {
              "hello"
            };
            """.formatted(LOCAL_MODULE_NAMESPACE);

    @RegisterExtension
    final SilentServer silentServer = new SilentServer();

    @BeforeAll
    static void setup() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final Collection testCollection = broker.getOrCreateCollection(transaction, TEST_COLLECTION);
            broker.storeDocument(transaction, XmldbURI.create("local.xqm"),
                    new StringInputSource(LOCAL_MODULE.getBytes(UTF_8)), MimeType.XQUERY_TYPE, testCollection);
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

    @AfterEach
    void resetConfiguration() {
        setConfiguration(XQueryContext.RESOLVE_NAMESPACE_AS_URL_DEFAULT, UrlConnections.DEFAULT_READ_TIMEOUT);
    }

    @Test
    void inspectUnresolvableNamespaceFailsWithoutNetwork() {
        final XPathException e = assertThrows(XPathException.class, () -> execute("""
                inspect:inspect-module-uri(xs:anyURI("%s"))
                """.formatted(silentNamespace())));
        assertSame(ErrorCodes.XQST0059, e.getErrorCode());
        assertEquals(0, silentServer.connectionCount(), "no connection to the namespace URI expected");
    }

    @Test
    void importUnresolvableNamespaceFailsWithoutNetwork() {
        final XPathException e = assertThrows(XPathException.class, () -> execute("""
                import module namespace silent = "%s";
                1
                """.formatted(silentNamespace())));
        assertSame(ErrorCodes.XQST0059, e.getErrorCode());
        assertEquals(0, silentServer.connectionCount(), "no connection to the namespace URI expected");
    }

    @Test
    void loadUnresolvableNamespaceFailsWithoutNetwork() {
        final XPathException e = assertThrows(XPathException.class, () -> execute("""
                load-xquery-module("%s")
                """.formatted(silentNamespace())));
        assertSame(ErrorCodes.FOQM0002, e.getErrorCode());
        assertEquals(0, silentServer.connectionCount(), "no connection to the namespace URI expected");
    }

    @Test
    void localNamespaceIsStillResolvedAsLocation() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = execute("""
                string(inspect:inspect-module-uri(xs:anyURI("%s"))/@uri)
                """.formatted(LOCAL_MODULE_NAMESPACE));
        assertEquals(LOCAL_MODULE_NAMESPACE, result.getStringValue());
    }

    @Test
    void nonNetworkNamespaceDoesNotSuggestTheOptIn() {
        final XPathException e = assertThrows(XPathException.class, () -> execute("""
                inspect:inspect-module-uri(xs:anyURI("urn:example:missing"))
                """));
        assertSame(ErrorCodes.XQST0059, e.getErrorCode());
        assertFalse(e.getMessage().contains(XQueryContext.RESOLVE_NAMESPACE_AS_URL_ATTRIBUTE), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.org/ns",
            "HTTPS://example.org/ns",
            "ftp://example.org/m.xqm",
            "jar:http://example.org/m.jar!/m.xqm"
    })
    void networkLocation(final String uri) {
        assertTrue(XQueryContext.isNetworkLocation(uri));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "m.xqm",
            "/db/apps/my:app/m.xqm",
            "xmldb:exist:///db/m.xqm",
            "resource:org/exist/m.xqm",
            "file:/tmp/m.xqm",
            "jar:file:/opt/m.jar!/m.xqm",
            "java:org.exist.xquery.functions.util.UtilModule",
            "urn:example:m"
    })
    void notNetworkLocation(final String uri) {
        assertFalse(XQueryContext.isNetworkLocation(uri));
    }

    @Test
    void networkNamespaceResolutionOptInIsBoundedByReadTimeout() {
        setConfiguration(true, 500);
        final XPathException e = assertThrows(XPathException.class, () -> execute("""
                inspect:inspect-module-uri(xs:anyURI("%s"))
                """.formatted(silentNamespace())));
        assertSame(ErrorCodes.XQST0059, e.getErrorCode());
        assertTrue(silentServer.connectionCount() > 0, "a connection to the namespace URI expected");
    }

    @Test
    void locationHintIsBoundedByReadTimeout() {
        setConfiguration(false, 500);
        final XPathException e = assertThrows(XPathException.class, () -> execute("""
                import module namespace silent = "http://example.org/silent" at "%s";
                1
                """.formatted(silentNamespace())));
        assertSame(ErrorCodes.XQST0059, e.getErrorCode());
        assertTrue(silentServer.connectionCount() > 0, "a connection to the location hint expected");
    }

    private String silentNamespace() {
        return silentServer.url("/ns");
    }

    private static void setConfiguration(final boolean resolveNamespaceAsUrl, final int readTimeout) {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        pool.getConfiguration().setProperty(XQueryContext.PROPERTY_RESOLVE_NAMESPACE_AS_URL, resolveNamespaceAsUrl);
        pool.getConfiguration().setProperty(XQueryContext.PROPERTY_URL_READ_TIMEOUT, readTimeout);
    }

    private static Sequence execute(final String query) throws XPathException, PermissionDeniedException, EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            return pool.getXQueryService().execute(broker, query, null);
        }
    }
}
