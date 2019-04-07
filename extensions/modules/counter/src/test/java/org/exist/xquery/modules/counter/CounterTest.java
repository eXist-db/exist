package org.exist.xquery.modules.counter;

import static org.junit.Assert.assertEquals;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xquery.XPathException;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Jasper Linthorst (jasper.linthorst@gmail.com)
 */
public class CounterTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true);

    private final static String IMPORT = "import module namespace counter=\"" + CounterModule.NAMESPACE_URI + "\" " +
        "at \"java:org.exist.xquery.modules.counter.CounterModule\"; ";
    
    @Test
    public void createAndDestroyCounter() throws XPathException, XMLDBException {
        String query = IMPORT + "counter:create('jasper1')";
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        String r = (String) result.getResource(0).getContent();
        assertEquals("0", r);

        query = IMPORT +"counter:next-value('jasper1')";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals("1", r);

        query = IMPORT +"counter:destroy('jasper1')";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals("true", r);
    }
    
    @Test
    public void createAndInitAndDestroyCounter() throws XPathException, XMLDBException {
        String query = IMPORT +"counter:create('jasper3',xs:long(1200))";
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        String r = (String) result.getResource(0).getContent();
        assertEquals("1200", r);

        query = IMPORT +"counter:next-value('jasper3')";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals("1201", r);

        query = IMPORT +"counter:destroy('jasper3')";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals("true", r);
    }
    
    @Test
    public void threadedIncrement() throws XPathException, InterruptedException, XMLDBException {
		String query = IMPORT +"counter:create('jasper2')";
		ResourceSet result = existEmbeddedServer.executeQuery(query);
		
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
		ResourceSet valueAfter = existEmbeddedServer.executeQuery(query);
		
		query = IMPORT +"counter:destroy('jasper2')";
		result = existEmbeddedServer.executeQuery(query);
		
		assertEquals("601", (String)valueAfter.getResource(0).getContent());
    }
    
    static class IncrementThread extends Thread {
    	
        public void run() {
        	try {
	        	for (int i=0; i<200; i++) {
	        		final String query = IMPORT +"counter:next-value('jasper2')";
	                final ResourceSet result = existEmbeddedServer.executeQuery(query);
	                result.getResource(0).getContent();
	        	}
        	} catch (XMLDBException e) {
        			e.printStackTrace();
        	}
        }
    }
}
