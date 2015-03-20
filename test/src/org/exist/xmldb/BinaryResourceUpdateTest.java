/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2011 The eXist-db Project
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
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.xmldb;

import org.junit.After;
import org.junit.Before;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.base.Resource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.XMLResource;

import java.io.File;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.exist.xmldb.XmldbLocalTests.*;

public class BinaryResourceUpdateTest  {

    private final static String TEST_COLLECTION = "testBinaryResource";

    private Database database;
    private Collection testCollection;

    private static final int REPEAT = 10;

    private static final File binFile;
    private static final File xmlFile;

    static {
      binFile = new File(getExistDir(), "LICENSE");
      xmlFile = new File(getExistDir(), "samples/examples.xml");
    }

    @Test
    public void updateBinary() throws XMLDBException {
        for (int i = 0; i < REPEAT; i++) {
            BinaryResource binaryResource = (BinaryResource)testCollection.createResource("test1.xml", "BinaryResource");
            binaryResource.setContent(binFile);
            testCollection.storeResource(binaryResource);

            Resource resource = testCollection.getResource("test1.xml");
            assertNotNull(resource);

            XMLResource xmlResource = (XMLResource) testCollection.createResource("test2.xml", "XMLResource");
            xmlResource.setContent(xmlFile);
            testCollection.storeResource(xmlResource);

            resource = testCollection.getResource("test2.xml");
            assertNotNull(resource);
        }
        
    }

    // with same docname test fails for windows
    @Test
    public void updateBinary_windows() throws XMLDBException {
        for (int i = 0; i < REPEAT; i++) {
            BinaryResource binaryResource = (BinaryResource)testCollection.createResource("test.xml", "BinaryResource");
            binaryResource.setContent(binFile);
            testCollection.storeResource(binaryResource);

            Resource resource = testCollection.getResource("test.xml");
            assertNotNull(resource);

            XMLResource xmlResource = (XMLResource) testCollection.createResource("test.xml", "XMLResource");
            xmlResource.setContent(xmlFile);
            testCollection.storeResource(xmlResource);

            resource = testCollection.getResource("test.xml");
            assertNotNull(resource);
            
        }
    }

    @Before
    public void setUp() throws Exception {
        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        Collection root = DatabaseManager.getCollection(ROOT_URI, ADMIN_UID, ADMIN_PWD);
        CollectionManagementService service = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
        testCollection = service.createCollection(TEST_COLLECTION);
        assertNotNull(testCollection);
    }

    @After
    public void tearDown() throws XMLDBException {

        //delete the test collection
        CollectionManagementService service = (CollectionManagementService)testCollection.getParentCollection().getService("CollectionManagementService", "1.0");
        service.removeCollection(TEST_COLLECTION);

        //shutdownDB the db
        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim = (DatabaseInstanceManager) testCollection.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();

        testCollection = null;
        database = null;
    }
}