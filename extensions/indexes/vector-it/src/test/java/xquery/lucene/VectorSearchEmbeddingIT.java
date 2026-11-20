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
package xquery.lucene;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration test with ONNX model: full Lucene vector indexing + query using vector:embed.
 * <p>
 * Skipped without the model, unless the vector profile is active, where a missing model fails (see {@link RequiresOnnxModel}). Run with:
 * <pre>
 *   mvn verify -pl extensions/indexes/vector-it -Pvector
 * </pre>
 * With dependencies built in reactor (like CI):
 * <pre>
 *   mvn verify -pl extensions/indexes/vector-it -am -Pvector -DskipUnitTests=true
 * </pre>
 */
@RequiresOnnxModel
public class VectorSearchEmbeddingIT {

    @RegisterExtension
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String MODEL = "all-MiniLM-L6-v2";
    private static final String MODEL_PATH = OnnxModelCondition.MODEL_PATH;

    private static final String COLLECTION = "/db/lucene-test-vector-embedding-it";
    private static final String COLLECTION_NAME = "lucene-test-vector-embedding-it";
    private static final String CONFIG_COLLECTION = "/db/system/config/db/" + COLLECTION_NAME;

    private static final String XCONF = """
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <lucene>
                    <text qname="article">
                        <field name="title" expression="title"/>
                        <vector-field name="embedding" expression="title" dimension="384" similarity="cosine"
                            embedding="local" model="%s" model-path="%s"/>
                    </text>
                </lucene>
            </index>
        </collection>""".formatted(MODEL, MODEL_PATH);

    private static final String INLINE_XCONF = """
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <lucene>
                    <text qname="article">
                        <inline qname="bold"/>
                        <field name="title" expression="p"/>
                        <vector-field name="embedding" expression="." dimension="384" similarity="cosine"
                            embedding="local" model="%s" model-path="%s"/>
                    </text>
                </lucene>
            </index>
        </collection>""".formatted(MODEL, MODEL_PATH);

    private static final String INLINE_NO_EXPRESSION_XCONF = """
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <lucene>
                    <text qname="article">
                        <inline qname="bold"/>
                        <field name="title" expression="p"/>
                        <vector-field name="embedding" dimension="384" similarity="cosine"
                            embedding="local" model="%s" model-path="%s"/>
                    </text>
                </lucene>
            </index>
        </collection>""".formatted(MODEL, MODEL_PATH);

    /** Nested elements with a vector field of the element's own text and no expression: every element has its own entry. */
    private static final String NESTED_XCONF = """
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <lucene>
                    <text qname="div">
                        <vector-field name="embedding" dimension="384" similarity="cosine"
                            embedding="local" model="%s" model-path="%s"/>
                    </text>
                </lucene>
            </index>
        </collection>""".formatted(MODEL, MODEL_PATH);

    private static final String NESTED_DATA = """
        <book>
            <div n="war">Wars and treaties of the twentieth century.
                <div n="ww1">The first world war began in 1914 and ended with the treaty of Versailles.</div>
                <div n="ww2">The second world war ended in 1945 with the surrender of Germany and Japan.</div>
                <div n="cold">The cold war was a standoff between the United States and the Soviet Union.</div>
            </div>
            <div n="food">Cooking and baking.
                <div n="bread">To bake bread, mix flour, water, yeast and salt, and let the dough rise.</div>
                <div n="soup">A good soup starts with onions, carrots and a slow simmer.</div>
            </div>
        </book>""";

    private static final String DATA = """
        <articles>
            <article><title>Hello world</title></article>
            <article><title>Machine learning</title></article>
            <article><title>Quantum physics</title></article>
        </articles>""";

    private static final String INLINE_DATA = """
        <articles>
            <article><p> this is a <bold>cute</bold> cat</p></article>
        </articles>""";

    @Test
    void embeddingLocalIndexedAndQueried() throws XPathException, PermissionDeniedException, EXistException {
        setupDefaultCollection();
        assertCount(queryVectorCount(COLLECTION, "Hello world", 3), 3);
    }

    /**
     * A node's score is the best similarity among its own entries, not a sum over the entries of the elements nested in
     * it: it is never above 1 and is the same whatever k is. A part of a book holds chapters, each with an entry of its
     * own, and the field has no expression, so the search looks among the documents.
     */
    @Test
    void nestedElementsScoreTheSameAtEveryK() throws XPathException, PermissionDeniedException, EXistException {
        setupCollection("/db/lucene-test-vector-nested-it", "lucene-test-vector-nested-it",
                "/db/system/config/db/lucene-test-vector-nested-it", NESTED_XCONF, NESTED_DATA);
        final String query = """
            xquery version "3.1";
            import module namespace ft="http://exist-db.org/xquery/lucene";
            import module namespace vector="http://exist-db.org/xquery/vector";
            let $v := %s
            let $scores := function($k) {
                map:merge(for $d in collection("/db/lucene-test-vector-nested-it")//div[ft:query-vector(., $v, $k)]
                    return map { string($d/@n): round(ft:score($d) * 10000) div 10000 })
            }
            let $few := $scores(3)
            let $all := $scores(20)
            return string-join((
                'few=' || count(map:keys($few)),
                'all=' || count(map:keys($all)),
                'above-1=' || count((map:keys($all) ! $all(.))[. gt 1]),
                'differing=' || count(map:keys($few)[$few(.) ne $all(.)])), ' ')""".formatted(embedExpr("the first world war"));
        assertEquals("few=3 all=7 above-1=0 differing=0", executeQuery(query).getStringValue());
    }

    @Test
    void embedReturnsDimension384() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery("""
            xquery version "3.1";
            import module namespace vector="http://exist-db.org/xquery/vector";
            count(vector:embed("Hello world", "%s", "%s")?*)""".formatted(MODEL, MODEL_PATH));
        assertEquals(384, result.itemAt(0).toJavaObject(Integer.class).intValue());
    }

    /**
     * The output tensor is owned by the ONNX {@code Result}; closing it a second time makes
     * onnxruntime log "Closing an already closed tensor." at WARN on every embedding call.
     */
    @Test
    void embedDoesNotCloseTensorsTwice() throws XPathException, PermissionDeniedException, EXistException {
        final List<String> warnings = new CopyOnWriteArrayList<>();
        // onnxruntime logs through java.util.logging; keep a strong reference so the logger
        // (and the handler attached to it) is not garbage collected mid-test.
        final Logger onnxLogger = Logger.getLogger("ai.onnxruntime");
        final Handler handler = new Handler() {
            @Override
            public void publish(final LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
                // nothing is buffered: publish() records straight into the list
            }

            @Override
            public void close() {
                // nothing to release: the list is local to the test
            }
        };
        onnxLogger.addHandler(handler);
        try {
            executeQuery("""
                xquery version "3.1";
                import module namespace vector="http://exist-db.org/xquery/vector";
                (vector:embed("Hello world", "%1$s", "%2$s"),
                 vector:embed-batch(("Machine learning", "Quantum physics"), "%1$s", "%2$s"))""".formatted(MODEL, MODEL_PATH));
        } finally {
            onnxLogger.removeHandler(handler);
        }
        assertTrue(warnings.isEmpty(), "onnxruntime logged: " + warnings);
    }

    @Test
    void embedBatchReturnsTwoArrays() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery("""
            xquery version "3.1";
            import module namespace vector="http://exist-db.org/xquery/vector";
            let $b := vector:embed-batch(("Hello world", "Machine learning"), "%s", "%s")
            return (count($b?*), count($b(1)?*), count($b(2)?*))""".formatted(MODEL, MODEL_PATH));
        assertEquals(3, result.getItemCount());
        assertEquals(2, result.itemAt(0).toJavaObject(Integer.class).intValue());
        assertEquals(384, result.itemAt(1).toJavaObject(Integer.class).intValue());
        assertEquals(384, result.itemAt(2).toJavaObject(Integer.class).intValue());
    }

    @Test
    void diagnosticsReportsModel() throws XPathException, PermissionDeniedException, EXistException {
        setupDefaultCollection();
        final Sequence result = executeQuery("""
            xquery version "3.1";
            import module namespace vector="http://exist-db.org/xquery/vector";
            count(vector:diagnostics())""");
        assertTrue(result.itemAt(0).toJavaObject(Integer.class).intValue() >= 1,
            "diagnostics should return at least one entry");
    }

    @Test
    void queryFieldVectorReturnsHits() throws XPathException, PermissionDeniedException, EXistException {
        setupDefaultCollection();
        assertCount(queryFieldVectorCount(COLLECTION, "Hello world", 2), 2);
    }

    @Test
    void kParameterLimitsResults() throws XPathException, PermissionDeniedException, EXistException {
        setupDefaultCollection();
        assertCount(queryVectorCount(COLLECTION, "Hello world", 1), 1);
    }

    @Test
    void closerTextRanksHigher() throws XPathException, PermissionDeniedException, EXistException {
        setupDefaultCollection();
        final Sequence result = executeQuery("""
            xquery version "3.1";
            import module namespace ft="http://exist-db.org/xquery/lucene";
            import module namespace vector="http://exist-db.org/xquery/vector";
            let $v := %s
            let $hits := collection("%s")//article[ft:query-vector(., $v, 3)]
            return ($hits[1]/title/string(), ft:score($hits[1]) >= ft:score($hits[2]))""".formatted(embedExpr("Hello world"), COLLECTION));
        assertEquals("Hello world", result.itemAt(0).getStringValue());
        assertEquals("true", result.itemAt(1).getStringValue());
    }

    @Test
    void embedEmptyTextErrors() throws EXistException, PermissionDeniedException {
        try {
            executeQuery("""
                xquery version "3.1";
                import module namespace vector="http://exist-db.org/xquery/vector";
                vector:embed("", "%s", "%s")""".formatted(MODEL, MODEL_PATH));
            fail("Expected XPathException for empty text");
        } catch (XPathException e) {
            assertEquals("vector:EXVECTOR0002", e.getErrorCode().getErrorQName().getStringValue());
        }
    }

    @Test
    void embedBlankTextErrors() throws EXistException, PermissionDeniedException {
        try {
            executeQuery("""
                xquery version "3.1";
                import module namespace vector="http://exist-db.org/xquery/vector";
                vector:embed("   ", "%s", "%s")""".formatted(MODEL, MODEL_PATH));
            fail("Expected XPathException for blank text");
        } catch (XPathException e) {
            assertEquals("vector:EXVECTOR0002", e.getErrorCode().getErrorQName().getStringValue());
        }
    }

    @Test
    void embedBatchEmptySequenceReturnsEmptyArray() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery("""
            xquery version "3.1";
            import module namespace vector="http://exist-db.org/xquery/vector";
            count(vector:embed-batch((), "%s", "%s")?*)""".formatted(MODEL, MODEL_PATH));
        assertEquals(0, result.itemAt(0).toJavaObject(Integer.class).intValue());
    }

    @Test
    void embedBatchSingleTextReturnsOneArray() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery("""
            xquery version "3.1";
            import module namespace vector="http://exist-db.org/xquery/vector";
            let $b := vector:embed-batch("Hello", "%s", "%s")
            return (count($b?*), count($b(1)?*))""".formatted(MODEL, MODEL_PATH));
        assertEquals(2, result.getItemCount());
        assertEquals(1, result.itemAt(0).toJavaObject(Integer.class).intValue());
        assertEquals(384, result.itemAt(1).toJavaObject(Integer.class).intValue());
    }

    @Test
    void queryVectorWithKZeroDefaultsToTen() throws XPathException, PermissionDeniedException, EXistException {
        setupDefaultCollection();
        assertCount("k=0 should default to 10, returning all 3 docs", queryVectorCount(COLLECTION, "Hello world", 0), 3);
    }

    @Test
    void inlineElementsDoNotPolluteVectors() throws XPathException, PermissionDeniedException, EXistException {
        assertInlineVectorHit("lucene-test-vector-inline-it", INLINE_XCONF);
    }

    @Test
    void inlineElementsWithoutExpressionDoNotPolluteVectors() throws XPathException, PermissionDeniedException, EXistException {
        assertInlineVectorHit("lucene-test-vector-inline-no-expr-it", INLINE_NO_EXPRESSION_XCONF);
    }

    private void assertInlineVectorHit(final String colName, final String xconf)
            throws XPathException, PermissionDeniedException, EXistException {
        final String inlineCol = "/db/" + colName;
        final String inlineConfCol = "/db/system/config/db/" + colName;
        setupCollection(inlineCol, colName, inlineConfCol, xconf, INLINE_DATA);
        try {
            assertCount(queryVectorCount(inlineCol, "cute cat", 1), 1);
        } finally {
            cleanupCollection(inlineCol, inlineConfCol);
        }
    }

    @AfterEach
    void cleanupCollections() {
        cleanupCollection(COLLECTION, CONFIG_COLLECTION);
    }

    private void cleanupCollection(final String col, final String confCol) {
        if (existEmbeddedServer.getBrokerPool() == null) {
            return;
        }
        try {
            executeQuery("""
                xquery version "3.1";
                import module namespace xmldb="http://exist-db.org/xquery/xmldb";
                try { xmldb:remove("%s") } catch * { () },
                try { xmldb:remove("%s") } catch * { () }""".formatted(col, confCol));
        } catch (final Exception e) {
            // ignore
        }
    }

    private void setupCollection(final String col, final String colName, final String confCol,
            final String xconf, final String data) throws EXistException, PermissionDeniedException, XPathException {
        final String dataEsc = data.replace("'", "''");
        final String xconfEsc = xconf.replace("'", "''");
        executeQuery("""
            xquery version "3.1";
            import module namespace xmldb="http://exist-db.org/xquery/xmldb";
            xmldb:create-collection("/db/system", "config"),
            xmldb:create-collection("/db/system/config", "db"),
            xmldb:create-collection("/db/system/config/db", "%1$s"),
            xmldb:create-collection("/db", "%1$s"),
            xmldb:store("%2$s", "test.xml", parse-xml('%3$s')),
            xmldb:store("%4$s", "collection.xconf", parse-xml('%5$s')),
            xmldb:reindex("%2$s")""".formatted(colName, col, dataEsc, confCol, xconfEsc));
    }

    private void setupDefaultCollection() throws EXistException, PermissionDeniedException, XPathException {
        setupCollection(COLLECTION, COLLECTION_NAME, CONFIG_COLLECTION, XCONF, DATA);
    }

    private String embedExpr(final String text) {
        return "vector:embed(\"" + text + "\", \"" + MODEL + "\", \"" + MODEL_PATH + "\")";
    }

    private String queryVectorCount(final String collection, final String text, final int k) {
        return """
            xquery version "3.1";
            import module namespace ft="http://exist-db.org/xquery/lucene";
            import module namespace vector="http://exist-db.org/xquery/vector";
            let $v := %s
            return count(collection("%s")//article[ft:query-vector(., $v, %d)])""".formatted(embedExpr(text), collection, k);
    }

    private String queryFieldVectorCount(final String collection, final String text, final int k) {
        return """
            xquery version "3.1";
            import module namespace ft="http://exist-db.org/xquery/lucene";
            import module namespace vector="http://exist-db.org/xquery/vector";
            let $v := %s
            return count(collection("%s")//article[ft:query-field-vector("embedding", $v, %d)])""".formatted(embedExpr(text), collection, k);
    }

    private void assertCount(final String query, final int expected) throws EXistException, PermissionDeniedException, XPathException {
        final Sequence result = executeQuery(query);
        assertEquals(expected, result.itemAt(0).toJavaObject(Integer.class).intValue());
    }

    private void assertCount(final String message, final String query, final int expected) throws EXistException, PermissionDeniedException, XPathException {
        final Sequence result = executeQuery(query);
        assertEquals(expected, result.itemAt(0).toJavaObject(Integer.class).intValue(), message);
    }

    private Sequence executeQuery(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = brokerPool.getXQueryService();
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            return xquery.execute(broker, query, null);
        }
    }
}

