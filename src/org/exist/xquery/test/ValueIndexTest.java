/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
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
package org.exist.xquery.test;

import java.io.File;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

import junit.framework.TestCase;

/**
 * @author wolf
 */
public class ValueIndexTest extends TestCase {

    private final static String URI = "xmldb:exist:///db";

    private Collection testCollection;

    protected void setUp() {
        try {
            // initialize driver
            Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);

            Collection root = DatabaseManager.getCollection(URI, "admin", null);
            CollectionManagementService service = (CollectionManagementService) root
                    .getService("CollectionManagementService", "1.0");
            testCollection = service.createCollection("test");
            assertNotNull(testCollection);
            storeXMLFileAndGetQueryService("collection.xconf", "src/org/exist/xquery/test/collection.xconf");
        } catch (ClassNotFoundException e) {
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
        } catch (XMLDBException e) {
            e.printStackTrace();
        }
    }

    public void testStrings() throws Exception {
        XPathQueryService service = storeXMLFileAndGetQueryService("items.xml", "src/org/exist/xquery/test/items.xml");
        
        queryResource(service, "items.xml", "//item[name = 'Racing Bicycle']", 1);
        queryResource(service, "items.xml", "//item[name > 'Racing Bicycle']", 4);
        queryResource(service, "items.xml", "//item[itemno = 3]", 1);
        queryResource(service, "items.xml", "//item[stock <= 10]", 5);
        queryResource(service, "items.xml", "//item[stock > 20]", 1);
        queryResource(service, "items.xml", "declare namespace x=\"http://www.foo.com\"; //item[x:rating > 8.0]", 2);
    }
    
    private ResourceSet queryResource(XPathQueryService service,
            String resource, String query, int expected) throws XMLDBException {
        return queryResource(service, resource, query, expected, null);
    }

    /**
     * @param service
     * @throws XMLDBException
     */
    private ResourceSet queryResource(XPathQueryService service,
            String resource, String query, int expected, String message)
            throws XMLDBException {
        ResourceSet result = service.queryResource(resource, query);
        if (message == null)
            assertEquals(expected, result.getSize());
        else
            assertEquals(message, expected, result.getSize());
        return result;
    }

    /**
     * @return
     * @throws XMLDBException
     */
    private XPathQueryService storeXMLFileAndGetQueryService(
            String documentName, String path) throws XMLDBException {
        XMLResource doc = (XMLResource) testCollection.createResource(
                documentName, "XMLResource");
        File f = new File(path);
        doc.setContent(f);
        testCollection.storeResource(doc);
        XPathQueryService service = (XPathQueryService) testCollection
                .getService("XPathQueryService", "1.0");
        return service;
    }
    
    public static void main(String[] args) {
		junit.textui.TestRunner.run(ValueIndexTest.class);
	}
}