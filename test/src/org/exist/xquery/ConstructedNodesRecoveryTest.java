package org.exist.xquery;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.security.SecurityManager;
import org.exist.security.xacml.AccessContext;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.Sequence;
import org.exist.util.Configuration;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;

import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;
import javax.xml.transform.OutputKeys;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/** Tests for recovery of database corruption after constructed node operations (in-memory nodes)
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class ConstructedNodesRecoveryTest extends TestCase
{	
	private final static String xquery =
		"declare variable $categories := \n" +
		"	<categories>\n" +
		"		<category uid=\"1\">Fruit</category>\n" +
		"		<category uid=\"2\">Vegetable</category>\n" +
		"		<category uid=\"3\">Meat</category>\n" +
		"		<category uid=\"4\">Dairy</category>\n" +
		"	</categories>\n" +
		";\n\n" + 
		
		"for $category in $categories/category return\n" +
		"	element option {\n" +
		"		attribute value {\n" +
		"			$category/@uid\n" +
		"		},\n" +
		"		text { $category }\n" +
		"	}";

	private final static String expectedResults [] = { 
		"Fruit",
		"Vegetable",
		"Meat",
		"Dairy"
	}; 
		
	private final static String testDocument = 
		"<fruit>" +
			"<apple colour=\"green\"/>" +
			"<pear colour=\"green\"/>" +
			"<orange colour=\"orange\"/>" +
			"<dragonfruit colour=\"pink\"/>" +
			"<grapefruit colour=\"yellow\"/>" +
		"</fruit>";
	
	public static void main(String[] args)
	{
        TestRunner.run(ConstructedNodesRecoveryTest.class);
    }

	/**
	 * Issues a query against constructed nodes and then corrupts the database (intentionally)
	 */
	public void testConstructedNodesCorrupt()
	{
		constructedNodeQuery(true);
    }
    
	/**
	 * Recovers from corruption (intentional) and then issues a query against constructed nodes
	 */
	public void testConstructedNodesRecover()
	{
		constructedNodeQuery(false);
	}
	
	private void storeTestDocument(DBBroker broker, TransactionManager transact, String documentName) throws Exception
	{
		//create a transaction
		Txn transaction = transact.beginTransaction();
        assertNotNull(transaction);            
        System.out.println("Transaction started ...");
		
		//get the test collection
        Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
        assertNotNull(root);
        broker.saveCollection(transaction, root);
		
		//store test document
        IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create(documentName), testDocument);
        assertNotNull(info);
        root.store(transaction, broker, info, new InputSource(new StringReader(testDocument)), false);
        
        //commit the transaction
        transact.commit(transaction);
        System.out.println("Transaction commited ...");
	}
	
	private void createTempChildCollection(DBBroker broker, TransactionManager transact, String childCollectionName) throws Exception
	{
		//create a transaction
		Txn transaction = transact.beginTransaction();
        assertNotNull(transaction);            
        System.out.println("Transaction started ...");
		
		//get the test collection
        Collection root = broker.getOrCreateCollection(transaction, XmldbURI.TEMP_COLLECTION_URI.append(childCollectionName));
        assertNotNull(root);
        broker.saveCollection(transaction, root);
		        
        //commit the transaction
        transact.commit(transaction);
        System.out.println("Transaction commited ...");
	}
	
	private void testDocumentIsValid(DBBroker broker, TransactionManager transact, String documentName) throws Exception
	{
		//create a transaction
		Txn transaction = transact.beginTransaction();
        assertNotNull(transaction);            
        System.out.println("Transaction started ...");
		
		//get the test collection
        Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
        assertNotNull(root);
        broker.saveCollection(transaction, root);
        
        //get the test document
        DocumentImpl doc = root.getDocumentWithLock(broker, XmldbURI.create(documentName), Lock.READ_LOCK);
        
        Serializer serializer = broker.getSerializer();
        serializer.reset();
        SAXSerializer sax = null;
        StringWriter writer = new StringWriter();
        sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
        Properties outputProperties = new Properties();
        outputProperties.setProperty(OutputKeys.INDENT, "no");
        outputProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
        sax.setOutput(writer, outputProperties);
        serializer.setProperties(outputProperties);
        serializer.setSAXHandlers(sax, sax);
        serializer.toSAX(doc);
        SerializerPool.getInstance().returnObject(sax);
        
        assertEquals(testDocument, writer.toString());
        
        transact.commit(transaction);
	}
	
	private void testTempChildCollectionExists(DBBroker broker, TransactionManager transact, String childCollectionName) throws Exception
	{
		//create a transaction
		Txn transaction = transact.beginTransaction();
        assertNotNull(transaction);            
        System.out.println("Transaction started ...");
		
		//get the temp child collection
        Collection tempChildCollection = broker.getOrCreateCollection(transaction, XmldbURI.TEMP_COLLECTION_URI.append(childCollectionName));
        assertNotNull(tempChildCollection);
        broker.saveCollection(transaction, tempChildCollection);
        
        transact.commit(transaction);
	}
	
	/**
	 * Performs a query against constructed nodes, with the option of forcefully corrupting the database
	 * 
	 * @param forceCorruption	Should the database be forcefully corrupted
	 */
	private void constructedNodeQuery(boolean forceCorruption)
	{
		BrokerPool.FORCE_CORRUPTION = forceCorruption;
	    BrokerPool pool = null;        
	    DBBroker broker = null;
	    
	    try
	    {
	    	pool = startDB();
	    	assertNotNull(pool);
	        broker = pool.get(SecurityManager.SYSTEM_USER);
	        
	        TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            
            //only store the documents the first time
            if(forceCorruption)
            {
            	//store a first test document
            	storeTestDocument(broker, transact, "testcr1.xml");

            	//store a second test document
            	storeTestDocument(broker, transact, "testcr2.xml");
            }
            
            //create some child collections in TEMP collection
	        createTempChildCollection(broker, transact, "testchild1");
	        createTempChildCollection(broker, transact, "testchild2");
            
            //execute an xquery
	        XQuery service = broker.getXQueryService();
	        assertNotNull(service);
	        
	        CompiledXQuery compiled = service.compile(service.newContext(AccessContext.TEST), new StringSource(xquery));
	        assertNotNull(compiled);
	        
	        Sequence result = service.execute(compiled, null);
	        assertNotNull(result);
	       
	        assertEquals(expectedResults.length, result.getItemCount());
	        
	        for(int i = 0; i < result.getItemCount(); i++)
			{
				assertEquals(expectedResults[i], (String)result.itemAt(i).getStringValue());
			}
	        
	        //read the first test document
	        testDocumentIsValid(broker, transact, "testcr1.xml");
	        
	        //read the second test document
	        testDocumentIsValid(broker, transact, "testcr1.xml");
	        
	        //test the child collections exist
	        testTempChildCollectionExists(broker, transact, "testchild1");
	        testTempChildCollectionExists(broker, transact, "testchild2");
	        
	        transact.getJournal().flushToLog(true);
	    }
	    catch(Exception e)
	    {            
	        fail(e.getMessage());
	        e.printStackTrace();
	    }
	    finally
	    {
	    	if (pool != null) pool.release(broker);
	    }
	}
	
	protected BrokerPool startDB() {
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {            
            fail(e.getMessage());
        }
        return null;
    }

    protected void tearDown() {
        BrokerPool.stopAll(false);
    }
}
