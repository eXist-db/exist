package org.exist.backup;

import org.exist.EXistException;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.repo.Deployment;
import org.exist.repo.ExistRepository;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.Repository;
import org.expath.pkg.repo.XarFileSource;
import org.expath.pkg.repo.XarSource;
import org.expath.pkg.repo.tui.BatchUserInteraction;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
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

    private static final String REPO_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<meta xmlns=\"http://exist-db.org/xquery/repo\">\n" +
            "    <description>Backup Test App</description>\n" +
            "    <type>application</type>\n" +
            "    <target>backup-test</target>" +
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
        removePackage(pool);

        createAndInstallApp("1.0.0");

        Path backup = export(pool);

        removePackage(pool);

        createAndInstallApp("2.0.0");

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            Restore restore = new Restore();
            TestRestoreListener listener = new TestRestoreListener();
            restore.restore(broker, transaction, null, backup, listener, false);

            assertEquals(1, listener.info.size());
            assertTrue(listener.info.get(0).endsWith("Newer version is already installed."));
        }
    }

    /**
     * Create an app package and generate a backup. Install an older version
     * of the same package and restore the backup. The newer version inside
     * the backup should overwrite the older in the database.
     *
     * @throws Exception in case of error
     */
    @Test
    public void restoreOverwriteOlder() throws IOException, EXistException, PackageException, PermissionDeniedException, SAXException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        removePackage(pool);

        createAndInstallApp("2.0.0");

        Path backup = export(pool);

        removePackage(pool);

        createAndInstallApp("1.0.0");

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            Restore restore = new Restore();
            TestRestoreListener listener = new TestRestoreListener();
            restore.restore(broker, transaction, null, backup, listener, false);

            assertEquals(0, listener.info.size());
        }
    }

    private void removePackage(BrokerPool pool) throws PackageException {
        Optional<ExistRepository> repo = pool.getExpathRepo();
        Repository parent_repo = repo.get().getParentRepo();
        parent_repo.removePackage("http://existsolutions.com/apps/backup-test", true, new BatchUserInteraction());
    }

    private Path export(BrokerPool pool) throws IOException, EXistException {
        Path backup;
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            SystemExport export = new SystemExport(broker, null, null, false);
            String backupDir = temporaryFolder.newFolder().getAbsolutePath();
            backup = export.export(backupDir, false, true, null);
        }
        assertNotNull(backup);
        assertTrue(Files.isReadable(backup));
        return backup;
    }

    private void createAndInstallApp(String version) throws IOException, PackageException, EXistException {
        String descriptor =
                "<package xmlns=\"http://expath.org/ns/pkg\" name=\"http://existsolutions.com/apps/backup-test\"\n" +
                "   abbrev=\"backup-test\" version=\"" + version + "\" spec=\"1.0\">\n" +
                "   <title>Backup Test App</title>\n" +
                "   <dependency processor=\"http://exist-db.org\" semver-min=\"5.0.0-RC8\"/>\n" +
                "</package>";
        Path xarFile = temporaryFolder.newFile().toPath();
        ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(xarFile, StandardOpenOption.WRITE));
        ZipEntry entry = new ZipEntry("expath-pkg.xml");
        zos.putNextEntry(entry);
        byte[] bytes = descriptor.getBytes(StandardCharsets.UTF_8);
        zos.write(bytes);
        zos.closeEntry();

        entry = new ZipEntry("repo.xml");
        zos.putNextEntry(entry);
        bytes = REPO_XML.getBytes(StandardCharsets.UTF_8);
        zos.write(bytes);
        zos.closeEntry();
        zos.close();

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        Optional<ExistRepository> repo = pool.getExpathRepo();
        if (!repo.isPresent()) {
            throw new RuntimeException("expath repository not available for test");
        }
        XarSource xar = new XarFileSource(xarFile);
        Deployment deployment = new Deployment();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            deployment.installAndDeploy(broker, transaction, xar, null);
            transaction.commit();
        }
    }

    class TestRestoreListener implements RestoreListener {

        private List<String> info = new ArrayList<>();

        @Override
        public void started(long numberOfFiles) {

        }

        @Override
        public void processingDescriptor(String backupDescriptor) {

        }

        @Override
        public void createdCollection(String collection) {

        }

        @Override
        public void restoredResource(String resource) {
        }

        @Override
        public void info(String message) {
            System.out.println(message);
            info.add(message);
        }

        @Override
        public void warn(String message) {

        }

        @Override
        public void error(String message) {

        }

        @Override
        public void finished() {

        }
    }
}