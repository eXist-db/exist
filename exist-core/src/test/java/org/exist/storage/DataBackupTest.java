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

package org.exist.storage;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

public class DataBackupTest {

    private static final long BACKUP_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);

    /**
     * Required .dbx entries in the backup zip. Update this list when adding new storage files to
     * {@link NativeBroker#backupToArchive} or {@link IndexManager#backupToArchive}.
     */
    private static final String[] REQUIRED_BACKUP_ENTRIES = {
        "collections.dbx",
        "dom.dbx",
        "structure.dbx",
        "symbols.dbx",
        "values.dbx",
        "blob.dbx",
        "vector.dbx"
    };

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();

    @After
    public void cleanup() throws EXistException, PermissionDeniedException, LockException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final Collection root = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
            if (root != null) {
                broker.removeCollection(transaction, root);
            }
            pool.getTransactionManager().commit(transaction);
        }
    }

    @Test
    public void backup() throws InterruptedException, IOException, EXistException, PermissionDeniedException, SAXException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();

        // Store a document to ensure all storage systems are initialized and flushed
        storeMinimalDocument(pool);

        final TestableDataBackup dataBackup = new TestableDataBackup(folder.getRoot().toPath());
        pool.triggerSystemTask(dataBackup);

        final long deadline = System.currentTimeMillis() + BACKUP_TIMEOUT_MS;
        while (!dataBackup.isCompleted()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Backup did not complete within " + BACKUP_TIMEOUT_MS + " ms");
            }
            Thread.sleep(100);
        }

        if (dataBackup.getError().isPresent()) {
            fail("Backup failed with error: " + dataBackup.getError().get().getMessage());
        }

        final Optional<Path> lastBackup = dataBackup.getLastBackup();
        assertTrue("Backup file should be present", lastBackup.isPresent());

        final Path backupPath = lastBackup.get();
        assertTrue("Backup file should exist: " + backupPath, Files.exists(backupPath));
        assertTrue("Backup file should not be empty: " + backupPath, Files.size(backupPath) > 0);

        try (final ZipFile zipFile = new ZipFile(backupPath.toFile())) {
            final List<String> missing = new ArrayList<>();
            for (final String entryName : REQUIRED_BACKUP_ENTRIES) {
                final ZipEntry entry = zipFile.getEntry(entryName);
                if (entry == null) {
                    missing.add(entryName);
                }
            }
            if (!missing.isEmpty()) {
                fail("Backup missing required entries: " + String.join(", ", missing) +
                    ". Zip contents: " + zipFile.stream().map(ZipEntry::getName).toList());
            }
        }
    }

    private void storeMinimalDocument(final BrokerPool pool) throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);
            broker.storeDocument(transaction, XmldbURI.create("backup-test.xml"),
                new StringInputSource("<root/>"), MimeType.XML_TYPE, root);
            pool.getTransactionManager().commit(transaction);
        }
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            broker.sync(Sync.MAJOR);
        }
    }

    private class TestableDataBackup extends DataBackup {
        private volatile boolean completed;
        private volatile Throwable error;

        public TestableDataBackup(final Path destination) {
            super(destination);
        }

        @Override
        public void execute(final DBBroker broker, final Txn transaction) throws EXistException {
            try {
                super.execute(broker, transaction);
            } catch (final Throwable t) {
                this.error = t;
                throw t;
            } finally {
                completed = true;
            }
        }

        public boolean isCompleted() {
            return completed;
        }

        public Optional<Throwable> getError() {
            return Optional.ofNullable(error);
        }
    }
}
