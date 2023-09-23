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
package org.exist.xmlrpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.exist.TestUtils;
import org.exist.test.ExistWebServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

public class MimeTypeTest {

    @ClassRule
    public final static ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private final static String COLLECTION_NAME = "rpctest";
    private static final String DOCUMENT_NAME = "myxmldoc";
    private final static String XML_CONTENT = "<xml><it><is></is></it></xml>";

    private static String getBaseUri() {
        return "xmldb:exist://localhost:" + existWebServer.getPort() + "/xmlrpc";
    }

    @Test
    public void testXMLMimeType() throws XMLDBException {
        // store an XML document without an .xml extension
        try(Collection collection = DatabaseManager.getCollection(getBaseUri() + "/db/" + COLLECTION_NAME, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)){
            final XMLResource resource = (XMLResource)collection.createResource(DOCUMENT_NAME, XMLResource.RESOURCE_TYPE);
            resource.setContent(XML_CONTENT);
            collection.storeResource(resource);
            assertEquals(XMLResource.RESOURCE_TYPE, resource.getResourceType());
        }

        // retrieve the document and verify its resource type
        try(Collection collection = DatabaseManager.getCollection(getBaseUri() + "/db/" + COLLECTION_NAME, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)){
            Resource resource = collection.getResource(DOCUMENT_NAME);
            assertEquals(XMLResource.RESOURCE_TYPE, resource.getResourceType());
        }
    }

    @BeforeClass
    public static void startServer() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException, SAXException {
        // initialize XML:DB driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        Database database = (Database) cl.newInstance();
        DatabaseManager.registerDatabase(database);

        Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);

        CollectionManagementService mgmt = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
        assertNotNull(mgmt.createCollection(COLLECTION_NAME));
    }

    @AfterClass
    public static void stopServer() throws XMLDBException {
        Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        CollectionManagementService mgmt = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
        mgmt.removeCollection(COLLECTION_NAME);

        Collection config = DatabaseManager.getCollection(getBaseUri() + "/db/system/config/db", "admin", "");
        mgmt = (CollectionManagementService) config.getService("CollectionManagementService", "1.0");
        mgmt.removeCollection(COLLECTION_NAME);
    }
}
