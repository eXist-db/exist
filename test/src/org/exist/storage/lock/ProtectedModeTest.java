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
package org.exist.storage.lock;

import org.exist.TestDataGenerator;
import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XPathQueryServiceImpl;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import java.io.File;
import java.util.Random;

public class ProtectedModeTest {

    private final static int COLLECTION_COUNT = 20;
    private final static int DOCUMENT_COUNT = 20;

    private final static String generateXQ = "<book id=\"{$filename}\" n=\"{$count}\">"
			+ "   <chapter>"
			+ "       <title>{pt:random-text(7)}</title>"
			+ "       {"
			+ "           for $section in 1 to 8 return"
			+ "               <section id=\"sect{$section}\">"
			+ "                   <title>{pt:random-text(7)}</title>"
			+ "                   {"
			+ "                       for $para in 1 to 10 return"
			+ "                           <para>{pt:random-text(40)}</para>"
			+ "                   }"
			+ "               </section>"
			+ "       }"
			+ "   </chapter>" + "</book>";

    @Test
    public void queryCollection() {
        try {
            Collection root = DatabaseManager.getCollection("xmldb:exist:///db/protected", "admin", null);
            XPathQueryServiceImpl service = (XPathQueryServiceImpl) root.getService("XQueryService", "1.0");
            try {
                service.beginProtected();
                ResourceSet result = service.query("collection('/db/protected/test5')//book");
                assertEquals(result.getSize(), DOCUMENT_COUNT);
            } finally {
                service.endProtected();
            }
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void queryRoot() {
        try {
            Collection root = DatabaseManager.getCollection("xmldb:exist:///db/protected", "admin", null);
            XPathQueryServiceImpl service = (XPathQueryServiceImpl) root.getService("XQueryService", "1.0");
            try {
                service.beginProtected();
                ResourceSet result = service.query("//book");
                assertEquals(result.getSize(), COLLECTION_COUNT * DOCUMENT_COUNT);
            } finally {
                service.endProtected();
            }
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void queryDocs() {
        try {
            Collection root = DatabaseManager.getCollection("xmldb:exist:///db/protected", "admin", null);
            XPathQueryServiceImpl service = (XPathQueryServiceImpl) root.getService("XQueryService", "1.0");
            Random random = new Random();
            for (int i = 0; i < COLLECTION_COUNT; i++) {
                String docURI = "doc('/db/protected/test" + i + "/xdb" + random.nextInt(DOCUMENT_COUNT) + ".xml')";
                try {
                    service.beginProtected();
                    ResourceSet result = service.query(docURI + "//book");
                    assertEquals(result.getSize(), 1);
                } finally {
                    service.endProtected();
                }
            }
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @BeforeClass
    public static void initDB() {
        // initialize XML:DB driver
        try {
            Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);

            Collection root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
            CollectionManagementService mgmt = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            Collection collection = mgmt.createCollection("protected");
            
            XMLResource hamlet = (XMLResource) collection.createResource("hamlet.xml", "XMLResource");
            hamlet.setContent(new File("samples/shakespeare/hamlet.xml"));
            collection.storeResource(hamlet);

            mgmt = (CollectionManagementService) collection.getService("CollectionManagementService", "1.0");
            
            TestDataGenerator generator = new TestDataGenerator("xdb", DOCUMENT_COUNT);
            for (int i = 0; i < COLLECTION_COUNT; i++) {
                Collection currentColl = mgmt.createCollection("test" + i);
                System.out.println("Generating " + DOCUMENT_COUNT + " files...");
                File[] files = generator.generate(currentColl, generateXQ);
                for (int j = 0; j < files.length; j++) {
                    XMLResource resource = (XMLResource) currentColl.createResource("xdb" + j + ".xml", "XMLResource");
                    resource.setContent(files[j]);
                    currentColl.storeResource(resource);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void closeDB() {
        try {
            Collection root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
            CollectionManagementService cmgr = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            cmgr.removeCollection("protected");
            
            DatabaseInstanceManager mgr = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
            mgr.shutdown();
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
