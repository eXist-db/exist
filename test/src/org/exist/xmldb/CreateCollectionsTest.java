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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.exist.TestUtils;
import org.exist.dom.persistent.XMLUtil;
import org.exist.security.Account;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.XMLFilenameFilter;
import org.junit.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import static org.exist.xmldb.XmldbLocalTests.*;

public class CreateCollectionsTest  {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true);

    private final static String TEST_COLLECTION = "testCreateCollection";

    @Before
    public void setUp() throws XMLDBException {
        //create a test collection
        final CollectionManagementService cms = (CollectionManagementService)existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        final Collection test = cms.createCollection(TEST_COLLECTION);
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
        // change ownership to guest
        Account guest = ums.getAccount(GUEST_UID);
        ums.chown(guest, guest.getPrimaryGroup());
        ums.chmod("rwxrwxrwx");
    }

    @After
    public void tearDown() throws XMLDBException {
        //delete the test collection
        final CollectionManagementService cms = (CollectionManagementService)existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        cms.removeCollection(TEST_COLLECTION);
    }

    @Test
    public void rootCollectionHasNoParent() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(ROOT_URI, ADMIN_UID, ADMIN_PWD);
        assertNull("root collection has no parent", root.getParentCollection());
    }

    @Test
    public void collectionMustProvideAtLeastOneService() throws XMLDBException {
        final Collection colTest = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        final Service[] services = colTest.getServices();
        assertTrue("Collection must provide at least one Service", services != null && services.length > 0);
    }

    @Test
    public void createCollection_hasNoSubCollections_andIsOpen() throws XMLDBException {
        final Collection colTest = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        final CollectionManagementService service = (CollectionManagementService) colTest.getService("CollectionManagementService", "1.0");
        final Collection testCollection = service.createCollection("test");
        assertNotNull(testCollection);

        assertEquals("Created Collection has zero child collections", 0, testCollection.getChildCollectionCount());
        assertTrue("Created Collection state should be Open after creation", testCollection.isOpen());
    }

    @Test
    public void storeSamplesShakespeare() throws XMLDBException, IOException {
        final Collection colTest = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        final CollectionManagementService service = (CollectionManagementService) colTest.getService("CollectionManagementService", "1.0");
        final Collection testCollection = service.createCollection("test");
        UserManagementService ums = (UserManagementService) testCollection.getService("UserManagementService", "1.0");
        ums.chmod("rwxr-xr-x");

        final List<String> storedResourceNames = new ArrayList<>();
        final List<String> filenames = new ArrayList<>();
        try(final Stream<Path> files = Files.list(TestUtils.shakespeareSamples()).filter(XMLFilenameFilter.asPredicate())) {
            //store the samples
            for (final Path file : files.collect(Collectors.toList())) {
                final Resource res = storeResourceFromFile(file, testCollection);
                storedResourceNames.add(res.getId());
                filenames.add(file.getFileName().toString());
            }
        }

        assertEquals(filenames, storedResourceNames);

        //get a list from the database of stored resource names
        final List<String> retrievedStoredResourceNames = Arrays.asList(testCollection.listResources());

        //order of names from database may not be the order in which the files were loaded!
        Collections.sort(filenames);
        Collections.sort(retrievedStoredResourceNames);

        assertEquals(filenames, retrievedStoredResourceNames);
    }

    @Test
    public void storeRemoveStoreResource() throws XMLDBException, IOException {
        final Collection colTest = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        final CollectionManagementService service = (CollectionManagementService) colTest.getService("CollectionManagementService", "1.0");
        final Collection testCollection = service.createCollection("test");
        UserManagementService ums = (UserManagementService) testCollection.getService("UserManagementService", "1.0");
        ums.chmod("rwxr-xr-x");

        final String testFile = "macbeth.xml";
        storeResourceFromFile(TestUtils.resolveShakespeareSample(testFile), testCollection);
        Resource resMacbeth = testCollection.getResource(testFile);
        assertNotNull("getResource(" + testFile + "\")", resMacbeth);

        final int resourceCount = testCollection.getResourceCount();

        testCollection.removeResource(resMacbeth);
        assertEquals("After removal resource count must decrease", resourceCount - 1, testCollection.getResourceCount());
        resMacbeth = testCollection.getResource(testFile);
        assertNull(resMacbeth);

        // restore the resource just removed
        storeResourceFromFile(TestUtils.resolveShakespeareSample(testFile), testCollection);
        assertEquals("After re-store resource count must increase", resourceCount, testCollection.getResourceCount());
        resMacbeth = testCollection.getResource(testFile);
        assertNotNull("getResource(" + testFile + "\")", resMacbeth);
    }

    @Test
    public void storeBinaryResource() throws XMLDBException, IOException {
        Collection colTest = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        CollectionManagementService service = (CollectionManagementService) colTest.getService("CollectionManagementService", "1.0");
        Collection testCollection = service.createCollection("test");
        UserManagementService ums = (UserManagementService) testCollection.getService("UserManagementService", "1.0");
        ums.chmod("rwxr-xr-x");

        byte[] data = storeBinaryResourceFromFile(TestUtils.getEXistHome().get().resolve("webapp/logo.jpg"), testCollection);
        Object content = testCollection.getResource("logo.jpg").getContent();
        byte[] dataStored = (byte[])content;
        assertArrayEquals("After storing binary resource, data out==data in", data, dataStored);
    }

    private XMLResource storeResourceFromFile(Path file, Collection testCollection) throws XMLDBException, IOException {
        XMLResource res = (XMLResource) testCollection.createResource(file.getFileName().toString(), "XMLResource");
        assertNotNull("storeResourceFromFile", res);
        String xml = XMLUtil.readFile(file, UTF_8);
        res.setContent(xml);
        testCollection.storeResource(res);
        return res;
    }

    private byte[] storeBinaryResourceFromFile(Path file, Collection testCollection) throws XMLDBException, IOException {
        final Resource res = testCollection.createResource(file.getFileName().toString(), "BinaryResource");
        assertNotNull("store binary Resource From File", res);
        // Get an array of bytes from the file:
        final byte[] data = Files.readAllBytes(file);
        res.setContent(data);
        testCollection.storeResource(res);
        return data;
    }

    @Test
    public void testMultipleCreates() throws XMLDBException {
        
        Collection testCol = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        CollectionManagementService cms = (CollectionManagementService)testCol.getService("CollectionManagementService", "1.0");
        assertNotNull(cms);

        cms.createCollection("dummy1");
        Collection c1 = testCol.getChildCollection("dummy1");
        assertNotNull(c1);

        cms.setCollection(c1);
        cms.createCollection("dummy2");
        Collection c2 = c1.getChildCollection("dummy2");
        assertNotNull(c2);

        cms.setCollection(c2);
        cms.createCollection("dummy3");
        Collection c3 = c2.getChildCollection("dummy3");
        assertNotNull(c3);

        cms.setCollection(testCol);
        cms.removeCollection("dummy1");
        c1 = testCol.getChildCollection("dummy1");
        assertNull(c1);
    }
}