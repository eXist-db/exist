package org.exist.xquery.functions.onvdl;

import org.exist.dom.NodeSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xquery.XPathException;

import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author jim.fuller@webcomposite.com
 */
public class ONVDLTest {

    private XPathQueryService service;
    private Collection root = null;
    private Database database = null;

    public ONVDLTest() {
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
    //System.out.println("tearDown PASSED");
    }

    @Test
    public void testONVDL() throws XPathException {

        ResourceSet result = null;
        String r = "";
        try {
            String query = "util:eval('onvdl:entry('test')')";
            result = service.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("1", r);

        } catch (XMLDBException e) {
            System.out.println("testEval(): " + e);
            fail(e.getMessage());
        }

    }

}