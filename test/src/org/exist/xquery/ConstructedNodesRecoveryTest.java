package org.exist.xquery;

import org.exist.security.SecurityManager;
import org.exist.security.xacml.AccessContext;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.xquery.value.Sequence;
import org.exist.util.Configuration;

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
