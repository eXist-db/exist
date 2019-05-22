package org.exist.util;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.collections.*;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Tests around security exploits of the {@link org.xml.sax.XMLReader}
 */
public class XMLReaderSecurityTest {

    private final static int START_CHAR_RANGE = '@';
    private final static int END_CHAR_RANGE = '~';

    private final static int SECRET_LENGTH = 100;

    private final static XmldbURI TEST_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("test");

    private final static String FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";

    private final static String EXTERNAL_FILE_PLACEHOLDER = "file:///topsecret";

    private final static String EXPANSION_DOC =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<!DOCTYPE foo [\n" +
                    "<!ELEMENT foo ANY >\n" +
                    "<!ENTITY xxe SYSTEM \"" + EXTERNAL_FILE_PLACEHOLDER + "\" >]>\n" +
                    "<foo>&xxe;</foo>";

    private final static String EXPECTED_EXPANSION_DISABLED_DOC = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo/>";

    private final static String EXPECTED_EXPANDED_DOC = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>" + EXTERNAL_FILE_PLACEHOLDER + "</foo>";

    @ClassRule
    public final static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeClass
    public static void setupTestData() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
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

    @AfterClass
    public static void removeTestData() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {
            final Collection testCollection = broker.getCollection(TEST_COLLECTION);
            if (testCollection != null) {
                if (!broker.removeCollection(transaction, testCollection)) {
                    transaction.abort();
                    fail("Unable to remove test collection");
                }
            }

            transaction.commit();
        }
    }

    @Test
    public void expandExternalEntities() throws EXistException, IOException, PermissionDeniedException, LockException, SAXException, TransformerException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final Map<String, Boolean> parserConfig = new HashMap<>();
        parserConfig.put(FEATURE_EXTERNAL_GENERAL_ENTITIES, true);
        brokerPool.getConfiguration().setProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY, parserConfig);

        // create a temporary file on disk that contains secret info
        final Tuple2<String, Path> secret = createTempSecretFile();

        final XmldbURI docName = XmldbURI.create("expand-secret.xml");

        // attempt to store a document with an external entity which would be expanded to the content of the secret file
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            try (final Collection testCollection = broker.openCollection(TEST_COLLECTION, Lock.LockMode.WRITE_LOCK)) {

                //debugReader("expandExternalEntities", broker, testCollection);

                final String docContent = EXPANSION_DOC.replace(EXTERNAL_FILE_PLACEHOLDER, secret._2.toUri().toString());
                final IndexInfo indexInfo = testCollection.validateXMLResource(transaction, broker, docName, docContent);
                testCollection.store(transaction, broker, indexInfo, docContent);
            }

            transaction.commit();
        }

        // read back the document, to confirm that it does contain the secret
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
                final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            try (final Collection testCollection = broker.openCollection(TEST_COLLECTION, Lock.LockMode.READ_LOCK)) {

                try (final LockedDocument testDoc = testCollection.getDocumentWithLock(broker, docName, Lock.LockMode.READ_LOCK);){

                    // release the collection lock early inline with asymmetrical locking
                    testCollection.close();

                    assertNotNull(testDoc);
                    final String expected = EXPECTED_EXPANDED_DOC.replace(EXTERNAL_FILE_PLACEHOLDER, secret._1);
                    final String actual = serialize(testDoc.getDocument());

                    assertEquals(expected, actual);
                }
            }

            transaction.commit();
        }
    }

    @Test
    public void cannotExpandExternalEntitiesWhenDisabled() throws EXistException, IOException, PermissionDeniedException, LockException, SAXException, TransformerException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final Map<String, Boolean> parserConfig = new HashMap<>();
        parserConfig.put(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
        brokerPool.getConfiguration().setProperty(XMLReaderPool.XmlParser.XML_PARSER_FEATURES_PROPERTY, parserConfig);

        // create a temporary file on disk that contains secret info
        final Tuple2<String, Path> secret = createTempSecretFile();

        final XmldbURI docName = XmldbURI.create("expand-secret.xml");

        // attempt to store a document with an external entity which would be expanded to the content of the secret file
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            try (final Collection testCollection = broker.openCollection(TEST_COLLECTION, Lock.LockMode.WRITE_LOCK);){

                //debugReader("cannotExpandExternalEntitiesWhenDisabled", broker, testCollection);

                final String docContent = EXPANSION_DOC.replace(EXTERNAL_FILE_PLACEHOLDER, secret._2.toUri().toString());
                final IndexInfo indexInfo = testCollection.validateXMLResource(transaction, broker, docName, docContent);
                testCollection.store(transaction, broker, indexInfo, docContent);
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

    private String serialize(final Document doc) throws TransformerException, IOException {
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
    private Tuple2<String, Path> createTempSecretFile() throws IOException {
        final Path file = Files.createTempFile("exist.XMLReaderSecurityTest", "topsecret");
        final String randomSecret = generateRandomString(SECRET_LENGTH);
        return new Tuple2<>(randomSecret, Files.write(file, randomSecret.getBytes(UTF_8)));
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
