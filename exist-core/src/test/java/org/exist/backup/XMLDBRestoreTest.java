/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
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
import org.exist.xmldb.RestoreServiceTaskListener;
import org.exist.xmldb.XmldbURI;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class XMLDBRestoreTest {

    @Rule
    public final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private static final String PORT_PLACEHOLDER = "${PORT}";

    @ClassRule
    public static final TemporaryFolder tempFolder = new TemporaryFolder();

    @Parameterized.Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"local", XmldbURI.EMBEDDED_SERVER_URI.toString()},
                {"remote", "xmldb:exist://localhost:" + PORT_PLACEHOLDER + "/xmlrpc"},
        });
    }

    @Parameterized.Parameter
    public String apiName;

    @Parameterized.Parameter(value = 1)
    public String baseUri;

    private final String getBaseUri() {
        return baseUri.replace(PORT_PLACEHOLDER, Integer.toString(existWebServer.getPort()));
    }


    @Test
    public void restoreIsBestEffortAttempt() throws IOException, XMLDBException {
        final Path contentsFile = createBackupWithInvalidContent();
        final TestRestoreListener listener = new TestRestoreListener();
        final XmldbURI rootUri = XmldbURI.create(getBaseUri()).append(XmldbURI.ROOT_COLLECTION_URI);

        restoreBackup(rootUri, contentsFile, listener);

        assertEquals(3, listener.restored.size());
        assertEquals(1, listener.warnings.size());
    }

    private static void restoreBackup(final XmldbURI uri, final Path backup, final RestoreServiceTaskListener listener) throws XMLDBException {
        final Collection collection = DatabaseManager.getCollection(uri.toString(), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final EXistRestoreService restoreService = (EXistRestoreService) collection.getService("RestoreService", "1.0");
        restoreService.restore(backup.normalize().toAbsolutePath().toString(), null, listener);
    }

    private static Path createBackupWithInvalidContent() throws IOException {
        final Path backupDir = tempFolder.newFolder().toPath();
        final Path col1 = Files.createDirectories(backupDir.resolve("db").resolve("col1"));

        final String contents =
                "<collection xmlns=\"http://exist.sourceforge.net/NS/exist\" name=\"/db/col1\" owner=\"admin\" group=\"dba\" mode=\"755\" created=\"2019-05-15T15:58:39.385+04:00\" deduplicate-blobs=\"false\" version=\"2\">\n" +
                "    <acl entries=\"0\" version=\"1\"/>\n" +
                "    <resource type=\"XMLResource\" name=\"doc1.xml\" owner=\"admin\" group=\"dba\" mode=\"644\" created=\"2019-05-15T15:58:48.638+04:00\" modified=\"2019-05-15T15:58:48.638+04:00\" filename=\"doc1.xml\" mimetype=\"application/xml\">\n" +
                "        <acl entries=\"0\" version=\"1\"/>\n" +
                "    </resource>\n" +
                "    <resource type=\"XMLResource\" name=\"doc2.xml\" owner=\"admin\" group=\"dba\" mode=\"644\" created=\"2019-05-15T15:58:48.638+04:00\" modified=\"2019-05-15T15:58:48.638+04:00\" filename=\"doc2.xml\" mimetype=\"application/xml\">\n" +
                "        <acl entries=\"0\" version=\"1\"/>\n" +
                "    </resource>\n" +
                "    <resource type=\"XMLResource\" name=\"doc3.xml\" owner=\"admin\" group=\"dba\" mode=\"644\" created=\"2019-05-15T15:58:49.618+04:00\" modified=\"2019-05-15T15:58:49.618+04:00\" filename=\"doc3.xml\" mimetype=\"application/xml\">\n" +
                "        <acl entries=\"0\" version=\"1\"/>\n" +
                "    </resource>\n" +
                "</collection>";

        final String doc1 = "<doc1/>";
        final String doc2 = "<doc2>invalid";
        final String doc3 = "<doc3/>";

        final Path contentsFile = Files.write(col1.resolve(BackupDescriptor.COLLECTION_DESCRIPTOR), contents.getBytes(UTF_8));
        Files.write(col1.resolve("doc1.xml"),  doc1.getBytes(UTF_8));
        Files.write(col1.resolve("doc2.xml"),  doc2.getBytes(UTF_8));
        Files.write(col1.resolve("doc3.xml"),  doc3.getBytes(UTF_8));

        return contentsFile;
    }

    private static class TestRestoreListener extends AbstractRestoreServiceTaskListener {
        final List<String> restored = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

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
            warnings.add(message);
        }

        @Override
        public void error(final String message) {
            errors.add(message);
        }
    }
}
