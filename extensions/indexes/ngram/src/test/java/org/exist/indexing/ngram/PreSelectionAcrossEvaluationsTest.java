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
package org.exist.indexing.ngram;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An ngram query that used the index must not leave what it found for the next evaluation of the same expression:
 * over a collection without the index there are no hits, however many the collection before had. Every query is
 * also run with the optimizer off, which gives the answer the optimized one must equal.
 */
@Execution(ExecutionMode.SAME_THREAD)
public class PreSelectionAcrossEvaluationsTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String CONFIG_COLLECTION = "/db/system/config/db";

    private static final String XCONF = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index>
                    <ngram qname="div"/>
                    <ngram qname="@cat"/>
                </index>
            </collection>""";

    private static final String DOCUMENT = """
            <doc>
                <div cat="zebra">zebra</div>
                <div cat="pony">pony</div>
                <div cat="zebra">zebra</div>
            </doc>""";

    private static final String SYSTEM = """
            import module namespace system = 'http://exist-db.org/xquery/system';
            declare namespace stats = 'http://exist-db.org/xquery/profiling';
            """;

    @BeforeAll
    static void store() throws Exception {
        execute("xmldb:create-collection('/db/system', 'config'), xmldb:create-collection('/db/system/config', 'db')");
        execute("xmldb:create-collection('" + CONFIG_COLLECTION + "', 'pn-indexed'), "
                + "xmldb:store('" + CONFIG_COLLECTION + "/pn-indexed', 'collection.xconf', " + XCONF + ")");
        execute("xmldb:create-collection('/db', 'pn-indexed'), xmldb:store('/db/pn-indexed', 'doc.xml', " + DOCUMENT + ")");
        execute("xmldb:create-collection('/db', 'pn-plain'), xmldb:store('/db/pn-plain', 'doc.xml', " + DOCUMENT + ")");
    }

    @AfterAll
    static void remove() throws Exception {
        execute("xmldb:remove('/db/pn-indexed'), xmldb:remove('/db/pn-plain'), xmldb:remove('" + CONFIG_COLLECTION + "/pn-indexed')");
    }

    private static String execute(final String query) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final CompiledXQuery compiled = xquery.compile(new XQueryContext(pool), query);
            final var result = xquery.execute(broker, compiled, null);
            final StringBuilder out = new StringBuilder();
            for (int i = 0; i < result.getItemCount(); i++) {
                out.append(i == 0 ? "" : " ").append(result.itemAt(i).getStringValue());
            }
            return out.toString();
        }
    }

    /**
     * The query reached the index: the profiler saw a pre-selection of that type at the OPTIMIZED level. Counts alone
     * cannot show it, since the same answer comes from evaluating as written.
     */
    private static void assertPreSelected(final String type, final String query) throws Exception {
        execute(SYSTEM + "system:clear-trace(), system:enable-tracing(true(), false())");
        try {
            execute(query);
            final int uses = Integer.parseInt(execute(SYSTEM + "count(system:trace()//stats:index[@type = '" + type
                    + "'][@optimization-level = 'OPTIMIZED'])"));
            assertTrue(uses > 0, "no pre-selection of type " + type + " for: " + query);
        } finally {
            execute(SYSTEM + "system:enable-tracing(false())");
        }
    }

    /** The answer with the optimizer on, which has to be the one with the optimizer off. */
    private static String answer(final String query) throws Exception {
        final String module = "import module namespace ngram = 'http://exist-db.org/xquery/ngram'; ";
        final String off = execute("declare option exist:optimize 'enable=no'; " + module + query);
        final String on = execute(module + query);
        assertEquals(off, on, "optimizer on and off");
        return on;
    }

    private static String over(final String first, final String second, final String predicate) {
        return "for $c in ('/db/" + first + "', '/db/" + second + "') return $c || '=' || count(collection($c)//div["
                + predicate + "])";
    }

    @Test
    void containsThenCollectionWithoutTheIndex() throws Exception {
        assertEquals("/db/pn-indexed=2 /db/pn-plain=0", answer(over("pn-indexed", "pn-plain", "ngram:contains(., 'zeb')")));
        assertEquals("/db/pn-plain=0 /db/pn-indexed=2", answer(over("pn-plain", "pn-indexed", "ngram:contains(., 'zeb')")));
        assertPreSelected("ngram", "import module namespace ngram = 'http://exist-db.org/xquery/ngram'; " + over("pn-indexed", "pn-plain", "ngram:contains(., 'zeb')"));
    }

    @Test
    void containsOnAnAttributeThenCollectionWithoutTheIndex() throws Exception {
        assertEquals("/db/pn-indexed=2 /db/pn-plain=0", answer(over("pn-indexed", "pn-plain", "ngram:contains(@cat, 'zeb')")));
        assertPreSelected("ngram", "import module namespace ngram = 'http://exist-db.org/xquery/ngram'; " + over("pn-indexed", "pn-plain", "ngram:contains(@cat, 'zeb')"));
    }

    @Test
    void containsOverASequenceThatIsSometimesEmpty() throws Exception {
        final String module = "import module namespace ngram = 'http://exist-db.org/xquery/ngram'; ";
        final String path = "string-join(for $n in (0, 3, 0, 3) return string(count(collection('/db/pn-indexed')//div"
                + "[position() <= $n][ngram:contains(., 'zeb')])), '|')";
        assertEquals("0|2|0|2", answer(path));
        assertPreSelected("ngram", module + path);
    }
}
