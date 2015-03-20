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
 *
 * $Id$
 */
package org.exist.validation;

import org.exist.security.Account;
import org.exist.security.Permission;
import org.junit.*;
import static org.junit.Assert.*;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

/**
 *  Created collections needed for validation tests.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class DatabaseCollectionTest {
    
    private final static String ROOT_URI = XmldbURI.LOCAL_DB;
    private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

    private final static String TEST_COLLECTION = "testValidationDatabaseCollection";

    public final static String ADMIN_UID = "admin";
    public final static String ADMIN_PWD = "";

    public final static String GUEST_UID = "guest";

    @Before
    public void setUp() {
        try {
            Class<?> cl = Class.forName(DRIVER);
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            Collection root = DatabaseManager.getCollection(ROOT_URI, ADMIN_UID, ADMIN_PWD);
            CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
            Collection test = cms.createCollection(TEST_COLLECTION);
            UserManagementService ums = (UserManagementService) test.getService("UserManagementService", "1.0");
            // change ownership to guest
            Account guest = ums.getAccount(GUEST_UID);
            ums.chown(guest, guest.getPrimaryGroup());
            ums.chmod(Permission.DEFAULT_COLLECTION_PERM);

            assertNotNull("Could not connect to database.");
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @After
    public void tearDown() throws Exception {

        //delete the test collection
        Collection root = DatabaseManager.getCollection(ROOT_URI, ADMIN_UID, ADMIN_PWD);
        CollectionManagementService cms = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
        cms.removeCollection(TEST_COLLECTION);

        DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
    }
    
    @Test
    public void createCollections() throws XMLDBException {

        Collection testCollection = DatabaseManager.getCollection(ROOT_URI + "/" + TEST_COLLECTION);
        CollectionManagementService service = (CollectionManagementService) testCollection.getService("CollectionManagementService", "1.0");
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