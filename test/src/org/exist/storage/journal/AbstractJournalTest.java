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
import org.exist.numbering.DLN;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.Signatures;
import org.exist.storage.dom.AddValueLoggable;
import org.exist.storage.dom.RemovePageLoggable;
import org.exist.storage.index.StoreValueLoggable;
import org.exist.storage.io.VariableByteInputStream;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.*;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.*;
import org.exist.util.io.FastByteArrayInputStream;
import org.exist.xmldb.XmldbURI;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.exist.storage.NativeBroker.COLLECTIONS_DBX_ID;
import static org.exist.util.ByteConversion.byteToInt;
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

    private static final boolean COMMIT = true;
    private static final boolean NO_COMMIT = false;

    protected static final int FIRST_USABLE_DOC_ID = 7;
    protected static final int FIRST_USABLE_PAGE = 7;

    /**
     * We set useTemporaryStorage=true for ExistEmbeddedServer
     * so that each test runs on its own data directory.
     */
    @Rule
    public final ExistEmbeddedServer existEmbeddedServer =
            new ExistEmbeddedServer(true, true);

    @Test
    public void store() throws LockException, SAXException, PermissionDeniedException, EXistException,
            IOException, NoSuchFieldException, IllegalAccessException {
        store(false, 0);
    }

    private void store(final boolean shouldGenerateReplaceEntry, final int offset) throws IllegalAccessException, EXistException, NoSuchFieldException, IOException, LockException, SAXException, PermissionDeniedException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for store
        if (!shouldGenerateReplaceEntry) {
            // expected STORE
            assertPartialOrdered(
                    store_expected(stored._1, stored._2, offset),
                    readLatestJournalEntries());
        } else {
            // expected REPLACE
            assertPartialOrdered(
                    replace_expected(stored._1, stored._2, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }
    }

    @Test
    public void store_isRepeatable() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        store(false, 0);
        existEmbeddedServer.restart();

        store(true, 1);
        existEmbeddedServer.restart();

        store(true, 2);
    }

    protected abstract List<ExpectedLoggable> store_expected(final long storedTxnId, final String storedDbPath, final int offset);

    @Test
    public void storeWithoutCommit() throws LockException, SAXException, PermissionDeniedException,
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

        // check journal entries written for store without commit
        assertPartialOrdered(
                storeWithoutCommit_expected(stored._1, stored._2),
                readLatestJournalEntries());
    }

    @Test
    public void storeWithoutCommit_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        storeWithoutCommit();
        existEmbeddedServer.restart();

        storeWithoutCommit();
        existEmbeddedServer.restart();

        storeWithoutCommit();
    }

    protected abstract List<ExpectedLoggable> storeWithoutCommit_expected(final long storedTxnId, final String storedDbPath);

    @Test
    public void storeThenDelete() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, NoSuchFieldException, IllegalAccessException {
        storeThenDelete(0);
    }

    private void storeThenDelete(final int offset) throws LockException, SAXException, PermissionDeniedException,
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
                storeThenDelete_expected(stored._1, stored._2, deleted._1, deleted._2, offset),
                readLatestJournalEntries());
    }

    @Test
    public void storeThenDelete_isRepeatable() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        storeThenDelete(0);
        existEmbeddedServer.restart();

        storeThenDelete(1);
        existEmbeddedServer.restart();

        storeThenDelete(2);
    }

    protected abstract List<ExpectedLoggable> storeThenDelete_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath, final int offset);

    @Test
    public void storeWithoutCommitThenDelete() throws LockException, SAXException, PermissionDeniedException,
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

    @Test
    public void storeWithoutCommitThenDelete_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        storeWithoutCommitThenDelete();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDelete();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDelete();
    }

    protected abstract List<ExpectedLoggable> storeWithoutCommitThenDelete_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath);

    @Test
    public void storeThenDeleteWithoutCommit() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, NoSuchFieldException, IllegalAccessException {
        storeThenDeleteWithoutCommit(false, 0);
    }

    private void storeThenDeleteWithoutCommit(final boolean shouldGenerateReplaceEntry, final int offset) throws LockException, SAXException, PermissionDeniedException,
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
        if (!shouldGenerateReplaceEntry) {
            // expected STORE
            assertPartialOrdered(
                    storeThenDeleteWithoutCommit_expected(stored._1, stored._2, deleted._1, deleted._2, offset),
                    readLatestJournalEntries());
        } else {
            // expected REPLACE
            assertPartialOrdered(
                    replaceThenDeleteWithoutCommit_expected(stored._1, stored._2, deleted._1, deleted._2, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }
    }

    @Test
    public void storeThenDeleteWithoutCommit_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        storeThenDeleteWithoutCommit(false, 0);
        existEmbeddedServer.restart();

        storeThenDeleteWithoutCommit(true, 1);
        existEmbeddedServer.restart();

        storeThenDeleteWithoutCommit(true, 2);
    }

    protected abstract List<ExpectedLoggable> storeThenDeleteWithoutCommit_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath, final int offset);

    @Test
    public void storeWithoutCommitThenDeleteWithoutCommit() throws LockException, SAXException,
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
    public void storeWithoutCommitThenDeleteWithoutCommit_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, NoSuchFieldException, IllegalAccessException, DatabaseConfigurationException {
        storeWithoutCommitThenDeleteWithoutCommit();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDeleteWithoutCommit();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDeleteWithoutCommit();
    }

    protected abstract List<ExpectedLoggable> storeWithoutCommitThenDeleteWithoutCommit_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath);

    @Test
    public void delete() throws LockException, SAXException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        delete(0);
    }

    private void delete(final int offset) throws LockException, SAXException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored._1, stored._2, offset),
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

        // check journal entries written for delete
        assertPartialOrdered(
                delete_expected(deleted._1, deleted._2, offset),
                readLatestJournalEntries());
    }

    @Test
    public void delete_isRepeatable() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        delete(0);
        existEmbeddedServer.restart();

        delete(1);
        existEmbeddedServer.restart();

        delete(2);
    }

    protected abstract List<ExpectedLoggable> delete_expected(final long deletedTxnId, final String deletedDbPath, final int offset);

    @Test
    public void deleteWithoutCommit() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        deleteWithoutCommit(false, 0);
    }

    private void deleteWithoutCommit(final boolean shouldGenerateReplaceEntry, final int offset) throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        if (!shouldGenerateReplaceEntry) {
            // expected STORE
            assertPartialOrdered(
                    store_expected(stored._1, stored._2, offset),
                    readLatestJournalEntries());
        } else {
            // expected REPLACE
            assertPartialOrdered(
                    replace_expected(stored._1, stored._2, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }

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
                deleteWithoutCommit_expected(deleted._1, deleted._2, offset),
                readLatestJournalEntries());
    }

    @Test
    public void deleteWithoutCommit_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        deleteWithoutCommit(false, 0);
        existEmbeddedServer.restart();

        deleteWithoutCommit(true, 1);
        existEmbeddedServer.restart();

        deleteWithoutCommit(true, 2);
    }

    protected abstract List<ExpectedLoggable> deleteWithoutCommit_expected(final long deletedTxnId, final String deletedDbPath, final int offset);

    @Test
    public void replace() throws LockException, SAXException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replace(false, 0);
    }

    private void replace(final boolean storeShouldGenerateReplaceEntry, final int offset) throws IllegalAccessException, EXistException, NoSuchFieldException, IOException, LockException, SAXException, PermissionDeniedException, DatabaseConfigurationException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        if (!storeShouldGenerateReplaceEntry) {
            // expected STORE
            assertPartialOrdered(
                    store_expected(stored._1, stored._2, offset),
                    readLatestJournalEntries());
        } else {
            // expected REPLACE
            assertPartialOrdered(
                    replace_expected(stored._1, stored._2, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }

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
                replace_expected(replaced._1, replaced._2, offset, false),
                readLatestJournalEntries());
    }

    @Test
    public void replace_isRepeatable() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replace(false, 0);
        existEmbeddedServer.restart();

        replace(true, 2);
        existEmbeddedServer.restart();

        replace(true, 4);
    }

    protected abstract List<ExpectedLoggable> replace_expected(final long replacedTxnId, final String replacedDbPath, final int offset, final boolean overridesStore);

    @Test
    public void replaceWithoutCommit() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replaceWithoutCommit(false, 0);
    }

    private void replaceWithoutCommit(final boolean storeShouldGenerateReplaceEntry, final int offset) throws IllegalAccessException, EXistException, NoSuchFieldException, IOException, LockException, SAXException, PermissionDeniedException, DatabaseConfigurationException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        if (!storeShouldGenerateReplaceEntry) {
            // expected STORE
            assertPartialOrdered(
                    store_expected(stored._1, stored._2, offset),
                    readLatestJournalEntries());
        } else {
            // expected REPLACE
            assertPartialOrdered(
                    replace_expected(stored._1, stored._2, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }

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
                replaceWithoutCommit_expected(replaced._1, replaced._2, offset),
                readLatestJournalEntries());
    }

    @Test
    public void replaceWithoutCommit_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replaceWithoutCommit(false, 0);
        existEmbeddedServer.restart();

        replaceWithoutCommit(true, 1);
        existEmbeddedServer.restart();

        replaceWithoutCommit(true, 2);
    }

    protected abstract List<ExpectedLoggable> replaceWithoutCommit_expected(final long replacedTxnId, final String replacedDbPath, final int offset);

    @Test
    public void replaceThenDelete() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replaceThenDelete(0);
    }

    private void replaceThenDelete(final int offset) throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        final boolean isXmlTest = this instanceof JournalXmlTest;
        assertPartialOrdered(
                store_expected_for_replaceThenDelete(stored._1, stored._2, offset),
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
                replaceThenDelete_expected(replaced._1, replaced._2, deleted._1, deleted._2, offset),
                readLatestJournalEntries());
    }

    @Test
    public void replaceThenDelete_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replaceThenDelete(0);
        existEmbeddedServer.restart();

        replaceThenDelete(2);
        existEmbeddedServer.restart();

        replaceThenDelete(4);
    }

    /**
     * NOTE: needs to be overridden by {@link JournalXmlTest}!
     */
    protected List<ExpectedLoggable> store_expected_for_replaceThenDelete(final long storedTxnId, final String storedDbPath, final int offset) {
        return store_expected(storedTxnId, storedDbPath, offset);
    }

    protected abstract List<ExpectedLoggable> replaceThenDelete_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath, final int offset);

    @Test
    public void replaceWithoutCommitThenDelete() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replaceWithoutCommitThenDelete(0);
    }

    private void replaceWithoutCommitThenDelete(final int offset) throws LockException, SAXException,
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
                store_expected(stored._1, stored._2, offset),
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
                replaceWithoutCommitThenDelete_expected(replaced._1, replaced._2, deleted._1, deleted._2, offset),
                readLatestJournalEntries());
    }

    /**
     * Shows that recovery of entries in the journal fail for a non-linear history of a resource.
     * The history created by this test, should never be created within by a single thread unless
     * due to a programming mistake.
     *
     * It is currently possible to create such a non-recoverable history between threads
     * for a single resource, however that must be solved by improved locking of resources
     * e.g. keeping resource locks for the duration of a transaction.
     *
     * This test creates the journal history (repetitively):
     *
     * 1. <START T-1>
     * 2. <T-1, A, null, x>
     * 3. <COMMIT T-1>      // store!
     * 4. <START T-2>
     * 5. <T-2, A, x, y>	// replace!
     * 6. <START T-3>
     * 7. <T-3, A, x, null>	// delete!
     * 8. <COMMIT T-3>
     * 9. CRASH!
     *
     * In the above:
     *     * "T-n" is the transaction id.
     *     * <T-n, A, v, w> is the tuple <transactionId, key, previousValue, newValue)
     *
     * The problem with the above schedule, after crash, the recovery will never set
     * key "A" to value "null", which it likely should.
     *
     * eXist-db performs the following recovery:
     *
     * R1. redo schedule step 5: A=y
     * R2. redo schedule step 7: A=null
     * R3. undo schedule step 5: A=x
     *
     * Step R3 will leaves the database in an inconsistent state (i.e. A != null).
     */
    @Ignore("Only possible from a single-thread by programming error. Journal is not expected to recover such cases!")
    @Test
    public void replaceWithoutCommitThenDelete_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replaceWithoutCommitThenDelete(0);
        existEmbeddedServer.restart();

        replaceWithoutCommitThenDelete(0);
        existEmbeddedServer.restart();

        replaceWithoutCommitThenDelete(0);
    }

    protected abstract List<ExpectedLoggable> replaceWithoutCommitThenDelete_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath, final int offset);

    @Test
    public void replaceThenDeleteWithoutCommit() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replaceThenDeleteWithoutCommit(false, 0);
    }

    private void replaceThenDeleteWithoutCommit(final boolean storeShouldGenerateReplaceEntry, final int offset) throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        if (!storeShouldGenerateReplaceEntry) {
            //  expected STORE
            assertPartialOrdered(
                    store_expected(stored._1, stored._2, offset),
                    readLatestJournalEntries());
        } else {
            // expected REPLACE
            assertPartialOrdered(
                    replace_expected(stored._1, stored._2, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }

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
                replaceThenDeleteWithoutCommit_expected(replaced._1, replaced._2, deleted._1, deleted._2, offset, false),
                readLatestJournalEntries());
    }

    @Test
    public void replaceThenDeleteWithoutCommit_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replaceThenDeleteWithoutCommit(false, 0);
        existEmbeddedServer.restart();

        replaceThenDeleteWithoutCommit(true, 2);
        existEmbeddedServer.restart();

        replaceThenDeleteWithoutCommit(true, 4);
    }

    protected abstract List<ExpectedLoggable> replaceThenDeleteWithoutCommit_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath, final int offset, final boolean overridesStore);

    @Test
    public void replaceWithoutCommitThenDeleteWithoutCommit() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replaceWithoutCommitThenDeleteWithoutCommit(false, 0);
    }

    private void replaceWithoutCommitThenDeleteWithoutCommit(final boolean storeShouldGenerateReplaceEntry, final int offset) throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        if (!storeShouldGenerateReplaceEntry) {
            // expected STORE
            assertPartialOrdered(
                    store_expected(stored._1, stored._2, offset),
                    readLatestJournalEntries());
        } else {
            //  expected REPLACE
            assertPartialOrdered(
                    replace_expected(stored._1, stored._2, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }

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
                replaceWithoutCommitThenDeleteWithoutCommit_expected(replaced._1, replaced._2, deleted._1, deleted._2, offset),
                readLatestJournalEntries());
    }

    @Test
    public void replaceWithoutCommitThenDeleteWithoutCommit_isRepeatable() throws LockException,
            SAXException, PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException {
        replaceWithoutCommitThenDeleteWithoutCommit(false, 0);
        existEmbeddedServer.restart();

        replaceWithoutCommitThenDeleteWithoutCommit(true, 1);
        existEmbeddedServer.restart();

        replaceWithoutCommitThenDeleteWithoutCommit(true, 2);
    }

    protected abstract List<ExpectedLoggable> replaceWithoutCommitThenDeleteWithoutCommit_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath, final int offset);


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
            IOException, SAXException, LockException {
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
            PermissionDeniedException, IOException, SAXException, LockException {
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
            IOException, SAXException, LockException;

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

    protected ExpectedCollectionNextDocId CollectionNextDocId(final long transactionId, final long page, final int nextDocId) {
        return new ExpectedCollectionNextDocId(transactionId, page, nextDocId);
    }

    protected ExpectedCollectionCreateDoc CollectionCreateDoc(final long transactionId, final long page, final int docId, final String docUri) {
        return new ExpectedCollectionCreateDoc(transactionId, page, docId, docUri);
    }

    protected ExpectedCollectionDeleteDoc CollectionDeleteDoc(final long transactionId, final long page, final int docId, final String docUri) {
        return new ExpectedCollectionDeleteDoc(transactionId, page, docId, docUri);
    }

    protected ExpectedStoreElementNode StoreElementNode(final long transactionId, final long page, final int children) {
        return new ExpectedStoreElementNode(transactionId, page, children);
    }

    protected ExpectedStoreTextNode StoreTextNode(final long transactionId, final long page, final String text) {
        return new ExpectedStoreTextNode(transactionId, page, text);
    }

    protected ExpectedDeleteElementNode DeleteElementNode(final long transactionId, final long page, final int children) {
        return new ExpectedDeleteElementNode(transactionId, page, children);
    }

    protected abstract static class AbstractExpectedStoreValue extends ExpectedLoggable {
        protected final long page;

        public AbstractExpectedStoreValue(final long transactionId, final long page) {
            super(transactionId);
            this.page = page;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != StoreValueLoggable.class) return false;

            final StoreValueLoggable that = (StoreValueLoggable) o;
            return that.transactionId == transactionId
                    && that.getPage() == page
                    && equalsStoreValue(that);
        }

        protected abstract boolean equalsStoreValue(final StoreValueLoggable o);

        @Override
        public String toString() {
            return "STORED VALUE T-" + transactionId;
        }
    }

    protected class ExpectedCollectionNextDocId extends AbstractExpectedStoreValue {
        private final int nextDocId;

        public ExpectedCollectionNextDocId(final long transactionId, final long pageId, final int nextDocId) {
            super(transactionId, pageId);
            this.nextDocId = nextDocId;
        }

        @Override
        protected boolean equalsStoreValue(final StoreValueLoggable o) {
            if (o.getFileId() != COLLECTIONS_DBX_ID) {
                return false;
            }

            final int thatDocId = byteToInt(o.getValue(), 0);
            return thatDocId == nextDocId;
        }

        @Override
        public String toString() {
            return "STORED VALUE T-" + transactionId + " nextDocId(txnId=" + transactionId + ", page=" + page + ", id=" + nextDocId + ")";
        }
    }

    protected class ExpectedCollectionCreateDoc extends AbstractExpectedStoreValue {
        private final int docId;
        private final String docUri;

        public ExpectedCollectionCreateDoc(final long transactionId, final long pageId, final int docId, final String docUri) {
            super(transactionId, pageId);
            this.docId = docId;
            this.docUri = docUri;
        }

        @Override
        protected boolean equalsStoreValue(final StoreValueLoggable o) {
            if (o.getFileId() != COLLECTIONS_DBX_ID) {
                return false;
            }

            try {
                final VariableByteInputStream vis = new VariableByteInputStream(new FastByteArrayInputStream(o.getValue()));
                final int thatDocId = vis.readInt();
                final String thatDocName = vis.readUTF();

                return thatDocId == docId
                        && thatDocName.equals(XmldbURI.create(docUri).lastSegment().toString());
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String toString() {
            return "STORED INDEX VALUE T-" + transactionId + " collectionCreateDoc(txnId=" + transactionId + ", page=" + page + ", docId=" + docId + ", uri=" + docUri + ")";
        }
    }

    protected abstract static class AbstractIndexExpectedRemoveValue extends ExpectedLoggable {
        protected final long page;

        public AbstractIndexExpectedRemoveValue(final long transactionId, final long page) {
            super(transactionId);
            this.page = page;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != org.exist.storage.index.RemoveValueLoggable.class) return false;

            final org.exist.storage.index.RemoveValueLoggable that = (org.exist.storage.index.RemoveValueLoggable) o;
            return that.transactionId == transactionId
                    && that.getPage() == page
                    && equalsRemoveValue(that);
        }

        protected abstract boolean equalsRemoveValue(final org.exist.storage.index.RemoveValueLoggable o);

        @Override
        public String toString() {
            return "REMOVED INDEX VALUE T-" + transactionId;
        }
    }

    protected class ExpectedCollectionDeleteDoc extends AbstractIndexExpectedRemoveValue {
        private final int docId;
        private final String docUri;

        public ExpectedCollectionDeleteDoc(final long transactionId, final long pageId, final int docId, final String docUri) {
            super(transactionId, pageId);
            this.docId = docId;
            this.docUri = docUri;
        }

        @Override
        protected boolean equalsRemoveValue(final org.exist.storage.index.RemoveValueLoggable o) {
            if (o.getFileId() != COLLECTIONS_DBX_ID) {
                return false;
            }

            try {
                final VariableByteInputStream vis = new VariableByteInputStream(new FastByteArrayInputStream(o.getOldData(), o.getOffset(), o.getLen()));
                final int thatDocId = vis.readInt();
                final String thatDocName = vis.readUTF();

                return thatDocId == docId
                        && thatDocName.equals(XmldbURI.create(docUri).lastSegment().toString());
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String toString() {
            return "REMOVED INDEX VALUE T-" + transactionId + " collectionDeleteDoc(txnId=" + transactionId + ", page=" + page + ", docId=" + docId + ", uri=" + docUri + ")";
        }
    }

    protected abstract static class AbstractExpectedAddValue extends ExpectedLoggable {
        protected final long page;

        public AbstractExpectedAddValue(final long transactionId, final long page) {
            super(transactionId);
            this.page = page;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != AddValueLoggable.class) return false;

            final AddValueLoggable that = (AddValueLoggable) o;
            return that.transactionId == transactionId
                    && that.getPageNum() == page
                    && equalsAddValue(that);
        }

        protected abstract boolean equalsAddValue(final AddValueLoggable o);

        @Override
        public String toString() {
            return "ADD VALUE T-" + transactionId;
        }
    }

    protected class ExpectedStoreElementNode extends AbstractExpectedAddValue {
        private final int children;

        public ExpectedStoreElementNode(final long transactionId, final long pageId, final int children) {
            super(transactionId, pageId);
            this.children = children;
        }

        @Override
        protected boolean equalsAddValue(final AddValueLoggable o) {
            final byte thatSignature = o.getValue()[0];
            final int thatChildren = ByteConversion.byteToInt(o.getValue(), 1);

            // check it is an ElementImpl and the number of children it has
            return thatSignature == ((Signatures.Elem << 0x5) | Signatures.byteContent)
                    && thatChildren == children;
        }

        @Override
        public String toString() {
            return "ADD VALUE T-" + transactionId + " storeElement(txnId=" + transactionId + ", page=" + page + ", children=" + children + ")";
        }
    }

    protected class ExpectedStoreTextNode extends AbstractExpectedAddValue {
        private final String text;

        public ExpectedStoreTextNode(final long transactionId, final long pageId, final String text) {
            super(transactionId, pageId);
            this.text = text;
        }

        @Override
        protected boolean equalsAddValue(final AddValueLoggable o) {
            int pos = 0;
            final byte thatSignature = o.getValue()[pos++];

            final int dlnLen = ByteConversion.byteToShort(o.getValue(), pos);
            pos += 2;
            final NodeId dln = new DLN(dlnLen, o.getValue(), pos);
            pos += dln.size();
            final String thatText = UTF8.decode(o.getValue(), pos, o.getValue().length - pos).toString();

            // check it is a TextImpl and the text matches
            return thatSignature == (Signatures.Char << 0x5)
                    && thatText.equals(text);
        }

        @Override
        public String toString() {
            return "ADD VALUE T-" + transactionId + " storeText(txnId=" + transactionId + ", page=" + page + ", text=" + text + ")";
        }
    }

    protected abstract static class AbstractExpectedRemovePage extends ExpectedLoggable {
        protected final long page;

        public AbstractExpectedRemovePage(final long transactionId, final long page) {
            super(transactionId);
            this.page = page;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != RemovePageLoggable.class) return false;

            final RemovePageLoggable that = (RemovePageLoggable) o;
            return that.transactionId == transactionId
                    && that.getPageNum() == page
                    && equalsRemovePage(that);
        }

        protected abstract boolean equalsRemovePage(final RemovePageLoggable o);

        @Override
        public String toString() {
            return "REMOVE PAGE T-" + transactionId;
        }
    }

    protected class ExpectedDeleteElementNode extends AbstractExpectedRemovePage {
        private final int children;

        public ExpectedDeleteElementNode(final long transactionId, final long pageId, final int children) {
            super(transactionId, pageId);
            this.children = children;
        }

        @Override
        protected boolean equalsRemovePage(final RemovePageLoggable o) {
            final byte thatSignature = o.getOldData()[4];
            final int thatChildren = ByteConversion.byteToInt(o.getOldData(), 5);

            // check it is an ElementImpl and the number of children it has
            return thatSignature == ((Signatures.Elem << 0x5) | Signatures.byteContent)
                    && thatChildren == children;
        }

        @Override
        public String toString() {
            return "REMOVE PAGE T-" + transactionId + " deleteElement(txnId=" + transactionId + ", page=" + page + ", children=" + children + ")";
        }
    }

    protected static <T> ExtendedArrayList<T> List(final T... items) {
        final ExtendedArrayList<T> list = new ExtendedArrayList<>(items.length);
        list.add(items);
        return list;
    }

    protected static class ExtendedArrayList<T> extends ArrayList<T> {
        private ExtendedArrayList(final int initialCapacity) {
            super(initialCapacity);
        }

        public ExtendedArrayList<T> add(final T... items) {
            for (final T item : items) {
                super.add(item);
            }
            return this;
        }
    }
}
