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
 * A full-text or vector query that used the index must not leave what it found for the next evaluation of the same
 * expression: over a collection without the index there are no hits, however many the collection before had. Every
 * query is also run with the optimizer off, which gives the answer the optimized one must equal.
 */
@Execution(ExecutionMode.SAME_THREAD)
public class PreSelectionAcrossEvaluationsTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String CONFIG_COLLECTION = "/db/system/config/db";

    private static final String XCONF = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index>
                    <lucene>
                        <text qname="div">
                            <field name="ttl" expression="*:t"/>
                            <vector-field name="vec" expression="*:embedding" dimension="3" similarity="cosine" encoding="text"/>
                        </text>
                        <!-- a field without an expression, the only kind a vector query pre-selects -->
                        <text qname="fv">
                            <vector-field name="fvec" dimension="3" similarity="cosine" encoding="text"/>
                        </text>
                    </lucene>
                </index>
            </collection>""";

    private static final String DOCUMENT = """
            <doc>
                <div>zebra<t>zebra</t><embedding>1 0 0</embedding></div>
                <div>pony<t>pony</t><embedding>0 1 0</embedding></div>
                <div>zebra<t>zebra</t><embedding>1 0.1 0</embedding></div>
                <fv>1 0 0</fv><fv>0 1 0</fv><fv>1 0.1 0</fv>
            </doc>""";

    private static final String SYSTEM = """
            import module namespace system = 'http://exist-db.org/xquery/system';
            declare namespace stats = 'http://exist-db.org/xquery/profiling';
            """;

    @BeforeAll
    static void store() throws Exception {
        execute("xmldb:create-collection('/db/system', 'config'), xmldb:create-collection('/db/system/config', 'db')");
        execute("xmldb:create-collection('" + CONFIG_COLLECTION + "', 'pl-indexed'), "
                + "xmldb:store('" + CONFIG_COLLECTION + "/pl-indexed', 'collection.xconf', " + XCONF + ")");
        execute("xmldb:create-collection('/db', 'pl-indexed'), xmldb:store('/db/pl-indexed', 'doc.xml', " + DOCUMENT + ")");
        execute("xmldb:create-collection('/db', 'pl-plain'), xmldb:store('/db/pl-plain', 'doc.xml', " + DOCUMENT + ")");
    }

    @AfterAll
    static void remove() throws Exception {
        execute("xmldb:remove('/db/pl-indexed'), xmldb:remove('/db/pl-plain'), xmldb:remove('" + CONFIG_COLLECTION + "/pl-indexed')");
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
        final String off = execute("declare option exist:optimize 'enable=no'; " + query);
        final String on = execute(query);
        assertEquals(off, on, "optimizer on and off");
        return on;
    }

    private static String over(final String first, final String second, final String predicate) {
        return over(first, second, "div", predicate);
    }

    private static String over(final String first, final String second, final String element, final String predicate) {
        return "for $c in ('/db/" + first + "', '/db/" + second + "') return $c || '=' || count(collection($c)//" + element + "["
                + predicate + "])";
    }

    @Test
    void queryThenCollectionWithoutTheIndex() throws Exception {
        assertEquals("/db/pl-indexed=2 /db/pl-plain=0", answer(over("pl-indexed", "pl-plain", "ft:query(., 'zebra')")));
        assertEquals("/db/pl-plain=0 /db/pl-indexed=2", answer(over("pl-plain", "pl-indexed", "ft:query(., 'zebra')")));
        assertPreSelected("lucene", over("pl-indexed", "pl-plain", "ft:query(., 'zebra')"));
    }

    @Test
    void fieldQueryThenCollectionWithoutTheIndex() throws Exception {
        assertEquals("/db/pl-indexed=2 /db/pl-plain=0", answer(over("pl-indexed", "pl-plain", "ft:query-field('ttl', 'zebra')")));
        assertEquals("/db/pl-plain=0 /db/pl-indexed=2", answer(over("pl-plain", "pl-indexed", "ft:query-field('ttl', 'zebra')")));
        assertPreSelected("lucene", over("pl-indexed", "pl-plain", "ft:query-field('ttl', 'zebra')"));
    }

    @Test
    void vectorQueryThenCollectionWithoutTheIndex() throws Exception {
        assertEquals("/db/pl-indexed=2 /db/pl-plain=0", answer(over("pl-indexed", "pl-plain", "fv", "ft:query-vector(., [1, 0, 0], 2)")));
        assertEquals("/db/pl-plain=0 /db/pl-indexed=2", answer(over("pl-plain", "pl-indexed", "fv", "ft:query-vector(., [1, 0, 0], 2)")));
        assertPreSelected("lucene-vector", over("pl-indexed", "pl-plain", "fv", "ft:query-vector(., [1, 0, 0], 2)"));
    }

    @Test
    void fieldVectorQueryThenCollectionWithoutTheIndex() throws Exception {
        assertEquals("/db/pl-indexed=2 /db/pl-plain=0", answer(over("pl-indexed", "pl-plain", "fv", "ft:query-field-vector('fvec', [1, 0, 0], 2)")));
        assertEquals("/db/pl-plain=0 /db/pl-indexed=2", answer(over("pl-plain", "pl-indexed", "fv", "ft:query-field-vector('fvec', [1, 0, 0], 2)")));
        assertPreSelected("lucene-vector", over("pl-indexed", "pl-plain", "fv", "ft:query-field-vector('fvec', [1, 0, 0], 2)"));
    }

    /** The same query again, over the nodes of a step that are none the first time, and over a sequence kept in a variable. */
    @Test
    void queryOverASequenceThatIsSometimesEmpty() throws Exception {
        final String path = "string-join(for $n in (0, 3, 0, 3) return string(count(collection('/db/pl-indexed')//div"
                + "[position() <= $n][ft:query(., 'zebra')])), '|')";
        assertEquals("0|2|0|2", answer(path));
        assertPreSelected("lucene", path);
        // kept in a variable ft:query is not pre-selected from the index, and the answer is the same
        assertEquals("2", answer("""
                sum(for $d in collection('/db/pl-indexed')/doc
                    for $n in (0, 3, 0, 3)
                    let $s := $d/div[position() <= $n]
                    return count($s[ft:query(., 'zebra')])) div 2"""));
    }
}
