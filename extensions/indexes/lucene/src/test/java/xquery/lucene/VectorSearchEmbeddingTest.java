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

import org.exist.util.ConfigurationHelper;

import java.util.Optional;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

/**
 * Embedding test with ONNX model. Skips when model is not available (e.g. CI).
 * Pattern: same as {@link org.expath.exist.HttpClientTest} with assumeTrue for optional resources.
 *
 * Run with model: mvn test -Dtest=VectorSearchEmbeddingTest -pl extensions/indexes/lucene -Ponnx-model
 * (profile sets exist.home to project.basedir and downloads model to target/onnx-models/all-MiniLM-L6-v2).
 */
public class VectorSearchEmbeddingTest {

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String COLLECTION = "/db/lucene-test-vector-embedding-local";
    private static final String COLLECTION_NAME = "lucene-test-vector-embedding-local";
    private static final String XCONF =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
        + "<index xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
        + "<lucene><text qname=\"article\">"
        + "<field name=\"title\" expression=\"title\"/>"
        + "<vector-field name=\"embedding\" expression=\"title\" dimension=\"384\" similarity=\"cosine\""
        + " embedding=\"local\" model=\"all-MiniLM-L6-v2\" model-path=\"target/onnx-models/all-MiniLM-L6-v2\"/>"
        + "</text></lucene></index></collection>";
    private static final String DATA =
        "<articles><article><title>Hello world</title></article>"
        + "<article><title>Machine learning</title></article></articles>";

    @Test
    public void embeddingLocalIndexedAndQueried() throws XPathException, PermissionDeniedException, EXistException {
        assumeTrue("ONNX model not found: skipping embedding test. Download to target/onnx-models/all-MiniLM-L6-v2, run with -Dexist.home=<repo-root>",
            hasEmbeddingModel());

        final String dataEsc = DATA.replace("'", "''");
        final String xconfEsc = XCONF.replace("'", "''");
        final String query =
            "xquery version \"3.1\";\n"
            + "import module namespace ft=\"http://exist-db.org/xquery/lucene\";\n"
            + "import module namespace vector=\"http://exist-db.org/xquery/vector\";\n"
            + "let $_ := (xmldb:create-collection(\"/db/system\", \"config\"),\n"
            + "           xmldb:create-collection(\"/db/system/config\", \"db\"),\n"
            + "           xmldb:create-collection(\"/db/system/config/db\", \"" + COLLECTION_NAME + "\"),\n"
            + "           xmldb:create-collection(\"/db\", \"" + COLLECTION_NAME + "\"),\n"
            + "           xmldb:store(\"" + COLLECTION + "\", \"test.xml\", parse-xml('" + dataEsc + "')),\n"
            + "           xmldb:store(\"/db/system/config/db/" + COLLECTION_NAME + "\", \"collection.xconf\", parse-xml('" + xconfEsc + "')),\n"
            + "           xmldb:reindex(\"" + COLLECTION + "\"))\n"
            + "let $query-vec := vector:embed(\"Hello world\", \"all-MiniLM-L6-v2\", \"target/onnx-models/all-MiniLM-L6-v2\")\n"
            + "return count(collection(\"" + COLLECTION + "\")//article[ft:query-vector(., $query-vec, 2)])";

        final Sequence result = executeQuery(query);
        assertEquals(1, result.getItemCount());
        assertEquals(Integer.valueOf(2), result.itemAt(0).toJavaObject(Integer.class));
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
