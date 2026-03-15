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
import org.exist.util.ConfigurationHelper;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Integration test with ONNX model: full Lucene vector indexing + query using vector:embed.
 * <p>
 * Skips when the model is not available. Run with:
 * <pre>
 *   mvn verify -pl extensions/indexes/vector-it -Ponnx-model
 * </pre>
 * With dependencies built in reactor (like CI):
 * <pre>
 *   mvn verify -pl extensions/indexes/vector-it -am -Ponnx-model -DskipUnitTests=true
 * </pre>
 */
public class VectorSearchEmbeddingIT {

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String MODEL = "all-MiniLM-L6-v2";
    private static final String MODEL_PATH = "target/onnx-models/all-MiniLM-L6-v2";

    private static final String COLLECTION = "/db/lucene-test-vector-embedding-it";
    private static final String COLLECTION_NAME = "lucene-test-vector-embedding-it";
    private static final String CONFIG_COLLECTION = "/db/system/config/db/" + COLLECTION_NAME;

    private static final String XCONF =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
        + "<index xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
        + "<lucene><text qname=\"article\">"
        + "<field name=\"title\" expression=\"title\"/>"
        + "<vector-field name=\"embedding\" expression=\"title\" dimension=\"384\" similarity=\"cosine\""
        + " embedding=\"local\" model=\"" + MODEL + "\" model-path=\"" + MODEL_PATH + "\"/>"
        + "</text></lucene></index></collection>";

    private static final String INLINE_XCONF =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
        + "<index xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
        + "<lucene><text qname=\"article\">"
        + "<inline qname=\"bold\"/>"
        + "<field name=\"title\" expression=\"p\"/>"
        + "<vector-field name=\"embedding\" expression=\".\" dimension=\"384\" similarity=\"cosine\""
        + " embedding=\"local\" model=\"" + MODEL + "\" model-path=\"" + MODEL_PATH + "\"/>"
        + "</text></lucene></index></collection>";

    private static final String DATA =
        "<articles><article><title>Hello world</title></article>"
        + "<article><title>Machine learning</title></article>"
        + "<article><title>Quantum physics</title></article></articles>";

    private static final String INLINE_DATA =
        "<articles><article><p> this is a <bold>cute</bold> cat</p></article></articles>";

    @Before
    public void checkModel() {
        assumeTrue("ONNX model not found — run with -Ponnx-model", hasEmbeddingModel());
    }

    @Test
    public void embeddingLocalIndexedAndQueried() throws XPathException, PermissionDeniedException, EXistException {
        setupCollection(COLLECTION, COLLECTION_NAME, CONFIG_COLLECTION, XCONF, DATA);
        final Sequence result = executeQuery(
            "xquery version \"3.1\";\n"
            + "import module namespace ft=\"http://exist-db.org/xquery/lucene\";\n"
            + "import module namespace vector=\"http://exist-db.org/xquery/vector\";\n"
            + "let $v := vector:embed(\"Hello world\", \"" + MODEL + "\", \"" + MODEL_PATH + "\")\n"
            + "return count(collection(\"" + COLLECTION + "\")//article[ft:query-vector(., $v, 3)])");
        assertEquals(3, result.itemAt(0).toJavaObject(Integer.class).intValue());
    }

    @Test
    public void embedReturnsDimension384() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery(
            "xquery version \"3.1\";\n"
            + "import module namespace vector=\"http://exist-db.org/xquery/vector\";\n"
            + "count(vector:embed(\"Hello world\", \"" + MODEL + "\", \"" + MODEL_PATH + "\")?*)");
        assertEquals(384, result.itemAt(0).toJavaObject(Integer.class).intValue());
    }

    @Test
    public void embedBatchReturnsTwoArrays() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery(
            "xquery version \"3.1\";\n"
            + "import module namespace vector=\"http://exist-db.org/xquery/vector\";\n"
            + "let $b := vector:embed-batch((\"Hello world\", \"Machine learning\"), \"" + MODEL + "\", \"" + MODEL_PATH + "\")\n"
            + "return (count($b?*), count($b(1)?*), count($b(2)?*))");
        assertEquals(3, result.getItemCount());
        assertEquals(2, result.itemAt(0).toJavaObject(Integer.class).intValue());
        assertEquals(384, result.itemAt(1).toJavaObject(Integer.class).intValue());
        assertEquals(384, result.itemAt(2).toJavaObject(Integer.class).intValue());
    }

    @Test
    public void diagnosticsReportsModel() throws XPathException, PermissionDeniedException, EXistException {
        setupCollection(COLLECTION, COLLECTION_NAME, CONFIG_COLLECTION, XCONF, DATA);
        final Sequence result = executeQuery(
            "xquery version \"3.1\";\n"
            + "import module namespace vector=\"http://exist-db.org/xquery/vector\";\n"
            + "count(vector:diagnostics())");
        assertTrue("diagnostics should return at least one entry",
            result.itemAt(0).toJavaObject(Integer.class).intValue() >= 1);
    }

    @Test
    public void queryFieldVectorReturnsHits() throws XPathException, PermissionDeniedException, EXistException {
        setupCollection(COLLECTION, COLLECTION_NAME, CONFIG_COLLECTION, XCONF, DATA);
        final Sequence result = executeQuery(
            "xquery version \"3.1\";\n"
            + "import module namespace ft=\"http://exist-db.org/xquery/lucene\";\n"
            + "import module namespace vector=\"http://exist-db.org/xquery/vector\";\n"
            + "let $v := vector:embed(\"Hello world\", \"" + MODEL + "\", \"" + MODEL_PATH + "\")\n"
            + "return count(collection(\"" + COLLECTION + "\")//article[ft:query-field-vector(\"embedding\", $v, 2)])");
        assertEquals(2, result.itemAt(0).toJavaObject(Integer.class).intValue());
    }

    @Test
    public void kParameterLimitsResults() throws XPathException, PermissionDeniedException, EXistException {
        setupCollection(COLLECTION, COLLECTION_NAME, CONFIG_COLLECTION, XCONF, DATA);
        final Sequence result = executeQuery(
            "xquery version \"3.1\";\n"
            + "import module namespace ft=\"http://exist-db.org/xquery/lucene\";\n"
            + "import module namespace vector=\"http://exist-db.org/xquery/vector\";\n"
            + "let $v := vector:embed(\"Hello world\", \"" + MODEL + "\", \"" + MODEL_PATH + "\")\n"
            + "return count(collection(\"" + COLLECTION + "\")//article[ft:query-vector(., $v, 1)])");
        assertEquals(1, result.itemAt(0).toJavaObject(Integer.class).intValue());
    }

    @Test
    public void closerTextRanksHigher() throws XPathException, PermissionDeniedException, EXistException {
        setupCollection(COLLECTION, COLLECTION_NAME, CONFIG_COLLECTION, XCONF, DATA);
        final Sequence result = executeQuery(
            "xquery version \"3.1\";\n"
            + "import module namespace ft=\"http://exist-db.org/xquery/lucene\";\n"
            + "import module namespace vector=\"http://exist-db.org/xquery/vector\";\n"
            + "let $v := vector:embed(\"Hello world\", \"" + MODEL + "\", \"" + MODEL_PATH + "\")\n"
            + "let $hits := collection(\"" + COLLECTION + "\")//article[ft:query-vector(., $v, 3)]\n"
            + "return ($hits[1]/title/string(), ft:score($hits[1]) >= ft:score($hits[2]))");
        assertEquals("Hello world", result.itemAt(0).getStringValue());
        assertEquals("true", result.itemAt(1).getStringValue());
    }

    @Test
    public void embedEmptyTextErrors() throws EXistException, PermissionDeniedException {
        try {
            executeQuery(
                "xquery version \"3.1\";\n"
                + "import module namespace vector=\"http://exist-db.org/xquery/vector\";\n"
                + "vector:embed(\"\", \"" + MODEL + "\", \"" + MODEL_PATH + "\")");
            assertTrue("Expected XPathException for empty text", false);
        } catch (XPathException e) {
            assertTrue(e.getMessage().contains("empty result") || e.getMessage().contains("Embedding"));
        }
    }

    @Test
    public void embedBlankTextErrors() throws EXistException, PermissionDeniedException {
        try {
            executeQuery(
                "xquery version \"3.1\";\n"
                + "import module namespace vector=\"http://exist-db.org/xquery/vector\";\n"
                + "vector:embed(\"   \", \"" + MODEL + "\", \"" + MODEL_PATH + "\")");
            assertTrue("Expected XPathException for blank text", false);
        } catch (XPathException e) {
            assertTrue(e.getMessage().contains("empty result") || e.getMessage().contains("Embedding"));
        }
    }

    @Test
    public void embedBatchEmptySequenceReturnsEmptyArray() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery(
            "xquery version \"3.1\";\n"
            + "import module namespace vector=\"http://exist-db.org/xquery/vector\";\n"
            + "count(vector:embed-batch((), \"" + MODEL + "\", \"" + MODEL_PATH + "\")?*)");
        assertEquals(0, result.itemAt(0).toJavaObject(Integer.class).intValue());
    }

    @Test
    public void embedBatchSingleTextReturnsOneArray() throws XPathException, PermissionDeniedException, EXistException {
        final Sequence result = executeQuery(
            "xquery version \"3.1\";\n"
            + "import module namespace vector=\"http://exist-db.org/xquery/vector\";\n"
            + "let $b := vector:embed-batch(\"Hello\", \"" + MODEL + "\", \"" + MODEL_PATH + "\")\n"
            + "return (count($b?*), count($b(1)?*))");
        assertEquals(2, result.getItemCount());
        assertEquals(1, result.itemAt(0).toJavaObject(Integer.class).intValue());
        assertEquals(384, result.itemAt(1).toJavaObject(Integer.class).intValue());
    }

    @Test
    public void queryVectorWithKZeroDefaultsToTen() throws XPathException, PermissionDeniedException, EXistException {
        setupCollection(COLLECTION, COLLECTION_NAME, CONFIG_COLLECTION, XCONF, DATA);
        final Sequence result = executeQuery(
            "xquery version \"3.1\";\n"
            + "import module namespace ft=\"http://exist-db.org/xquery/lucene\";\n"
            + "import module namespace vector=\"http://exist-db.org/xquery/vector\";\n"
            + "let $v := vector:embed(\"Hello world\", \"" + MODEL + "\", \"" + MODEL_PATH + "\")\n"
            + "return count(collection(\"" + COLLECTION + "\")//article[ft:query-vector(., $v, 0)])");
        assertEquals("k=0 should default to 10, returning all 3 docs", 3, result.itemAt(0).toJavaObject(Integer.class).intValue());
    }

    @Test
    public void inlineElementsDoNotPolluteVectors() throws XPathException, PermissionDeniedException, EXistException {
        final String inlineCol = "/db/lucene-test-vector-inline-it";
        final String inlineColName = "lucene-test-vector-inline-it";
        final String inlineConfCol = "/db/system/config/db/" + inlineColName;
        setupCollection(inlineCol, inlineColName, inlineConfCol, INLINE_XCONF, INLINE_DATA);
        try {
            final Sequence result = executeQuery(
                "xquery version \"3.1\";\n"
                + "import module namespace ft=\"http://exist-db.org/xquery/lucene\";\n"
                + "import module namespace vector=\"http://exist-db.org/xquery/vector\";\n"
                + "let $v := vector:embed(\"cute cat\", \"" + MODEL + "\", \"" + MODEL_PATH + "\")\n"
                + "return count(collection(\"" + inlineCol + "\")//article[ft:query-vector(., $v, 1)])");
            assertEquals(1, result.itemAt(0).toJavaObject(Integer.class).intValue());
        } finally {
            cleanupCollection(inlineCol, inlineConfCol);
        }
    }

    @After
    public void cleanupCollections() {
        cleanupCollection(COLLECTION, CONFIG_COLLECTION);
    }

    private void cleanupCollection(final String col, final String confCol) {
        if (existEmbeddedServer.getBrokerPool() == null) {
            return;
        }
        try {
            executeQuery("xquery version \"3.1\";\n"
                + "import module namespace xmldb=\"http://exist-db.org/xquery/xmldb\";\n"
                + "try { xmldb:remove(\"" + col + "\") } catch * { () },\n"
                + "try { xmldb:remove(\"" + confCol + "\") } catch * { () }");
        } catch (final Exception e) {
            // ignore
        }
    }

    private void setupCollection(final String col, final String colName, final String confCol,
            final String xconf, final String data) throws EXistException, PermissionDeniedException, XPathException {
        final String dataEsc = data.replace("'", "''");
        final String xconfEsc = xconf.replace("'", "''");
        executeQuery(
            "xquery version \"3.1\";\n"
            + "import module namespace xmldb=\"http://exist-db.org/xquery/xmldb\";\n"
            + "xmldb:create-collection(\"/db/system\", \"config\"),\n"
            + "xmldb:create-collection(\"/db/system/config\", \"db\"),\n"
            + "xmldb:create-collection(\"/db/system/config/db\", \"" + colName + "\"),\n"
            + "xmldb:create-collection(\"/db\", \"" + colName + "\"),\n"
            + "xmldb:store(\"" + col + "\", \"test.xml\", parse-xml('" + dataEsc + "')),\n"
            + "xmldb:store(\"" + confCol + "\", \"collection.xconf\", parse-xml('" + xconfEsc + "')),\n"
            + "xmldb:reindex(\"" + col + "\")");
    }

    private Sequence executeQuery(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = brokerPool.getXQueryService();
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            return xquery.execute(broker, query, null);
        }
    }

    private boolean hasEmbeddingModel() {
        final Path base = ConfigurationHelper.getExistHome()
            .orElse(Paths.get(System.getProperty("user.dir", ".")));
        final Path modelDir = base.resolve("target/onnx-models/all-MiniLM-L6-v2");
        return Files.isRegularFile(modelDir.resolve("model.onnx"))
            && Files.isRegularFile(modelDir.resolve("tokenizer.json"));
    }
}

