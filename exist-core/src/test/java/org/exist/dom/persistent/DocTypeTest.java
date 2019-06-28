package org.exist.dom.persistent;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.junit.runner.RunWith;
import org.w3c.dom.DocumentType;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests basic DOM methods like getChildNodes(), getAttribute() ...
 * 
 * @author wolf
 *
 */
@RunWith(ParallelRunner.class)
public class DocTypeTest {

	public final static Properties OUTPUT_PROPERTIES = new Properties();
    static {
    	OUTPUT_PROPERTIES.setProperty(OutputKeys.INDENT, "no");
    	OUTPUT_PROPERTIES.setProperty(OutputKeys.ENCODING, "UTF-8");
    	OUTPUT_PROPERTIES.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
    	OUTPUT_PROPERTIES.setProperty(EXistOutputKeys.OUTPUT_DOCTYPE, "yes");
    	OUTPUT_PROPERTIES.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
    	OUTPUT_PROPERTIES.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
    }
	
	private static final String XML =
		"<!DOCTYPE topic PUBLIC \"-//OASIS//DTD DITA Topic//EN\" \"http://docs.oasis-open.org/dita/v1.2/os/dtd1.2/technicalContent/dtd/topic.dtd\">" +
		"<!-- doc starts here -->" +
        "<topic >" +
		"	<title>abc</title>" +
		"	<shortdesc>def</shortdesc>" +
        "   <body>ghi</body>" +
		"</topic>";

	private static Collection root = null;

    @Test
	public void docType_usingInputSource() throws EXistException, URISyntaxException, LockException, SAXException, PermissionDeniedException, IOException {
		final BrokerPool pool = existEmbeddedServer.getBrokerPool();
		final TransactionManager transact = pool.getTransactionManager();

		try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final URL testFileUrl = getClass().getResource("test_content.xml");
			final Path testFile = Paths.get(testFileUrl.toURI());
			assertTrue(Files.isReadable(testFile));
			
			final InputSource is = new FileInputSource(testFile);

			try(final Txn transaction = transact.beginTransaction()) {
                final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test2.xml"), is);

                assertNotNull(info);
                root.store(transaction, broker, info, is);

                transact.commit(transaction);
            }

			try(final LockedDocument lockedDoc = broker.getXMLResource(root.getURI().append(XmldbURI.create("test2.xml")),LockMode.READ_LOCK)) {
			    final DocumentImpl doc = lockedDoc.getDocument();
                final DocumentType docType = doc.getDoctype();
                assertNotNull(docType);
                assertEquals("-//OASIS//DTD DITA Reference//EN", docType.getPublicId());

                final Serializer serializer = broker.getSerializer();
                serializer.reset();

                serializer.setProperties(OUTPUT_PROPERTIES);

                final String serialized = serializer.serialize(doc);

                assertTrue("Checking for Public Id in output", serialized.contains("-//OASIS//DTD DITA Reference//EN"));
            }
		}
	}

    @Test
	public void docType_usingString() throws EXistException, PermissionDeniedException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
		try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final LockedDocument lockedDoc = broker.getXMLResource(root.getURI().append(XmldbURI.create("test.xml")),LockMode.READ_LOCK)) {
            final DocumentImpl doc = lockedDoc.getDocument();

            DocumentType docType = doc.getDoctype();

            assertNotNull(docType);

            assertEquals("-//OASIS//DTD DITA Topic//EN", docType.getPublicId());

            Serializer serializer = broker.getSerializer();
            serializer.reset();

            serializer.setProperties(OUTPUT_PROPERTIES);

            String serialized = serializer.serialize(doc);

            assertTrue("Checking for Public Id in output", serialized.contains("-//OASIS//DTD DITA Topic//EN"));

        }
	}

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

	@BeforeClass
    public static void setUp() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, DatabaseConfigurationException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
	    final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {
            
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test"));
            assertNotNull(root);
            broker.saveCollection(transaction, root);
            
            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), XML);
            //TODO : unlock the collection here ?
            assertNotNull(info);
            root.store(transaction, broker, info, XML);
            
            transact.commit(transaction);
        }
	}

    @AfterClass
    public static void tearDown() throws PermissionDeniedException, IOException, TriggerException, EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
	    final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {
            
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test"));
            assertNotNull(root);
            broker.removeCollection(transaction, root);
            
            transact.commit(transaction);
        }
    }
}
