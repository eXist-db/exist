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


import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

/**
 * The corpus, Lucene configuration and query helpers shared by {@link UtilExpandHighlightingBenchmark}
 * and {@link UtilExpandHighlightingColdCacheBenchmark}, so that their results stay comparable.
 */
final class UtilExpandHighlightingCorpus {

    static final int ENTRY_COUNT = 5000;
    static final int PARAGRAPHS_PER_ENTRY = 20;

    /** Half the corpus (even i) gets an 'a'-prefixed headword, which an {@code a*} wildcard matches. */
    static final int A_WORD_ENTRY_COUNT = (ENTRY_COUNT + 1) / 2;

    static final String LUCENE_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
              <index>
                <lucene>
                  <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                  <text qname="entry">
                    <field name="text" expression="normalize-space()"/>
                    <field name="lemma" expression=".//form[@type='lemma']/orth"/>
                  </text>
                </lucene>
              </index>
            </collection>""";

    private UtilExpandHighlightingCorpus() {
    }

    static ExistEmbeddedServer startServer() throws EXistException, DatabaseConfigurationException, IOException {
        final Properties configProperties = new Properties();
        // BrokerPool expects a Long for this property (see BrokerPool.PROPERTY_SHUTDOWN_DELAY).
        configProperties.put("wait-before-shutdown", 0L);
        final ExistEmbeddedServer server = new ExistEmbeddedServer(configProperties, true, true);
        server.startDb();
        return server;
    }

    /**
     * Store the corpus as {@code dict.xml} in a new collection configured with {@link #LUCENE_CONFIG}.
     */
    static void storeCorpus(final BrokerPool pool, final XmldbURI collection) throws EXistException,
            PermissionDeniedException, IOException, CollectionConfigurationException, LockException,
            SAXException, TriggerException {
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn tx = transact.beginTransaction()) {

            final Collection coll = broker.getOrCreateCollection(tx, collection);
            broker.saveCollection(tx, coll);

            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(tx, broker, coll, LUCENE_CONFIG);

            broker.storeDocument(tx, XmldbURI.create("dict.xml"), new StringInputSource(generateCorpus()),
                    MimeType.XML_TYPE, coll);

            transact.commit(tx);
        }
    }

    static CompiledXQuery compile(final XQuery xquery, final DBBroker broker, final String query)
            throws XPathException, PermissionDeniedException {
        final XQueryContext context = new XQueryContext(broker.getBrokerPool());
        return xquery.compile(context, query);
    }

    /**
     * Runs the query and returns the resulting node count, throwing if it doesn't match the
     * expected hit count - a "fast but wrong" guard, not a performance threshold (the ratios are
     * read off the JMH/dashboard series, not asserted here).
     */
    static int execute(final BrokerPool pool, final CompiledXQuery compiledQuery, final int expectedCount)
            throws EXistException, PermissionDeniedException, XPathException, IOException {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final Sequence result = xquery.execute(broker, compiledQuery, null);
            final int count = result.getItemCount();
            if (count != expectedCount) {
                throw new IllegalStateException("Expected " + expectedCount + " top-level results, got " + count);
            }
            return count;
        }
    }

    /**
     * Dict/entry corpus: {@value #ENTRY_COUNT} entries, half with an 'a'-prefixed headword (the
     * {@code lemma:a*} wildcard target), each padded with {@value #PARAGRAPHS_PER_ENTRY}
     * paragraphs so per-entry tokenization cost is measurable - mirrors the corpus shape in the
     * original (deleted) {@code UtilExpandHighlightingPerformanceTest}, minus the TEI namespace
     * (dropped in the xqsuite migration as boilerplate without correctness value; irrelevant to
     * the perf shape measured here).
     */
    static String generateCorpus() {
        final StringBuilder doc = new StringBuilder();
        doc.append("<dict>\n");
        for (int i = 0; i < ENTRY_COUNT; i++) {
            final String letter = (i % 2 == 0) ? "a" : "b";
            final String word = letter + "word" + i;
            doc.append("  <entry xml:id=\"e").append(i).append("\">")
                    .append("<form type=\"lemma\"><orth>").append(word).append("</orth></form>")
                    .append("<sense><def>Definition for ").append(word).append(". ");
            for (int j = 0; j < PARAGRAPHS_PER_ENTRY; j++) {
                doc.append("This is paragraph ").append(j).append(" of the explanation for ")
                        .append(word).append(", with additional descriptive sentences ")
                        .append("that emulate real lexicographic content. The headword ")
                        .append(word).append(" appears multiple times in the body. ");
            }
            doc.append("</def></sense></entry>\n");
        }
        doc.append("</dict>\n");
        return doc.toString();
    }
}
