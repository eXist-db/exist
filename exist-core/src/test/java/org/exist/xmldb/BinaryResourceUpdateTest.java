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

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.base.Resource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.XMLResource;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;
import static org.junit.Assert.assertNotNull;

public class BinaryResourceUpdateTest  {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private final static String TEST_COLLECTION = "testBinaryResource";

    private Collection testCollection;

    private static final int REPEAT = 10;

    private URL binFile;
    private URL xmlFile;

    @Test
    public void updateBinary() throws XMLDBException, URISyntaxException {
        for (int i = 0; i < REPEAT; i++) {
            BinaryResource binaryResource = (BinaryResource)testCollection.createResource("test1.xml", "BinaryResource");
            binaryResource.setContent(Paths.get(binFile.toURI()));
            testCollection.storeResource(binaryResource);

            Resource resource = testCollection.getResource("test1.xml");
            assertNotNull(resource);

            XMLResource xmlResource = (XMLResource) testCollection.createResource("test2.xml", "XMLResource");
            xmlResource.setContent(Paths.get(xmlFile.toURI()));
            testCollection.storeResource(xmlResource);

            resource = testCollection.getResource("test2.xml");
            assertNotNull(resource);
        }
        
    }

    // with same docname test fails for windows
    @Test
    public void updateBinary_windows() throws XMLDBException, URISyntaxException {
        for (int i = 0; i < REPEAT; i++) {
            BinaryResource binaryResource = (BinaryResource)testCollection.createResource("test.xml", "BinaryResource");
            binaryResource.setContent(Paths.get(binFile.toURI()));
            testCollection.storeResource(binaryResource);

            Resource resource = testCollection.getResource("test.xml");
            assertNotNull(resource);

            XMLResource xmlResource = (XMLResource) testCollection.createResource("test.xml", "XMLResource");
            xmlResource.setContent(Paths.get(xmlFile.toURI()));
            testCollection.storeResource(xmlResource);

            resource = testCollection.getResource("test.xml");
            assertNotNull(resource);
            
        }
    }

    @Before
    public void setUp() throws Exception {
        final CollectionManagementService service = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        testCollection = service.createCollection(TEST_COLLECTION);
        assertNotNull(testCollection);
        binFile = getClass().getClassLoader().getResource("org/exist/xmldb/test.bin");
        assertNotNull(binFile);
        xmlFile = getClass().getClassLoader().getResource("org/exist/xmldb/test.xml");
        assertNotNull(xmlFile);
    }

    @After
    public void tearDown() throws XMLDBException {
        //delete the test collection
        final CollectionManagementService service = (CollectionManagementService)testCollection.getParentCollection().getService("CollectionManagementService", "1.0");
        service.removeCollection(TEST_COLLECTION);
        binFile = null;
        xmlFile = null;
    }
}