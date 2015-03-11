package org.exist.xquery.functions.fn;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

/**
 *
 * @author ljo
 */
public class FunLangTest {
    
    private final static String TEST_DB_USER = "admin";
    private final static String TEST_DB_PWD = "";
	
    private XPathQueryService service;
    private Collection root = null;
    private Database database = null;
    
    @Test
    public void testFnLangWithContext() throws XMLDBException {
        ResourceSet resourceSet = service.query(
            "let $doc-frag := " +
	    "<desclist xml:lang=\"en\">" +
	       "<desc xml:lang=\"en-US\" n=\"1\">" +
	          "<line>The first line of the description.</line>" +
	       "</desc>" +
	       "<desc xml:lang=\"fr\" n=\"2\">"+
	          "<line>La premi&#232;re ligne de la déscription.</line>" +
	       "</desc>" +
	    "</desclist>" + 
            "return $doc-frag//desc[lang(\"en-US\")]"
        );
        
        assertEquals(1, resourceSet.getSize());
        assertEquals("<desc xml:lang=\"en-US\" n=\"1\">\n    <line>The first line of the description.</line>\n</desc>", resourceSet.getResource(0).getContent());
    }

        @Test
    public void testFnLangWithArgument() throws XMLDBException {
        ResourceSet resourceSet = service.query(
            "let $doc-frag := " +
	    "<desclist xml:lang=\"en\">" +
	       "<desc xml:lang=\"en-US\" n=\"1\">" +
	          "<line>The first line of the description.</line>" +
	       "</desc>" +
	       "<desc xml:lang=\"fr\" n=\"2\">"+
	          "<line>La premi&#232;re ligne de la déscription.</line>" +
	       "</desc>" +
	    "</desclist>" + 
            "return lang(\"en-US\", $doc-frag//desc[@n eq \"2\"])"
        );
        
        assertEquals(1, resourceSet.getSize());
        assertEquals("false", resourceSet.getResource(0).getContent());
    }
    
    @Test
    public void testFnLangWithAttributeArgument() throws XMLDBException {
        ResourceSet resourceSet = service.query(
            "let $doc-frag := " +
	    "<desclist xml:lang=\"en\">" +
	       "<desc xml:lang=\"en-US\" n=\"1\">" +
	          "<line>The first line of the description.</line>" +
	       "</desc>" +
	       "<desc xml:lang=\"fr\" n=\"2\">"+
	          "<line>La premi&#232;re ligne de la déscription.</line>" +
	       "</desc>" +
	    "</desclist>" + 
            "return lang(\"en-US\", $doc-frag//desc/@n[. eq \"1\"])"
        );
        
        assertEquals(1, resourceSet.getSize());
        assertEquals("true", resourceSet.getResource(0).getContent());
    }
    
    @Before
    public void setUp() throws Exception {
        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TEST_DB_USER, TEST_DB_PWD);
        service = (XPathQueryService) root.getService( "XQueryService", "1.0" );
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
}
