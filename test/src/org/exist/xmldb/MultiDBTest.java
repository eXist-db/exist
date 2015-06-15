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
package org.exist.xmldb;

import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.SingleInstanceConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import java.io.File;
import java.io.FileOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author wolf
 *
 */
public class MultiDBTest {

//    public static void main(String[] args) {
//        TestRunner.run(MultiDBTest.class);
//    }
    
    private final static int INSTANCE_COUNT = 5;
    
    private final static String CONFIG =
        "<exist>" +
        "   <db-connection database=\"native\" files=\".\" cacheSize=\"32M\">" +
        "       <pool min=\"1\" max=\"5\" sync-period=\"120000\"/>" +
        "   </db-connection>" +
        "</exist>";

    @Test
    public void store()
       throws Exception
    {
        for (int i = 0; i < INSTANCE_COUNT; i++) {
            Collection root = DatabaseManager.getCollection("xmldb:test" + i + "://" + XmldbURI.ROOT_COLLECTION);
            Collection test = root.getChildCollection("test");
            if (test == null) {
                CollectionManagementService service = (CollectionManagementService)
                    root.getService("CollectionManagementService", "1.0");
                test = service.createCollection("test");
            }

                 String existHome = System.getProperty("exist.home");
                 File existDir = existHome==null ? new File(".") : new File(existHome);
            File samples = new File(existDir,"samples/shakespeare");
            File[] files = samples.listFiles();
            MimeTable mimeTab = MimeTable.getInstance();
            for (int j = 0; j < files.length; j++) {
                MimeType mime = mimeTab.getContentTypeFor(files[j].getName());
                if(mime != null && mime.isXMLType())
                    loadFile(test, files[j].getAbsolutePath());
            }

            doQuery(test, "//SPEECH[SPEAKER='HAMLET']");
        }
    }
    
    protected static void loadFile(Collection collection, String path) throws XMLDBException {
        // create new XMLResource; an id will be assigned to the new resource
        XMLResource document = (XMLResource)
            collection.createResource(path.substring(path.lastIndexOf(File.separatorChar)),
                "XMLResource");
        document.setContent(new File(path));
        collection.storeResource(document);
    }
    
    private static void doQuery(Collection collection, String query) throws XMLDBException {
        XQueryService service = (XQueryService)
            collection.getService("XQueryService", "1.0");
        ResourceSet result = service.query(query);
        for(ResourceIterator i = result.getIterator(); i.hasMoreResources(); ) {
            @SuppressWarnings("unused")
            String content = i.nextResource().getContent().toString();
        }
    }

    @Before
    public void setUp()
       throws Exception
    {
       String homeDir = SingleInstanceConfiguration.getPath();
       if (homeDir == null) {
          homeDir = ".";
       } else {
          homeDir = (new File(homeDir)).getParent();
       }
       File testDir = new File(homeDir + File.separatorChar + "test" + File.separatorChar + "temp");
       if (!testDir.canWrite()) {
          testDir.mkdirs();
       }
       // initialize database drivers
       Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
       for (int i = 0; i < INSTANCE_COUNT; i++) {
          File dir = new File(testDir, "db" + i);
          dir.mkdirs();
          File conf = new File(dir, "conf.xml");
          FileOutputStream os = new FileOutputStream(conf);
          os.write(CONFIG.getBytes(UTF_8));
          os.close();

          Database database = (Database) cl.newInstance();
          database.setProperty("create-database", "true");
          database.setProperty("configuration", conf.getAbsolutePath());
          database.setProperty("database-id", "test" + i);
          DatabaseManager.registerDatabase(database);
       }
    }

    @After
    public void tearDown()
       throws Exception
    {
        Runtime rt = Runtime.getRuntime();
        long free = rt.freeMemory() / 1024;
        long total = rt.totalMemory() / 1024;
        for (int i = 0; i < INSTANCE_COUNT; i++) {
            Collection root = DatabaseManager.getCollection("xmldb:test" + i + "://" + XmldbURI.ROOT_COLLECTION, "admin", null);
            CollectionManagementService service = (CollectionManagementService)
                root.getService("CollectionManagementService", "1.0");
            service.removeCollection("test");

            DatabaseInstanceManager mgr = (DatabaseInstanceManager)
                root.getService("DatabaseInstanceManager", "1.0");
            mgr.shutdown();
        }
    }
}
