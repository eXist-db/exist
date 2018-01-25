/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xmldb;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xmldb.api.base.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.modules.XMLResource;

import static org.junit.Assert.assertNotNull;

/**
 * Tests the TreeLevelOrder function.
 *
 * @author Tobias Wunden
 * @version 1.0
 */

public class TreeLevelOrderTest {

    /**
     * eXist database url
     */
    static final String eXistUrl = "xmldb:exist://";

    static Method getNodeValueMethod = null;

    static {
        try {
            getNodeValueMethod = Node.class.getMethod("getNodeValue", (Class[]) null);
        } catch (Exception ex) {
        }
    }

    private Collection root = null;

    public static String nodeValue(Node n) {
        if (getNodeValueMethod != null) {
            try {
                return (String) getNodeValueMethod.invoke(n, (Object[]) null);
            } catch (IllegalArgumentException ex) {
                ex.printStackTrace();
                return null;
            } catch (InvocationTargetException ex) {
                ex.printStackTrace();
                return null;
            } catch (IllegalAccessException ex) {
                ex.printStackTrace();
                return null;
            }
        }
        if (n.getNodeType() == Node.ELEMENT_NODE) {
            StringBuffer builder = new StringBuffer();
            Node current = n.getFirstChild();
            while (current != null) {
                int type = current.getNodeType();
                if (type == Node.CDATA_SECTION_NODE || type == Node.TEXT_NODE) {
                    builder.append(current.getNodeValue());
                }
                current = current.getNextSibling();
            }
            return builder.toString();
        } else {
            return n.getNodeValue();
        }
    }

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
    public final void treeLevelOrder() throws XMLDBException, IllegalAccessException, InstantiationException, ClassNotFoundException {
        String document = "survey.xml";
        EXistXQueryService service = null;

        // Obtain XQuery service
        service = getXQueryService();
        assertNotNull(service);
        // create document
        StringBuilder xmlDocument = new StringBuilder();
        xmlDocument.append("<survey>");
        xmlDocument.append("<date>2004/11/24 17:42:31 GMT</date>");
        xmlDocument.append("<from><![CDATA[tobias.wunden@o2it.ch]]></from>");
        xmlDocument.append("<to><![CDATA[tobias.wunden@o2it.ch]]></to>");
        xmlDocument.append("<subject><![CDATA[Test]]></subject>");
        xmlDocument.append("<field>");
        xmlDocument.append("<name><![CDATA[homepage]]></name>");
        xmlDocument.append("<value><![CDATA[-]]></value>");
        xmlDocument.append("</field>");
        xmlDocument.append("</survey>");
        // write document to the database
        store(xmlDocument.toString(), service, document);

        // read document back from database
        Node elem = load(service, document);
        assertNotNull(elem);

        //get node using DOM
        String strTo = null;

        NodeList rootChildren = elem.getChildNodes();
        for (int r = 0; r < rootChildren.getLength(); r++) {
            if (rootChildren.item(r).getLocalName().equals("to")) {
                Node to = rootChildren.item(r);

                //strTo = to.getTextContent();
                strTo = nodeValue(to);
            }
        }

        assertNotNull(strTo);
    }

    /**
     * Stores the given xml fragment into the database.
     *
     * @param xml      the xml document
     * @param service  the xquery service
     * @param document the document name
     */
    private final void store(String xml, EXistXQueryService service, String document) throws XMLDBException {
        StringBuilder query = new StringBuilder();
        query.append("xquery version \"1.0\";");
        query.append("declare namespace xdb=\"http://exist-db.org/xquery/xmldb\";");
        query.append("let $isLoggedIn := xdb:login('" + eXistUrl + XmldbURI.ROOT_COLLECTION + "', 'admin', ''),");
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
     * @param service  the xquery service
     * @param document the document to load
     */
    private final Node load(EXistXQueryService service, String document) throws XMLDBException {
        StringBuilder query = new StringBuilder();
        query.append("xquery version \"1.0\";");
        query.append("let $survey := xmldb:document(string-join(('" + XmldbURI.ROOT_COLLECTION + "', $document), '/'))");
        query.append("return $survey");

        service.declareVariable("document", document);
        CompiledExpression cQuery = service.compile(query.toString());
        ResourceSet set = service.execute(cQuery);
        if (set != null && set.getSize() > 0) {
            return ((XMLResource) set.getIterator().nextResource()).getContentAsDOM();
        }
        return null;
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
