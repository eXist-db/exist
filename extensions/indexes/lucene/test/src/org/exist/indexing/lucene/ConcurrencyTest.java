/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
package org.exist.indexing.lucene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.exist.TestUtils;
import org.exist.util.FileUtils;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.IndexQueryService;
import org.exist.xmldb.XQueryService;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XUpdateQueryService;

public class ConcurrencyTest {

    private static Collection test;

    private static String COLLECTION_CONFIG1 =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
        "       <lucene>" +
        "           <text qname=\"LINE\"/>" +
        "           <text qname=\"SPEAKER\"/>" +
        "       </lucene>" +
        "	</index>" +
    	"</collection>";

    @Test
	public void store() {
		ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 10; i++) {
            final String name = "thread" + i;
            final Runnable run = () -> {
                try {
                    storeRemoveDocs(name);
                } catch(final XMLDBException | IOException e) {
                    e.printStackTrace();;
                    fail(e.getMessage());
                }
            };
            executor.submit(run);
        }

        executor.shutdown();
		boolean terminated = false;
		try {
			terminated = executor.awaitTermination(60 * 60, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
		    //Nothing to do
		}
		assertTrue(terminated);
    }

    @Test
	public void update() {
		ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 5; i++) {
            final String name = "thread" + i;
            Runnable run = () -> {
                try {
                    xupdateDocs(name);
                } catch (XMLDBException | IOException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
            };
            executor.submit(run);
        }

        executor.shutdown();
		boolean terminated = false;
		try {
			terminated = executor.awaitTermination(60 * 60, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
		    //Nothing to do
		}
		assertTrue(terminated);
    }

    private void storeRemoveDocs(final String collectionName) throws XMLDBException, IOException {
        storeDocs(collectionName);

        XQueryService xqs = (XQueryService) test.getService("XQueryService", "1.0");
        ResourceSet result = xqs.query("//SPEECH[ft:query(LINE, 'king')]");
        assertEquals(98, result.getSize());
        result = xqs.query("//SPEECH[ft:query(SPEAKER, 'juliet')]");
        assertEquals(118, result.getSize());

        String[] resources = test.listResources();
        for (int i = 0; i < resources.length; i++) {
            Resource resource = test.getResource(resources[i]);
            test.removeResource(resource);
        }
        result = xqs.query("//SPEECH[ft:query(LINE, 'king')]");
        assertEquals(0, result.getSize());
        result = xqs.query("//SPEECH[ft:query(SPEAKER, 'juliet')]");
        assertEquals(0, result.getSize());
    }

    private void xupdateDocs(final String collectionName) throws XMLDBException, IOException {
        storeDocs(collectionName);

        XQueryService xqs = (XQueryService) test.getService("XQueryService", "1.0");
        ResourceSet result = xqs.query("//SPEECH[ft:query(SPEAKER, 'juliet')]");
        assertEquals(118, result.getSize());

        String xupdate =
            LuceneIndexTest.XUPDATE_START +
            "   <xu:remove select=\"//SPEECH[ft:query(SPEAKER, 'juliet')]\"/>" +
            LuceneIndexTest.XUPDATE_END;
        XUpdateQueryService xuqs = (XUpdateQueryService) test.getService("XUpdateQueryService", "1.0");
        xuqs.update(xupdate);

        result = xqs.query("//SPEECH[ft:query(SPEAKER, 'juliet')]");
        assertEquals(0, result.getSize());
        result = xqs.query("//SPEECH[ft:query(LINE, 'king')]");
        assertEquals(98, result.getSize());
    }

    private void storeDocs(final String collectionName) throws XMLDBException, IOException {
        CollectionManagementService service = (CollectionManagementService) test.getService("CollectionManagementService", "1.0");
        Collection collection = service.createCollection(collectionName);
        IndexQueryService iqs = (IndexQueryService) collection.getService("IndexQueryService", "1.0");
        iqs.configureCollection(COLLECTION_CONFIG1);

        String existHome = System.getProperty("exist.home");
        Path existDir = existHome==null ? Paths.get(".") : Paths.get(existHome);
        existDir = existDir.normalize();
        Path samples = existDir.resolve("samples/shakespeare");
        List<Path> files = FileUtils.list(samples);
        MimeTable mimeTab = MimeTable.getInstance();
        for (final Path file : files) {
            MimeType mime = mimeTab.getContentTypeFor(FileUtils.fileName(file));
            if(mime != null && mime.isXMLType()) {
                Resource resource = collection.createResource(FileUtils.fileName(file), XMLResource.RESOURCE_TYPE);
                resource.setContent(file);
                collection.storeResource(resource);
            }
        }
    }

    @BeforeClass
    public static void initDB() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        // initialize XML:DB driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        org.xmldb.api.base.Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        CollectionManagementService mgmt = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
        test = mgmt.createCollection("test");
    }

    @AfterClass
    public static void closeDB() throws XMLDBException {
        TestUtils.cleanupDB();

        Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        DatabaseInstanceManager mgr = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
        mgr.shutdown();
    }
}
