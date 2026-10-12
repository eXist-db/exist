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
package org.exist.indexing.lucene;

import java.util.Optional;

import org.apache.lucene.analysis.Analyzer;
import org.exist.collections.Collection;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A store that fails while the document is being parsed and indexed, after its nodes were written to the DOM, must not
 * leave the broker's current DOM page pointing at the failed document. Otherwise the next document written by the same
 * broker continues on that page, and replacing the failed document's resource later frees the page together with the
 * other document's nodes.
 *
 * <p>The failure is an analyzer that throws an unchecked exception for the {@code p} element; documents without
 * {@code p} are indexed normally.</p>
 */
public class FailedStoreCurrentPageTest {

    public static class FailingAnalyzer extends Analyzer {
        @Override
        protected TokenStreamComponents createComponents(final String fieldName) {
            throw new IllegalStateException("failure while indexing (test)");
        }
    }

    private static final XmldbURI COLLECTION_URI = XmldbURI.create("/db/failed-store-page");
    private static final XmldbURI KEEP = XmldbURI.create("keep.xml");
    private static final XmldbURI OTHER = XmldbURI.create("other.xml");

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeEach
    public void setUp() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager tm = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn txn = tm.beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(txn, COLLECTION_URI);
            broker.saveCollection(txn, collection);
            broker.storeDocument(txn, KEEP, new StringInputSource("<doc><q>keep v1</q></doc>"), MimeType.XML_TYPE, collection);
            tm.commit(txn);
        }
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn txn = tm.beginTransaction()) {
            final Collection collection = broker.getCollection(COLLECTION_URI);
            final String configuration = "<collection xmlns='http://exist-db.org/collection-config/1.0'><index><lucene>"
                    + "<analyzer id='failing' class='" + FailingAnalyzer.class.getName() + "'/>"
                    + "<text qname='p' analyzer='failing'/></lucene></index></collection>";
            pool.getConfigurationManager().addConfiguration(txn, broker, collection, configuration);
            tm.commit(txn);
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager tm = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn txn = tm.beginTransaction()) {
            final Collection collection = broker.getCollection(COLLECTION_URI);
            if (collection != null) {
                broker.removeCollection(txn, collection);
            }
            final Collection config = broker.getCollection(XmldbURI.CONFIG_COLLECTION_URI.append("db").append("failed-store-page"));
            if (config != null) {
                broker.removeCollection(txn, config);
            }
            tm.commit(txn);
        }
    }

    @Test
    public void documentStoredAfterAFailedReplaceSurvivesTheNextReplace() throws Exception {
        // 1. replace keep.xml with content that fails in the index phase, after its nodes were written
        assertThrows(IllegalStateException.class, () ->
                store(KEEP, "<doc><q>first</q><p>boom</p></doc>"));

        // 2. an unrelated document written afterwards by the same broker
        store(OTHER, "<doc><q>other</q></doc>");
        assertEquals("other", content(OTHER));

        // 3. replacing keep.xml again frees the pages of the failed replace
        store(KEEP, "<doc><q>keep v3</q></doc>");

        assertEquals("keep v3", content(KEEP));
        assertEquals("other", content(OTHER), "the unrelated document was damaged by the replace of keep.xml");
    }

    private void store(final XmldbURI name, final String xml) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager tm = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn txn = tm.beginTransaction()) {
            final Collection collection = broker.getCollection(COLLECTION_URI);
            try {
                broker.storeDocument(txn, name, new StringInputSource(xml), MimeType.XML_TYPE, collection);
            } catch (final Throwable t) {
                tm.abort(txn);
                throw t;
            }
            tm.commit(txn);
        }
    }

    private String content(final XmldbURI name) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            return pool.getXQueryService().execute(broker,
                    "doc('" + COLLECTION_URI.append(name.toString()) + "')/doc/q/string()", null).getStringValue();
        }
    }
}
