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
package org.exist.validation;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.collections.Collection;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.util.PropertiesBuilder.propertiesBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documents that refer to different DTDs must each be parsed with their own DTD, whatever
 * order they are stored in, when the grammar cache is in use.
 *
 * See <a href="https://github.com/eXist-db/exist/issues/1757">#1757</a>.
 */
class DtdGrammarCacheTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(
            propertiesBuilder()
                    .set(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "auto")
                    .build(),
            true,
            true);

    private static final String COLLECTION = "/db/dtdgrammarcache";

    private static final String DOC_A = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE a PUBLIC "-//EXIST-TEST//DTD Entities//EN" "entity-test.dtd">
            <a>&alpha;</a>""";

    private static final String DOC_B = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE b PUBLIC "-//EXIST-TEST//DTD Entities B//EN" "entity-test-b.dtd">
            <b>&beta;</b>""";

    @BeforeAll
    static void createCollection() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(txn, XmldbURI.create(COLLECTION));
            collection.getPermissions().setMode(0777);
            broker.saveCollection(txn, collection);
            txn.commit();
        }
    }

    @Test
    void alternatingDtdsAreEachResolved() throws Exception {
        final String[] order = {"a", "b", "a", "b", "b", "a", "a", "b"};

        for (int i = 0; i < order.length; i++) {
            final boolean isA = "a".equals(order[i]);
            final String name = "doc" + i + ".xml";

            TestTools.insertDocumentToURL(
                    new UnsynchronizedByteArrayInputStream((isA ? DOC_A : DOC_B).getBytes(UTF_8)),
                    "xmldb:exist://" + COLLECTION + "/" + name);

            final Sequence result = TestTools.executeQuery(existEmbeddedServer.getBrokerPool(),
                    "string(doc('" + COLLECTION + "/" + name + "')/*)");
            assertEquals(isA ? "from-A" : "from-B", result.getStringValue(), "stored as " + name + " (" + order[i] + ")");
        }
    }

    @Test
    void dtdIsReusedAndEntitiesStayCorrect() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final GrammarPool grammarPool = (GrammarPool) pool.getConfiguration().getProperty(GrammarPool.GRAMMAR_POOL_ELEMENT);

        for (int i = 0; i < 3; i++) {
            store("reuse-a" + i + ".xml", DOC_A);
            store("reuse-b" + i + ".xml", DOC_B);
        }

        // both DTDs are cached, and the entities of each are still expanded for every document
        assertEquals(2, grammarPool.retrieveCachedDtdGrammars().length);
        assertEquals("from-A", query("string(doc('" + COLLECTION + "/reuse-a2.xml')/*)"));
        assertEquals("from-B", query("string(doc('" + COLLECTION + "/reuse-b2.xml')/*)"));
    }

    @Test
    void entityOfAnotherDtdIsNotDeclared() throws Exception {
        // load both DTDs into the cache first
        store("leak-a.xml", DOC_A);
        store("leak-b.xml", DOC_B);

        final String docWithForeignEntity = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE a PUBLIC "-//EXIST-TEST//DTD Entities//EN" "entity-test.dtd">
                <a>&beta;</a>""";

        final IOException e = assertThrows(IOException.class, () -> store("leak-foreign.xml", docWithForeignEntity));
        assertTrue(e.getMessage().contains("beta"), e.getMessage());
    }

    @Test
    void internalSubsetTakesPrecedenceOverCachedDtd() throws Exception {
        store("internal-a.xml", DOC_A);  // caches the DTD

        final String doc = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE a PUBLIC "-//EXIST-TEST//DTD Entities//EN" "entity-test.dtd" [
                    <!ENTITY alpha "internal">
                ]>
                <a>&alpha;</a>""";
        store("internal-override.xml", doc);

        assertEquals("internal", query("string(doc('" + COLLECTION + "/internal-override.xml')/*)"));
    }

    @Test
    void otherParsersAreNotGivenCachedDtds() throws Exception {
        // the DTD is in the cache after this
        store("parse-a.xml", DOC_A);

        // fn:parse-xml uses a pooled reader too, and must still see the entities of the DTD each time
        final String query = "string(fn:parse-xml('<!DOCTYPE a PUBLIC \"-//EXIST-TEST//DTD Entities//EN\" \"entity-test.dtd\"><a>&amp;alpha;</a>')/*)";
        assertEquals("from-A", query(query));
        assertEquals("from-A", query(query));
    }

    private static void store(final String name, final String content) throws IOException {
        TestTools.insertDocumentToURL(
                new UnsynchronizedByteArrayInputStream(content.getBytes(UTF_8)),
                "xmldb:exist://" + COLLECTION + "/" + name);
    }

    private static String query(final String query) throws Exception {
        return TestTools.executeQuery(existEmbeddedServer.getBrokerPool(), query).getStringValue();
    }
}
