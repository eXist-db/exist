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

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.io.InputStreamUtil;
import org.exist.xmldb.IndexQueryService;
import org.junit.*;
import org.junit.runner.RunWith;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import java.io.IOException;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.samples.Samples.SAMPLES;

/**
 * Performance regression test for <a href="https://github.com/eXist-db/exist/issues/873">GH-873</a>:
 * a full text query against a let-bound variable
 * ({@code let $a := //SPEECH return $a[ft:query(LINE, ...)]}) ran significantly
 * slower than the equivalent direct form ({@code //SPEECH[ft:query(LINE, ...)]}).
 *
 * <p>Correctness coverage (indirect vs. direct result parity, including an
 * additional non-optimizable predicate and the {@code //$v[pred]} abbreviated
 * form) lives in the XQSuite module
 * {@code extensions/indexes/lucene/src/test/xquery/lucene/issue-873.xql},
 * which needs no Java-internal state. This class covers only the timing
 * regression, which does need Java-level {@code System.nanoTime()}.
 *
 * <p>Two bugs combined to cause the slowdown:
 * <ol>
 *   <li>The parser wraps a FLWOR clause's terminal return expression in a
 *   {@code DebuggableExpression} (see the {@code "return"} action in
 *   {@code XQueryTree.g}). {@code DebuggableExpression#analyze} deliberately
 *   does not set itself as the parent when an ancestor already provided one,
 *   so that structural {@code getParent()} lookups see through it -- but that
 *   also means {@link org.exist.xquery.Optimizer#visitFilteredExpr} resolves
 *   the filtered expression's parent to the {@code LetExpr} itself, not the
 *   wrapper. The resulting {@code letExpr.replace(filtered, extension)} was a
 *   silent no-op, since {@code letExpr.returnExpr} was actually the wrapper,
 *   not {@code filtered} -- so the {@code (#exist:optimize#)} pragma was
 *   built but never wired into the executed tree. Fixed in
 *   {@link org.exist.xquery.BindingExpression#replace} (and, for the same
 *   defect in an {@code if/then/else} else-branch, {@link org.exist.xquery.ConditionalExpression#replace}).</li>
 *   <li>Even once the pragma is wired up, {@link org.exist.xquery.pragmas.Optimize#eval}
 *   discarded the index pre-selected candidate set whenever the filtered
 *   expression's source was a plain variable reference, because
 *   {@code VariableReference#eval} ignores whatever contextSequence it is
 *   passed and always returns the variable's full, unfiltered value. Fixed in
 *   {@link org.exist.xquery.FilteredExpression#evalOnPreselected} and its use
 *   in {@code Optimize#eval}.</li>
 * </ol>
 */
@RunWith(ParallelRunner.class)
public class Issue873RegressionTest {

    @ClassRule
    public final static ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

    private final static String COLLECTION_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index xmlns:mods="http://www.loc.gov/mods/v3">
                    <lucene>
                        <text qname="LINE"/>
                        <text qname="SPEAKER"/>
                    </lucene>
                </index>
            </collection>""";
    private static Collection testCollection;

    // The bug scales with corpus size: the buggy code path re-evaluates the
    // predicate once per node across the FULL, unfiltered variable value
    // instead of the small index pre-selected candidate set. The bundled
    // Shakespeare samples (3 plays) aren't large enough on their own to make
    // that gap reliably visible in wall-clock time, so hamlet.xml is stored
    // repeatedly under distinct names to inflate the corpus.
    private static final int HAMLET_COPIES = 40;

    /**
     * With the optimizer enabled, the indirect form must be in the same
     * ballpark as the direct form. Before the fix (measured on this corpus):
     * direct ~10ms, indirect ~60ms. After the fix: direct ~10ms, indirect
     * ~15-17ms.
     *
     * <p>Uses the minimum across several iterations rather than the mean: a
     * GC pause or scheduling hiccup can only ever inflate a wall-clock
     * measurement, never make the query artificially faster, so the minimum
     * is the more robust statistic for a perf-regression ceiling and is less
     * prone to CI flakiness than an average.
     */
    @Test
    public void indirectQueryUnderLoosePerfBound() throws XMLDBException {
        final XQueryService service = testCollection.getService(XQueryService.class);

        final String direct = "//SPEECH[ft:query(LINE, 'king')]";
        final String indirect = "let $a := //SPEECH return $a[ft:query(LINE, 'king')]";

        // warm up (JIT, lucene caches) -- not timed
        service.query(direct);
        service.query(indirect);

        long directMinNanos = Long.MAX_VALUE;
        long indirectMinNanos = Long.MAX_VALUE;
        final int iterations = 8;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            service.query(direct);
            directMinNanos = Math.min(directMinNanos, System.nanoTime() - start);

            start = System.nanoTime();
            service.query(indirect);
            indirectMinNanos = Math.min(indirectMinNanos, System.nanoTime() - start);
        }

        final long directMs = directMinNanos / 1_000_000;
        final long indirectMs = indirectMinNanos / 1_000_000;

        Assert.assertTrue(
                """
                Indirect form (%dms) should not be drastically slower than the direct form (%dms) \
                -- the (#exist:optimize#) pragma may not be reaching the index pre-select again (GH-873)""".formatted(indirectMs, directMs),
                indirectMs <= directMs * 3 + 15);
    }

    @BeforeClass
    public static void initDatabase() throws XMLDBException, IOException {
        CollectionManagementService service = server.getRoot().getService(CollectionManagementService.class);
        testCollection = service.createCollection("test873");
        Assert.assertNotNull(testCollection);

        IndexQueryService idxConf = testCollection.getService(IndexQueryService.class);
        idxConf.configureCollection(COLLECTION_CONFIG);

        final String hamletContent;
        try (final InputStream is = SAMPLES.getShakespeareSample("hamlet.xml")) {
            hamletContent = InputStreamUtil.readString(is, UTF_8);
        }
        for (int i = 0; i < HAMLET_COPIES; i++) {
            final XMLResource resource = testCollection.createResource("hamlet-copy-" + i + ".xml", XMLResource.class);
            resource.setContent(hamletContent);
            testCollection.storeResource(resource);
        }
    }
}
