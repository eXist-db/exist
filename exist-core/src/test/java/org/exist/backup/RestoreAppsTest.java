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
package org.exist.backup;

import org.exist.EXistException;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.repo.Deployment;
import org.exist.repo.ExistRepository;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.expath.pkg.repo.*;
import org.expath.pkg.repo.tui.BatchUserInteraction;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class RestoreAppsTest {

    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String REPO_XML_APP =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<meta xmlns=\"http://exist-db.org/xquery/repo\">\n" +
            "    <description>Backup Test App</description>\n" +
            "    <type>application</type>\n" +
            "    <target>backup-test</target>\n" +
            "</meta>";

    private static final String REPO_XML_LIB =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<meta xmlns=\"http://exist-db.org/xquery/repo\">\n" +
        "    <description>Backup Test App</description>\n" +
        "    <type>library</type>\n" +
        "</meta>";

    /**
     * Create an app package and generate a backup. Install a newer version
     * of the same package and restore the backup. The newer version inside
     * the expath repo should be preserved and not overwritten.
     *
     * @throws Exception in case of error
     */
    @Test
    public void restoreSkipNewer() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();

        createAndInstallApp("1.0.0", REPO_XML_APP);

        Path backup = export(pool);

        removePackage(pool);

        createAndInstallApp("2.0.0", REPO_XML_APP);

        restoreAndCheck(pool, backup, "Newer version is already installed.");
    }

    /**
     * Create a library package and generate a backup. Install a newer version
     * of the same package and restore the backup. The newer version inside
     * the expath repo should be preserved and not overwritten.
     *
     * Library packages are restored into /db/system/repo, not /db/apps, therefore
     * we need an extra test.
     *
     * @throws Exception in case of error
     */
    @Test
    public void restoreSkipNewerLib() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();

        createAndInstallApp("1.0.0", REPO_XML_LIB);

        Path backup = export(pool);

        removePackage(pool);

        createAndInstallApp("2.0.0", REPO_XML_LIB);

        restoreAndCheck(pool, backup, "Newer version is already installed.");
    }

    /**
     * Semver coercion: create an app with an incomplete semver and try to restore it. The newer version inside
     * the expath repo should be preserved and not overwritten.
     *
     * @throws Exception in case of error
     */
    @Test
    public void restoreWithIncompleteSemverAndSkipNewer() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();

        createAndInstallApp("1", REPO_XML_APP);

        Path backup = export(pool);

        removePackage(pool);

        createAndInstallApp("2.0.0", REPO_XML_APP);

        restoreAndCheck(pool, backup, "Newer version is already installed.");
    }

    /**
     * Create an app package and generate a backup. Install an older version
     * of the same package and restore the backup. The newer version inside
     * the backup should overwrite the older in the database.
     *
     * @throws Exception in case of error
     */
    @Test
    public void restoreOverwriteOlder() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();

        createAndInstallApp("2.0.0", REPO_XML_APP);

        Path backup = export(pool);

        removePackage(pool);

        createAndInstallApp("1.0.0", REPO_XML_APP);

        restoreAndCheck(pool, backup, null);
    }

    /**
     * Create a library package and generate a backup. Install an older version
     * of the same package and restore the backup. The newer version inside
     * the backup should overwrite the older in the database.
     *
     * Library packages are restored into /db/system/repo, not /db/apps, therefore
     * we need an extra test.
     *
     * @throws Exception in case of error
     */
    @Test
    public void restoreOverwriteOlderLib() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();

        createAndInstallApp("2.0.0", REPO_XML_LIB);

        Path backup = export(pool);

        removePackage(pool);

        createAndInstallApp("1.0.0", REPO_XML_LIB);

        restoreAndCheck(pool, backup, null);
    }

    /**
     * Semver coercion: create an app with an incomplete semver and try to restore it.
     * The newer version inside the backup should overwrite the older in the database.
     *
     * @throws Exception in case of error
     */
    @Test
    public void restoreOverwriteOlderWithIncompleteSemver() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();

        createAndInstallApp("2.0.0", REPO_XML_APP);

        Path backup = export(pool);

        removePackage(pool);

        createAndInstallApp("1.0", REPO_XML_APP);

        restoreAndCheck(pool, backup, null);
    }

    private void restoreAndCheck(BrokerPool pool, Path backup, String expectedMessage) throws Exception {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            Restore restore = new Restore();
            TestRestoreListener listener = new TestRestoreListener();
            restore.restore(broker, transaction, null, backup, listener, false);

            if (expectedMessage != null) {
                assertEquals(1, listener.skipped.size());
                assertTrue(listener.skipped.getFirst().endsWith(expectedMessage));
            } else {
                assertEquals(0, listener.skipped.size());
            }
        }
        existEmbeddedServer.restart(true);
    }

    private void removePackage(BrokerPool pool) throws PackageException {
        Optional<ExistRepository> repo = pool.getExpathRepo();
        Repository parent_repo = repo.get().getParentRepo();
        parent_repo.removePackage("http://existsolutions.com/apps/backup-test", true, new BatchUserInteraction());
    }

    private Path export(BrokerPool pool) throws IOException, EXistException {
        Path backup;
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            SystemExport export = new SystemExport(broker, transaction, null, null, false);
            String backupDir = temporaryFolder.newFolder().getAbsolutePath();
            backup = export.export(backupDir, false, true, null);

            transaction.commit();
        }
        assertNotNull(backup);
        assertTrue(Files.isReadable(backup));
        return backup;
    }

    private void createAndInstallApp(String version, String repoDescriptor) throws IOException, PackageException, EXistException {
        String descriptor =
                "<package xmlns=\"http://expath.org/ns/pkg\" name=\"http://existsolutions.com/apps/backup-test\"\n" +
                "   abbrev=\"backup-test\" version=\"" + version + "\" spec=\"1.0\">\n" +
                "   <title>Backup Test App</title>\n" +
                "   <dependency processor=\"http://exist-db.org\" semver-min=\"5.0.0-RC8\"/>\n" +
                "</package>";
        Path xarFile = temporaryFolder.newFile().toPath();
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(xarFile, StandardOpenOption.WRITE))) {
            ZipEntry entry = new ZipEntry("expath-pkg.xml");
            zos.putNextEntry(entry);
            byte[] bytes = descriptor.getBytes(StandardCharsets.UTF_8);
            zos.write(bytes);
            zos.closeEntry();

            entry = new ZipEntry("repo.xml");
            zos.putNextEntry(entry);
            bytes = repoDescriptor.getBytes(StandardCharsets.UTF_8);
            zos.write(bytes);
            zos.closeEntry();
        }

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        Optional<ExistRepository> repo = pool.getExpathRepo();
        if (!repo.isPresent()) {
            throw new EXistException("expath repository not available for test");
        }
        XarSource xar = new XarFileSource(xarFile);
        Deployment deployment = new Deployment();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            deployment.installAndDeploy(broker, transaction, xar, null);
            transaction.commit();
        }

        Packages pkgs = repo.get().getParentRepo().getPackages("http://existsolutions.com/apps/backup-test");
        assertEquals(1, pkgs.packages().size());
    }

    class TestRestoreListener implements RestoreListener {

        private List<String> info = new ArrayList<>();
        private List<String> skipped = new ArrayList<>();

        @Override
        public void started(long numberOfFiles) {
            // unused
        }

        @Override
        public void processingDescriptor(String backupDescriptor) {
            // unused
        }

        @Override
        public void createdCollection(String collection) {
            // unused
        }

        @Override
        public void restoredResource(String resource) {
            // unused
        }

        @Override
        public void skipResources(String message, long count) {
            skipped.add(message);
        }

        @Override
        public void info(String message) {
            info.add(message);
        }

        @Override
        public void warn(String message) {
            // unused
        }

        @Override
        public void error(String message) {
            // unused
        }

        @Override
        public void finished() {
            // unused
        }
    }
}
