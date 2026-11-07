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
package org.exist.indexing.range;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.exist.collections.Collection;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A document is analyzed with the analyzers of its own collection's configuration, whatever another collection
 * registers for the same field name while the document is being added: the range index and its writer are shared by
 * all collections, and a field's analyzer is looked up by the field's name.
 * <p>
 * All fields of a {@code create} go into one Lucene document, and the writer asks for each field's analyzer as it gets
 * to the field. A filter on the first field that registers another analyzer for the second field, from another thread,
 * so makes the swap happen where a store into another collection at the same moment would.
 */
class RangeIndexFieldAnalyzerTest {

    @RegisterExtension
    static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String COLLECTION_NAME = "range-field-analyzer";

    /** Runs once, on the next reset of a {@link SwapFilter}, which disarms it. */
    private static final AtomicReference<Runnable> ON_RESET = new AtomicReference<>();

    private static final String COLLECTION_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <range>
                        <create match="//address">
                            <filter class="org.exist.indexing.range.RangeIndexFieldAnalyzerTest$SwapFilter"/>
                            <filter class="org.apache.lucene.analysis.core.LowerCaseFilter"/>
                            <field name="range-field-analyzer-city" match="city" type="xs:string"/>
                            <field name="range-field-analyzer-street" match="street" type="xs:string"/>
                        </create>
                    </range>
                </index>
            </collection>""";

    private static final String DATA = """
            <test>
                <address>
                    <city>Rüsselsheim</city>
                    <street>Hauptstraße</street>
                </address>
            </test>""";

    /** Passes tokens through; on reset it runs {@link #ON_RESET} once. */
    public static final class SwapFilter extends TokenFilter {
        public SwapFilter(final TokenStream input) {
            super(input);
        }

        @Override
        public boolean incrementToken() throws IOException {
            return input.incrementToken();
        }

        @Override
        public void reset() throws IOException {
            super.reset();
            final Runnable action = ON_RESET.getAndSet(null);
            if (action != null) {
                action.run();
            }
        }
    }

    @Test
    void documentIsAnalyzedWithItsOwnAnalyzersWhenAnotherCollectionRegistersOthersMeanwhile() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final RangeIndex index = (RangeIndex) pool.getIndexManager().getIndexById(RangeIndex.ID);
        final AtomicBoolean registered = new AtomicBoolean();
        // another collection indexes the same field names without lowercasing
        ON_RESET.set(() -> {
            final Thread other = new Thread(() -> {
                index.addFieldAnalyzer("range-field-analyzer-city", new KeywordAnalyzer());
                index.addFieldAnalyzer("range-field-analyzer-street", new KeywordAnalyzer());
            });
            other.start();
            try {
                other.join();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            registered.set(true);
        });

        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            try (final Txn transaction = transact.beginTransaction()) {
                final Collection collection = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append(COLLECTION_NAME));
                broker.saveCollection(transaction, collection);
                pool.getConfigurationManager().addConfiguration(transaction, broker, collection, COLLECTION_CONFIG);
                broker.storeDocument(transaction, XmldbURI.create("test.xml"), new StringInputSource(DATA), MimeType.XML_TYPE, collection);
                transact.commit(transaction);
            } finally {
                ON_RESET.set(null);
            }
            assertTrue(registered.get(), "the other analyzers were registered while the document was being added");

            assertEquals(1, count(pool, broker, "city = 'Rüsselsheim'"), "the first field");
            assertEquals(1, count(pool, broker, "street = 'Hauptstraße'"), "the second field, analyzed after the other analyzers were registered");
        }
    }

    private static int count(final BrokerPool pool, final DBBroker broker, final String predicate) throws Exception {
        final XQuery xquery = pool.getXQueryService();
        final Sequence result = xquery.execute(broker, xquery.compile(new XQueryContext(pool),
                "count(collection('/db/" + COLLECTION_NAME + "')//address[" + predicate + "])"), null);
        return Integer.parseInt(result.getStringValue());
    }
}
