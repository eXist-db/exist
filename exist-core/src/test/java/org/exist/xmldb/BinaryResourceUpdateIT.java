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

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotNull;

/**
 * Integration test: replacing a binary resource with an XML resource using the same document name.
 * This scenario exhibits platform-specific behaviour (notably on Windows) and is run in Failsafe
 * alongside other integration tests.
 */
public class BinaryResourceUpdateIT {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String TEST_COLLECTION = "testBinaryResource";
    private static final int REPEAT = 10;

    private Collection testCollection;
    private URL binFile;
    private URL xmlFile;

    @Before
    public void setUp() throws Exception {
        final CollectionManagementService service = existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        testCollection = service.createCollection(TEST_COLLECTION);
        assertNotNull(testCollection);
        binFile = getClass().getClassLoader().getResource("org/exist/xmldb/test.bin");
        assertNotNull(binFile);
        xmlFile = getClass().getClassLoader().getResource("org/exist/xmldb/test.xml");
        assertNotNull(xmlFile);
    }

    @After
    public void tearDown() throws XMLDBException {
        final CollectionManagementService service = testCollection.getParentCollection().getService(CollectionManagementService.class);
        service.removeCollection(TEST_COLLECTION);
        testCollection = null;
        binFile = null;
        xmlFile = null;
    }

    @Test
    public void updateBinarySameName() throws XMLDBException, URISyntaxException {
        for (int i = 0; i < REPEAT; i++) {
            BinaryResource binaryResource = testCollection.createResource("test.xml", BinaryResource.class);
            binaryResource.setContent(Paths.get(binFile.toURI()));
            testCollection.storeResource(binaryResource);

            Resource resource = testCollection.getResource("test.xml");
            assertNotNull(resource);

            XMLResource xmlResource = testCollection.createResource("test.xml", XMLResource.class);
            xmlResource.setContent(Paths.get(xmlFile.toURI()));
            testCollection.storeResource(xmlResource);

            resource = testCollection.getResource("test.xml");
            assertNotNull(resource);
        }
    }
}
