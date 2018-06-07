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
package org.exist.storage.journal;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.*;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests to check that the correct entries
 * are written to the journal during
 * various operations.
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public abstract class AbstractJournalTest {

    private static boolean COMMIT = true;
    private static boolean NO_COMMIT = false;

    /**
     * We set useTemporaryStorage=true for ExistEmbeddedServer
     * so that each test runs on its own data directory.
     */
    @Rule
    public final ExistEmbeddedServer existEmbeddedServer =
            new ExistEmbeddedServer(true, true);

    @Test
    public void store() throws LockException, TriggerException, PermissionDeniedException, EXistException,
            IOException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                store_expected(stored._1, stored._2),
                readLatestJournalEntries());
    }

    @Test
    public void store_isRepeatable() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        store();
        existEmbeddedServer.restart();

        store();
        existEmbeddedServer.restart();

        store();
    }

    protected abstract List<ExpectedLoggable> store_expected(final long storedTxnId, final String storedDbPath);

    @Test
    public void storeWithoutCommit() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> stored = store(NO_COMMIT, testFile);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                storeWithoutCommit_expected(stored._1, stored._2),
                readLatestJournalEntries());
    }

    @Test
    public void storeWithoutCommit_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        storeWithoutCommit();
        existEmbeddedServer.restart();

        storeWithoutCommit();
        existEmbeddedServer.restart();

        storeWithoutCommit();
    }

    protected abstract List<ExpectedLoggable> storeWithoutCommit_expected(final long storedTxnId, final String storedDbPath);

    @Test
    public void storeThenDelete() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);
        final Tuple2<Long, String> deleted = delete(COMMIT, testFile);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                storeThenDelete_expected(stored._1, stored._2, deleted._1, deleted._2),
                readLatestJournalEntries());
    }

    @Test
    public void storeThenDelete_isRepeatable() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        storeThenDelete();
        existEmbeddedServer.restart();

        storeThenDelete();
        existEmbeddedServer.restart();

        storeThenDelete();
    }

    protected abstract List<ExpectedLoggable> storeThenDelete_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath);

    @Test
    public void storeWithoutCommitThenDelete() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> stored = store(NO_COMMIT, testFile);
        final Tuple2<Long, String> deleted = delete(COMMIT, testFile);
        flushJournal();


        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                storeWithoutCommitThenDelete_expected(stored._1, stored._2, deleted._1, deleted._2),
                readLatestJournalEntries());
    }

    @Ignore("Shows a bug with recovery of binary entries in the journal")
    @Test
    public void storeWithoutCommitThenDelete_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        storeWithoutCommitThenDelete();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDelete();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDelete();
    }

    protected abstract List<ExpectedLoggable> storeWithoutCommitThenDelete_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath);

    @Test
    public void storeThenDeleteWithoutCommit() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);
        final Tuple2<Long, String> deleted = delete(NO_COMMIT, testFile);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                storeThenDeleteWithoutCommit_expected(stored._1, stored._2, deleted._1, deleted._2),
                readLatestJournalEntries());
    }

    @Test
    public void storeThenDeleteWithoutCommit_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        storeThenDeleteWithoutCommit();
        existEmbeddedServer.restart();

        storeThenDeleteWithoutCommit();
        existEmbeddedServer.restart();

        storeThenDeleteWithoutCommit();
    }

    protected abstract List<ExpectedLoggable> storeThenDeleteWithoutCommit_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath);

    @Test
    public void storeWithoutCommitThenDeleteWithoutCommit() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> stored = store(NO_COMMIT, testFile);
        final Tuple2<Long, String> deleted = delete(NO_COMMIT, testFile);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                storeWithoutCommitThenDeleteWithoutCommit_expected(stored._1, stored._2, deleted._1, deleted._2),
                readLatestJournalEntries());
    }

    @Test
    public void storeWithoutCommitThenDeleteWithoutCommit_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, NoSuchFieldException, IllegalAccessException, DatabaseConfigurationException {
        storeWithoutCommitThenDeleteWithoutCommit();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDeleteWithoutCommit();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDeleteWithoutCommit();
    }

    protected abstract List<ExpectedLoggable> storeWithoutCommitThenDeleteWithoutCommit_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath);

    @Test
    public void delete() throws LockException, TriggerException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored._1, stored._2),
                readLatestJournalEntries());

        // restart the database server
        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> deleted = delete(COMMIT, testFile);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                delete_expected(deleted._1, deleted._2),
                readLatestJournalEntries());
    }

    @Test
    public void delete_isRepeatable() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        delete();
        existEmbeddedServer.restart();

        delete();
        existEmbeddedServer.restart();

        delete();
    }

    protected abstract List<ExpectedLoggable> delete_expected(final long deletedTxnId, final String deletedDbPath);

    @Test
    public void deleteWithoutCommit() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored._1, stored._2),
                readLatestJournalEntries());

        // restart the database server
        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> deleted = delete(NO_COMMIT, testFile);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                deleteWithoutCommit_expected(deleted._1, deleted._2),
                readLatestJournalEntries());
    }

    @Test
    public void deleteWithoutCommit_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        deleteWithoutCommit();
        existEmbeddedServer.restart();

        deleteWithoutCommit();
        existEmbeddedServer.restart();

        deleteWithoutCommit();
    }

    protected abstract List<ExpectedLoggable> deleteWithoutCommit_expected(final long deletedTxnId, final String deletedDbPath);

    @Test
    public void replace() throws LockException, TriggerException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored._1, stored._2),
                readLatestJournalEntries());

        // restart the database server
        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> replaced = store(COMMIT, testFile2, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replace_expected(replaced._1, replaced._2),
                readLatestJournalEntries());
    }

    @Test
    public void replace_isRepeatable() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replace();
        existEmbeddedServer.restart();

        replace();
        existEmbeddedServer.restart();

        replace();
    }

    protected abstract List<ExpectedLoggable> replace_expected(final long replacedTxnId, final String replacedDbPath);

    @Test
    public void replaceWithoutCommit() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored._1, stored._2),
                readLatestJournalEntries());

        // restart the database server
        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> replaced = store(NO_COMMIT, testFile2, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceWithoutCommit_expected(replaced._1, replaced._2),
                readLatestJournalEntries());
    }

    @Test
    public void replaceWithoutCommit_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replaceWithoutCommit();
        existEmbeddedServer.restart();

        replaceWithoutCommit();
        existEmbeddedServer.restart();

        replaceWithoutCommit();
    }

    protected abstract List<ExpectedLoggable> replaceWithoutCommit_expected(final long replacedTxnId, final String replacedDbPath);

    @Test
    public void replaceThenDelete() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored._1, stored._2),
                readLatestJournalEntries());

        // restart the database server
        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> replaced = store(COMMIT, testFile2, testFilename);
        final Tuple2<Long, String> deleted = delete(COMMIT, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceThenDelete_expected(replaced._1, replaced._2, deleted._1, deleted._2),
                readLatestJournalEntries());
    }

    @Test
    public void replaceThenDelete_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replaceThenDelete();
        existEmbeddedServer.restart();

        replaceThenDelete();
        existEmbeddedServer.restart();

        replaceThenDelete();
    }

    protected abstract List<ExpectedLoggable> replaceThenDelete_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath);

    @Test
    public void replaceWithoutCommitThenDelete() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored._1, stored._2),
                readLatestJournalEntries());

        // restart the database server
        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> replaced = store(NO_COMMIT, testFile2, testFilename);
        final Tuple2<Long, String> deleted = delete(COMMIT, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceWithoutCommitThenDelete_expected(replaced._1, replaced._2, deleted._1, deleted._2),
                readLatestJournalEntries());
    }

    @Ignore("Shows a bug with recovery of binary entries in the journal")
    @Test
    public void replaceWithoutCommitThenDelete_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replaceWithoutCommitThenDelete();
        existEmbeddedServer.restart();

        replaceWithoutCommitThenDelete();
        existEmbeddedServer.restart();

        replaceWithoutCommitThenDelete();
    }

    protected abstract List<ExpectedLoggable> replaceWithoutCommitThenDelete_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath);

    @Test
    public void replaceThenDeleteWithoutCommit() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored._1, stored._2),
                readLatestJournalEntries());

        // restart the database server
        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> replaced = store(COMMIT, testFile2, testFilename);
        final Tuple2<Long, String> deleted = delete(NO_COMMIT, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceThenDeleteWithoutCommit_expected(replaced._1, replaced._2, deleted._1, deleted._2),
                readLatestJournalEntries());
    }

    @Test
    public void replaceThenDeleteWithoutCommit_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replaceThenDeleteWithoutCommit();
        existEmbeddedServer.restart();

        replaceThenDeleteWithoutCommit();
        existEmbeddedServer.restart();

        replaceThenDeleteWithoutCommit();
    }

    protected abstract List<ExpectedLoggable> replaceThenDeleteWithoutCommit_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath);

    @Test
    public void replaceWithoutCommitThenDeleteWithoutCommit() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored._1, stored._2),
                readLatestJournalEntries());

        // restart the database server
        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> replaced = store(NO_COMMIT, testFile2, testFilename);
        final Tuple2<Long, String> deleted = delete(NO_COMMIT, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceWithoutCommitThenDeleteWithoutCommit_expected(replaced._1, replaced._2, deleted._1, deleted._2),
                readLatestJournalEntries());
    }

    @Test
    public void replaceWithoutCommitThenDeleteWithoutCommit_isRepeatable() throws LockException,
            TriggerException, PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replaceWithoutCommitThenDeleteWithoutCommit();
        existEmbeddedServer.restart();

        replaceWithoutCommitThenDeleteWithoutCommit();
        existEmbeddedServer.restart();

        replaceWithoutCommitThenDeleteWithoutCommit();
    }

    protected abstract List<ExpectedLoggable> replaceWithoutCommitThenDeleteWithoutCommit_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath);


    private void assertPartialOrdered(final List<ExpectedLoggable> expectedPartialOrderedJournalEntries, final List<Loggable> actualJournalEntries) throws AssertionError {
        int expectedIdx = 0;

        final List<Long> expectedStartedTxns = new ArrayList<>();
        final Set<Long> unexpectedCommitted = new HashSet<>();
        final Set<Long> unexpectedAborted = new HashSet<>();

        for (final Loggable journalEntry : actualJournalEntries) {
            // we have found TxnStart, now compare journal entries in order against some expected partial order
            final ExpectedLoggable expected = expectedPartialOrderedJournalEntries.get(expectedIdx);
            if (expected.equals(journalEntry)) {
                // matched an expected partial entry

                if(journalEntry instanceof TxnStart) {
                    expectedStartedTxns.add(expected.transactionId);
                }

                expectedIdx++;  // move to the next expected partial

                if(expectedIdx == expectedPartialOrderedJournalEntries.size()) {
                    break; // we have matched all expected
                }
            } else {
                if (journalEntry instanceof TxnAbort) {
                    unexpectedAborted.add(journalEntry.getTransactionId());
                }

                if (journalEntry instanceof TxnCommit) {
                    unexpectedCommitted.add(journalEntry.getTransactionId());
                }
            }
        }

        if (expectedIdx != expectedPartialOrderedJournalEntries.size()) {
            throw new AssertionError("Expected " + expectedPartialOrderedJournalEntries.get(expectedIdx) + ", not found in journal entries");
        }

        for (final long expectedStartedTxn : expectedStartedTxns) {
            if (unexpectedAborted.contains(expectedStartedTxn)) {
                throw new AssertionError("ABORT T-" + expectedStartedTxn + " was not expected! ABORT must be explicitly specified in expected list.");
            }

            if (unexpectedCommitted.contains(expectedStartedTxn)) {
                throw new AssertionError("COMMIT T-" + expectedStartedTxn + " was not expected! COMMIT must be explicitly specified in expected list.");
            }
        }
    }



    protected abstract Path getTestFile1() throws IOException;

    protected abstract Path getTestFile2() throws IOException;

    /**
     * Check point's the journal, and forces switching to a new journal file.
     */
    private void checkpointJournalAndSwitchFile() throws NoSuchFieldException, IllegalAccessException, TransactionException {

        //set Journal#journalMinSize = 0, so that switch files will always happen
        final Field fldMinReplace = Journal.class.getDeclaredField("journalSizeMin");
        fldMinReplace.setAccessible(true);
        final Field modifiersField = Field.class.getDeclaredField( "modifiers" );
        modifiersField.setAccessible( true );
        modifiersField.setInt( fldMinReplace, fldMinReplace.getModifiers() & ~Modifier.FINAL );
        final Field fldJournal = JournalManager.class.getDeclaredField("journal");
        fldJournal.setAccessible(true);
        final Journal journal = (Journal)fldJournal.get(existEmbeddedServer.getBrokerPool().getJournalManager().get());
        final long existingMinReplaceValue = fldMinReplace.getLong(journal);
        fldMinReplace.setLong(journal, 0);

        // checkpoint the journal and switch file
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        synchronized (pool) {
            pool.getTransactionManager().checkpoint(true);
        }

        //restore the Journal#journalMinSize to its previous value
        fldMinReplace.set(journal, existingMinReplaceValue);
    }

    private List<Loggable> readLatestJournalEntries() throws IOException, LogException {
        final Configuration configuration = existEmbeddedServer.getBrokerPool().getConfiguration();
        final Path journalDir = (Path) Optional.ofNullable(configuration.getProperty(Journal.PROPERTY_RECOVERY_JOURNAL_DIR))
                .orElse(configuration.getProperty(BrokerPool.PROPERTY_DATA_DIR));

        final int lastNum;
        try (final Stream<Path> files = Files.list(journalDir).filter(f -> f.getFileName().toString().endsWith("." + Journal.LOG_FILE_SUFFIX))) {
            lastNum = Journal.findLastFile(files);
        }
        final Path lastJournalFile = journalDir.resolve(Journal.getFileName(lastNum));

        final List<Loggable> entries = new ArrayList<>();
        try (final JournalReader reader = new JournalReader(null, lastJournalFile, lastNum)) {
            Loggable entry = null;
            while((entry = reader.nextEntry()) != null) {
                entries.add(entry);
            }
        }
        return entries;
    }


    /**
     * Store a document into the database.
     *
     * @param commitAndClose true if the transaction should be committed. false will leave the transaction
     *      unfinished (i.e. neither committed, aborted, or closed)
     * @param file The file that to store
     *
     * @return a Tuple2(id, path), where id is of the transaction which stored the document, and path
     *     is the path to the document in the database.
     */
    private Tuple2<Long, String> store(final boolean commitAndClose, final Path file) throws EXistException, PermissionDeniedException,
            IOException, TriggerException, LockException {
        return store(commitAndClose, file, FileUtils.fileName(file));
    }

    /**
     * Store a document into the database.
     *
     * @param commitAndClose true if the transaction should be committed. false will leave the transaction
     *      unfinished (i.e. neither committed, aborted, or closed)
     * @param file The file that to store
     * @param dbFilename the name to use when storing the file in the database
     *
     * @return a Tuple2(id, path), where id is of the transaction which stored the document, and path
     *     is the path to the document in the database.
     */
    private Tuple2<Long, String> store(final boolean commitAndClose, final Path file, final String dbFilename) throws EXistException,
            PermissionDeniedException, IOException, TriggerException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            final Txn transaction = transact.beginTransaction();

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            final XmldbURI docDbUri = storeAndVerify(broker, transaction, root, file, dbFilename);

            if(commitAndClose) {
                transaction.commit();
                transaction.close();
            }

            return new Tuple2<>(transaction.getId(), docDbUri.getRawCollectionPath());
        }
    }

    /**
     * Store a document into the database and verify its correctness.
     *
     * @param broker The database broker
     * @param transaction The database transaction
     * @param collection The Collection into which the document should be stored
     * @param file The file which holds the content for the document to store in the database
     * @param dbFilename The name to store the document as in the database
     *
     * @return the path to the document stored in the database.
     */
    protected abstract XmldbURI storeAndVerify(final DBBroker broker, final Txn transaction, final Collection collection,
                                           final Path file, final String dbFilename) throws EXistException, PermissionDeniedException,
            IOException, TriggerException, LockException;

    /**
     * Read a document from the database.
     *
     * @param shouldExist true if the document should exist in the database, false if the document should not exist
     * @param file The file that was previously stored
     */
    private void read(final boolean shouldExist, final Path file)
            throws EXistException, PermissionDeniedException, IOException {
        read(shouldExist, file, FileUtils.fileName(file));
    }

    /**
     * Read a document from the database.
     *
     * @param shouldExist true if the document should exist in the database, false if the document should not exist
     * @param file The file that was previously stored
     * @param dbFilename The name of the file to read from the database
     */
    private void read(final boolean shouldExist, final Path file, final String dbFilename)
            throws EXistException, PermissionDeniedException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XmldbURI uri = TestConstants.TEST_COLLECTION_URI.append(dbFilename);
            try (final LockedDocument lockedDoc = broker.getXMLResource(uri, Lock.LockMode.READ_LOCK)) {

                if (!shouldExist) {
                    assertNull("Document should not exist in the database: " + uri, lockedDoc);
                } else {
                    assertNotNull("Document does not exist in the database: " + uri, lockedDoc);

                    readAndVerify(broker, lockedDoc.getDocument(), file, dbFilename);
                }
            }
        }
    }

    /**
     * Read and Verify that the document from the database is correct.
     *
     * @param broker The database broker.
     * @param doc The document from the database.
     * @param file The file that was previously stored
     * @param dbFilename The name of the file read from the database
     */
    protected abstract void readAndVerify(final DBBroker broker, final DocumentImpl doc,
                                          final Path file, final String dbFilename) throws EXistException, PermissionDeniedException, IOException;

    /**
     * Delete a document from the database.
     *
     * @param commitAndClose true if the transaction should be committed. false will leave the transaction
     *      unfinished (i.e. neither committed, aborted, or closed)
     * @param file The file that was previously stored, that should be deleted
     *
     * @return a Tuple2(id, path), where id is of the transaction which deleted the document, and path
     *     is the path of the deleted document from the database.
     */
    private Tuple2<Long, String> delete(final boolean commitAndClose, final Path file)
            throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        return delete(commitAndClose, FileUtils.fileName(file));
    }

    /**
     * Delete a document from the database.
     *
     * @param commitAndClose true if the transaction should be committed. false will leave the transaction
     *      unfinished (i.e. neither committed, aborted, or closed)
     * @param dbFilename The name of the file that was previously stored, that should be deleted
     *
     * @return a Tuple2(id, path), where id is of the transaction which deleted the document, and path
     *     is the path of the deleted document from the database.
     */
    private Tuple2<Long, String> delete(final boolean commitAndClose, final String dbFilename)
            throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            // the following transaction will not be committed. It will thus be rolled back by recovery
            final Txn transaction = transact.beginTransaction();

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            final DocumentImpl doc = root.getDocument(broker, XmldbURI.create(dbFilename));
            if(doc != null) {
                root.removeResource(transaction, broker, doc);
            }

            if(commitAndClose) {
                transaction.commit();
                transaction.close();
            }

            return new Tuple2<>(transaction.getId(), doc.getURI().getRawCollectionPath());
        }
    }

    private void flushJournal() {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        pool.getJournalManager().get().flush(true, false);
    }

    protected Path resolveTestFile(final String fileName) throws IOException {
        final Path path = TestUtils.getEXistHome().orElseGet(() -> Paths.get(".")).resolve(fileName);
        if(!Files.exists(path)) {
            throw new IOException("No such test file: " + path.toAbsolutePath().toString());
        }
        return path;
    }

    protected static ExpectedStart Start(final long transactionId) {
        return new ExpectedStart(transactionId);
    }

    protected static ExpectedAbort Abort(final long transactionId) {
        return new ExpectedAbort(transactionId);
    }

    protected static ExpectedCommit Commit(final long transactionId) {
        return new ExpectedCommit(transactionId);
    }

    protected static Checkpoint Checkpoint(final long transactionId) {
        return new Checkpoint(transactionId);
    }

    protected static abstract class ExpectedLoggable {
        protected final long transactionId;
        protected ExpectedLoggable(final long transactionId) {
            this.transactionId = transactionId;
        }
    }

    protected static class ExpectedStart extends ExpectedLoggable {
        public ExpectedStart(final long transactionId) {
            super(transactionId);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != TxnStart.class) return false;

            final TxnStart that = (TxnStart) o;

            return that.transactionId == transactionId;
        }

        @Override
        public String toString() {
            return "START T-" + transactionId;
        }
    }

    protected static class ExpectedAbort extends ExpectedLoggable {
        public ExpectedAbort(final long transactionId) {
            super(transactionId);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != TxnAbort.class) return false;

            final TxnAbort that = (TxnAbort) o;

            return that.transactionId == transactionId;
        }

        @Override
        public String toString() {
            return "ABORT T-" + transactionId;
        }
    }

    protected static class ExpectedCommit extends ExpectedLoggable {
        public ExpectedCommit(final long transactionId) {
            super(transactionId);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != TxnCommit.class) return false;

            final TxnCommit that = (TxnCommit) o;

            return that.transactionId == transactionId;
        }

        @Override
        public String toString() {
            return "COMMIT T-" + transactionId;
        }
    }

    protected static class ExpectedCheckpoint extends ExpectedLoggable {
        public ExpectedCheckpoint(final long transactionId) {
            super(transactionId);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != Checkpoint.class) return false;

            final Checkpoint that = (Checkpoint) o;

            return that.transactionId == transactionId;
        }

        @Override
        public String toString() {
            return "CHECKPOINT T-" + transactionId;
        }
    }
}
