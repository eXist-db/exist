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

import org.exist.TestUtils;
import org.exist.util.io.InputStreamUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.samples.Samples.SAMPLES;

/**
 * @author wolf
 */
public class MultiDBTest {

    private final static int INSTANCE_COUNT = 5;

    private final static String CONFIG =
            "<exist>" +
            "   <db-connection database=\"native\" files=\".\" cacheSize=\"32M\">" +
            "       <pool min=\"1\" max=\"5\" sync-period=\"120000\"/>" +
            "       <recovery enabled=\"yes\" group-commit=\"no\" journal-dir=\".\" size=\"100M\" sync-on-commit=\"no\" force-restart=\"no\" consistency-check=\"yes\"/>" +
            "   </db-connection>" +
            "</exist>";

    @Test
    public void store() throws XMLDBException, IOException {
        for (int i = 0; i < INSTANCE_COUNT; i++) {
            Collection root = DatabaseManager.getCollection("xmldb:test" + i + "://" + XmldbURI.ROOT_COLLECTION, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
            Collection test = root.getChildCollection("test");
            if (test == null) {
                CollectionManagementService service = (CollectionManagementService)
                        root.getService("CollectionManagementService", "1.0");
                test = service.createCollection("test");
            }

            for (final String sampleName : SAMPLES.getShakespeareXmlSampleNames()) {
                loadFile(SAMPLES.getShakespeareSample(sampleName), test, sampleName);
            }

            doQuery(test, "//SPEECH[SPEAKER='HAMLET']");
        }
    }

    protected static void loadFile(final InputStream is, final Collection collection, final String fileName) throws XMLDBException, IOException {
        // create new XMLResource; an id will be assigned to the new resource
        XMLResource document = (XMLResource)
                collection.createResource(fileName,
                        "XMLResource");
        document.setContent(InputStreamUtil.readString(is, UTF_8));
        collection.storeResource(document);
    }

    private static void doQuery(Collection collection, String query) throws XMLDBException {
        EXistXQueryService service = (EXistXQueryService)
                collection.getService("XQueryService", "1.0");
        ResourceSet result = service.query(query);
        for (ResourceIterator i = result.getIterator(); i.hasMoreResources(); ) {
            @SuppressWarnings("unused")
            String content = i.nextResource().getContent().toString();
        }
    }

    @ClassRule
    public static TemporaryFolder TEMP_FOLDER = new TemporaryFolder();

    @Before
    public void setUp() throws ClassNotFoundException, IOException, IllegalAccessException, InstantiationException, XMLDBException {

        // initialize database drivers
        final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        for (int i = 0; i < INSTANCE_COUNT; i++) {
            final Path dir = TEMP_FOLDER.newFolder("db" + i).toPath();
            final Path conf = dir.resolve("conf.xml");

            try (final OutputStream os = Files.newOutputStream(conf)) {
                os.write(CONFIG.getBytes(UTF_8));
            }

            final Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            database.setProperty("configuration", conf.toAbsolutePath().toString());
            database.setProperty("database-id", "test" + i);
            DatabaseManager.registerDatabase(database);
        }
    }

    @After
    public void tearDown() throws XMLDBException {
        for (int i = 0; i < INSTANCE_COUNT; i++) {
            Collection root = DatabaseManager.getCollection("xmldb:test" + i + "://" + XmldbURI.ROOT_COLLECTION, "admin", "");
            final CollectionManagementService service = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            service.removeCollection("test");

            final DatabaseInstanceManager mgr = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
            mgr.shutdown();
        }
    }
}
