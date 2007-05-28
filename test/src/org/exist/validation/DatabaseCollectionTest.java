/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.modules.CollectionManagementService;

/**
 *  Created collections needed for validation tests.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class DatabaseCollectionTest  extends TestCase {
    
    private final static String URI = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
    private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
    private Collection rootCollection = null;
    
    private static Database database =null;
    
    public DatabaseCollectionTest(String testName) {
        super(testName);
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite(DatabaseCollectionTest.class);
        return suite;
    }
    
    public void setUp() {
        try {
            System.out.println(">>> setUp");
            Class cl = Class.forName(DRIVER);
            /* Database */ database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            rootCollection = DatabaseManager.getCollection(URI, "admin", null);
            
            assertNotNull("Could not connect to database.");
            System.out.println("<<<\n");
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
    protected void tearDown() throws Exception {
        
        DatabaseManager.deregisterDatabase(database);
        DatabaseInstanceManager dim =
            (DatabaseInstanceManager) rootCollection.getService(
            "DatabaseInstanceManager", "1.0");
        dim.shutdown();
        database = null;
        
        System.out.println("tearDown PASSED");
    }
    
    
    
    public void testCreateCollections() {
        System.out.println(this.getName());
        try {
            Collection root = DatabaseManager.getCollection(URI, "guest", "guest");
            CollectionManagementService service =
                (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            Collection testCollection = service.createCollection(TestTools.VALIDATION_HOME);
            assertNotNull(testCollection);
            
            testCollection = service.createCollection(TestTools.VALIDATION_TMP);
            assertNotNull(testCollection);
            
            testCollection = service.createCollection(TestTools.VALIDATION_XSD);
            assertNotNull(testCollection);
            
            testCollection = service.createCollection(TestTools.VALIDATION_DTD);
            assertNotNull(testCollection);
            
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
}
