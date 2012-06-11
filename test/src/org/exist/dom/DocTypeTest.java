package org.exist.dom;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.custommonkey.xmlunit.XMLTestCase;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.FileInputSource;
import org.exist.util.VirtualTempFile;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Attr;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Tests basic DOM methods like getChildNodes(), getAttribute() ...
 * 
 * @author wolf
 *
 */
public class DocTypeTest extends XMLTestCase {

	public static void main(String[] args) {
		junit.textui.TestRunner.run(DocTypeTest.class);
	}

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
	
	public void testDocType_usingInputSource() throws Exception{
		DBBroker broker = null;
		DocumentImpl doc = null;
		TransactionManager transact = null;
		Txn transaction = null;
		try {
			assertNotNull(pool);
			
			
			File existHome = pool.getConfiguration().getExistHome();
			
			File testFile = new File(existHome, "/test/src/org/exist/dom/test_content.xml");
			
			System.out.println("path: " + testFile);
			
			assertNotNull(testFile);
			
			assertEquals(true, testFile.canRead());
			
			broker = pool.get(pool.getSecurityManager().getSystemSubject());
			assertNotNull(broker);
			
			InputSource is = new FileInputSource(testFile);
			transact = pool.getTransactionManager();
			assertNotNull(transact);
			transaction = transact.beginTransaction();
			IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test2.xml"), is);
			
			assertNotNull(info);
			root.store(transaction, broker, info, is, false);

			transact.commit(transaction);

			doc = broker.getXMLResource(root.getURI().append(XmldbURI.create("test2.xml")),Lock.READ_LOCK);

			DocumentType docType = doc.getDoctype();
			
			assertNotNull(docType);
			
			assertEquals("-//OASIS//DTD DITA Reference//EN", docType.getPublicId());
			
			Serializer serializer = broker.getSerializer();
			serializer.reset();
			
			serializer.setProperties(OUTPUT_PROPERTIES);
			
			String serialized = serializer.serialize(doc);

			System.out.println(serialized);
			assertTrue("Checking for Public Id in output", serialized.contains("-//OASIS//DTD DITA Reference//EN"));
				
			
		} catch (Exception e) {
        	transact.abort(transaction);
		    e.printStackTrace();
		    fail(e.getMessage());
		} finally {
		    if (doc != null) doc.getUpdateLock().release(Lock.READ_LOCK);
		    if (pool != null) pool.release(broker);
		}
	}
	
	public void testDocType_usingString() throws Exception{
		DBBroker broker = null;
		DocumentImpl doc = null;
		try {
			assertNotNull(pool);

			broker = pool.get(pool.getSecurityManager().getSystemSubject());
			doc = broker.getXMLResource(root.getURI().append(XmldbURI.create("test.xml")),Lock.READ_LOCK);

			DocumentType docType = doc.getDoctype();
			
			assertNotNull(docType);
			
			assertEquals("-//OASIS//DTD DITA Topic//EN", docType.getPublicId());
			
			Serializer serializer = broker.getSerializer();
			serializer.reset();
			
			serializer.setProperties(OUTPUT_PROPERTIES);
			
			String serialized = serializer.serialize(doc);
			
			System.out.println(serialized);
			
			assertTrue("Checking for Public Id in output", serialized.contains("-//OASIS//DTD DITA Topic//EN"));
			
		} catch (Exception e) {
		    e.printStackTrace();
		    fail(e.getMessage());
		} finally {
		    if (doc != null) doc.getUpdateLock().release(Lock.READ_LOCK);
		    if (pool != null) pool.release(broker);
		}
	}
    
    
	protected void setUp() throws Exception {        
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
        	pool = startDB();
        	assertNotNull(pool);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);            
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);            
            System.out.println("NodeTest#setUp ...");
            
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test"));
            assertNotNull(root);
            broker.saveCollection(transaction, root);
            
            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), XML);
            //TODO : unlock the collection here ?
            assertNotNull(info);
            root.store(transaction, broker, info, XML, false);
            
            transact.commit(transaction);
            System.out.println("NodeTest#setUp finished.");
        } catch (Exception e) {
        	transact.abort(transaction);
	        fail(e.getMessage()); 	        
        } finally {
        	if (pool != null) pool.release(broker);
        }
	}
	
	protected BrokerPool startDB() {
        String home, file = "conf.xml";
        home = System.getProperty("exist.home");
        if (home == null)
            home = System.getProperty("user.dir");
        try {
            Configuration config = new Configuration(file, home);
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {            
            fail(e.getMessage());
        }
        return null;
    }

    protected void tearDown() {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);            
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);            
            System.out.println("BasicNodeSetTest#tearDown >>>");
            
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test"));
            assertNotNull(root);
            broker.removeCollection(transaction, root);
            
            transact.commit(transaction);
        } catch (Exception e) {
        	transact.abort(transaction);
            e.printStackTrace();
        } finally {
            if (pool != null) pool.release(broker);
        }
        BrokerPool.stopAll(false);
        root = null;
        pool = null;
    }
}
