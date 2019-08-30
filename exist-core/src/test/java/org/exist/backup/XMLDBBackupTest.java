/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.backup;

import org.exist.TestUtils;
import org.exist.test.ExistWebServer;
import org.exist.xmldb.AbstractRestoreServiceTaskListener;
import org.exist.xmldb.EXistRestoreService;
import org.exist.xmldb.XmldbURI;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class XMLDBBackupTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);
    private static final String PORT_PLACEHOLDER = "${PORT}";

    private static final String COLLECTION_NAME = "test-xmldb-backup-restore";

    @ClassRule
    public static final TemporaryFolder tempFolder = new TemporaryFolder();

    @Parameterized.Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "local (classic)", XmldbURI.EMBEDDED_SERVER_URI.toString(), false },
                { "remote (classic)", "xmldb:exist://localhost:" + PORT_PLACEHOLDER + "/xmlrpc", false },
                { "local (dedup)", XmldbURI.EMBEDDED_SERVER_URI.toString(), false },
                { "remote (dedup)", "xmldb:exist://localhost:" + PORT_PLACEHOLDER + "/xmlrpc", true },
        });
    }

    @Parameterized.Parameter
    public String apiName;

    @Parameterized.Parameter(value = 1)
    public String baseUri;

    @Parameterized.Parameter(value = 2)
    public boolean deduplicateBlobs;

    private static final String DOC1_NAME = "doc1.xml";
    private final String doc1Content = "<timestamp>" + System.nanoTime() + "</timestamp>";

    private static final String BIN_DOC1_NAME = "doc1.bin";
    private final String binDoc1Content = Long.toString(System.nanoTime());
    private static final String BIN_DOC2_NAME = "doc2.bin";
    private final String binDoc2Content = Long.toString(System.nanoTime());

    private final String getBaseUri() {
        return baseUri.replace(PORT_PLACEHOLDER, Integer.toString(existWebServer.getPort()));
    }

    @Test
    public void backupRestore() throws XMLDBException, SAXException, IOException, URISyntaxException, ParserConfigurationException {
        final XmldbURI collectionUri = XmldbURI.create(getBaseUri()).append("/db").append(COLLECTION_NAME);
        final String backupFilename = "test-xmldb-backup-" + System.currentTimeMillis() + ".zip";

        // backup the collection
        final Path backupFile = backup(backupFilename, collectionUri);

        // delete the collection
        deleteCollection(collectionUri);

        // restore the collection
        restore(backupFile, XmldbURI.create(getBaseUri()).append("/db"));

        // check restore has restored the collection
        final Collection testCollection = DatabaseManager.getCollection(collectionUri.toString(), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        assertNotNull(testCollection);

        final Resource doc1 = testCollection.getResource(DOC1_NAME);
        assertNotNull(doc1);
        final Source expected = Input.fromString(doc1Content).build();
        final Source actual = Input.fromString(doc1.getContent().toString()).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForIdentical()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());

        final Resource binDoc1 = testCollection.getResource(BIN_DOC1_NAME);
        assertEquals(binDoc1Content, new String((byte[])binDoc1.getContent(), UTF_8));

        final Resource binDoc2 = testCollection.getResource(BIN_DOC2_NAME);
        assertEquals(binDoc2Content, new String((byte[])binDoc2.getContent(), UTF_8));
    }

    private Path backup(final String filename, final XmldbURI collectionUri) throws IOException, XMLDBException, SAXException {
        final Path backupFile = tempFolder.newFile(filename).toPath();
        final Backup backup = new Backup(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD,
                backupFile,
                collectionUri,
                null,
                deduplicateBlobs);
        backup.backup(false, null);
        return backupFile;
    }

    private void restore(final Path backupFile, final XmldbURI collectionUri) throws XMLDBException, SAXException, URISyntaxException, ParserConfigurationException, IOException {
        final Collection collection = DatabaseManager.getCollection(collectionUri.toString(), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final EXistRestoreService restoreService = (EXistRestoreService) collection.getService("RestoreService", "1.0");
        final TestRestoreListener listener = new TestRestoreListener();
        restoreService.restore(backupFile.normalize().toAbsolutePath().toString(), null, listener, false);
    }

    private void deleteCollection(final XmldbURI collectionUri) throws XMLDBException {
        final Collection parent = DatabaseManager.getCollection(collectionUri.removeLastSegment().toString(), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService colService = (CollectionManagementService) parent.getService("CollectionManagementService", "1.0");
        colService.removeCollection(collectionUri.lastSegment().toString());
    }

    @Before
    public void before() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final CollectionManagementService colService = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
        final Collection testCollection = colService.createCollection(COLLECTION_NAME);
        assertNotNull(testCollection);

        final Resource doc1 = testCollection.createResource(DOC1_NAME, XMLResource.RESOURCE_TYPE);
        doc1.setContent(doc1Content);
        testCollection.storeResource(doc1);

        final Resource binDoc1 = testCollection.createResource(BIN_DOC1_NAME, BinaryResource.RESOURCE_TYPE);
        binDoc1.setContent(binDoc1Content);
        testCollection.storeResource(binDoc1);

        final Resource binDoc2 = testCollection.createResource(BIN_DOC2_NAME, BinaryResource.RESOURCE_TYPE);
        binDoc2.setContent(binDoc2Content);
        testCollection.storeResource(binDoc2);
    }

    private static class TestRestoreListener extends AbstractRestoreServiceTaskListener {
        final List<String> restored = new ArrayList<>();

        @Override
        public void createdCollection(final String collection) {
            restored.add(collection);
        }

        @Override
        public void restoredResource(final String resource) {
            restored.add(resource);
        }

        @Override
        public void info(final String message) {
        }

        @Override
        public void warn(final String message) {
        }

        @Override
        public void error(final String message) {
        }
    }
}
