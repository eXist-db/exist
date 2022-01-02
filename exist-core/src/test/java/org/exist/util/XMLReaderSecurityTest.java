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
import org.exist.collections.*;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.Rule;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests around security exploits of the {@link org.xml.sax.XMLReader}
 */
public class XMLReaderSecurityTest extends AbstractXMLReaderSecurityTest {

    private static final String EXPECTED_EXPANSION_DISABLED_DOC = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo/>";

    private static final Properties secureConfigProperties = new Properties();
    static {
        final Map<String, Boolean> secureProperties = new HashMap<>();
        secureProperties.put(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
        secureProperties.put("http://xml.org/sax/features/external-parameter-entities", false);
        secureProperties.put("http://javax.xml.XMLConstants/feature/secure-processing", true);
        secureConfigProperties.put(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY, secureProperties);
    }

    @Rule
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(secureConfigProperties, true, true);

    @Override
    protected ExistEmbeddedServer getExistEmbeddedServer() {
        return existEmbeddedServer;
    }

    @Test
    public void cannotExpandExternalEntitiesWhenDisabled() throws EXistException, IOException, PermissionDeniedException, LockException, SAXException, TransformerException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();

        // create a temporary file on disk that contains secret info
        final Tuple2<String, Path> secret = createTempSecretFile();

        final XmldbURI docName = XmldbURI.create("expand-secret.xml");

        // attempt to store a document with an external entity which would be expanded to the content of the secret file
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            try (final Collection testCollection = broker.openCollection(TEST_COLLECTION, Lock.LockMode.WRITE_LOCK)) {

                //debugReader("cannotExpandExternalEntitiesWhenDisabled", broker, testCollection);

                final String docContent = EXPANSION_DOC.replace(EXTERNAL_FILE_PLACEHOLDER, secret._2.toUri().toString());
                testCollection.storeDocument(transaction, broker, docName, new StringInputSource(docContent), MimeType.XML_TYPE);
            }

            transaction.commit();
        }

        // read back the document, to confirm that it does not contain the secret
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            try (final Collection testCollection = broker.openCollection(TEST_COLLECTION, Lock.LockMode.READ_LOCK)) {

                try (final LockedDocument testDoc = testCollection.getDocumentWithLock(broker, docName, Lock.LockMode.READ_LOCK)) {

                    // release the collection lock early inline with asymmetrical locking
                    testCollection.close();

                    assertNotNull(testDoc);

                    final String expected = EXPECTED_EXPANSION_DISABLED_DOC;
                    final String actual = serialize(testDoc.getDocument());

                    assertEquals(expected, actual);
                }
            }

            transaction.commit();
        }
    }
}
