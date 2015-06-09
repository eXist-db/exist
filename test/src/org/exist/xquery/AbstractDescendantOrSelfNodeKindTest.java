package org.exist.xquery;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertEquals;

/**
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public abstract class AbstractDescendantOrSelfNodeKindTest {

    protected final static String TEST_DOCUMENT = "<doc xml:id=\"x\">\n"+
        "<?xml-stylesheet type=\"text/xsl\" href=\"test\"?>\n"+
        "    <a>\n"+
        "        <b x=\"1\">text<e>text</e>text</b>\n"+
        "        </a>\n"+
        "    <a>\n"+
        "        <c><!--comment-->\n"+
        "            <d xmlns=\"x\" y=\"2\" z=\"3\">text</d>\n"+
        "            </c>\n"+
        "    </a>\n"+
        "</doc>";

    protected static Collection root = null;

    protected abstract ResourceSet executeQueryOnDoc(final String docQuery) throws XMLDBException;

    @Test
    public void documentNodeCount() throws XMLDBException {
        final ResourceSet result = executeQueryOnDoc("count($doc//document-node())");
        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt((String)result.getResource(0).getContent()));
    }

    @Test
    public void nodeCount() throws XMLDBException {
        final ResourceSet result = executeQueryOnDoc("count($doc//node())");
        assertEquals(1, result.getSize());
        assertEquals(13, Integer.parseInt((String)result.getResource(0).getContent()));
    }

    @Test
    public void elementCount() throws XMLDBException {
        final ResourceSet result = executeQueryOnDoc("count($doc//element())");
        assertEquals(1, result.getSize());
        assertEquals(7, Integer.parseInt((String)result.getResource(0).getContent()));
    }

    @Test
    public void textCount() throws XMLDBException {
        final ResourceSet result = executeQueryOnDoc("count($doc//text())");
        assertEquals(1, result.getSize());
        assertEquals(4, Integer.parseInt((String)result.getResource(0).getContent()));
    }

    @Test
    public void attributeCount() throws XMLDBException {
        final ResourceSet result = executeQueryOnDoc("count($doc//attribute())");
        assertEquals(1, result.getSize());
        assertEquals(4, Integer.parseInt((String)result.getResource(0).getContent()));
    }

    @Test
    public void commentCount() throws XMLDBException {
        final ResourceSet result = executeQueryOnDoc("count($doc//comment())");
        assertEquals(1, result.getSize());
        assertEquals(1, Integer.parseInt((String)result.getResource(0).getContent()));
    }

    @Test
    public void processingInstructionCount() throws XMLDBException {
        final ResourceSet result = executeQueryOnDoc("count($doc//processing-instruction())");
        assertEquals(1, result.getSize());
        assertEquals(1, Integer.parseInt((String)result.getResource(0).getContent()));
    }

    @BeforeClass
    public static void startDbAndCreateTestCollection() throws Exception {
        // initialize driver
        final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        final Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        final DatabaseInstanceManager dim = (DatabaseInstanceManager)root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
        root = null;
    }

}
