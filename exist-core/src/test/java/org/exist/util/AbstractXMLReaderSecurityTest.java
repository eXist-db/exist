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
package org.exist.util;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

public abstract class AbstractXMLReaderSecurityTest {

    private static final int START_CHAR_RANGE = '@';
    private static final int END_CHAR_RANGE = '~';

    private static final int SECRET_LENGTH = 100;

    protected static final XmldbURI TEST_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("test");

    protected static final String FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";

    protected static final String EXTERNAL_FILE_PLACEHOLDER = "file:///topsecret";

    protected static final String EXPANSION_DOC =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<!DOCTYPE foo [\n" +
                    "<!ELEMENT foo ANY >\n" +
                    "<!ENTITY xxe SYSTEM \"" + EXTERNAL_FILE_PLACEHOLDER + "\" >]>\n" +
                    "<foo>&xxe;</foo>";

    protected abstract ExistEmbeddedServer getExistEmbeddedServer();

    @Before
    public void setupTestData() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool brokerPool = getExistEmbeddedServer().getBrokerPool();
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            createCollection(broker, transaction, TEST_COLLECTION);

            transaction.commit();
        }
    }

    private static Collection createCollection(final DBBroker broker, final Txn transaction, final XmldbURI uri) throws PermissionDeniedException, IOException, TriggerException {
        final Collection collection = broker.getOrCreateCollection(transaction, uri);
        broker.saveCollection(transaction, collection);
        return collection;
    }

    @After
    public void removeTestData() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool brokerPool = getExistEmbeddedServer().getBrokerPool();
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {
            final Collection testCollection = broker.getCollection(TEST_COLLECTION);
            if (testCollection != null && !broker.removeCollection(transaction, testCollection)) {
                transaction.abort();
                fail("Unable to remove test collection");
            }

            transaction.commit();
        }
    }

    protected String serialize(final Document doc) throws TransformerException, IOException {
        final Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

        try(final StringWriter writer = new StringWriter()) {
            final StreamResult result = new StreamResult(writer);
            final DOMSource source = new DOMSource(doc);
            transformer.transform(source, result);
            return writer.toString();
        }
    }

    /**
     * @return A tuple whose first item is the secret, and the second which is the path to a temporary file containing the secret
     */
    protected Tuple2<String, Path> createTempSecretFile() throws IOException {
        final Path file = Files.createTempFile("exist.XMLReaderSecurityTest", "topsecret");
        final String randomSecret = generateRandomString(SECRET_LENGTH);
        return new Tuple2<>(randomSecret, Files.writeString(file, randomSecret));
    }

    private String generateRandomString(final int length) {
        final char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            final char c = (char) ThreadLocalRandom.current().nextInt(START_CHAR_RANGE, END_CHAR_RANGE + 1);
            chars[i] = c;
        }
        return String.valueOf(chars);
    }

//    private void debugReader(final String label, final DBBroker broker, final Collection collection) {
//        try {
//            final Method method = MutableCollection.class.getDeclaredMethod("getReader", DBBroker.class, boolean.class, CollectionConfiguration.class);
//            method.setAccessible(true);
//
//            final XMLReader reader = (XMLReader)method.invoke(LockedCollection.unwrapLocked(collection), broker, false, collection.getConfiguration(broker));
//
//            System.out.println(label + ": READER: " + reader.getClass().getName());
//            System.out.println(label + ": " + FEATURE_EXTERNAL_GENERAL_ENTITIES + "=" + reader.getFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES));
//
//        } catch (final Throwable e) {
//            e.printStackTrace();
//        }
//    }
}
