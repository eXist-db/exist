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
package org.exist.xquery.modules.range;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.source.Source;
import org.exist.source.StringSource;
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
 * A predicate that used the range index must not leave what it found behind for the next evaluation of the same
 * expression, whatever that evaluation is over: another collection, with another index configuration, or the same
 * collection again after an evaluation that found nothing. Every query is also run with the optimizer off, which
 * gives the answer the optimized one must equal.
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/6846">Range-indexed predicate returns 0 hits on an unindexed
 * collection after the same predicate used the index</a>
 * @see <a href="https://github.com/eXist-db/exist/issues/7034">A path with two range-indexed predicates returns no
 * hits for a collection that has an index on the second predicate only</a>
 * @see <a href="https://github.com/eXist-db/exist/issues/7058">A general comparison in a predicate on a let-bound
 * sequence returns an empty sequence after an evaluation in which the sequence was empty</a>
 */
@Execution(ExecutionMode.SAME_THREAD)
public class PreSelectionAcrossEvaluationsTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String CONFIG_COLLECTION = "/db/system/config/db";

    private static final String RANGE = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <range>%s</range>
                </index>
            </collection>""";

    private static final String LEGACY = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index xmlns:xs="http://www.w3.org/2001/XMLSchema">%s</index>
            </collection>""";

    private static final String CATEGORIES = """
            <doc>
                <div cat="zebra">a</div>
                <div cat="pony">b</div>
                <div cat="zebra">c</div>
            </doc>""";

    private static final String PAIRS = """
            <doc>
                <div a="x" b="y">1</div>
                <div a="x" b="n">2</div>
                <div a="x" b="y">3</div>
            </doc>""";

    private static final String SYSTEM = """
            import module namespace system = 'http://exist-db.org/xquery/system';
            declare namespace stats = 'http://exist-db.org/xquery/profiling';
            """;

    @BeforeAll
    static void store() throws Exception {
        execute("xmldb:create-collection('/db/system', 'config'), xmldb:create-collection('/db/system/config', 'db')");
        collection("sp-cat-indexed", RANGE.formatted("<create qname=\"@cat\" type=\"xs:string\"/>"), CATEGORIES);
        collection("sp-cat-plain", null, CATEGORIES);
        collection("sp-pairs-both", RANGE.formatted("<create qname=\"@a\" type=\"xs:string\"/><create qname=\"@b\" "
                + "type=\"xs:string\"/>"), PAIRS);
        collection("sp-pairs-b-only", RANGE.formatted("<create qname=\"@b\" type=\"xs:string\"/>"), PAIRS);
        collection("sp-leg-indexed", LEGACY.formatted("<create qname=\"@cat\" type=\"xs:string\"/>"), CATEGORIES);
        collection("sp-leg-plain", null, CATEGORIES);
        collection("sp-leg-both", LEGACY.formatted("<create qname=\"@a\" type=\"xs:string\"/><create qname=\"@b\" "
                + "type=\"xs:string\"/>"), PAIRS);
        collection("sp-leg-b-only", LEGACY.formatted("<create qname=\"@b\" type=\"xs:string\"/>"), PAIRS);
        collection("sp-field", RANGE.formatted("<create qname=\"div\"><field name=\"f\" match=\"@cat\" "
                + "type=\"xs:string\"/></create>"), CATEGORIES);
        collection("sp-field-plain", null, CATEGORIES);
        collection("sp-upd", RANGE.formatted("<create qname=\"@cat\" type=\"xs:string\"/>"), CATEGORIES);
        collection("sp-upd-plain", null, CATEGORIES);
        execute("xmldb:create-collection('/db', 'sp-mix'), xmldb:create-collection('/db/sp-mix', 'idx'), "
                + "xmldb:create-collection('/db/sp-mix', 'plain')");
        execute("xmldb:create-collection('" + CONFIG_COLLECTION + "', 'sp-mix'), "
                + "xmldb:create-collection('" + CONFIG_COLLECTION + "/sp-mix', 'idx'), "
                + "xmldb:store('" + CONFIG_COLLECTION + "/sp-mix/idx', 'collection.xconf', "
                + RANGE.formatted("<create qname=\"@cat\" type=\"xs:string\"/>") + ")");
        execute("xmldb:store('/db/sp-mix/idx', 'doc.xml', " + CATEGORIES + "), xmldb:store('/db/sp-mix/plain', 'doc.xml', "
                + CATEGORIES + ")");
        execute("xmldb:create-collection('/db', 'sp-mix-pairs'), xmldb:create-collection('/db/sp-mix-pairs', 'both'), "
                + "xmldb:create-collection('/db/sp-mix-pairs', 'b-only')");
        execute("xmldb:create-collection('" + CONFIG_COLLECTION + "', 'sp-mix-pairs'), "
                + "xmldb:create-collection('" + CONFIG_COLLECTION + "/sp-mix-pairs', 'both'), "
                + "xmldb:create-collection('" + CONFIG_COLLECTION + "/sp-mix-pairs', 'b-only'), "
                + "xmldb:store('" + CONFIG_COLLECTION + "/sp-mix-pairs/both', 'collection.xconf', "
                + RANGE.formatted("<create qname=\"@a\" type=\"xs:string\"/><create qname=\"@b\" type=\"xs:string\"/>") + "), "
                + "xmldb:store('" + CONFIG_COLLECTION + "/sp-mix-pairs/b-only', 'collection.xconf', "
                + RANGE.formatted("<create qname=\"@b\" type=\"xs:string\"/>") + ")");
        execute("xmldb:store('/db/sp-mix-pairs/both', 'doc.xml', " + PAIRS + "), xmldb:store('/db/sp-mix-pairs/b-only', 'doc.xml', "
                + PAIRS + ")");
        execute("xmldb:store('/db', 'sp-target.xml', <t>sp-cat-indexed</t>)");
        collection("sp-items", RANGE.formatted("<create qname=\"t\" type=\"xs:string\"/>"), null);
        execute("for $i in 1 to 4 return xmldb:store('/db/sp-items', 'd' || $i || '.xml', "
                + "<wrap><item n=\"{$i}\"><t>hit</t></item></wrap>)");
    }

    private static void collection(final String name, final String config, final String document) throws Exception {
        if (config != null) {
            execute("xmldb:create-collection('" + CONFIG_COLLECTION + "', '" + name + "'), "
                    + "xmldb:store('" + CONFIG_COLLECTION + "/" + name + "', 'collection.xconf', " + config + ")");
        }
        execute("xmldb:create-collection('/db', '" + name + "')");
        if (document != null) {
            execute("xmldb:store('/db/" + name + "', 'doc.xml', " + document + ")");
        }
    }

    @AfterAll
    static void remove() throws Exception {
        for (final String name : new String[] {"sp-cat-indexed", "sp-cat-plain", "sp-pairs-both", "sp-pairs-b-only", "sp-items",
                "sp-leg-indexed", "sp-leg-plain", "sp-leg-both", "sp-leg-b-only", "sp-field", "sp-field-plain",
                "sp-upd", "sp-upd-plain", "sp-mix", "sp-mix-pairs"}) {
            execute("xmldb:remove('/db/" + name + "')");
            execute("if (xmldb:collection-available('" + CONFIG_COLLECTION + "/" + name + "')) "
                    + "then xmldb:remove('" + CONFIG_COLLECTION + "/" + name + "') else ()");
        }
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

    private static String over(final String first, final String second, final String path) {
        return "for $c in ('/db/" + first + "', '/db/" + second + "') return $c || '=' || count(collection($c)" + path + ")";
    }

    @Test
    void indexedThenUnindexedCollection() throws Exception {
        assertEquals("/db/sp-cat-indexed=2 /db/sp-cat-plain=2", answer(over("sp-cat-indexed", "sp-cat-plain", "//div[@cat = 'zebra']")));
        assertPreSelected("new-range", over("sp-cat-indexed", "sp-cat-plain", "//div[@cat = 'zebra']"));
    }

    @Test
    void unindexedThenIndexedCollection() throws Exception {
        assertEquals("/db/sp-cat-plain=2 /db/sp-cat-indexed=2", answer(over("sp-cat-plain", "sp-cat-indexed", "//div[@cat = 'zebra']")));
    }

    @Test
    void twoPredicatesThenAnIndexOnTheSecondOnly() throws Exception {
        assertEquals("/db/sp-pairs-both=2 /db/sp-pairs-b-only=2", answer(over("sp-pairs-both", "sp-pairs-b-only", "//div[@a = 'x'][@b = 'y']")));
        assertPreSelected("new-range", over("sp-pairs-both", "sp-pairs-b-only", "//div[@a = 'x'][@b = 'y']"));
    }

    @Test
    void twoComparisonsThenAnIndexOnTheSecondOnly() throws Exception {
        assertEquals("/db/sp-pairs-both=2 /db/sp-pairs-b-only=2", answer(over("sp-pairs-both", "sp-pairs-b-only", "//div[@a = 'x' and @b = 'y']")));
        assertPreSelected("new-range", over("sp-pairs-both", "sp-pairs-b-only", "//div[@a = 'x' and @b = 'y']"));
    }

    @Test
    void anIndexOnTheSecondOnlyThenTwoPredicates() throws Exception {
        assertEquals("/db/sp-pairs-b-only=2 /db/sp-pairs-both=2", answer(over("sp-pairs-b-only", "sp-pairs-both", "//div[@a = 'x'][@b = 'y']")));
    }

    /** {@code range:eq} has no fallback to evaluate as written, so it must not be pre-selected over nothing. */
    @Test
    void rangeFunctionOverASequenceThatIsSometimesEmpty() throws Exception {
        assertEquals("2", answer("""
                import module namespace range = 'http://exist-db.org/xquery/range';
                sum(for $w in collection('/db/sp-items')/wrap
                    let $d := $w/item[xs:integer(@n) mod 2 = 0]
                    return count($d[range:eq(t, 'hit')]))"""));
    }

    /** The same comparison again, over the nodes of a step that are none the first time, and over a sequence in a variable. */
    @Test
    void comparisonOverASequenceThatIsSometimesEmpty() throws Exception {
        final String path = "string-join(for $n in (0, 3, 0, 3) return string(count(collection('/db/sp-cat-indexed')//div"
                + "[position() <= $n][@cat = 'zebra'])), '|')";
        assertEquals("0|2|0|2", answer(path));
        assertPreSelected("new-range", path);
        assertEquals("2", answer("""
                sum(for $w in collection('/db/sp-items')/wrap
                    let $d := $w/item[xs:integer(@n) mod 2 = 0]
                    return count($d[t = 'hit']))"""));
    }

    /** The same expression is entered again while an evaluation of it is still open: the second predicate calls back. */
    @Test
    void expressionEnteredAgainWhileEvaluated() throws Exception {
        final String query = """
                declare function local:deep($n as xs:integer) as xs:boolean {
                    $n = 0 or exists(collection('/db/sp-cat-indexed')//div[@cat = 'zebra'][local:deep($n - 1)])
                };
                local:deep(3)""";
        assertEquals("true", answer(query));
        assertPreSelected("new-range", query);
    }

    @Test
    void legacyIndexedThenUnindexedCollection() throws Exception {
        assertEquals("/db/sp-leg-indexed=2 /db/sp-leg-plain=2", answer(over("sp-leg-indexed", "sp-leg-plain", "//div[@cat = 'zebra']")));
        assertPreSelected("range", over("sp-leg-indexed", "sp-leg-plain", "//div[@cat = 'zebra']"));
    }

    @Test
    void legacyTwoPredicatesThenAnIndexOnTheSecondOnly() throws Exception {
        assertEquals("/db/sp-leg-both=2 /db/sp-leg-b-only=2", answer(over("sp-leg-both", "sp-leg-b-only", "//div[@a = 'x'][@b = 'y']")));
        assertPreSelected("range", over("sp-leg-both", "sp-leg-b-only", "//div[@a = 'x'][@b = 'y']"));
    }

    @Test
    void matchesThenUnindexedCollection() throws Exception {
        assertEquals("/db/sp-cat-indexed=2 /db/sp-cat-plain=2", answer(over("sp-cat-indexed", "sp-cat-plain", "//div[matches(@cat, '^zeb')]")));
        assertPreSelected("new-range", over("sp-cat-indexed", "sp-cat-plain", "//div[matches(@cat, '^zeb')]"));
    }

    /** A comparison the optimizer answers from a field, over a collection that has the field and one that has not. */
    @Test
    void fieldComparisonThenCollectionWithoutIt() throws Exception {
        final String query = over("sp-field", "sp-field-plain", "//div[@cat = 'zebra']");
        assertEquals("/db/sp-field=2 /db/sp-field-plain=2", answer(query));
        assertPreSelected("new-range", query);
    }

    /** A field function has no fallback: a collection without the field has no hits, and must not be given the one before's. */
    @Test
    void fieldFunctionThenCollectionWithoutIt() throws Exception {
        assertEquals("/db/sp-field=2 /db/sp-field-plain=0", answer("""
                import module namespace range = 'http://exist-db.org/xquery/range';
                for $c in ('/db/sp-field', '/db/sp-field-plain')
                return $c || '=' || count(collection($c)//div[range:field-eq('f', 'zebra')])"""));
    }

    /** The same predicate, a position the first time, a boolean the next, a position again. */
    @Test
    void predicateThatIsAPositionAndThenABoolean() throws Exception {
        assertEquals("20|10,20,30|10|", answer("string-join(for $p in (2, true(), 1, false()) "
                + "return string-join((10, 20, 30)[$p], ','), '|')"));
        assertEquals("1|3|1", answer("string-join(for $p in (2, true(), 1) "
                + "return string(count(collection('/db/sp-cat-indexed')/doc/div[$p])), '|')"));
        assertEquals("3|1", answer("string-join(for $p in (<a/>, 2, ()) return string(count((1, 2, 3)[$p])), '|')"));
    }

    /** A where clause over the nodes of a for clause, over collections with and without the index, and over nothing. */
    @Test
    void whereClauseOverCollectionsAndOverNothing() throws Exception {
        assertEquals("2|2|2", answer("string-join(for $c in ('/db/sp-cat-indexed', '/db/sp-cat-plain', '/db/sp-cat-indexed') "
                + "return string(count(for $i in collection($c)//div where $i/@cat = 'zebra' return $i)), '|')"));
        assertEquals("0|2|0|2", answer("string-join(for $n in (0, 3, 0, 3) "
                + "return string(count(for $i in collection('/db/sp-cat-indexed')//div[position() <= $n] "
                + "where $i/@cat = 'zebra' return $i)), '|')"));
    }

    /** A comparison's cached result must not outlive an update of the document it was asked about. */
    @Test
    void comparisonAfterAnUpdateOfTheCollection() throws Exception {
        for (final String name : new String[] {"sp-upd", "sp-upd-plain"}) {
            final String query = "string-join(for $i in 1 to 3 "
                    + "let $n := count(collection('/db/" + name + "')//div[@cat = 'zebra']) "
                    + "let $u := update insert <div cat=\"zebra\">x</div> into doc('/db/" + name + "/doc.xml')/doc "
                    + "return string($n), '|')";
            execute("xmldb:store('/db/" + name + "', 'doc.xml', " + CATEGORIES + ")");
            assertEquals("2|3|4", execute("declare option exist:optimize 'enable=no'; " + query), name + ", optimizer off");
            execute("xmldb:store('/db/" + name + "', 'doc.xml', " + CATEGORIES + ")");
            assertEquals("2|3|4", execute(query), name + ", optimizer on");
        }
    }

    /** One compiled query, borrowed from the pool and returned, executed again over other data. */
    @Test
    void oneCompiledQueryExecutedAgainThroughThePool() throws Exception {
        final String query = "count(collection('/db/' || string(doc('/db/sp-target.xml')/t))//div[@cat = 'zebra'])";
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        execute(SYSTEM + "system:clear-trace(), system:enable-tracing(true(), false())");
        try {
            for (final String target : new String[] {"sp-cat-indexed", "sp-cat-plain", "sp-cat-indexed", "sp-cat-plain"}) {
                execute("xmldb:store('/db', 'sp-target.xml', <t>" + target + "</t>)");
                try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
                    final XQuery xquery = pool.getXQueryService();
                    CompiledXQuery compiled = pool.getXQueryPool().borrowCompiledXQuery(broker, source);
                    if (compiled == null) {
                        compiled = xquery.compile(new XQueryContext(pool), source);
                    }
                    try {
                        assertEquals("2", xquery.execute(broker, compiled, null).itemAt(0).getStringValue(), target);
                    } finally {
                        pool.getXQueryPool().returnCompiledXQuery(source, compiled);
                    }
                }
            }
            final int uses = Integer.parseInt(execute(SYSTEM + "count(system:trace()//stats:index[@type = 'new-range']"
                    + "[@optimization-level = 'OPTIMIZED'])"));
            assertTrue(uses > 0, "the compiled query never pre-selected from the index");
        } finally {
            execute(SYSTEM + "system:enable-tracing(false())");
        }
    }

    /** The same expression is entered again, over another collection, while the result of the earlier entry is consumed. */
    @Test
    void expressionEnteredAgainWhileTheEarlierResultIsConsumed() throws Exception {
        final String zebras = "declare function local:zebras($c as xs:string) { collection($c)//div[@cat = 'zebra'] }; ";
        final String indexedFirst = zebras + "string-join(for $x in local:zebras('/db/sp-cat-indexed') "
                + "return string(count(local:zebras('/db/sp-cat-plain'))), ',')";
        assertEquals("2,2", answer(indexedFirst));
        assertEquals("2,2", answer(zebras + "string-join(for $x in local:zebras('/db/sp-cat-plain') "
                + "return string(count(local:zebras('/db/sp-cat-indexed'))), ',')"));
        assertPreSelected("new-range", indexedFirst);
    }

    /**
     * The result of one evaluation is consumed after the same expression was evaluated again, over another collection:
     * what it holds must not depend on what the expression kept for the evaluation that made it.
     */
    @Test
    void resultConsumedAfterTheExpressionWasEvaluatedAgain() throws Exception {
        final String query = "declare function local:zebras($c as xs:string) { collection($c)//div[@cat = 'zebra'] }; "
                + "let $first := local:zebras('/db/sp-cat-indexed') "
                + "let $again := local:zebras('/db/sp-cat-plain') "
                + "return string-join((count($first), count($again), count($first/@cat), count($first/parent::*), "
                + "string-join($first/string(.), ''), string-join(subsequence($again, 2)/string(.), '')), '|')";
        assertEquals("2|2|2|1|ac|c", answer(query));
        assertPreSelected("new-range", query);
    }

    /** One context sequence, kept in a variable, asked again and again. A range comparison over a variable is not pre-selected from the index, but evaluated as written. */
    @Test
    void oneContextAskedAgain() throws Exception {
        assertEquals("2|2|2", answer("string-join(let $all := collection('/db/sp-cat-indexed')//div "
                + "return for $i in 1 to 3 return string(count($all[@cat = 'zebra'])), '|')"));
    }

    @Test
    void oneContextAskedForDifferentKeys() throws Exception {
        assertEquals("2|1|2|0", answer("string-join(let $all := collection('/db/sp-cat-indexed')//div "
                + "return for $k in ('zebra', 'pony', 'zebra', 'none') return string(count($all[@cat = $k])), '|')"));
    }

    /** Collections with different configurations under one parent, evaluated together. */
    @Test
    void collectionsWithDifferentConfigurationsEvaluatedTogether() throws Exception {
        assertEquals("4", answer("count(collection('/db/sp-mix')//div[@cat = 'zebra'])"));
        assertEquals("4", answer("count(collection('/db/sp-mix')//div[matches(@cat, '^zeb')])"));
        assertEquals("4", answer("count(collection('/db/sp-mix-pairs')//div[@a = 'x'][@b = 'y'])"));
    }
}
