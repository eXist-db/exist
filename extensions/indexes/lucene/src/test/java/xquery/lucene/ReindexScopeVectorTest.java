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
import org.exist.dom.persistent.NodeProxy;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.storage.vector.VectorStoreImpl;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.ConfigurationHelper;
import org.exist.xquery.XPathException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every scope of xmldb:reindex keeps a node's vectors and its full-text index: "fulltext" restores the vectors from
 * vector.dbx, "vector" computes them again, and both index the full text, as a node's Lucene document is written
 * whole.
 */
class ReindexScopeVectorTest {

    @RegisterExtension
    static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String MODEL = "target/onnx-models/all-MiniLM-L6-v2";

    /** A field the model embeds, so that its vectors are kept in vector.dbx. */
    private static final String EMBEDDED_XCONF = """
        <collection xmlns="http://exist-db.org/collection-config/1.0"><index><lucene><text qname="p">\
        <vector-field name="v" dimension="384" similarity="cosine" embedding="local" model="all-MiniLM-L6-v2" model-path="%s"/>\
        </text></lucene></index></collection>""".formatted(MODEL);

    /** A field whose vectors are given in the document, so that no model is needed. */
    private static final String GIVEN_XCONF = """
        <collection xmlns="http://exist-db.org/collection-config/1.0"><index><lucene><text qname="p">\
        <vector-field name="g" dimension="3" similarity="cosine" encoding="text" expression="@v"/>\
        </text></lucene></index></collection>""";

    @Test
    void embeddedVectorsSurviveEveryScope() throws Exception {
        assumeTrue(hasEmbeddingModel(), "ONNX model not found in " + MODEL + ": skipping");
        // the store's entries may count other tests' vectors: count this document's from here
        final long before = entries();
        store("scope-embedded", EMBEDDED_XCONF, "<d><p>alpha beta</p><p>gamma delta</p><p>epsilon</p></d>");
        final String stored = state("scope-embedded", entries() - before);
        assertEquals("entries=3 qname-vector=3 field-vector=3 fulltext=1", stored);
        for (final String scope : new String[] {"fulltext", "all", "vector", "fulltext"}) {
            execute("xmldb:reindex('/db/scope-embedded', '" + scope + "')");
            assertEquals(stored, state("scope-embedded", entries() - before), "after a \"" + scope + "\" reindex");
        }
    }

    @Test
    void givenVectorsAndFullTextSurviveEveryScope() throws Exception {
        store("scope-given", GIVEN_XCONF, "<d><p v='1 0 0'>alpha beta</p><p v='0 1 0'>gamma delta</p><p v='0 0 1'>epsilon</p></d>");
        final String query = """
            (count(collection('/db/scope-given')//p[ft:query-field-vector('g', [1, 0, 0], 3)]),
             count(collection('/db/scope-given')//p[ft:query(., 'alpha')]))""";
        assertEquals("3 1", execute("string-join(" + query + ", ' ')"));
        for (final String scope : new String[] {"fulltext", "all", "vector"}) {
            execute("xmldb:reindex('/db/scope-given', '" + scope + "')");
            assertEquals("3 1", execute("string-join(" + query + ", ' ')"), "after a \"" + scope + "\" reindex");
        }
    }

    /**
     * A "fulltext" reindex restores the stored vector rather than computing it: a vector stored for the node, which
     * no text embeds to, is the node's vector after the reindex.
     */
    @Test
    void aFulltextReindexRestoresTheStoredVector() throws Exception {
        assumeTrue(hasEmbeddingModel(), "ONNX model not found in " + MODEL + ": skipping");
        store("scope-restored", EMBEDDED_XCONF, "<d><p>alpha beta</p></d>");
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final NodeProxy node;
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            node = (NodeProxy) pool.getXQueryService().execute(broker, "collection('/db/scope-restored')//p", null).itemAt(0);
        }
        final ByteBuffer planted = ByteBuffer.allocate(384 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        planted.putFloat(0, 1);
        try (final Txn txn = pool.getTransactionManager().beginTransaction()) {
            pool.getVectorStore().put(txn, node.getOwnerDocument().getURI().toString(), node.getNodeId(), planted.array());
            pool.getTransactionManager().commit(txn);
        }
        execute("xmldb:reindex('/db/scope-restored', 'fulltext')");
        final String score = execute("""
            let $hit := collection('/db/scope-restored')//p[ft:query-field-vector('v', [1%s], 1)]
            return format-number(ft:score($hit), '0.######')""".formatted(", 0".repeat(383)));
        assertEquals("1", score, "the node's vector is the stored one");
    }

    private static void store(final String collection, final String xconf, final String document) throws Exception {
        execute("""
            (xmldb:create-collection('/db/system/config/db', '%s'),
             xmldb:store('/db/system/config/db/%s', 'collection.xconf', parse-xml("%s")),
             xmldb:create-collection('/db', '%s'),
             xmldb:store('/db/%s', 'd.xml', %s))
            """.formatted(collection, collection, xconf.replace("\"", "\"\""), collection, collection, document));
    }

    private static String state(final String collection, final long entries) throws Exception {
        final String vector = "vector:embed('alpha beta', 'all-MiniLM-L6-v2', '" + MODEL + "')";
        return "entries=" + entries + " " + execute("""
            import module namespace vector='http://exist-db.org/xquery/vector';
            'qname-vector=' || count(collection('/db/%s')//p[ft:query-vector(., %s, 3)])
            || ' field-vector=' || count(collection('/db/%s')//p[ft:query-field-vector('v', %s, 3)])
            || ' fulltext=' || count(collection('/db/%s')//p[ft:query(., 'alpha')])
            """.formatted(collection, vector, collection, vector, collection));
    }

    private static long entries() throws IOException {
        final VectorStoreImpl store = (VectorStoreImpl) existEmbeddedServer.getBrokerPool().getVectorStore();
        store.resetEntryCountCache();
        return store.getEntryCount();
    }

    private static String execute(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            return pool.getXQueryService().execute(broker, query, null).getStringValue();
        }
    }

    private static boolean hasEmbeddingModel() {
        final Path base = ConfigurationHelper.getExistHome().orElse(Path.of(System.getProperty("user.dir", ".")));
        final Path modelDir = base.resolve(MODEL);
        return Files.isRegularFile(modelDir.resolve("model.onnx")) && Files.isRegularFile(modelDir.resolve("tokenizer.json"));
    }
}
