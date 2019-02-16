package org.exist.xquery.value;

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
public class Base64BinaryTest {

    private XPathQueryService service;
    private Collection        root     = null;
    private Database          database = null;

    @Test
    public void castToBase64ThenBackToString() throws XMLDBException {

        final String base64String = "QWxhZGRpbjpvcGVuIHNlc2FtZQ==";
        final String query = "let $data := '" + base64String + "' cast as xs:base64Binary return $data cast as xs:string";

        ResourceSet result = service.query(query);

        String queryResult = (String)result.getResource(0).getContent();

        assertEquals(base64String, queryResult);
    }

    @Before
    public void setUp() throws Exception {
        // initialize driver
        Class<?> cl = Class.forName( "org.exist.xmldb.DatabaseImpl" );
        database = (Database)cl.newInstance();
        database.setProperty( "create-database", "true" );
        DatabaseManager.registerDatabase(database);
        root    = DatabaseManager.getCollection( XmldbURI.LOCAL_DB, "admin", "" );
        service = (XPathQueryService)root.getService( "XQueryService", "1.0" );
    }


    @After
    public void tearDown() throws Exception {
        DatabaseManager.deregisterDatabase( database );
        DatabaseInstanceManager dim = (DatabaseInstanceManager)root.getService( "DatabaseInstanceManager", "1.0" );
        dim.shutdown();

        // clear instance variables
        service = null;
        root    = null;
    }
}
