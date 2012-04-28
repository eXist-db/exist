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
 * @author aretter
 */
public class FunNumberTest {
    
    private final static String TEST_DB_USER = "admin";
    private final static String TEST_DB_PWD = "";
	
    private XPathQueryService service;
    private Collection root = null;
    private Database database = null;
    
    @Test
    public void testFnNumberWithContext() throws XMLDBException {
        ResourceSet resourceSet = service.query(
            "let $errors := " +
                "<report>" +
                    "<message level=\"Error\" line=\"1191\" column=\"49\" repeat=\"96\"></message>" +
                    "<message level=\"Error\" line=\"161740\" column=\"25\"></message>" +
                    "<message level=\"Error\" line=\"162327\" column=\"92\" repeat=\"87\"></message>" +
                    "<message level=\"Error\" line=\"255090\" column=\"25\">c</message>" +
                    "<message level=\"Error\" line=\"255702\" column=\"414\" repeat=\"9\"></message>" +
                "</report>" +
            "return sum($errors//message/(@repeat/number(),1)[1])"
        );
        
        assertEquals(1, resourceSet.getSize());
        assertEquals("194", resourceSet.getResource(0).getContent());
    }
    
    @Test
    public void testFnNumberWithArgument() throws XMLDBException {
        ResourceSet resourceSet = service.query(
            "let $errors := " +
                "<report>" +
                    "<message level=\"Error\" line=\"1191\" column=\"49\" repeat=\"96\"></message>" +
                    "<message level=\"Error\" line=\"161740\" column=\"25\"></message>" +
                    "<message level=\"Error\" line=\"162327\" column=\"92\" repeat=\"87\"></message>" +
                    "<message level=\"Error\" line=\"255090\" column=\"25\">c</message>" +
                    "<message level=\"Error\" line=\"255702\" column=\"414\" repeat=\"9\"></message>" +
                "</report>" +
            "return sum($errors//message/(number(@repeat),1)[1])"
        );
        
        assertEquals(1, resourceSet.getSize());
        assertEquals("NaN", resourceSet.getResource(0).getContent());
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
