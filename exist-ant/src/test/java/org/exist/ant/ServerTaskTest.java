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
package org.exist.ant;

import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerTaskTest extends AbstractTaskTest {

    private static final String PROP_ANT_TEST_DATA_BACKUP_DIR = "test.data.backup.dir";

    @TempDir
    public File temporaryFolder;

    @Nullable
    @Override
    protected URL getBuildFile() {
        return getClass().getResource("server.xml");
    }

    @Test
    void backup() throws IOException {
        final Project project = buildFileRule.getProject();
        final Path backupDir = newFolder(temporaryFolder, "junit").toPath();
        project.setProperty(PROP_ANT_TEST_DATA_BACKUP_DIR, backupDir.toAbsolutePath().toString());

        buildFileRule.executeTarget("backup");

        assertTrue(Files.exists(backupDir.resolve("db").resolve("__contents__.xml")));
    }

    @Test
    void restore() throws URISyntaxException, XMLDBException {
        final URL backupContentsUrl = getClass().getResource("backup-test/db/__contents__.xml");
        assertNotNull(backupContentsUrl);
        final Path backupDir = Path.of(backupContentsUrl.toURI()).getParent().getParent();

        final Project project = buildFileRule.getProject();
        project.setProperty(PROP_ANT_TEST_DATA_BACKUP_DIR, backupDir.toAbsolutePath().toString());

        buildFileRule.executeTarget("restore");

        final Resource res = existEmbeddedServer.getRoot().getResource("example.xml");
        assertNotNull(res);
    }

    @Test
    void backupRestore() throws IOException {
        final Project project = buildFileRule.getProject();
        final Path backupDir = newFolder(temporaryFolder, "junit").toPath();
        project.setProperty(PROP_ANT_TEST_DATA_BACKUP_DIR, backupDir.toAbsolutePath().toString());

        buildFileRule.executeTarget("backup");

        buildFileRule.executeTarget("restore");
    }

    @Test
    void shutdown() {
        buildFileRule.executeTarget("shutdown");
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
