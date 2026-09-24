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
 * Regression test for <a href="https://github.com/eXist-db/exist/issues/6759">GH-6759</a>:
 * {@code count($a[ft:query(...)])}, where {@code $a} is a let-bound persistent
 * path, ran significantly slower than the equivalent form that wraps the
 * whole {@code let} in {@code count()} instead of just its return expression
 * ({@code count(let $a := X return $a[pred])} vs.
 * {@code let $a := X return count($a[pred])}) -- even though both were
 * already fast for the plain, unwrapped {@code let $a := X return $a[pred]}
 * form fixed in <a href="https://github.com/eXist-db/exist/issues/873">GH-873</a>.
 *
 * <p>Root cause: {@link org.exist.xquery.Function#checkArgument} statically
 * type-checks each argument during {@code analyze()}, and wraps an argument
 * whose static type doesn't fully guarantee the declared parameter type in a
 * {@link org.exist.xquery.DynamicCardinalityCheck} (or a sibling runtime
 * check) <em>after</em> {@code analyze()} has already fixed that argument's
 * {@code parent} pointer. {@link org.exist.xquery.Optimizer#visitFilteredExpr}'s
 * {@code parentFunction.replace(filtered, extension)} call then silently
 * no-ops, because the function's argument list holds the wrapper, not
 * {@code filtered} directly -- so the {@code (#exist:optimize#)} pragma is
 * built but never wired into the executed tree. Fixed by having the runtime
 * check wrapper classes implement {@link org.exist.xquery.RewritableExpression}
 * and having {@link org.exist.xquery.Function#replace} look one level inside
 * such a wrapper.
 */
@RunWith(ParallelRunner.class)
public class Issue6759RegressionTest {

    @ClassRule
    public final static ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

    private final static String COLLECTION_CONFIG = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index>
                    <lucene>
                        <text qname="LINE"/>
                    </lucene>
                </index>
            </collection>""";
    private static Collection testCollection;

    // Corpus needs to be large enough that an unindexed, per-node full scan
    // is measurably slower than an index pre-select; a single hamlet.xml is
    // too small for that gap to be reliable, so it's stored repeatedly under
    // distinct names.
    private static final int HAMLET_COPIES = 40;

    /**
     * Correctness: {@code count($a[pred])} inside the return clause must
     * match the direct form, regardless of the optimizer taking effect.
     */
    @Test
    public void countWrappedMatchesDirect() throws XMLDBException {
        final XQueryService service = testCollection.getService(XQueryService.class);

        final String direct = "count(//SPEECH[ft:query(LINE, 'king')])";
        final String countWrapped = "let $a := //SPEECH return count($a[ft:query(LINE, 'king')])";

        final long directCount = Long.parseLong(service.query(direct).getResource(0).getContent().toString());
        final long countWrappedCount = Long.parseLong(service.query(countWrapped).getResource(0).getContent().toString());

        Assert.assertEquals(directCount, countWrappedCount);
        Assert.assertTrue("Sanity check: query should actually match something", directCount > 0);
    }

    /**
     * Performance: {@code let $a := X return count($a[pred])} must be in the
     * same ballpark as the direct form. Before the fix (measured on this
     * corpus): direct ~7ms, count-wrapped ~47ms. After the fix: direct ~7ms,
     * count-wrapped ~10ms.
     *
     * <p>Uses the minimum across several iterations rather than the mean,
     * for the same reason as {@code Issue873RegressionTest}: a GC pause or
     * scheduling hiccup can only ever inflate a wall-clock measurement, never
     * make the query artificially faster.
     */
    @Test
    public void countWrappedUnderLoosePerfBound() throws XMLDBException {
        final XQueryService service = testCollection.getService(XQueryService.class);

        final String direct = "count(//SPEECH[ft:query(LINE, 'king')])";
        final String countWrapped = "let $a := //SPEECH return count($a[ft:query(LINE, 'king')])";

        // warm up (JIT, lucene caches) -- not timed
        service.query(direct);
        service.query(countWrapped);

        long directMinNanos = Long.MAX_VALUE;
        long countWrappedMinNanos = Long.MAX_VALUE;
        final int iterations = 8;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            service.query(direct);
            directMinNanos = Math.min(directMinNanos, System.nanoTime() - start);

            start = System.nanoTime();
            service.query(countWrapped);
            countWrappedMinNanos = Math.min(countWrappedMinNanos, System.nanoTime() - start);
        }

        final long directMs = directMinNanos / 1_000_000;
        final long countWrappedMs = countWrappedMinNanos / 1_000_000;

        Assert.assertTrue(
                """
                count($a[pred]) form (%dms) should not be drastically slower than the direct form (%dms) \
                -- the DynamicCardinalityCheck wrapper may be blocking the optimizer rewrite again (GH-6759)""".formatted(countWrappedMs, directMs),
                countWrappedMs <= directMs * 3 + 15);
    }

    @BeforeClass
    public static void initDatabase() throws XMLDBException, IOException {
        CollectionManagementService service = server.getRoot().getService(CollectionManagementService.class);
        testCollection = service.createCollection("test6759");
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
