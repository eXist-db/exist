/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist-db Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.validation;

import org.exist.TestUtils;
import org.exist.security.Account;
import org.exist.security.Permission;
import org.exist.test.ExistXmldbEmbeddedServer;

import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import static org.junit.Assert.assertNotNull;

/**
 *  Created collections needed for validation tests.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class DatabaseCollectionTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existServer = new ExistXmldbEmbeddedServer(false, true, true);

    private final static String ROOT_URI = XmldbURI.LOCAL_DB;
    private final static String TEST_COLLECTION = "testValidationDatabaseCollection";

    @Before
    public void setUp() throws XMLDBException {
        final CollectionManagementService cms = (CollectionManagementService)existServer.getRoot().getService("CollectionManagementService", "1.0");
        final Collection test = cms.createCollection(TEST_COLLECTION);
        final UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");

        // change ownership to guest
        final Account guest = ums.getAccount(TestUtils.GUEST_DB_USER);
        ums.chown(guest, guest.getPrimaryGroup());
        ums.chmod(Permission.DEFAULT_COLLECTION_PERM);
    }

    @After
    public void tearDown() throws XMLDBException {
        //delete the test collection
        final CollectionManagementService cms = (CollectionManagementService)existServer.getRoot().getService("CollectionManagementService", "1.0");
        cms.removeCollection(TEST_COLLECTION);
    }
    
    @Test
    public void createCollections() throws XMLDBException {
        final Collection testCollection = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        final CollectionManagementService service = (CollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
        Collection validationCollection = service.createCollection(TestTools.VALIDATION_HOME_COLLECTION);
        assertNotNull(validationCollection);

        validationCollection = service.createCollection(TestTools.VALIDATION_HOME_COLLECTION + "/" + TestTools.VALIDATION_TMP_COLLECTION);
        assertNotNull(validationCollection);

        validationCollection = service.createCollection(TestTools.VALIDATION_HOME_COLLECTION + "/" + TestTools.VALIDATION_XSD_COLLECTION);
        assertNotNull(validationCollection);

        validationCollection = service.createCollection(TestTools.VALIDATION_HOME_COLLECTION + "/" + TestTools.VALIDATION_DTD_COLLECTION);
        assertNotNull(validationCollection);
    }
}
