package org.exist.xquery.functions.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xquery.XPathException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

/**
 * @author Jasper Linthorst (jasper.linthorst@gmail.com)
 */
public class CounterTest {

    private XPathQueryService service;
    private Collection root = null;
    private Database database = null;

    public CounterTest() {
    }

    @Before
    public void setUp() throws Exception {
        // initialize driver
        Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
        service = (XPathQueryService) root.getService("XQueryService", "1.0");
    }

    @After
    public void tearDown() throws Exception {
        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
        
        // clear instance variables
        service = null;
        root = null;
    }
    
    @Test
    public void testCreateAndDestroyCounter() throws XPathException {
        ResourceSet result = null;
        String r = "";
        try {
        	String query = "util:create-counter('jasper1')";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("0", r);
            
            query = "util:next-value('jasper1')";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("1", r);
            
            query = "util:destroy-counter('jasper1')";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("true", r);
        } catch (XMLDBException e) {
            System.out.println("testCreateAndDestroyCounter(): " + e.getMessage());
            fail(e.getMessage());
        }
    }
    
    @Test
    public void testCreateAndInitAndDestroyCounter() throws XPathException {
        ResourceSet result = null;
        String r = "";
        try {
        	String query = "util:create-counter('jasper3',xs:long(1200))";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("1200", r);
            
            query = "util:next-value('jasper3')";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("1201", r);
            
            query = "util:destroy-counter('jasper3')";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("true", r);
        } catch (XMLDBException e) {
            System.out.println("testCreateAndDestroyCounter(): " + e.getMessage());
            fail(e.getMessage());
        }
    }
    
    @Test
    public void threadedIncrementTest() throws XPathException, InterruptedException, XMLDBException {
		service = (XPathQueryService) root.getService("XQueryService", "1.0");
		
		String query = "util:create-counter('jasper2')";
		ResourceSet result = service.query(query);
		
		assertEquals("0", (String)result.getResource(0).getContent());
		
		Thread a = new IncrementThread();
		a.start();
		Thread b = new IncrementThread();
		b.start();
		Thread c = new IncrementThread();
		c.start();
		
		a.join();
		b.join();
		c.join();
		
		query = "util:next-value('jasper2')";
		ResourceSet valueAfter = service.query(query);
		
		query = "util:destroy-counter('jasper2')";
		result = service.query(query);
		
		assertEquals("601", (String)valueAfter.getResource(0).getContent());
    }
    
    class IncrementThread extends Thread {
    	
        public void run() {
        	
        	ResourceSet result = null;
        	String query="";
        	try {
        		service = (XPathQueryService) root.getService("XQueryService", "1.0");
	        	
	        	for (int i=0; i<200; i++) {
	        		query = "util:next-value('jasper2')";
	                result = service.query(query);
	                System.out.println("Thread "+getId()+": Counter value:"+result.getResource(0).getContent());
	        	}
        	} catch (XMLDBException e) {
        			e.printStackTrace();
        	}
        }
    }
}
