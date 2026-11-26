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
import org.apache.xerces.impl.dtd.DTDGrammar;
import org.apache.xerces.xni.grammars.Grammar;
import org.exist.collections.Collection;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.util.PropertiesBuilder.propertiesBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grammar cache holds the grammars of external DTDs and nothing else: a document that has an internal subset on top
 * of its DTD adds nothing of its own to it, and the DTD is still cached when every document of a corpus has one.
 *
 * See <a href="https://github.com/eXist-db/exist/issues/1757">#1757</a>.
 */
class DtdGrammarSeedingTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(
            propertiesBuilder()
                    .set(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "no")
                    .build(),
            true,
            true);

    private static final String COLLECTION = "/db/dtdgrammarseeding";

    private static final String ENTITIES_PUBLIC_ID = "-//EXIST-TEST//DTD Entities//EN";
    private static final String ENTITIES_B_PUBLIC_ID = "-//EXIST-TEST//DTD Entities B//EN";

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

    /** Every document has an internal subset: there is no document without one for the DTD to be cached from. */
    @Test
    void aCorpusOfDocumentsWithAnInternalSubsetCachesTheDtdAlone() throws Exception {
        assertNull(cachedGrammar(ENTITIES_PUBLIC_ID), "nothing is cached before the first document");

        store("first.xml", "<?xml version=\"1.0\"?><!DOCTYPE a PUBLIC \"" + ENTITIES_PUBLIC_ID + "\" \"entity-test.dtd\""
                + " [<!ENTITY seedone \"one\">]><a>&alpha; &seedone;</a>");
        assertEquals("from-A one", query("string(doc('" + COLLECTION + "/first.xml')/*)"));

        final DTDGrammar dtd = cachedGrammar(ENTITIES_PUBLIC_ID);
        assertNotNull(dtd, "the DTD is cached, although its first document has an internal subset");
        assertTrue(dtd.getEntityDeclIndex("alpha") >= 0, "the entities of the DTD");
        assertEquals(-1, dtd.getEntityDeclIndex("seedone"), "and none of the internal subset of the document");

        store("second.xml", "<?xml version=\"1.0\"?><!DOCTYPE a PUBLIC \"" + ENTITIES_PUBLIC_ID + "\" \"entity-test.dtd\""
                + " [<!ENTITY seedtwo \"two\">]><a>&alpha; &seedtwo;</a>");
        assertEquals("from-A two", query("string(doc('" + COLLECTION + "/second.xml')/*)"));
        assertEquals(-1, cachedGrammar(ENTITIES_PUBLIC_ID).getEntityDeclIndex("seedtwo"), "the second document adds nothing either");
    }

    /** A parse that does not go through the indexer, here of the DTD that no document of this class has used before. */
    @Test
    void aParseOutsideTheIndexerCachesNoGrammar() throws Exception {
        assertNull(cachedGrammar(ENTITIES_B_PUBLIC_ID));

        final String parsed = query("string(fn:parse-xml('<!DOCTYPE b PUBLIC \"" + ENTITIES_B_PUBLIC_ID + "\" \"entity-test-b.dtd\""
                + " [<!ENTITY parsed \"p\">]><b>&amp;beta;&amp;parsed;</b>')/*)");
        assertEquals("from-Bp", parsed, "the parse itself works");
        assertNull(cachedGrammar(ENTITIES_B_PUBLIC_ID), "but it leaves no grammar for the next document to take");

        // control: a stored document does cache the DTD
        store("b.xml", "<?xml version=\"1.0\"?><!DOCTYPE b PUBLIC \"" + ENTITIES_B_PUBLIC_ID + "\" \"entity-test-b.dtd\"><b>&beta;</b>");
        assertNotNull(cachedGrammar(ENTITIES_B_PUBLIC_ID));
    }

    /**
     * A DTD that declares a notation or an unparsed entity is never cached, so the document's own parse is the only one
     * that reads it. The parse that builds a grammar is asked for once, finds the DTD not reusable and is not asked
     * for again, until the grammar cache is cleared.
     */
    @Test
    void aDtdThatIsNeverCachedIsNotReadAnExtraTimeForEveryDocument() throws Exception {
        final String validating = createValidatingCollection("/db/dtdgrammarseeding-notreusable");
        final AtomicInteger requests = new AtomicInteger();
        final HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/", exchange -> {
            requests.incrementAndGet();
            final byte[] body = ("<!ELEMENT a ANY><!ATTLIST a eid ID #IMPLIED>"
                    + "<!NOTATION gif SYSTEM \"image/gif\"><!ENTITY logo SYSTEM \"logo.gif\" NDATA gif>").getBytes(UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        http.start();
        try {
            final String dtd = "http://127.0.0.1:" + http.getAddress().getPort() + "/notreusable.dtd";
            final int[] perDocument = new int[6];
            for (int i = 0; i < perDocument.length; i++) {
                if (i == 4) {
                    query("import module namespace validation = 'http://exist-db.org/xquery/validation'; validation:clear-grammar-cache()");
                }
                perDocument[i] = storeValidated(validating, dtd, "n" + i, requests);
                assertEquals("1", query("count(id('n" + i + "', doc('" + validating + "/n" + i + ".xml')))"), "the DTD is applied to document " + i);
            }
            assertTrue(perDocument[0] <= 3, "the first document asks for the grammar: " + perDocument[0]);
            // the two passes of the store read the DTD, as they do without the parse that builds a grammar
            assertEquals(2, perDocument[1], "document 1: its own two reads and no parse for a grammar");
            assertEquals(2, perDocument[2], "document 2");
            assertEquals(2, perDocument[3], "document 3");
            assertTrue(perDocument[4] > 2, "after the cache was cleared the grammar is asked for once more: " + perDocument[4]);
            assertEquals(2, perDocument[5], "and not again");
        } finally {
            http.stop(0);
        }
    }

    private String createValidatingCollection(final String path) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(txn, XmldbURI.create(path));
            collection.getPermissions().setMode(0777);
            broker.saveCollection(txn, collection);
            pool.getConfigurationManager().addConfiguration(txn, broker, collection,
                    "<collection xmlns=\"http://exist-db.org/collection-config/1.0\"><validation mode=\"yes\"/></collection>");
            txn.commit();
        }
        return path;
    }

    /** Stores a document that names the DTD at the URL, and returns how often the DTD was requested meanwhile. */
    private static int storeValidated(final String collection, final String dtd, final String name, final AtomicInteger requests) throws IOException {
        final int before = requests.get();
        TestTools.insertDocumentToURL(
                new UnsynchronizedByteArrayInputStream(("<?xml version=\"1.0\"?><!DOCTYPE a SYSTEM \"" + dtd + "\"><a eid=\"" + name + "\"/>").getBytes(UTF_8)),
                "xmldb:exist://" + collection + "/" + name + ".xml");
        return requests.get() - before;
    }

    /**
     * In a collection that does not validate (every collection of this server inherits a configuration without a
     * validation mode, which means no validation), a DTD at a URL is not fetched, and the parse that builds the grammar of a DTD does not change that: it
     * reads what the parse of the document reads, with the same reader and configuration.
     */
    @Test
    void aDtdAtAUrlIsNotFetchedForACollectionThatDoesNotValidate() throws Exception {
        final AtomicInteger requests = new AtomicInteger();
        final HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/", exchange -> {
            requests.incrementAndGet();
            final byte[] body = "<!ELEMENT a ANY>".getBytes(UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        http.start();
        try {
            final String dtd = "http://127.0.0.1:" + http.getAddress().getPort() + "/never-fetched.dtd";
            for (int i = 0; i < 3; i++) {
                store("url-" + i + ".xml", "<?xml version=\"1.0\"?><!DOCTYPE a SYSTEM \"" + dtd + "\"><a/>");
            }
            assertEquals("3", query("count(collection('" + COLLECTION + "')[starts-with(util:document-name(.), 'url-')])"), "the documents are stored");
            assertEquals(0, requests.get(), "and the DTD server was never asked");
        } finally {
            http.stop(0);
        }
    }

    /**
     * In a collection that validates, a DTD at a URL is read from its system id, as the validation settings of
     * {@code conf.xml} say. The first document of the DTD reads it more than once, as its grammar is not taken from the
     * document's own parse any more: at most the two passes of the store and the parse that builds the grammar. Later
     * documents read it no more.
     */
    @Test
    void aDtdAtAUrlIsReadOnceForACollectionThatValidates() throws Exception {
        final String validating = "/db/dtdgrammarseeding-validating";
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn txn = pool.getTransactionManager().beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(txn, XmldbURI.create(validating));
            collection.getPermissions().setMode(0777);
            broker.saveCollection(txn, collection);
            pool.getConfigurationManager().addConfiguration(txn, broker, collection,
                    "<collection xmlns=\"http://exist-db.org/collection-config/1.0\"><validation mode=\"yes\"/></collection>");
            txn.commit();
        }

        final AtomicInteger requests = new AtomicInteger();
        final HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/", exchange -> {
            requests.incrementAndGet();
            final byte[] body = "<!ELEMENT a ANY><!ATTLIST a eid ID #IMPLIED>".getBytes(UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        http.start();
        try {
            final String dtd = "http://127.0.0.1:" + http.getAddress().getPort() + "/validated.dtd";
            final int[] perDocument = new int[4];
            for (int i = 0; i < perDocument.length; i++) {
                final int before = requests.get();
                TestTools.insertDocumentToURL(
                        new UnsynchronizedByteArrayInputStream(("<?xml version=\"1.0\"?><!DOCTYPE a SYSTEM \"" + dtd + "\"><a eid=\"v" + i + "\"/>").getBytes(UTF_8)),
                        "xmldb:exist://" + validating + "/v" + i + ".xml");
                perDocument[i] = requests.get() - before;
                assertEquals("1", query("count(id('v" + i + "', doc('" + validating + "/v" + i + ".xml')))"), "the DTD is applied to document " + i);
            }
            assertTrue(perDocument[0] >= 1 && perDocument[0] <= 3, "the first document reads the DTD, at most three times: " + perDocument[0]);
            for (int i = 1; i < perDocument.length; i++) {
                assertEquals(0, perDocument[i], "document " + i + " uses the cached grammar");
            }
        } finally {
            http.stop(0);
        }
    }

    private static DTDGrammar cachedGrammar(final String publicId) {
        final GrammarPool grammarPool = (GrammarPool) existEmbeddedServer.getBrokerPool().getConfiguration().getProperty(GrammarPool.GRAMMAR_POOL_ELEMENT);
        for (final Grammar grammar : grammarPool.retrieveCachedDtdGrammars()) {
            if (grammar instanceof final DTDGrammar dtd && publicId.equals(dtd.getGrammarDescription().getPublicId())) {
                return dtd;
            }
        }
        return null;
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
