package org.exist.dom.memtree;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

import static org.junit.Assert.assertEquals;

/**
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class MemtreeInXQuery {

    private final static String TEST_DB_USER = "guest";
    private final static String TEST_DB_PWD = "guest";

    private static XPathQueryService service;
    private static Collection root = null;
    private static Database database = null;

    @Test
    public void pi_attributes() throws XMLDBException {
        final String xquery = "let $doc := document{\n" +
                "    processing-instruction{\"ok\"}{\"ok\"},\n" +
                "    <root/>\n" +
                "}\n" +
                "return count($doc//processing-instruction()/@*)";

        final ResourceSet result = service.query(xquery);

        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt(result.getResource(0).getContent().toString()));

        result.clear();
    }

    @Test
    public void pi_children() throws XMLDBException {
        final String xquery = "let $doc := document{\n" +
                "    processing-instruction{\"ok\"}{\"ok\"},\n" +
                "    <root/>\n" +
                "}\n" +
                "return count($doc//processing-instruction()/node())";

        final ResourceSet result = service.query(xquery);

        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt(result.getResource(0).getContent().toString()));

        result.clear();
    }

    @Test
    public void pi_descendantAttributes() throws XMLDBException {
        final String xquery = "let $doc := document{\n" +
                "    processing-instruction{\"ok\"}{\"ok\"},\n" +
                "    <root/>\n" +
                "}\n" +
                "return count($doc//processing-instruction()//@*)";

        final ResourceSet result = service.query(xquery);

        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt(result.getResource(0).getContent().toString()));

        result.clear();
    }

    @Test
    public void attr_attributes() throws XMLDBException {
        final String xquery = "let $doc := document {\n" +
                "    element a {\n" +
                "        attribute x { \"y\" }\n" +
                "    }\n" +
                "} return\n" +
                "    count($doc/a/@x/@y)";

        final ResourceSet result = service.query(xquery);

        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt(result.getResource(0).getContent().toString()));

        result.clear();
    }

    @Test
    public void attr_children() throws XMLDBException {
        final String xquery = "let $doc := document {\n" +
                "    element a {\n" +
                "        attribute x { \"y\" }\n" +
                "    }\n" +
                "} return\n" +
                "    count($doc/a/@x/node())";

        final ResourceSet result = service.query(xquery);

        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt(result.getResource(0).getContent().toString()));

        result.clear();
    }

    @BeforeClass
    public static void setUp() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        // initialize driver
        final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TEST_DB_USER, TEST_DB_PWD);
        service = (XPathQueryService) root.getService( "XQueryService", "1.0" );
    }

    @AfterClass
    public static void tearDown() throws XMLDBException {
        DatabaseManager.deregisterDatabase(database);
        final DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();

        // clear instance variables
        service = null;
        root = null;
    }
}
