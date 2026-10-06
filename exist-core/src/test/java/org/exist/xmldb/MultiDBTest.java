/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xmldb;

import org.exist.TestUtils;
import org.exist.util.io.InputStreamUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import java.io.File;
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

    @TempDir
    public static File TEMP_FOLDER;

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
                CollectionManagementService service = root.getService(CollectionManagementService.class);
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
        XMLResource document = 
                collection.createResource(fileName,
                        XMLResource.class);
        document.setContent(InputStreamUtil.readString(is, UTF_8));
        collection.storeResource(document);
    }

    private static void doQuery(Collection collection, String query) throws XMLDBException {
        EXistXQueryService service = collection.getService(EXistXQueryService.class);
        ResourceSet result = service.query(query);
        for (ResourceIterator i = result.getIterator(); i.hasMoreResources(); ) {
            @SuppressWarnings("unused")
            String content = i.nextResource().getContent().toString();
        }
    }

    @BeforeEach
    public void setUp() throws ClassNotFoundException, IOException, IllegalAccessException, InstantiationException, XMLDBException {

        // initialize database drivers
        final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        for (int i = 0; i < INSTANCE_COUNT; i++) {
            final Path dir = newFolder(TEMP_FOLDER, "db" + i).toPath();
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

    @AfterEach
    public void tearDown() throws XMLDBException {
        for (int i = 0; i < INSTANCE_COUNT; i++) {
            Collection root = DatabaseManager.getCollection("xmldb:test" + i + "://" + XmldbURI.ROOT_COLLECTION, "admin", "");
            final CollectionManagementService service = root.getService(CollectionManagementService.class);
            service.removeCollection("test");

            final DatabaseInstanceManager mgr = root.getService(DatabaseInstanceManager.class);
            mgr.shutdown();
        }
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            if (result.isDirectory()) {
                // repeated or parameterized calls can share a root and reuse subDirs, so fall back to a
                // uniquely-suffixed sibling instead of colliding with the earlier directory
                result = Files.createTempDirectory(root.toPath(), subFolder + "-").toFile();
            } else {
                throw new IOException("Couldn't create folders " + root);
            }
        }
        return result;
    }
}
