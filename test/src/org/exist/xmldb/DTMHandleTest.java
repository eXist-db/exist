/**
 * DTMHandleTest.java
 * <p/>
 * 2004 by O2 IT Engineering
 * Zurich,  Switzerland (CH)
 */
package org.exist.xmldb;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xmldb.api.base.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.modules.XMLResource;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the TreeLevelOrder function.
 *
 * @author Tobias Wunden
 * @version 1.0
 */

public class DTMHandleTest {

    /** eXist database url */
    static final String eXistUrl = "xmldb:exist://";

    private Collection root = null;

    /**
     * Test for the TreeLevelOrder function. This test
     * <ul>
     * <li>Registers a database instance</li>
     * <li>Writes a document to the database using the XQueryService</li>
     * <li>Reads the document from the database using XmlDB</li>
     * <li>Accesses the document using DOM</li>
     * </ul>
     */
    @Test
    public final void treeLevelOrder() throws ClassNotFoundException, InstantiationException, XMLDBException, IllegalAccessException {
        String document = "survey.xml";

        StringBuilder xmlDocument = new StringBuilder();
        xmlDocument.append("<survey>");
        xmlDocument.append("<date>2004/11/24 17:42:31 GMT</date>");
        xmlDocument.append("<from>tobias.wunden@o2it.ch</from>");
        xmlDocument.append("<to>tobias.wunden@o2it.ch</to>");
        xmlDocument.append("<subject>Test</subject>");
        xmlDocument.append("<field>");
        xmlDocument.append("<name>homepage</name>");
        xmlDocument.append("<value>-</value>");
        xmlDocument.append("</field>");
        xmlDocument.append("</survey>");

        // Obtain XQuery service
        EXistXQueryService service = getXQueryService();
        assertNotNull("Failed to obtain xquery service instance!", service);
        // write document to the database
        store(xmlDocument.toString(), service, document);
        // read document back from database
        Node root = load(service, document);
        assertNotNull("Document " + document + " was not found in the database!", root);

        boolean foundFieldText = false;

        NodeList rootChildren = root.getChildNodes();
        for (int r = 0; r < rootChildren.getLength(); r++) {
            if (rootChildren.item(r).getLocalName().equals("field")) {
                foundFieldText = false;

                Node field = rootChildren.item(r);

                NodeList fieldChildren = field.getChildNodes();
                for (int f = 0; f < fieldChildren.getLength(); f++) {
                    if (fieldChildren.item(f).getLocalName().equals("name")) {
                        foundFieldText = true;

                        Node name = fieldChildren.item(f);
                        //String nameText = name.getTextContent();
                        String nameText = TreeLevelOrderTest.nodeValue(name);
                        assertNotNull("Failed to read existing field[" + 1 + "]/name/text()", nameText);
                    }
                }

                assertTrue("Failed to read existing field[" + 1 + "]/name/text()", foundFieldText);
            }
        }
    }

    /**
     * Stores the given xml fragment into the database.
     *
     * @param xml the xml document
     * @param service the xquery service
     * @param document the document name
     */
    private final void store(String xml, EXistXQueryService service, String document) throws XMLDBException {
        StringBuilder query = new StringBuilder();
        query.append("xquery version \"1.0\";");
        query.append("declare namespace xdb=\"http://exist-db.org/xquery/xmldb\";");
        query.append("let $isLoggedIn := xdb:login(\"" + eXistUrl + XmldbURI.ROOT_COLLECTION + "\", \"admin\", \"\"),");
        query.append("$doc := xdb:store(\"" + eXistUrl + XmldbURI.ROOT_COLLECTION + "\", $document, $survey)");
        query.append("return <result/>");

        service.declareVariable("survey", xml);
        service.declareVariable("document", document);
        CompiledExpression cQuery = service.compile(query.toString());
        service.execute(cQuery);
    }

    /**
     * Loads the xml document identified by <code>document</code> from the database.
     *
     * @param service the xquery service
     * @param document the document to load
     */
    private final Node load(EXistXQueryService service, String document) throws XMLDBException {
        StringBuilder query = new StringBuilder();
        query.append("xquery version \"1.0\";");
        query.append("let $survey := xmldb:document(concat('" + XmldbURI.ROOT_COLLECTION + "', '/', $document))");
        query.append("return ($survey)");

        service.declareVariable("document", document);
        CompiledExpression cQuery = service.compile(query.toString());
        ResourceSet set = service.execute(cQuery);
        assertNotNull(set);
        assertTrue(set.getSize() > 0);
        return ((XMLResource) set.getIterator().nextResource()).getContentAsDOM();
    }

    /**
     * Retrieves the base collection and thereof returns a reference to the collection's
     * xquery service.
     *
     * @return the xquery service
     */
    private final EXistXQueryService getXQueryService() throws XMLDBException {
        EXistXQueryService service = (EXistXQueryService) root.getService("XQueryService", "1.0");
        return service;
    }

    /**
     * Registers a new database instance and returns it.
     */
    @Before
    public final void registerDatabase() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        String driverName = "org.exist.xmldb.DatabaseImpl";
        Class<?> driver = Class.forName(driverName);
        Database database = (Database) driver.newInstance();
        database.setProperty("create-database", "true");

        DatabaseManager.registerDatabase(database);
        this.root = DatabaseManager.getCollection(eXistUrl + XmldbURI.ROOT_COLLECTION, "admin", "");
    }

    @After
    public final void deregisterDatabase() throws XMLDBException {
        if (root != null) {
            DatabaseInstanceManager mgr = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
            root.close();
            mgr.shutdown();
        }
    }

}