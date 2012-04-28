/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
 *  http://exist-db.org
 *  http://exist.sourceforge.net
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
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import org.exist.source.StringSource;
import org.exist.xmldb.XQueryService;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import junit.framework.TestCase;
import junit.textui.TestRunner;

public class QueryPoolTest extends TestCase {

    private final static String URI = XmldbURI.LOCAL_DB;
    
    private Collection testCollection;
    
    public static void main(String[] args) {
        TestRunner.run(QueryPoolTest.class);
    }
    
    public void testDifferentQueries() {
        try {
            XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
            for (int i = 0; i < 10000; i++) {
                System.out.println("Inserting node " + i + "...");
                String query = "update insert <node id='id" + Integer.toHexString(i) + "'>" +
                        "<p>Some longer text <b>content</b> in this node. Some longer text <b>content</b> in this node. " +
                        "Some longer text <b>content</b> in this node. Some longer text <b>content</b> in this node.</p>" +
                        "</node> " +
                        "into //test[@id = 't1']";
                service.execute(new StringSource(query));
            }
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testRead() {
        try {
            XMLResource res = (XMLResource) testCollection.getResource("large_list.xml");
            assertNotNull(res);
            System.out.println(res.getContent());
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    protected void setUp() {
        try {
            // initialize driver
            Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            
            Collection root =
                DatabaseManager.getCollection(
                    URI,
                    "admin",
                    null);
            CollectionManagementService service =
                (CollectionManagementService) root.getService(
                    "CollectionManagementService",
                    "1.0");
            testCollection = service.createCollection("test-pool");
            assertNotNull(testCollection);
            
          XMLResource doc =
              (XMLResource) testCollection.createResource("large_list.xml", "XMLResource");
          doc.setContent("<test id='t1'/>");
          testCollection.storeResource(doc);
        } catch (ClassNotFoundException e) {
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
        } catch (XMLDBException e) {
            e.printStackTrace();
        }
    }
    
    protected void tearDown() throws Exception {
        try {
            CollectionManagementService service = (CollectionManagementService)
                testCollection.getService("CollectionManagementService", "1.0");
            service.removeCollection("/db/test-pool");
            
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
