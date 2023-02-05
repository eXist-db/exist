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
import org.exist.security.Account;
import org.exist.security.Permission;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static org.exist.TestUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;


public class CopyMoveTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private final static String TEST_COLLECTION = "testCopyMove";

    @Test
    public void copyResourceChangeName() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
        XMLResource original = testCollection.createResource("original", XMLResource.class);
        original.setContent("<sample/>");
        testCollection.storeResource(original);
        EXistCollectionManagementService cms = testCollection.getService(EXistCollectionManagementService.class);
        cms.copyResource("original", "", "duplicate");
        assertEquals(2, testCollection.getResourceCount());
        XMLResource duplicate = (XMLResource) testCollection.getResource("duplicate");
        assertNotNull(duplicate);
    }

    @Test
    public void queryCopiedResource() throws XMLDBException {
        Collection testCollection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION);
        XMLResource original = testCollection.createResource("original", XMLResource.class);
        original.setContent("<sample/>");
        testCollection.storeResource(original);
        EXistCollectionManagementService cms = testCollection.getService(EXistCollectionManagementService.class);
        cms.copyResource("original", "", "duplicate");
        XMLResource duplicate = (XMLResource) testCollection.getResource("duplicate");
        assertNotNull(duplicate);
        XPathQueryService xq = testCollection.getService(XPathQueryService.class);
        ResourceSet rs = xq.queryResource("duplicate", "/sample");
        assertEquals(1, rs.getSize());
    }
    
    @Test
    public void changePermissionsAfterCopy() throws XMLDBException {
        final String collectionURL = XmldbURI.LOCAL_DB + "/" + TEST_COLLECTION;
        final String originalResource = "original.xml";
        final String copyResource = "copy.xml";
        
        final String resourceURL = collectionURL + "/" + originalResource;
        
        //get collection & services
        EXistCollection col = (EXistCollection)DatabaseManager.getCollection(collectionURL);
        EXistCollectionManagementService service = col.getService(EXistCollectionManagementService.class);
        UserManagementService ums = DatabaseManager.getCollection(collectionURL, ADMIN_DB_USER, ADMIN_DB_PWD).getService(UserManagementService.class);
        
        //store xml document
        XMLResource original = col.createResource(originalResource, XMLResource.class);
        original.setContent("<sample/>");
        col.storeResource(original);

        //get original resource
        Resource orgnRes = col.getResource(originalResource);

        //check permission before copy
        Permission prm = ums.getPermissions(orgnRes);
        assertEquals("rw-r--r--", prm.toString());
        
        //copy
        service.copyResource(XmldbURI.create(resourceURL), col.getPathURI(), XmldbURI.create(copyResource));

        //check permission after copy
        prm = ums.getPermissions(orgnRes);
        assertEquals("rw-r--r--", prm.toString());

        //get copy resource
        Resource copyRes = col.getResource(copyResource);
        
        //change permission on copy
        Account admin = ums.getAccount(ADMIN_DB_USER);
        ums.chown(copyRes, admin, admin.getPrimaryGroup());
        ums.chmod(copyRes, "rwx--x---");
        
        //check permission of copy
        prm = ums.getPermissions(copyRes);
        assertEquals("rwx--x---", prm.toString());

        //check permission of original
        prm = ums.getPermissions(orgnRes);
        assertEquals("rw-r--r--", prm.toString());
    }

    @Before
    public void setUp() throws Exception {
        final CollectionManagementService cms = existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        final Collection testCollection = cms.createCollection(TEST_COLLECTION);
        final UserManagementService ums = testCollection.getService(UserManagementService.class);
        // change ownership to guest
        final Account guest = ums.getAccount(GUEST_DB_USER);
        ums.chown(guest, guest.getPrimaryGroup());
        ums.chmod("rwxr-xr-x");
    }

    @After
    public void tearDown() throws XMLDBException {
        //delete the test collection
        final CollectionManagementService cms = existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        cms.removeCollection(TEST_COLLECTION);
    }
}