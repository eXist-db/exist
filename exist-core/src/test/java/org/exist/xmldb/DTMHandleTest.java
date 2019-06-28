/**
 * DTMHandleTest.java
 *
 * 2004 by O2 IT Engineering
 * Zurich,  Switzerland (CH)
 */
package org.exist.xmldb;

import org.exist.TestUtils;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the TreeLevelOrder function.
 *
 * @author Tobias Wunden
 * @version 1.0
 */

public class DTMHandleTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String DOC1_NAME = "survey.xml";

    private static final String DOC1 =
            "<survey>" +
            "<date>2004/11/24 17:42:31 GMT</date>" +
            "<from>tobias.wunden@o2it.ch</from>" +
            "<to>tobias.wunden@o2it.ch</to>" +
            "<subject>Test</subject>" +
            "<field>" +
            "<name>homepage</name>" +
            "<value>-</value>" +
            "</field>" +
            "</survey>";

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
    public void treeLevelOrder() throws XMLDBException {

        // write document to the database
        store(DOC1, DOC1_NAME);
        // read document back from database
        Node root = load(DOC1_NAME);
        assertNotNull("Document " + DOC1_NAME + " was not found in the database!", root);

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
                        String nameText = name.getTextContent();
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
     * @param document the document name
     */
    private void store(final String xml, final String document) throws XMLDBException {
        final XQueryService service = (XQueryService) server.getRoot().getService("XQueryService", "1.0");

        final StringBuilder query = new StringBuilder();
        query.append("declare namespace xmldb=\"http://exist-db.org/xquery/xmldb\";");
        query.append("let $isLoggedIn := xmldb:login('" + XmldbURI.ROOT_COLLECTION + "', '" + TestUtils.ADMIN_DB_USER + "', '" + TestUtils.ADMIN_DB_PWD + "'),");
        query.append("$doc := xmldb:store('" + XmldbURI.ROOT_COLLECTION + "', $document, $survey)");
        query.append("return <result/>");

        service.declareVariable("survey", xml);
        service.declareVariable("document", document);
        CompiledExpression cQuery = service.compile(query.toString());
        service.execute(cQuery);
    }

    /**
     * Loads the xml document identified by <code>document</code> from the database.
     *
     * @param document the document to load
     */
    private Node load(final String document) throws XMLDBException {
        final StringBuilder query = new StringBuilder();
        query.append("let $survey := doc(concat('" + XmldbURI.ROOT_COLLECTION + "', '/', $document))");
        query.append("return ($survey)");

        final XQueryService service = (XQueryService) server.getRoot().getService("XQueryService", "1.0");
        service.declareVariable("document", document);
        CompiledExpression cQuery = service.compile(query.toString());
        final ResourceSet set = service.execute(cQuery);
        assertNotNull(set);
        assertTrue(set.getSize() > 0);
        return ((XMLResource) set.getIterator().nextResource()).getContentAsDOM();
    }
}