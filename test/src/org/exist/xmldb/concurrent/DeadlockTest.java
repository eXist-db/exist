/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xmldb.concurrent;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import static org.junit.Assert.assertNotNull;

public class DeadlockTest {

    public static final String DOCUMENT_CONTENT = "<document>\n"
            + "  <element1>value1</element1>\n"
            + "  <element2>value2</element2>\n"
            + "  <element3>value3</element3>\n"
            + "  <element4>value4</element4>\n" + "</document>\n";

    private String rootCollection = XmldbURI.LOCAL_DB;
    private Collection root;

    @Test
    public void deadlock() throws Exception {
        int threads = 20;
        int resources = 200;

        Thread[] writerThreads = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            writerThreads[i] = new WriterThread(rootCollection, resources);
            writerThreads[i].setName("T" + i);
            writerThreads[i].start();
        }
        for (int i = 0; i < threads; i++) {
            writerThreads[i].join();
        }
    }

    @Before
    public void setUp() throws ClassNotFoundException, XMLDBException, IllegalAccessException, InstantiationException {
        String driver = "org.exist.xmldb.DatabaseImpl";
        Class<?> cl = Class.forName(driver);
        Database database = (Database) cl.newInstance();
        assertNotNull(database);
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        root = DatabaseManager.getCollection(rootCollection, "admin", "");
        assertNotNull(root);
    }

    @After
    public void tearDown() throws XMLDBException {
        DatabaseInstanceManager manager =
            (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        assertNotNull(manager);
        manager.shutdown();
    }

    public static class WriterThread extends Thread {
        protected Collection collection = null;

        protected int resources = 0;

        public WriterThread(String collectionURI, int resources) throws Exception {
            this.collection = DatabaseManager.getCollection(collectionURI);
            this.resources = resources;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < resources; i++) {
                    XMLResource document = (XMLResource) collection
                            .createResource(Thread.currentThread().getName()
                                    + "_" + i, "XMLResource");
                    document.setContent(DOCUMENT_CONTENT);
                    System.out.print("storing document " + document.getId()
                            + "\n");
                    collection.storeResource(document);
                }
            } catch (Exception e) {
                System.err.println("Writer " + Thread.currentThread().getName()
                        + " failed: " + e);
                e.printStackTrace();
            }
        }
    }
}