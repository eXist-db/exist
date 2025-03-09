/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
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

/**
 * Tests the TreeLevelOrder function.
 *
 * @author Tobias Wunden
 * @version 1.0
 */

public class TreeLevelOrderTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String DOC1_NAME = "survey.xml";

    private static final String DOC1 =
            "<survey>" +
            "<date>2004/11/24 17:42:31 GMT</date>" +
            "<from><![CDATA[tobias.wunden@o2it.ch]]></from>" +
            "<to><![CDATA[tobias.wunden@o2it.ch]]></to>" +
            "<subject><![CDATA[Test]]></subject>" +
            "<field>" +
            "<name><![CDATA[homepage]]></name>" +
            "<value><![CDATA[-]]></value>" +
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
    public void treeLevelOrder() throws XMLDBException, IllegalAccessException, InstantiationException, ClassNotFoundException {
        // create document
        // write document to the database
        store(DOC1, DOC1_NAME);

        // read document back from database
        Node elem = load(DOC1_NAME);
        assertNotNull(elem);

        //get node using DOM
        String strTo = null;

        NodeList rootChildren = elem.getChildNodes();
        for (int r = 0; r < rootChildren.getLength(); r++) {
            if ("to".equals(rootChildren.item(r).getLocalName())) {
                Node to = rootChildren.item(r);
                strTo = to.getTextContent();
                break;
            }
        }

        assertNotNull(strTo);
    }

    /**
     * Stores the given xml fragment into the database.
     *
     * @param xml      the xml document
     * @param document the document name
     */
    private void store(final String xml, final String document) throws XMLDBException {
        final StringBuilder query = new StringBuilder();
        query.append("declare namespace xmldb='http://exist-db.org/xquery/xmldb';");
        query.append("let $isLoggedIn := xmldb:login('" + XmldbURI.ROOT_COLLECTION + "', '" + TestUtils.ADMIN_DB_USER + "', '" + TestUtils.ADMIN_DB_PWD + "'),");
        query.append("$doc := xmldb:store('" + XmldbURI.ROOT_COLLECTION + "', $document, $survey)");
        query.append("return <result/>");

        final XQueryService service = server.getRoot().getService(XQueryService.class);
        service.declareVariable("survey", xml);
        service.declareVariable("document", document);
        final CompiledExpression cQuery = service.compile(query.toString());
        service.execute(cQuery);
    }

    /**
     * Loads the xml document identified by <code>document</code> from the database.
     *
     * @param document the document to load
     */
    private Node load(final String document) throws XMLDBException {
        final StringBuilder query = new StringBuilder();
        query.append("let $survey := doc(string-join(('" + XmldbURI.ROOT_COLLECTION + "', $document), '/'))");
        query.append("return $survey");

        final XQueryService service = server.getRoot().getService(XQueryService.class);
        service.declareVariable("document", document);
        final CompiledExpression cQuery = service.compile(query.toString());
        final ResourceSet set = service.execute(cQuery);
        if (set != null && set.getSize() > 0) {
            return ((XMLResource) set.getIterator().nextResource()).getContentAsDOM();
        }
        return null;
    }
}
