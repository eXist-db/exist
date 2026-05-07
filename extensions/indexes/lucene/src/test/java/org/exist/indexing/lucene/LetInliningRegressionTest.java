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
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompileContext;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for <a href="https://github.com/eXist-db/exist/issues/873">issue
 * #873</a>: a let-bound persistent path used as the source of a FilteredExpression
 * with an Optimizable predicate should not be ~167x slower than the direct form.
 *
 * <p>Validates the {@code LetInliner} rewrite that turns
 * {@code let $a := //X return $a[Optimizable-pred]} into
 * {@code //X[Optimizable-pred]} so the legacy Optimizer pass can attach the
 * {@code (#exist:optimize#)} pragma to the LocationStep, routing the predicate
 * through the lucene pre-select.
 */
public class LetInliningRegressionTest {

    private static final String COLLECTION_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index>
                    <lucene>
                        <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                        <text qname="LINE"/>
                    </lucene>
                </index>
            </collection>
            """;

    /** ~200 LINE elements, one of which contains the sentinel "Denmark". */
    private static final String CORPUS = buildCorpus();

    private static String buildCorpus() {
        final StringBuilder sb = new StringBuilder("<play><speech>");
        for (int i = 0; i < 199; i++) {
            sb.append("<LINE>line number ").append(i).append("</LINE>");
        }
        sb.append("<LINE>something is rotten in the state of Denmark</LINE>");
        sb.append("</speech></play>");
        return sb.toString();
    }

    /** Enables the optimizer per-query so the test does not require conf.xml flips. */
    /** Enables the optimizer per-query so the test does not require conf.xml flips. */
    private static final String OPTIMIZE = "declare option exist:optimize 'enable=yes';";

    private static final String QUERY_DIRECT = """
            declare namespace ft="http://exist-db.org/xquery/lucene";
            %s
            collection('%s')//LINE[ft:query(., 'Denmark')]
            """.formatted(OPTIMIZE, TestConstants.TEST_COLLECTION_URI.toString());

    private static final String QUERY_INDIRECT = """
            declare namespace ft="http://exist-db.org/xquery/lucene";
            %s
            let $a := collection('%s')//LINE
            return $a[ft:query(., 'Denmark')]
            """.formatted(OPTIMIZE, TestConstants.TEST_COLLECTION_URI.toString());

    private static final String QUERY_LET_REFERENCED_TWICE = """
            declare namespace ft="http://exist-db.org/xquery/lucene";
            %s
            let $a := collection('%s')//LINE
            return ($a[ft:query(., 'Denmark')], $a[1])
            """.formatted(OPTIMIZE, TestConstants.TEST_COLLECTION_URI.toString());

    private static final String QUERY_LET_BOUND_TO_COUNT = """
            %s
            let $a := collection('%s')//LINE
            return count($a)
            """.formatted(OPTIMIZE, TestConstants.TEST_COLLECTION_URI.toString());

    private static final String QUERY_LET_IS_TYPED = """
            declare namespace ft="http://exist-db.org/xquery/lucene";
            %s
            let $a as element(LINE)+ := collection('%s')//LINE
            return $a[ft:query(., 'Denmark')]
            """.formatted(OPTIMIZE, TestConstants.TEST_COLLECTION_URI.toString());

    private static Collection root;

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Before
    public void setupAndStore() throws EXistException, PermissionDeniedException, IOException,
            TriggerException, CollectionConfigurationException, SAXException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transact.beginTransaction()) {
            root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);
            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, root, COLLECTION_CONFIG);
            broker.storeDocument(transaction, XmldbURI.create("corpus.xml"),
                    new StringInputSource(CORPUS), MimeType.XML_TYPE, root);
            transact.commit(transaction);
        }
    }

    @AfterClass
    public static void cleanup() throws Exception {
        org.exist.TestUtils.cleanupDB();
    }

    @Test
    public void issue873_indirectQueryReturnsSameNodes() throws Exception {
        final long directCount = countOf(QUERY_DIRECT);
        final long indirectCount = countOf(QUERY_INDIRECT);
        assertEquals("indirect form must return same hit count as direct form",
                directCount, indirectCount);
        assertEquals("expected exactly one Denmark hit", 1L, directCount);
    }

    @Test
    public void issue873_inlineRewriteLogged() throws Exception {
        final List<String> rewrites = compileAndCaptureLog(QUERY_INDIRECT);
        assertTrue("expected an 'inline let' rewrite in the optimizer log; got: " + rewrites,
                rewrites.stream().anyMatch(s -> s.contains("inline let $a")));
    }

    @Test
    public void inline_doesNotFireWhen_letReferencedTwice() throws Exception {
        final List<String> rewrites = compileAndCaptureLog(QUERY_LET_REFERENCED_TWICE);
        assertTrue("inline must not fire when $v is used more than once; got: " + rewrites,
                rewrites.stream().noneMatch(s -> s.contains("inline let $a")));
    }

    @Test
    public void inline_doesNotFireWhen_letBoundToCount() throws Exception {
        final List<String> rewrites = compileAndCaptureLog(QUERY_LET_BOUND_TO_COUNT);
        assertTrue("inline must not fire when body is not a FilteredExpression; got: " + rewrites,
                rewrites.stream().noneMatch(s -> s.contains("inline let $a")));
    }

    @Test
    public void inline_doesNotFireWhen_letIsTyped() throws Exception {
        final List<String> rewrites = compileAndCaptureLog(QUERY_LET_IS_TYPED);
        assertTrue("inline must not fire when binding has a static type; got: " + rewrites,
                rewrites.stream().noneMatch(s -> s.contains("inline let $a")));
    }

    @Test
    public void issue873_indirectQueryUnderLoosePerfBound() throws Exception {
        // Warmup the JIT and the cached pragma machinery on both forms.
        for (int i = 0; i < 3; i++) {
            countOf(QUERY_DIRECT);
            countOf(QUERY_INDIRECT);
        }
        final long directNs = timeOf(QUERY_DIRECT);
        final long indirectNs = timeOf(QUERY_INDIRECT);
        // Pre-fix this ratio is ~167x. The fix brings it to ~1x. A 20x ceiling
        // catches the regression without inviting CI flakiness from JIT noise
        // on tiny absolute timings.
        final long ceilingNs = directNs * 20L + 500_000_000L; // +500ms slack
        assertTrue("indirect form took " + (indirectNs / 1_000_000) + "ms; direct took "
                        + (directNs / 1_000_000) + "ms; ceiling "
                        + (ceilingNs / 1_000_000) + "ms",
                indirectNs < ceilingNs);
    }

    private long countOf(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final Sequence seq = xquery.execute(broker, query, null);
            return seq.getItemCount();
        }
    }

    private long timeOf(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final long start = System.nanoTime();
        countOf(query);
        return System.nanoTime() - start;
    }

    private List<String> compileAndCaptureLog(final String query) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final XQueryContext context = new XQueryContext(pool);
            final CompiledXQuery compiled = xquery.compile(broker, context, query);
            assertNotNull(compiled);
            final CompileContext cc = context.getLastCompileContext();
            assertNotNull("optimize() pass should have run", cc);
            return cc.log();
        }
    }
}
