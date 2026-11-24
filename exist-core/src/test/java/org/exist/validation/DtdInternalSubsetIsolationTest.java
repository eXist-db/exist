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
import org.exist.dom.persistent.LockedDocument;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.util.PropertiesBuilder.propertiesBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a document declares in its own internal subset is its own: it wins over the external DTD for that document, and
 * it is not part of the DTD for the documents that are stored after it. The DTD grammar cache is keyed by the DTD,
 * so a grammar that was built together with an internal subset must not be cached.
 *
 * See <a href="https://github.com/eXist-db/exist/issues/1757">#1757</a>.
 */
class DtdInternalSubsetIsolationTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(
            propertiesBuilder()
                    .set(XMLReaderObjectFactory.PROPERTY_VALIDATION_MODE, "no")
                    .build(),
            true,
            true);

    private static final String COLLECTION = "/db/dtdinternalsubsetisolation";

    /** A DOCTYPE for a DTD that declares {@code eid} of {@code c} as an ID. */
    private static final String ATTRIBUTES = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<!DOCTYPE r PUBLIC \"-//EXIST-TEST//DTD Attributes//EN\" \"attr-test.dtd\"";

    /** A DOCTYPE for a DTD that declares the entity {@code alpha} of {@code a}. */
    private static final String ENTITIES = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<!DOCTYPE a PUBLIC \"-//EXIST-TEST//DTD Entities//EN\" \"entity-test.dtd\"";

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
    void anInternalDeclarationOfAnotherTypeOverridesAnExternalIdDeclaration() throws Exception {
        final String overridden = ATTRIBUTES + " [<!ATTLIST c eid CDATA #IMPLIED>]><r><c eid=\"%s\"/></r>";
        final String plain = ATTRIBUTES + "><r><c eid=\"%s\"/></r>";

        // the DTD is cached from a document that declares nothing of its own, so the parser holds it when the next one comes
        store("override-plain-0.xml", plain.formatted("p0"));
        assertEquals("1", query("count(id('p0', doc('" + COLLECTION + "/override-plain-0.xml')))"), "the external DTD declares it as an ID");

        store("override-1.xml", overridden.formatted("o1"));
        assertEquals("0", query("count(id('o1', doc('" + COLLECTION + "/override-1.xml')))"), "declared as CDATA by its own internal subset");

        store("override-plain.xml", plain.formatted("p1"));
        assertEquals("1", query("count(id('p1', doc('" + COLLECTION + "/override-plain.xml')))"),
                "the external DTD declares it as an ID, whatever the document stored before declared");

        store("override-2.xml", overridden.formatted("o2"));
        assertEquals("0", query("count(id('o2', doc('" + COLLECTION + "/override-2.xml')))"), "declared as CDATA again, after a document that is not");

        assertEquals("<!ATTLIST c eid CDATA #IMPLIED>", internalSubsetOf("override-2.xml"),
                "kept, so that a restore parses the document again with the override");
        assertNull(internalSubsetOf("override-plain.xml"));
    }

    @Test
    void anAttributeThatAnotherDocumentDeclaredAsAnIdIsNotOne() throws Exception {
        store("declares.xml", ATTRIBUTES + " [<!ATTLIST c aid ID #IMPLIED>]><r><c aid=\"a1\"/></r>");
        assertEquals("1", query("count(id('a1', doc('" + COLLECTION + "/declares.xml')))"));

        store("uses.xml", ATTRIBUTES + "><r><c aid=\"b1\"/></r>");
        assertEquals("0", query("count(id('b1', doc('" + COLLECTION + "/uses.xml')))"), "aid is not declared for this document");
    }

    @Test
    void anEntityThatAnotherDocumentDeclaredIsNotDeclared() throws Exception {
        store("entity-declares.xml", ENTITIES + " [<!ENTITY onlyhere \"declared by one document\">]><a>&onlyhere;</a>");
        assertEquals("declared by one document", query("string(doc('" + COLLECTION + "/entity-declares.xml')/*)"));

        final IOException e = assertThrows(IOException.class, () -> store("entity-uses.xml", ENTITIES + "><a>&onlyhere;</a>"));
        assertTrue(e.getMessage().contains("onlyhere"), e.getMessage());
    }

    private static String internalSubsetOf(final String name) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final LockedDocument locked = broker.getXMLResource(XmldbURI.create(COLLECTION + "/" + name), LockMode.READ_LOCK)) {
            return locked.getDocument().getDoctype().getInternalSubset();
        }
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
