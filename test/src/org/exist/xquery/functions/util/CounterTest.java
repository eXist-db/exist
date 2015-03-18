package org.exist.xquery.functions.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.modules.counter.CounterModule;
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

    private final static String IMPORT = "import module namespace counter=\"" + CounterModule.NAMESPACE_URI + "\" " +
        "at \"java:org.exist.xquery.modules.counter.CounterModule\"; ";
    
    private XPathQueryService service;
    private Collection root = null;
    private Database database = null;

    public CounterTest() {
    }

    @Before
    public void setUp() throws Exception {
        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
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
        	String query = IMPORT + "counter:create('jasper1')";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("0", r);
            
            query = IMPORT +"counter:next-value('jasper1')";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("1", r);
            
            query = IMPORT +"counter:destroy('jasper1')";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("true", r);
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    @Test
    public void testCreateAndInitAndDestroyCounter() throws XPathException {
        ResourceSet result = null;
        String r = "";
        try {
        	String query = IMPORT +"counter:create('jasper3',xs:long(1200))";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("1200", r);
            
            query = IMPORT +"counter:next-value('jasper3')";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("1201", r);
            
            query = IMPORT +"counter:destroy('jasper3')";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("true", r);
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    @Test
    public void threadedIncrementTest() throws XPathException, InterruptedException, XMLDBException {
		service = (XPathQueryService) root.getService("XQueryService", "1.0");
		
		String query = IMPORT +"counter:create('jasper2')";
		ResourceSet result = service.query(query);
		
		assertEquals("0", result.getResource(0).getContent());
		
		Thread a = new IncrementThread();
		a.start();
		Thread b = new IncrementThread();
		b.start();
		Thread c = new IncrementThread();
		c.start();
		
		a.join();
		b.join();
		c.join();
		
		query = IMPORT +"counter:next-value('jasper2')";
		ResourceSet valueAfter = service.query(query);
		
		query = IMPORT +"counter:destroy('jasper2')";
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
	        		query = IMPORT +"counter:next-value('jasper2')";
	                result = service.query(query);
	                result.getResource(0).getContent();
	        	}
        	} catch (XMLDBException e) {
        			e.printStackTrace();
        	}
        }
    }
}
