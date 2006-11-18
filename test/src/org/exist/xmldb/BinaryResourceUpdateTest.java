/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  \$Id\$
 */
package org.exist.xmldb;

import org.xmldb.api.base.Database;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.base.Resource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.XMLResource;
import org.exist.storage.DBBroker;
import org.apache.log4j.BasicConfigurator;
import junit.framework.TestCase;
import junit.textui.TestRunner;

import java.io.File;

public class BinaryResourceUpdateTest extends TestCase {
    
    private Database database;
    private Collection testCollection;

    private static File binFile = null;
    private static File xmlFile = null;

    private static final int REPEAT = 10;

    static {
      String existHome = System.getProperty("exist.home");
      File existDir = existHome==null ? new File(".") : new File(existHome);
      binFile = new File(existDir, "LICENSE");
      xmlFile = new File(existDir, "samples/examples.xml");
    }

    public void testUpdateBinary() {
        try {
            for (int i = 0; i < REPEAT; i++) {
                BinaryResource binaryResource = (BinaryResource)
                        testCollection.createResource("test.xml", "BinaryResource");
                binaryResource.setContent(binFile);
                testCollection.storeResource(binaryResource);

                Resource resource = testCollection.getResource("test.xml");
                assertNotNull(resource);
                System.out.println("Content:\n" + resource.getContent().toString());

                XMLResource xmlResource = (XMLResource) testCollection.createResource("test.xml", "XMLResource");
                xmlResource.setContent(xmlFile);
                testCollection.storeResource(xmlResource);

                resource = testCollection.getResource("test.xml");
                assertNotNull(resource);
                System.out.println("Content:\n" + resource.getContent().toString());
            }
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    protected void setUp() {
		try {
			// initialize driver
			Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);

            Collection root =
				DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin",	null);
			CollectionManagementService service =
				(CollectionManagementService) root.getService("CollectionManagementService", "1.0");
			testCollection = service.createCollection("test");
			assertNotNull(testCollection);

		} catch (ClassNotFoundException e) {
		} catch (InstantiationException e) {
		} catch (IllegalAccessException e) {
		} catch (XMLDBException e) {
			e.printStackTrace();
		}
	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
        CollectionManagementService service = (CollectionManagementService)
            testCollection.getParentCollection().getService("CollectionManagementService", "1.0");
        service.removeCollection("/db/test");


        DatabaseManager.deregisterDatabase(database);
		DatabaseInstanceManager dim =
			(DatabaseInstanceManager) testCollection.getService(
				"DatabaseInstanceManager", "1.0");
		dim.shutdown();
        testCollection = null;
        database = null;

		System.out.println("tearDown PASSED");
	}

    public static void main(String[] args) {
        BasicConfigurator.configure();
        TestRunner.run(BinaryResourceUpdateTest.class);
    }
}
