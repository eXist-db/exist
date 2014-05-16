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
 *  $Id: BinaryResourceUpdateTest.java 11148 2010-02-07 14:37:35Z dizzzz $
 */
package org.exist.xmldb;

import org.exist.test.TestConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import static org.exist.xmldb.XmldbLocalTests.*;

public class CollectionTest {

    @Test
    public void create() throws XMLDBException {
        Collection root = DatabaseManager.getCollection(ROOT_URI, ADMIN_UID, ADMIN_PWD);
        CollectionManagementService service = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
        Collection testCollection = service.createCollection(TestConstants.SPECIAL_NAME);
        assertNotNull(testCollection);
    }

    @Test
    public void testRead() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(ROOT_URI, ADMIN_UID, ADMIN_PWD);
        assertNotNull(test);
        Collection root = DatabaseManager.getCollection(ROOT_URI, ADMIN_UID, ADMIN_PWD);
        test = root.getChildCollection(TestConstants.SPECIAL_NAME);
        assertNotNull(test);
        CollectionManagementService service = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
        service.removeCollection(TestConstants.SPECIAL_NAME);
    }

    @Before
    public void setUp() throws Exception {
        // initialize driver
        Class<?> cl = Class.forName(DRIVER);
        Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
    }

    @After
    public void tearDown() throws XMLDBException {
        Collection root = DatabaseManager.getCollection(ROOT_URI, ADMIN_UID, ADMIN_PWD);
        DatabaseInstanceManager mgr = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        mgr.shutdown();
    }
}
