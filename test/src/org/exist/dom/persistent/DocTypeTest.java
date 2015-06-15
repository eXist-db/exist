package org.exist.dom.persistent;

import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
        "   <body><p>ghi</p></body>" +
		"</topic>";
	
	private BrokerPool pool = null;
	private Collection root = null;

    @Test
	public void docType_usingInputSource() throws Exception{
		DocumentImpl doc = null;
		final TransactionManager transact = pool.getTransactionManager();

		try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
			final File existHome = pool.getConfiguration().getExistHome();
			final File testFile = new File(existHome, "/test/src/org/exist/dom/persistent/test_content.xml");
			assertEquals(true, testFile.canRead());
			
			final InputSource is = new FileInputSource(testFile);

			try(final Txn transaction = transact.beginTransaction()) {
                final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test2.xml"), is);

                assertNotNull(info);
                root.store(transaction, broker, info, is, false);

                transact.commit(transaction);
            }

			doc = broker.getXMLResource(root.getURI().append(XmldbURI.create("test2.xml")),Lock.READ_LOCK);

			final DocumentType docType = doc.getDoctype();
			assertNotNull(docType);
			assertEquals("-//OASIS//DTD DITA Reference//EN", docType.getPublicId());
			
			final Serializer serializer = broker.getSerializer();
			serializer.reset();
			
			serializer.setProperties(OUTPUT_PROPERTIES);
			
			final String serialized = serializer.serialize(doc);

			assertTrue("Checking for Public Id in output", serialized.contains("-//OASIS//DTD DITA Reference//EN"));

		} finally {
		    if (doc != null) {
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
		}
	}

    @Test
	public void docType_usingString() throws Exception{
		DocumentImpl doc = null;
		try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            doc = broker.getXMLResource(root.getURI().append(XmldbURI.create("test.xml")), Lock.READ_LOCK);

            DocumentType docType = doc.getDoctype();

            assertNotNull(docType);

            assertEquals("-//OASIS//DTD DITA Topic//EN", docType.getPublicId());

            Serializer serializer = broker.getSerializer();
            serializer.reset();

            serializer.setProperties(OUTPUT_PROPERTIES);

            String serialized = serializer.serialize(doc);

            assertTrue("Checking for Public Id in output", serialized.contains("-//OASIS//DTD DITA Topic//EN"));

        } finally {
		    if (doc != null) {
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
		}
	}
    
    
	@Before
    public void setUp() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, DatabaseConfigurationException {
        pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
            final Txn transaction = transact.beginTransaction()) {
            
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test"));
            assertNotNull(root);
            broker.saveCollection(transaction, root);
            
            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), XML);
            //TODO : unlock the collection here ?
            assertNotNull(info);
            root.store(transaction, broker, info, XML, false);
            
            transact.commit(transaction);
        }
	}
	
	protected BrokerPool startDB() throws DatabaseConfigurationException, EXistException {
        final String file = "conf.xml";
        String home = System.getProperty("exist.home");
        if (home == null) {
            home = System.getProperty("user.dir");
        }

        final Configuration config = new Configuration(file, home);
        BrokerPool.configure(1, 5, config);
        return BrokerPool.getInstance();

    }

    @After
    public void tearDown() throws PermissionDeniedException, IOException, TriggerException, EXistException {
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
            final Txn transaction = transact.beginTransaction()) {
            
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test"));
            assertNotNull(root);
            broker.removeCollection(transaction, root);
            
            transact.commit(transaction);
        } finally {
            BrokerPool.stopAll(false);
        }
    }
}
