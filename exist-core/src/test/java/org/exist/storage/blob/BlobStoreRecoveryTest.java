/*
 * Copyright (C) 2018 Adam Retter
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
package org.exist.storage.blob;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.scheduler.Scheduler;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.storage.DBBroker;
import org.exist.storage.SystemTaskManager;
import org.exist.storage.journal.Journal;
import org.exist.storage.journal.JournalManager;
import org.exist.storage.journal.LogException;
import org.exist.storage.lock.FileLockHeartBeat;
import org.exist.storage.recovery.RecoveryManager;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.crypto.digest.DigestInputStream;
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.MessageDigest;
import org.exist.util.crypto.digest.StreamableDigest;
import org.exist.util.io.FastByteArrayInputStream;
import org.exist.util.io.FastByteArrayOutputStream;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.easymock.EasyMock.*;
import static org.exist.storage.journal.Journal.*;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@RunWith(Parameterized.class)
public class BlobStoreRecoveryTest {

    private static final boolean SIMULATE_CRASH = false;
    private static final boolean CLEAN_SHUTDOWN = true;

    private static final DigestType DIGEST_TYPE = DigestType.BLAKE_256;
    private final Random random = new Random();

    @Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "crash", SIMULATE_CRASH },
                { "shutdown", CLEAN_SHUTDOWN }
        });
    }

    @Parameter
    public String testTypeName;

    @Parameter(value = 1)
    public boolean cleanShutdown;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @AfterClass
    public static void cleanup() {
        BrokerPool.FORCE_CORRUPTION = false;
    }

//    @Test
//    public void test1() {
//        Add_commit(),
//        Remove_commit(),
//        Add_commit(),
//
//        Add_commit(),
//        Remove_commit(),
//        Add_noCommit(),
//    }

    // TODO(AR) need to introduce additional checkpoints to check that deletion of staging file / remove file is okay!

    /**
     * Add Blob and commit, then stop (either by shutdown or simulated crash).
     *
     * Expect that the Blob is present after recovery.
     */
    @Test
    public void addCommit() throws IOException, BrokerPoolServiceException, EXistException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> tempBin1 = generateTestFile();

        // write the data
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            addCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
        }
        // NOTE: when BlobDB is closed by the ARM expression above it simulates a crash as BrokerPool.FORCE_CORRUPTION = true was set in #newBlobDb()


        // test the recovery
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            final BlobId blobId = new BlobId(tempBin1._2.getValue());
            final Tuple2<byte[], MessageDigest> actual = getCommit(blobDb.transactionManager, blobDb.blobStore, blobId);
            assertNotNull(actual);
            assertEquals(tempBin1._2, tempBin1._2);
            assertArrayEquals(tempBin1._1, tempBin1._1);
            if (blobDb.blobStore instanceof BlobStoreImpl) {
                assertEquals(1, ((BlobStoreImpl)blobDb.blobStore).getReferenceCount(blobId).intValue());
            }
        }
    }

    /**
     * Add Blob (DO NOT commit), then stop (either by shutdown or simulated crash).
     *
     * Expect that the Blob is NOT present after recovery.
     */
    @Test
    public void addNoCommit() throws IOException, BrokerPoolServiceException, EXistException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> tempBin1 = generateTestFile();

        // write the data
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            addNoCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
        }
        // NOTE: when BlobDB is closed by the ARM expression above it simulates a crash as BrokerPool.FORCE_CORRUPTION = true was set in #newBlobDb()


        // test the recovery
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            final BlobId blobId = new BlobId(tempBin1._2.getValue());
            final Tuple2<byte[], MessageDigest> actual = getCommit(blobDb.transactionManager, blobDb.blobStore, blobId);
            assertNull(actual);
            if (blobDb.blobStore instanceof BlobStoreImpl) {
                assertNull(((BlobStoreImpl)blobDb.blobStore).getReferenceCount(blobId));
            }
        }
    }

    /**
     * Add Blob and commit, Remove Blob and commit, then stop (either by shutdown or simulated crash).
     *
     * Expect that the Blob is NOT present after recovery.
     */
    @Test
    public void addCommit_removeCommit() throws IOException, BrokerPoolServiceException, EXistException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> tempBin1 = generateTestFile();

        // write the data
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            addCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
            removeCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
        }
        // NOTE: when BlobDB is closed by the ARM expression above it simulates a crash as BrokerPool.FORCE_CORRUPTION = true was set in #newBlobDb()


        // test the recovery
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            final BlobId blobId = new BlobId(tempBin1._2.getValue());
            final Tuple2<byte[], MessageDigest> actual = getCommit(blobDb.transactionManager, blobDb.blobStore, blobId);
            assertNull(actual);
            if (blobDb.blobStore instanceof BlobStoreImpl) {
                assertNull(((BlobStoreImpl)blobDb.blobStore).getReferenceCount(blobId));
            }
        }
    }

    /**
     * Add Blob and commit, Remove Blob (DO NOT commit), then stop (either by shutdown or simulated crash).
     *
     * Expect that the Blob is present after recovery.
     */
    @Test
    public void addCommit_removeNoCommit() throws IOException, BrokerPoolServiceException, EXistException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> tempBin1 = generateTestFile();

        // write the data
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            addCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
            removeNoCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
        }
        // NOTE: when BlobDB is closed by the ARM expression above it simulates a crash as BrokerPool.FORCE_CORRUPTION = true was set in #newBlobDb()


        // test the recovery
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            final BlobId blobId = new BlobId(tempBin1._2.getValue());
            final Tuple2<byte[], MessageDigest> actual = getCommit(blobDb.transactionManager, blobDb.blobStore, blobId);
            assertNotNull(actual);
            assertEquals(tempBin1._2, tempBin1._2);
            assertArrayEquals(tempBin1._1, tempBin1._1);
            if (blobDb.blobStore instanceof BlobStoreImpl) {
                assertEquals(1, ((BlobStoreImpl)blobDb.blobStore).getReferenceCount(blobId).intValue());
            }
        }
    }

    /**
     * Add Blob and commit, re-add same Blob and commit, then stop (either by shutdown or simulated crash).
     *
     * Expect that the Blob is present after recovery.
     */
    @Test
    public void addCommit_addCommit() throws IOException, BrokerPoolServiceException, EXistException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> tempBin1 = generateTestFile();

        // write the data
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            addCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
            addCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
        }
        // NOTE: when BlobDB is closed by the ARM expression above it simulates a crash as BrokerPool.FORCE_CORRUPTION = true was set in #newBlobDb()


        // test the recovery
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            final BlobId blobId = new BlobId(tempBin1._2.getValue());
            final Tuple2<byte[], MessageDigest> actual = getCommit(blobDb.transactionManager, blobDb.blobStore, blobId);
            assertNotNull(actual);
            assertEquals(tempBin1._2, tempBin1._2);
            assertArrayEquals(tempBin1._1, tempBin1._1);
            if (blobDb.blobStore instanceof BlobStoreImpl) {
                assertEquals(2, ((BlobStoreImpl)blobDb.blobStore).getReferenceCount(blobId).intValue());
            }
        }
    }

    /**
     * Add Blob and commit, re-add same Blob (DO NOT commit), then stop (either by shutdown or simulated crash).
     *
     * Expect that the Blob is present after recovery.
     */
    @Test
    public void addCommit_addNoCommit() throws IOException, BrokerPoolServiceException, EXistException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> tempBin1 = generateTestFile();

        // write the data
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            addCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
            addNoCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
        }
        // NOTE: when BlobDB is closed by the ARM expression above it simulates a crash as BrokerPool.FORCE_CORRUPTION = true was set in #newBlobDb()


        // test the recovery
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            final BlobId blobId = new BlobId(tempBin1._2.getValue());
            final Tuple2<byte[], MessageDigest> actual = getCommit(blobDb.transactionManager, blobDb.blobStore, blobId);
            assertNotNull(actual);
            assertEquals(tempBin1._2, tempBin1._2);
            assertArrayEquals(tempBin1._1, tempBin1._1);
            if (blobDb.blobStore instanceof BlobStoreImpl) {
                assertEquals(1, ((BlobStoreImpl)blobDb.blobStore).getReferenceCount(blobId).intValue());
            }
        }
    }

    /**
     * Add Blob and commit, re-add same Blob and commit, Remove Blob and commit, then stop (either by shutdown or simulated crash).
     *
     * Expect that the Blob is present after recovery.
     */
    @Test
    public void addCommit_addCommit_removeCommit() throws IOException, BrokerPoolServiceException, EXistException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> tempBin1 = generateTestFile();

        // write the data
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            addCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
            addCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
            removeCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
        }
        // NOTE: when BlobDB is closed by the ARM expression above it simulates a crash as BrokerPool.FORCE_CORRUPTION = true was set in #newBlobDb()


        // test the recovery
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            final BlobId blobId = new BlobId(tempBin1._2.getValue());
            final Tuple2<byte[], MessageDigest> actual = getCommit(blobDb.transactionManager, blobDb.blobStore, blobId);
            assertNotNull(actual);
            assertEquals(tempBin1._2, tempBin1._2);
            assertArrayEquals(tempBin1._1, tempBin1._1);
            if (blobDb.blobStore instanceof BlobStoreImpl) {
                assertEquals(1, ((BlobStoreImpl)blobDb.blobStore).getReferenceCount(blobId).intValue());
            }
        }
    }

    /**
     * Add Blob and commit, re-add same Blob and commit, Remove Blob (DO NOT commit), then stop (either by shutdown or simulated crash).
     *
     * Expect that the Blob is present after recovery.
     */
    @Test
    public void addCommit_addCommit_removeNoCommit() throws IOException, BrokerPoolServiceException, EXistException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> tempBin1 = generateTestFile();

        // write the data
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            addCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
            addCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
            removeNoCommit(blobDb.transactionManager, blobDb.blobStore, tempBin1._1);
        }
        // NOTE: when BlobDB is closed by the ARM expression above it simulates a crash as BrokerPool.FORCE_CORRUPTION = true was set in #newBlobDb()


        // test the recovery
        try (final BlobDb blobDb = newBlobDb(temporaryFolder.getRoot().toPath(), blobDbx,  blobDir)) {
            blobDb.blobStore.open();

            final BlobId blobId = new BlobId(tempBin1._2.getValue());
            final Tuple2<byte[], MessageDigest> actual = getCommit(blobDb.transactionManager, blobDb.blobStore, blobId);
            assertNotNull(actual);
            assertEquals(tempBin1._2, tempBin1._2);
            assertArrayEquals(tempBin1._1, tempBin1._1);
            if (blobDb.blobStore instanceof BlobStoreImpl) {
                assertEquals(2, ((BlobStoreImpl)blobDb.blobStore).getReferenceCount(blobId).intValue());
            }
        }
    }

    private void addCommit(final TransactionManager transactionManager, final BlobStore blobStore, final byte[] blob) throws IOException, TransactionException {
        try (final InputStream is = new FastByteArrayInputStream(blob)) {
            try (final Txn transaction = transactionManager.beginTransaction()) {
                blobStore.add(transaction, is);

                transaction.commit();
            }
        }
    }

    private void addNoCommit(final TransactionManager transactionManager, final BlobStore blobStore, final byte[] blob) throws IOException, TransactionException {
        try (final InputStream is = new FastByteArrayInputStream(blob)) {
            final Txn transaction = transactionManager.beginTransaction();
            blobStore.add(transaction, is);
            // NOTE must not use ARM to close the transaction, otherwise it will auto-abort!
        }
    }

    private void removeCommit(final TransactionManager transactionManager, final BlobStore blobStore, final byte[] blob) throws IOException, TransactionException {
        final StreamableDigest streamableDigest = DIGEST_TYPE.newStreamableDigest();
        streamableDigest.update(blob);
        final BlobId blobId = new BlobId(streamableDigest.getMessageDigest());

        try (final Txn transaction = transactionManager.beginTransaction()) {
            blobStore.remove(transaction, blobId);

            transaction.commit();
        }
    }

    private void removeNoCommit(final TransactionManager transactionManager, final BlobStore blobStore, final byte[] blob) throws IOException, TransactionException {
        final StreamableDigest streamableDigest = DIGEST_TYPE.newStreamableDigest();
        streamableDigest.update(blob);
        final BlobId blobId = new BlobId(streamableDigest.getMessageDigest());

        final Txn transaction = transactionManager.beginTransaction();
        blobStore.remove(transaction, blobId);
        // NOTE must not use ARM to close the transaction, otherwise it will auto-abort!
    }

    private Tuple2<byte[], MessageDigest> getCommit(final TransactionManager transactionManager, final BlobStore blobStore, final BlobId blobId) throws IOException, TransactionException {
        try (final Txn transaction = transactionManager.beginTransaction()) {
            try (final InputStream is = blobStore.get(transaction, blobId)) {
                if (is == null) {
                    return null;
                }
                return readAll(is);
            } finally {
                transaction.commit();
            }
        }
    }

    private BlobDb newBlobDb(final Path journalDir, final Path blobDbx, final Path blobDir) throws BrokerPoolServiceException, EXistException {
        final Configuration mockConfiguration = createNiceMock(Configuration.class);
        expect(mockConfiguration.getProperty(Journal.PROPERTY_RECOVERY_JOURNAL_DIR)).andReturn(journalDir);
        expect(mockConfiguration.getProperty(BrokerPool.PROPERTY_RECOVERY_GROUP_COMMIT, false)).andReturn(false);

        expect(mockConfiguration.getProperty(PROPERTY_RECOVERY_SYNC_ON_COMMIT, true)).andReturn(true);
        expect(mockConfiguration.getProperty(PROPERTY_RECOVERY_SIZE_MIN, 1)).andReturn(1);
        expect(mockConfiguration.getProperty(PROPERTY_RECOVERY_SIZE_LIMIT, 100)).andReturn(100);
        replay(mockConfiguration);

        final BrokerPool mockBrokerPool = createNiceMock(BrokerPool.class);
        if (!cleanShutdown) {
            mockBrokerPool.FORCE_CORRUPTION = true;  // NOTE: needed so we don't checkpoint at clean shutdown and can simulate a crash!
        }

        final SecurityManager mockSecurityManager = createNiceMock(SecurityManager.class);
        final Subject mockSystemSubject = createNiceMock(Subject.class);
        expect(mockBrokerPool.getSecurityManager()).andReturn(mockSecurityManager).anyTimes();
        expect(mockSecurityManager.getSystemSubject()).andReturn(mockSystemSubject).anyTimes();
        replay(mockSecurityManager);

        final JournalManager journalManager = new JournalManager();
        journalManager.configure(mockConfiguration);

        final DBBroker mockSystemBroker = createNiceMock(DBBroker.class);
        final Txn mockSystemTransaction = createNiceMock(Txn.class);

        final SystemTaskManager mockSystemTaskManager = createNiceMock(SystemTaskManager.class);
        mockSystemTaskManager.processTasks(mockSystemBroker, mockSystemTransaction);
        expectLastCall().anyTimes();
        replay(mockSystemTaskManager);

        final DBBroker mockBroker = createNiceMock(DBBroker.class);
        expect(mockBroker.getBrokerPool()).andReturn(mockBrokerPool).anyTimes();
        expect(mockBrokerPool.getBroker()).andReturn(mockBroker).anyTimes();
        replay(mockBroker);

        final TransactionManager transactionManager = new TransactionManager(mockBrokerPool, Optional.of(journalManager), mockSystemTaskManager);

        final Scheduler mockScheduler = createNiceMock(Scheduler.class);

        final BlobStore blobStore = new BlobStoreImpl(mockBrokerPool, blobDbx, blobDir, DIGEST_TYPE);

        expect(mockBrokerPool.getConfiguration()).andReturn(mockConfiguration).anyTimes();
        expect(mockBrokerPool.getScheduler()).andReturn(mockScheduler);
        expect(mockScheduler.createPeriodicJob(anyLong(), anyObject(FileLockHeartBeat.class), anyLong(), anyObject(Properties.class))).andReturn(true);
        expect(mockBrokerPool.getTransactionManager()).andReturn(transactionManager).anyTimes();

        expect(mockBrokerPool.getThreadGroup()).andReturn(Thread.currentThread().getThreadGroup());
        expect(mockBrokerPool.getId()).andReturn("BlobStoreRecoveryTest").times(2);
        expect(mockBrokerPool.getJournalManager()).andReturn(Optional.of(journalManager)).anyTimes();
        expect(mockBrokerPool.getBlobStore()).andReturn(blobStore).anyTimes();
        replay(mockBrokerPool);
        journalManager.prepare(mockBrokerPool);

        final RecoveryManager recoveryManager = new RecoveryManager(mockBroker, journalManager, false);

        recoveryManager.recover();

        return new BlobDb(transactionManager, blobStore);
    }

    private static class BlobDb implements AutoCloseable {
        public final TransactionManager transactionManager;
        public final BlobStore blobStore;


        public BlobDb(final TransactionManager transactionManager, final BlobStore blobStore) {
            this.transactionManager = transactionManager;
            this.blobStore = blobStore;
        }

        @Override
        public void close() throws IOException {
            blobStore.close();
            transactionManager.shutdown();
        }
    }

    private Tuple2<byte[], MessageDigest> readAll(InputStream is) throws IOException {
        final StreamableDigest streamableDigest = DIGEST_TYPE.newStreamableDigest();
        is = new DigestInputStream(is, streamableDigest);
        try (final FastByteArrayOutputStream os = new FastByteArrayOutputStream()) {
            os.write(is);
            return Tuple(os.toByteArray(), streamableDigest.copyMessageDigest());
        }
    }

    private Tuple2<byte[], MessageDigest> generateTestFile() {
        // generate random data
        final byte[] data = new byte[1024 * 1024];  // 1MB
        random.nextBytes(data);

        // get the checksum of the random data
        final StreamableDigest streamableDigest = DIGEST_TYPE.newStreamableDigest();
        streamableDigest.update(data);
        final MessageDigest expectedDataDigest = streamableDigest.copyMessageDigest();

        return Tuple(data, expectedDataDigest);
    }
}
