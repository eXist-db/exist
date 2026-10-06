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

import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.NodeProxy;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The facets of a search result must stay readable after the index was refreshed.
 * <p>
 * The facets of a search ({@code ft:facets}) are read after the search has returned its searcher. The index is
 * shared by all collections, so a write by another thread, followed by a refresh, replaces the searcher, and the
 * taxonomy reader of the old one was closed, so reading the facets failed with "this TaxonomyReader is closed".
 */
public class LuceneFacetsReaderLifecycleTest {

    private static final String COLLECTION_NAME = "facets-reader-lifecycle";
    private static final XmldbURI COLLECTION = XmldbURI.create("/db/" + COLLECTION_NAME);
    private static final XmldbURI CONFIG_COLLECTION = XmldbURI.CONFIG_COLLECTION_URI.append("db").append(COLLECTION_NAME);
    private static final String DIMENSION = "kind";

    private static final String CONFIGURATION = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
              <index>
                <lucene>
                  <text qname="item">
                    <facet dimension="%s" expression="@kind"/>
                  </text>
                </lucene>
              </index>
            </collection>
            """.formatted(DIMENSION);

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @AfterEach
    void cleanup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transactionManager = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transactionManager.beginTransaction()) {
            for (final XmldbURI uri : List.of(COLLECTION, CONFIG_COLLECTION)) {
                try (final Collection collection = broker.openCollection(uri, LockMode.WRITE_LOCK)) {
                    if (collection != null) {
                        broker.removeCollection(transaction, collection);
                    }
                }
            }
            transactionManager.commit(transaction);
        }
    }

    @Test
    void facetsAreReadableAfterTheIndexWasRefreshed() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transactionManager = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final LuceneIndex index = ((LuceneIndexWorker) broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID)).index;

            try (final Txn transaction = transactionManager.beginTransaction()) {
                final Collection collection = broker.getOrCreateCollection(transaction, COLLECTION);
                broker.saveCollection(transaction, collection);
                pool.getConfigurationManager().addConfiguration(transaction, broker, collection, CONFIGURATION);
                store(broker, transaction, collection, "first.xml", "red");
                transactionManager.commit(transaction);
            }

            final XQuery xquery = pool.getXQueryService();
            final Sequence hits = xquery.execute(broker, "collection('" + COLLECTION + "')//item[ft:query(., ())]", null);
            assertEquals(2, hits.getItemCount());
            final Facets facets = facetsOf((NodeProxy) hits.itemAt(0));

            // another writer changes the index and a search refreshes it: the searcher the facets came from is replaced
            try (final Txn transaction = transactionManager.beginTransaction();
                 final Collection collection = broker.openCollection(COLLECTION, LockMode.WRITE_LOCK)) {
                store(broker, transaction, collection, "second.xml", "blue");
                transactionManager.commit(transaction);
            }
            index.withSearcher(searcher -> null);

            final FacetResult result = facets.getTopChildren(10, DIMENSION);
            assertNotNull(result, "facets of the first search");
            assertEquals(2, result.childCount, "values of the facet in the first search");
        }
    }

    private static void store(final DBBroker broker, final Txn transaction, final Collection collection, final String name, final String kind) throws Exception {
        final String xml = """
                <div><item kind="%s">match</item><item kind="%s">match</item></div>""".formatted(kind, kind + "s");
        broker.storeDocument(transaction, XmldbURI.create(name), new StringInputSource(xml), MimeType.XML_TYPE, collection);
    }

    private static Facets facetsOf(final NodeProxy hit) {
        for (Match match = hit.getMatches(); match != null; match = match.getNextMatch()) {
            if (match instanceof LuceneMatch luceneMatch) {
                return luceneMatch.getFacets();
            }
        }
        throw new AssertionError("no Lucene match on " + hit);
    }
}
