package org.exist.xquery.value;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MapTest {

    private static Collection root;
    private static XPathQueryService queryService;

    @Test
    public void effectiveBooleanValue() throws XMLDBException {
        try {
            queryService.query("fn:boolean(map{})");
        } catch(final XMLDBException e) {
           final Throwable cause = e.getCause();
           if(cause instanceof XPathException) {
               final XPathException xpe = (XPathException)cause;
               assertEquals(ErrorCodes.FORG0006, xpe.getErrorCode());
               return;
           }
        }
        fail("effectiveBooleanValue of a map should cause the error FORG0006");
    }

    @BeforeClass
    public static void setUp() throws ClassNotFoundException, XMLDBException, IllegalAccessException, InstantiationException {
        final Database database = (Database) Class.forName("org.exist.xmldb.DatabaseImpl").newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        queryService = (XPathQueryService) root.getService("XPathQueryService", "1.0");
    }

    @AfterClass
    public static void tearDown() throws XMLDBException {
        if (root != null) {
            final DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
            root.close();
            dim.shutdown();
        }
    }
}
