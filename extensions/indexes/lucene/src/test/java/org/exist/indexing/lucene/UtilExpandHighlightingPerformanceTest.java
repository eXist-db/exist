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
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for issue #5738: util:expand becomes orders of magnitude slower
 * once match-highlighting is enabled, even when only one full-text hit is being
 * expanded.
 *
 * <p>The reporter (merenyics) and follow-up commenter daliboris isolated the slowdown
 * to the highlighting code path: WithoutExpand and ExpandSkipXIncludeHighlightNone
 * complete in milliseconds, while ExpandOnly with highlighting takes ~1 second per
 * hit on a 50MB single-document corpus.
 *
 * <p>The fix in {@link LuceneMatchListener} caches the rewritten Lucene terms per
 * Query so that batch util:expand($hits) does not re-rewrite the same wildcard or
 * prefix query on every input node, and skips scanMatches when termMap is empty
 * after the configured-fields exclusion (which is the normal case for
 * `lemma:Aachen`-style queries).
 */
public class UtilExpandHighlightingPerformanceTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final int ENTRY_COUNT = 5000;

    private static final String LUCENE_CONF =
            """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index xmlns:tei="http://www.tei-c.org/ns/1.0">
                    <lucene>
                        <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                        <text qname="tei:entry">
                            <field name="text" expression="normalize-space()"/>
                            <field name="lemma" expression=".//tei:form[@type='lemma']/tei:orth"/>
                        </text>
                    </lucene>
                </index>
            </collection>
            """;

    private static final String DECLARE_NS = "declare namespace tei='http://www.tei-c.org/ns/1.0'; ";
    private static final String COLL = "collection('" + TestConstants.TEST_COLLECTION_URI + "')";

    @BeforeClass
    public static void startDB() throws DatabaseConfigurationException, EXistException, PermissionDeniedException, IOException, TriggerException, SAXException, CollectionConfigurationException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, root, LUCENE_CONF);
            broker.saveCollection(transaction, root);

            // TEI-flavoured corpus mirroring daliboris's Damen-Conversations-Lexikon-style
            // setup: one document, many entries, half with headwords starting with 'a'.
            // Each entry pads its body with several paragraphs of text so the per-entry
            // tokenization cost is measurable.
            final StringBuilder doc = new StringBuilder();
            doc.append("""
                <TEI xmlns="http://www.tei-c.org/ns/1.0">
                  <text><body>
                """);
            for (int i = 0; i < ENTRY_COUNT; i++) {
                final String letter = (i % 2 == 0) ? "a" : "b";
                final String word = letter + "word" + i;
                doc.append("    <entry xml:id=\"e").append(i).append("\">")
                        .append("<form type=\"lemma\"><orth>").append(word).append("</orth></form>")
                        .append("<sense><def>Definition for ").append(word).append(". ");
                for (int j = 0; j < 20; j++) {
                    doc.append("This is paragraph ").append(j).append(" of the explanation for ")
                            .append(word).append(", with additional descriptive sentences ")
                            .append("that emulate real lexicographic content. The headword ")
                            .append(word).append(" appears multiple times in the body. ");
                }
                doc.append("</def></sense></entry>\n");
            }
            doc.append("""
                  </body></text>
                </TEI>
                """);

            broker.storeDocument(transaction, XmldbURI.create("dict.xml"), new StringInputSource(doc.toString()),
                    MimeType.XML_TYPE, root);
            transact.commit(transaction);
        }
    }

    @AfterClass
    public static void closeDB() throws Exception {
        org.exist.TestUtils.cleanupDB();
    }

    /**
     * Correctness check: PR #3467 specifies that named-field queries (e.g.
     * {@code lemma:Aachen}) must NOT produce {@code <exist:match>} highlights, since
     * those terms are configured-field metadata rather than main content. The fix
     * relies on this property to short-circuit when termMap is empty; verify the
     * short-circuit still produces the expected (zero-match) output.
     */
    @Test
    public void namedFieldQueryProducesNoHighlights() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);

            // Named field 'lemma' is configured, so per PR #3467 lemma:* queries should
            // not produce exist:match wrappers in util:expand output.
            final Sequence seq = xquery.execute(broker, DECLARE_NS +
                    "let $hit := subsequence(" + COLL + "//tei:entry[ft:query(., 'lemma:aword42')], 1, 1) " +
                    "return count(util:expand($hit)//exist:match)", null);
            assertEquals(1, seq.getItemCount());
            final int matches = seq.itemAt(0).toJavaObject(Integer.class);
            assertEquals("Named-field query should produce no exist:match wrappers (PR #3467)",
                    0, matches);
        }
    }

    /**
     * Correctness check: util:expand against an implicit-field full-text query still
     * produces the expected {@code <exist:match>} wrapping after the cache fix.
     */
    @Test
    public void highlightingStillWorksForImplicitFieldQuery() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);

            // Implicit-field query - terms aren't excluded so we get exist:match wrappers.
            final Sequence seq = xquery.execute(broker, DECLARE_NS +
                    "let $hit := subsequence(" + COLL + "//tei:entry[ft:query(., 'aword42')], 1, 1) " +
                    "return count(util:expand($hit)//exist:match)", null);
            assertEquals(1, seq.getItemCount());
            final int matches = seq.itemAt(0).toJavaObject(Integer.class);
            assertTrue("Should produce at least one exist:match for 'aword42'; got " + matches,
                    matches >= 1);
        }
    }

    /**
     * Correctness check: batch util:expand($hits) with a wildcard query produces the
     * same number of {@code <exist:match>} elements as the equivalent per-hit
     * for-loop. This is the path the cache exercises most.
     */
    @Test
    public void batchExpandMatchesPerHitForLoop() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);

            // Implicit-field 'a*' so terms aren't excluded by the configured-fields filter.
            final Sequence forLoop = xquery.execute(broker, DECLARE_NS +
                    "sum(for $h in " + COLL + "//tei:entry[ft:query(., 'aword42')] " +
                    "return count(util:expand($h)//exist:match))", null);
            assertEquals(1, forLoop.getItemCount());
            final int forLoopCount = forLoop.itemAt(0).toJavaObject(Integer.class);

            final Sequence batch = xquery.execute(broker, DECLARE_NS +
                    "let $hits := " + COLL + "//tei:entry[ft:query(., 'aword42')] " +
                    "return count(util:expand($hits)//exist:match)", null);
            assertEquals(1, batch.getItemCount());
            final int batchCount = batch.itemAt(0).toJavaObject(Integer.class);

            assertEquals("Batch util:expand should produce the same exist:match count as the per-hit for-loop",
                    forLoopCount, batchCount);
            assertTrue("Should produce at least one exist:match for 'aword42'; got " + batchCount,
                    batchCount >= 1);
        }
    }

    /**
     * Performance threshold: util:expand on a single hit with highlighting on must not
     * be orders of magnitude slower than the same call with highlighting off. Pre-fix,
     * the ratio was 50x-500x on the reporter's data; post-fix it is ~1-3x. The 20x
     * threshold leaves comfortable headroom for noisy CI runners.
     */
    @Test
    public void singleHitHighlightingNotOrderOfMagnitudeSlower() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);

            // Warm up: JIT, lucene reader caches, etc.
            for (int i = 0; i < 3; i++) {
                xquery.execute(broker, DECLARE_NS +
                        "util:expand(subsequence(" + COLL + "//tei:entry[ft:query(., 'aword42')], 1, 1))", null);
            }

            final int iterations = 20;
            long t0 = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                xquery.execute(broker, DECLARE_NS +
                        "let $hits := " + COLL + "//tei:entry[ft:query(., 'aword42')] " +
                        "return util:expand(subsequence($hits, 1, 1), 'highlight-matches=none expand-xincludes=no')", null);
            }
            final long highlightOffNanos = System.nanoTime() - t0;

            t0 = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                xquery.execute(broker, DECLARE_NS +
                        "let $hits := " + COLL + "//tei:entry[ft:query(., 'aword42')] " +
                        "return util:expand(subsequence($hits, 1, 1))", null);
            }
            final long highlightOnNanos = System.nanoTime() - t0;

            final double ratio = (double) highlightOnNanos / Math.max(highlightOffNanos, 1);
            assertTrue("util:expand with highlighting should not be > 20x slower than without; was " +
                    String.format("%.1f", ratio) + "x (off=" + (highlightOffNanos / 1_000_000) +
                    "ms, on=" + (highlightOnNanos / 1_000_000) + "ms)", ratio < 20);
        }
    }

    /**
     * Performance threshold: batch util:expand($hits) on ~2500 wildcard hits should
     * not be more than ~5x slower than the equivalent call with highlighting off.
     * Pre-fix the ratio was ~50x (every reset() rebuilt the term map by re-rewriting
     * the wildcard query against the IndexReader); post-fix it is ~3-5x. The 15x
     * threshold catches a regression while leaving headroom for noisy CI.
     */
    @Test
    public void batchWildcardExpandRatioUnderThreshold() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);

            // Warm up
            xquery.execute(broker, DECLARE_NS +
                    "util:expand(" + COLL + "//tei:entry[ft:query(., 'lemma:a*')])", null);

            long t0 = System.nanoTime();
            xquery.execute(broker, DECLARE_NS +
                    "util:expand(" + COLL + "//tei:entry[ft:query(., 'lemma:a*')], 'highlight-matches=none expand-xincludes=no')", null);
            final long highlightOffNanos = System.nanoTime() - t0;

            t0 = System.nanoTime();
            xquery.execute(broker, DECLARE_NS +
                    "util:expand(" + COLL + "//tei:entry[ft:query(., 'lemma:a*')])", null);
            final long highlightOnNanos = System.nanoTime() - t0;

            final double ratio = (double) highlightOnNanos / Math.max(highlightOffNanos, 1);
            assertTrue("Batch util:expand with highlighting should not be > 15x slower than without; " +
                    "was " + String.format("%.1f", ratio) + "x (off=" + (highlightOffNanos / 1_000_000) +
                    "ms, on=" + (highlightOnNanos / 1_000_000) + "ms)", ratio < 15);
        }
    }
}
