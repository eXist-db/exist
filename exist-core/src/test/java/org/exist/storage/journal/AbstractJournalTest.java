/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.storage.journal;

import com.evolvedbinary.j8fu.function.BiFunction5E;
import com.evolvedbinary.j8fu.function.Supplier5E;
import org.exist.EXistException;
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
import org.exist.storage.dom.WriteOverflowPageLoggable;
import org.exist.storage.index.StoreValueLoggable;
import org.exist.storage.io.VariableByteInputStream;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.*;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.*;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public abstract class AbstractJournalTest<T> {

    protected static final boolean COMMIT = true;
    protected static final boolean NO_COMMIT = false;

    protected static final int FIRST_USABLE_DOC_ID = 5;
    protected static final int FIRST_USABLE_PAGE = 5;

    /**
     * We set useTemporaryStorage=true for ExistEmbeddedServer
     * so that each test runs on its own data directory.
     */
    @Rule
    public final ExistEmbeddedServer existEmbeddedServer =
            new ExistEmbeddedServer(true, true);

    @After
    public void tearDown() {
        BrokerPool.FORCE_CORRUPTION = false;
    }

    @Test
    public void store() throws LockException, SAXException, PermissionDeniedException, EXistException,
            IOException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        store(false, 0);
    }

    private void store(final boolean shouldGenerateReplaceEntry, final int offset) throws IllegalAccessException, EXistException, NoSuchFieldException, IOException, LockException, SAXException, PermissionDeniedException, InterruptedException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> stored = store(COMMIT, testFile);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for store
        if (!shouldGenerateReplaceEntry) {
            // expected STORE
            assertPartialOrdered(
                    store_expected(stored, offset),
                    readLatestJournalEntries());
        } else {
            // expected REPLACE
            assertPartialOrdered(
                    replaceSameContent_expected(stored, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }
    }

    @Test
    public void store_isRepeatable() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        store(false, 0);
        existEmbeddedServer.restart();

        store(true, 1);
        existEmbeddedServer.restart();

        store(true, 2);
    }

    protected abstract List<ExpectedLoggable> store_expected(final TxnDoc<T> stored, final int offset);

    @Test
    public void storeWithoutCommit() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, NoSuchFieldException, IllegalAccessException, InterruptedException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> stored = store(NO_COMMIT, testFile);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for store without commit
        assertPartialOrdered(
                storeWithoutCommit_expected(stored),
                readLatestJournalEntries());
    }

    @Test
    public void storeWithoutCommit_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        storeWithoutCommit();
        existEmbeddedServer.restart();

        storeWithoutCommit();
        existEmbeddedServer.restart();

        storeWithoutCommit();
    }

    protected abstract List<ExpectedLoggable> storeWithoutCommit_expected(final TxnDoc<T> stored);

    @Test
    public void storeThenDelete() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        storeThenDelete(0);
    }

    private void storeThenDelete(final int offset) throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, NoSuchFieldException, IllegalAccessException, InterruptedException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> stored = store(COMMIT, testFile);
        final TxnDoc<T> deleted = delete(COMMIT, testFile);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                storeThenDelete_expected(stored, deleted, offset),
                readLatestJournalEntries());
    }

    @Test
    public void storeThenDelete_isRepeatable() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        storeThenDelete(0);
        existEmbeddedServer.restart();

        storeThenDelete(1);
        existEmbeddedServer.restart();

        storeThenDelete(2);
    }

    protected abstract List<ExpectedLoggable> storeThenDelete_expected(final TxnDoc<T> stored, final TxnDoc<T> deleted, final int offset);

    @Test
    public void storeWithoutCommitThenDelete() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, NoSuchFieldException, IllegalAccessException, InterruptedException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> stored = store(NO_COMMIT, testFile);
        final TxnDoc<T> deleted = delete(COMMIT, testFile);
        flushJournal();


        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                storeWithoutCommitThenDelete_expected(stored, deleted),
                readLatestJournalEntries());
    }

    @Test
    public void storeWithoutCommitThenDelete_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        storeWithoutCommitThenDelete();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDelete();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDelete();
    }

    protected abstract List<ExpectedLoggable> storeWithoutCommitThenDelete_expected(final TxnDoc<T> stored, final TxnDoc<T> deleted);

    @Test
    public void storeThenDeleteWithoutCommit() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        storeThenDeleteWithoutCommit(false, 0);
    }

    private void storeThenDeleteWithoutCommit(final boolean shouldGenerateReplaceEntry, final int offset) throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, NoSuchFieldException, IllegalAccessException, InterruptedException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> stored = store(COMMIT, testFile);
        final TxnDoc<T> deleted = delete(NO_COMMIT, testFile);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        if (!shouldGenerateReplaceEntry) {
            // expected STORE
            assertPartialOrdered(
                    storeThenDeleteWithoutCommit_expected(stored, deleted, offset),
                    readLatestJournalEntries());
        } else {
            // expected REPLACE
            assertPartialOrdered(
                    replaceSameContentThenDeleteWithoutCommit_expected(stored, deleted, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }
    }

    @Test
    public void storeThenDeleteWithoutCommit_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        storeThenDeleteWithoutCommit(false, 0);
        existEmbeddedServer.restart();

        storeThenDeleteWithoutCommit(true, 1);
        existEmbeddedServer.restart();

        storeThenDeleteWithoutCommit(true, 2);
    }

    protected abstract List<ExpectedLoggable> storeThenDeleteWithoutCommit_expected(final TxnDoc<T> stored, final TxnDoc<T> deleted, final int offset);

    @Test
    public void storeWithoutCommitThenDeleteWithoutCommit() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, NoSuchFieldException, IllegalAccessException, InterruptedException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> stored = store(NO_COMMIT, testFile);
        final TxnDoc<T> deleted = delete(NO_COMMIT, testFile);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                storeWithoutCommitThenDeleteWithoutCommit_expected(stored, deleted),
                readLatestJournalEntries());
    }

    @Test
    public void storeWithoutCommitThenDeleteWithoutCommit_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, NoSuchFieldException, IllegalAccessException, DatabaseConfigurationException, InterruptedException {
        storeWithoutCommitThenDeleteWithoutCommit();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDeleteWithoutCommit();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDeleteWithoutCommit();
    }

    protected abstract List<ExpectedLoggable> storeWithoutCommitThenDeleteWithoutCommit_expected(final TxnDoc<T> stored, final TxnDoc<T> deleted);

    @Test
    public void delete() throws LockException, SAXException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        delete(0);
    }

    private void delete(final int offset) throws LockException, SAXException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = false;
        final TxnDoc<T> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored, offset),
                readLatestJournalEntries());

        // restart the database server
        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> deleted = delete(COMMIT, testFile);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for delete
        assertPartialOrdered(
                delete_expected(deleted, offset),
                readLatestJournalEntries());
    }

    @Test
    public void delete_isRepeatable() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        delete(0);
        existEmbeddedServer.restart();

        delete(1);
        existEmbeddedServer.restart();

        delete(2);
    }

    protected abstract List<ExpectedLoggable> delete_expected(final TxnDoc<T> deleted, final int offset);

    @Test
    public void deleteWithoutCommit() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        deleteWithoutCommit(false, 0);
    }

    private void deleteWithoutCommit(final boolean shouldGenerateReplaceEntry, final int offset) throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = false;
        final TxnDoc<T> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        if (!shouldGenerateReplaceEntry) {
            // expected STORE
            assertPartialOrdered(
                    store_expected(stored, offset),
                    readLatestJournalEntries());
        } else {
            // expected REPLACE
            assertPartialOrdered(
                    replaceSameContent_expected(stored, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }

        // restart the database server
        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> deleted = delete(NO_COMMIT, testFile);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                deleteWithoutCommit_expected(deleted, offset),
                readLatestJournalEntries());
    }

    @Test
    public void deleteWithoutCommit_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        deleteWithoutCommit(false, 0);
        existEmbeddedServer.restart();

        deleteWithoutCommit(true, 1);
        existEmbeddedServer.restart();

        deleteWithoutCommit(true, 2);
    }

    protected abstract List<ExpectedLoggable> deleteWithoutCommit_expected(final TxnDoc<T> deleted, final int offset);

    @Test
    public void replaceSameContent() throws LockException, SAXException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceSameContent(false, 0);
    }

    private void replaceSameContent(final boolean storeShouldGenerateReplaceEntry, final int offset) throws IllegalAccessException, EXistException, NoSuchFieldException, IOException, LockException, SAXException, PermissionDeniedException, DatabaseConfigurationException, InterruptedException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final TxnDoc<T> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        if (!storeShouldGenerateReplaceEntry) {
            // expected STORE
            assertPartialOrdered(
                    store_expected(stored, offset),
                    readLatestJournalEntries());
        } else {
            // expected REPLACE
            assertPartialOrdered(
                    replaceSameContent_expected(stored, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }

        // restart the database server
        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile
        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> replaced = store(COMMIT, testFile, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceSameContent_expected(replaced, offset, false),
                readLatestJournalEntries());
    }

    @Test
    public void replaceSameContent_isRepeatable() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceSameContent(false, 0);
        existEmbeddedServer.restart();

        replaceSameContent(true, 2);
        existEmbeddedServer.restart();

        replaceSameContent(true, 4);
    }

    protected abstract List<ExpectedLoggable> replaceSameContent_expected(final TxnDoc<T> replaced, final int offset, final boolean overridesStore);

    @Test
    public void replaceDifferentContent() throws LockException, SAXException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceDifferentContent(false, 0);
    }

    private void replaceDifferentContent(final boolean storeShouldGenerateReplaceEntry, final int offset) throws IllegalAccessException, EXistException, NoSuchFieldException, IOException, LockException, SAXException, PermissionDeniedException, DatabaseConfigurationException, InterruptedException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final TxnDoc<T> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        if (!storeShouldGenerateReplaceEntry) {
            // expected STORE
            assertPartialOrdered(
                    store_expected(stored, offset),
                    readLatestJournalEntries());
        } else {
            // expected REPLACE
            final TxnDoc<T> original = new TxnDoc<>(stored.transactionId, calcDocLocation(getTestFile2(), testFilename));
            assertPartialOrdered(
                    replaceDifferentContent_expected(original, stored, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }

        // restart the database server
        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> replaced = store(COMMIT, testFile2, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceDifferentContent_expected(stored, replaced, offset, false),
                readLatestJournalEntries());
    }

    @Test
    public void replaceDifferentContent_isRepeatable() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceDifferentContent(false, 0);
        existEmbeddedServer.restart();

        replaceDifferentContent(true, 2);
        existEmbeddedServer.restart();

        replaceDifferentContent(true, 4);
    }

    protected abstract List<ExpectedLoggable> replaceDifferentContent_expected(final TxnDoc<T> original, final TxnDoc<T> replacement, final int offset, final boolean overridesStore);

    @Test
    public void replaceSameContentWithoutCommit() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceSameContentWithoutCommit(false, 0);
    }

    private void replaceSameContentWithoutCommit(final boolean storeShouldGenerateReplaceEntry, final int offset) throws IllegalAccessException, EXistException, NoSuchFieldException, IOException, LockException, SAXException, PermissionDeniedException, DatabaseConfigurationException, InterruptedException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final TxnDoc<T> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        if (!storeShouldGenerateReplaceEntry) {
            // expected STORE
            assertPartialOrdered(
                    store_expected(stored, offset),
                    readLatestJournalEntries());
        } else {
            // expected REPLACE
            assertPartialOrdered(
                    replaceSameContent_expected(stored, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }

        // restart the database server
        existEmbeddedServer.restart();

        // replace testFile with testFile
        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> replaced = store(NO_COMMIT, testFile, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceSameContentWithoutCommit_expected(replaced, offset),
                readLatestJournalEntries());
    }

    @Test
    public void replaceSameContentWithoutCommit_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceSameContentWithoutCommit(false, 0);
        existEmbeddedServer.restart();

        replaceSameContentWithoutCommit(true, 1);
        existEmbeddedServer.restart();

        replaceSameContentWithoutCommit(true, 2);
    }

    protected abstract List<ExpectedLoggable> replaceSameContentWithoutCommit_expected(final TxnDoc<T> replaced, final int offset);

    @Test
    public void replaceDifferentContentWithoutCommit() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceDifferentContentWithoutCommit(false, 0);
    }

    private void replaceDifferentContentWithoutCommit(final boolean storeShouldGenerateReplaceEntry, final int offset) throws IllegalAccessException, EXistException, NoSuchFieldException, IOException, LockException, SAXException, PermissionDeniedException, DatabaseConfigurationException, InterruptedException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final TxnDoc<T> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        if (!storeShouldGenerateReplaceEntry) {
            // expected STORE
            assertPartialOrdered(
                    store_expected(stored, offset),
                    readLatestJournalEntries());
        } else {
            // expected REPLACE
            assertPartialOrdered(
                    replaceSameContent_expected(stored, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }

        // restart the database server
        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> replaced = store(NO_COMMIT, testFile2, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceDifferentContentWithoutCommit_expected(stored, replaced, offset),
                readLatestJournalEntries());
    }

    @Test
    public void replaceDifferentContentWithoutCommit_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceDifferentContentWithoutCommit(false, 0);
        existEmbeddedServer.restart();

        replaceDifferentContentWithoutCommit(true, 1);
        existEmbeddedServer.restart();

        replaceDifferentContentWithoutCommit(true, 2);
    }

    protected abstract List<ExpectedLoggable> replaceDifferentContentWithoutCommit_expected(final TxnDoc<T> original, final TxnDoc<T> replaced, final int offset);

    @Test
    public void replaceSameContentThenDelete() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceSameContentThenDelete(0);
    }

    private void replaceSameContentThenDelete(final int offset) throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final TxnDoc<T> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected_for_replaceThenDelete(stored, offset),
                readLatestJournalEntries());

        // restart the database server
        existEmbeddedServer.restart();

        // replace testFile with testFile
        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> replaced = store(COMMIT, testFile, testFilename);
        final TxnDoc<T> deleted = delete(COMMIT, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceSameContentThenDelete_expected(replaced, deleted, offset),
                readLatestJournalEntries());
    }

    @Test
    public void replaceSameContentThenDelete_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceSameContentThenDelete(0);
        existEmbeddedServer.restart();

        replaceSameContentThenDelete(2);
        existEmbeddedServer.restart();

        replaceSameContentThenDelete(4);
    }

    /**
     * NOTE: needs to be overridden by {@link JournalXmlTest}!
     */
    protected List<ExpectedLoggable> store_expected_for_replaceThenDelete(final TxnDoc<T> stored, final int offset) {
        return store_expected(stored, offset);
    }

    protected abstract List<ExpectedLoggable> replaceSameContentThenDelete_expected(final TxnDoc<T> replaced, final TxnDoc<T> deleted, final int offset);

    @Test
    public void replaceDifferentContentThenDelete() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceDifferentContentThenDelete(0);
    }

    private void replaceDifferentContentThenDelete(final int offset) throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final TxnDoc<T> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected_for_replaceThenDelete(stored, offset),
                readLatestJournalEntries());

        // restart the database server
        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> replaced = store(COMMIT, testFile2, testFilename);
        final TxnDoc<T> deleted = delete(COMMIT, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceDifferentContentThenDelete_expected(stored, replaced, deleted, offset),
                readLatestJournalEntries());
    }

    @Test
    public void replaceDifferentContentThenDelete_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceDifferentContentThenDelete(0);
        existEmbeddedServer.restart();

        replaceDifferentContentThenDelete(2);
        existEmbeddedServer.restart();

        replaceDifferentContentThenDelete(4);
    }

    protected abstract List<ExpectedLoggable> replaceDifferentContentThenDelete_expected(final TxnDoc<T> original, final TxnDoc<T> replacement, final TxnDoc<T> deleted, final int offset);

    @Test
    public void replaceSameContentWithoutCommitThenDelete() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceSameContentWithoutCommitThenDelete(0);
    }

    private void replaceSameContentWithoutCommitThenDelete(final int offset) throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final TxnDoc<T> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored, offset),
                readLatestJournalEntries());

        // restart the database server
        existEmbeddedServer.restart();

        // replace testFile with testFile
        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> replaced = store(NO_COMMIT, testFile, testFilename);
        final TxnDoc<T> deleted = delete(COMMIT, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceSameContentWithoutCommitThenDelete_expected(replaced, deleted, offset),
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
    public void replaceSameContentWithoutCommitThenDelete_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceSameContentWithoutCommitThenDelete(0);
        existEmbeddedServer.restart();

        replaceSameContentWithoutCommitThenDelete(0);
        existEmbeddedServer.restart();

        replaceSameContentWithoutCommitThenDelete(0);
    }

    protected abstract List<ExpectedLoggable> replaceSameContentWithoutCommitThenDelete_expected(final TxnDoc<T> replaced, final TxnDoc<T> deleted, final int offset);

    @Test
    public void replaceDifferentContentWithoutCommitThenDelete() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceDifferentContentWithoutCommitThenDelete(0);
    }

    private void replaceDifferentContentWithoutCommitThenDelete(final int offset) throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {

        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final TxnDoc<T> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored, offset),
                readLatestJournalEntries());

        // restart the database server
        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> replaced = store(NO_COMMIT, testFile2, testFilename);
        final TxnDoc<T> deleted = delete(COMMIT, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceDifferentContentWithoutCommitThenDelete_expected(stored, replaced, deleted, offset),
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
    public void replaceDifferentContentWithoutCommitThenDelete_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceDifferentContentWithoutCommitThenDelete(0);
        existEmbeddedServer.restart();

        replaceDifferentContentWithoutCommitThenDelete(0);
        existEmbeddedServer.restart();

        replaceDifferentContentWithoutCommitThenDelete(0);
    }

    protected abstract List<ExpectedLoggable> replaceDifferentContentWithoutCommitThenDelete_expected(final TxnDoc<T> original, final TxnDoc<T> replacement, final TxnDoc<T> deleted, final int offset);

    @Test
    public void replaceSameContentThenDeleteWithoutCommit() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceSameContentThenDeleteWithoutCommit(false, 0);
    }

    private void replaceSameContentThenDeleteWithoutCommit(final boolean storeShouldGenerateReplaceEntry, final int offset) throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final TxnDoc<T> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        if (!storeShouldGenerateReplaceEntry) {
            //  expected STORE
            assertPartialOrdered(
                    store_expected(stored, offset),
                    readLatestJournalEntries());
        } else {
            // expected REPLACE
            final TxnDoc<T> original = new TxnDoc<>(stored.transactionId, calcDocLocation(getTestFile2(), testFilename));
            assertPartialOrdered(
                    replaceSameContent_expected(stored, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }

        // restart the database server
        existEmbeddedServer.restart();

        // replace testFile with testFile
        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> replaced = store(COMMIT, testFile, testFilename);
        final TxnDoc<T> deleted = delete(NO_COMMIT, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceSameContentThenDeleteWithoutCommit_expected(replaced, deleted, offset, false),
                readLatestJournalEntries());
    }

    @Test
    public void replaceSameContentThenDeleteWithoutCommit_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceSameContentThenDeleteWithoutCommit(false, 0);
        existEmbeddedServer.restart();

        replaceSameContentThenDeleteWithoutCommit(true, 2);
        existEmbeddedServer.restart();

        replaceSameContentThenDeleteWithoutCommit(true, 4);
    }

    protected abstract List<ExpectedLoggable> replaceSameContentThenDeleteWithoutCommit_expected(final TxnDoc<T> replaced, final TxnDoc<T> deleted, final int offset, final boolean overridesStore);

    @Test
    public void replaceDifferentContentThenDeleteWithoutCommit() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceDifferentContentThenDeleteWithoutCommit(false, 0);
    }

    private void replaceDifferentContentThenDeleteWithoutCommit(final boolean storeShouldGenerateReplaceEntry, final int offset) throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final TxnDoc<T> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        if (!storeShouldGenerateReplaceEntry) {
            //  expected STORE
            assertPartialOrdered(
                    store_expected(stored, offset),
                    readLatestJournalEntries());
        } else {
            // expected REPLACE
            final TxnDoc<T> original = new TxnDoc<>(stored.transactionId, calcDocLocation(getTestFile2(), testFilename));
            assertPartialOrdered(
                    replaceDifferentContent_expected(original, stored, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }

        // restart the database server
        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> replaced = store(COMMIT, testFile2, testFilename);
        final TxnDoc<T> deleted = delete(NO_COMMIT, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceDifferentContentThenDeleteWithoutCommit_expected(stored, replaced, deleted, offset, false),
                readLatestJournalEntries());
    }

    @Test
    public void replaceDifferentContentThenDeleteWithoutCommit_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceDifferentContentThenDeleteWithoutCommit(false, 0);
        existEmbeddedServer.restart();

        replaceDifferentContentThenDeleteWithoutCommit(true, 2);
        existEmbeddedServer.restart();

        replaceDifferentContentThenDeleteWithoutCommit(true, 4);
    }

    protected abstract List<ExpectedLoggable> replaceDifferentContentThenDeleteWithoutCommit_expected(final TxnDoc<T> original, final TxnDoc<T> replacement, final TxnDoc<T> deleted, final int offset, final boolean overridesStore);

    @Test
    public void replaceSameContentWithoutCommitThenDeleteWithoutCommit() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceSameContentWithoutCommitThenDeleteWithoutCommit(false, 0);
    }

    private void replaceSameContentWithoutCommitThenDeleteWithoutCommit(final boolean storeShouldGenerateReplaceEntry, final int offset) throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final TxnDoc<T> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        if (!storeShouldGenerateReplaceEntry) {
            // expected STORE
            assertPartialOrdered(
                    store_expected(stored, offset),
                    readLatestJournalEntries());
        } else {
            //  expected REPLACE
            assertPartialOrdered(
                    replaceSameContent_expected(stored, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }

        // restart the database server
        existEmbeddedServer.restart();

        // replace testFile with testFile
        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> replaced = store(NO_COMMIT, testFile, testFilename);
        final TxnDoc<T> deleted = delete(NO_COMMIT, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceSameContentWithoutCommitThenDeleteWithoutCommit_expected(replaced, deleted, offset),
                readLatestJournalEntries());
    }

    @Test
    public void replaceSameContentWithoutCommitThenDeleteWithoutCommit_isRepeatable() throws LockException,
            SAXException, PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceSameContentWithoutCommitThenDeleteWithoutCommit(false, 0);
        existEmbeddedServer.restart();

        replaceSameContentWithoutCommitThenDeleteWithoutCommit(true, 1);
        existEmbeddedServer.restart();

        replaceSameContentWithoutCommitThenDeleteWithoutCommit(true, 2);
    }

    protected abstract List<ExpectedLoggable> replaceSameContentWithoutCommitThenDeleteWithoutCommit_expected(final TxnDoc<T> replaced, final TxnDoc<T> deleted, final int offset);

    @Test
    public void replaceDifferentContentWithoutCommitThenDeleteWithoutCommit() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceDifferentContentWithoutCommitThenDeleteWithoutCommit(false, 0);
    }

    private void replaceDifferentContentWithoutCommitThenDeleteWithoutCommit(final boolean storeShouldGenerateReplaceEntry, final int offset) throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        checkpointJournalAndSwitchFile();

        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        final TxnDoc<T> stored = store(COMMIT, testFile);

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        if (!storeShouldGenerateReplaceEntry) {
            // expected STORE
            assertPartialOrdered(
                    store_expected(stored, offset),
                    readLatestJournalEntries());
        } else {
            //  expected REPLACE
            assertPartialOrdered(
                    replaceSameContent_expected(stored, offset > 0 ? offset - 1 : offset, true),
                    readLatestJournalEntries());
        }

        // restart the database server
        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        final TxnDoc<T> replaced = store(NO_COMMIT, testFile2, testFilename);
        final TxnDoc<T> deleted = delete(NO_COMMIT, testFilename);
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for replace
        assertPartialOrdered(
                replaceDifferentContentWithoutCommitThenDeleteWithoutCommit_expected(stored, replaced, deleted, offset),
                readLatestJournalEntries());
    }

    @Test
    public void replaceDifferentContentWithoutCommitThenDeleteWithoutCommit_isRepeatable() throws LockException,
            SAXException, PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, NoSuchFieldException, IllegalAccessException, InterruptedException {
        replaceDifferentContentWithoutCommitThenDeleteWithoutCommit(false, 0);
        existEmbeddedServer.restart();

        replaceDifferentContentWithoutCommitThenDeleteWithoutCommit(true, 1);
        existEmbeddedServer.restart();

        replaceDifferentContentWithoutCommitThenDeleteWithoutCommit(true, 2);
    }

    protected abstract List<ExpectedLoggable> replaceDifferentContentWithoutCommitThenDeleteWithoutCommit_expected(final TxnDoc<T> original, final TxnDoc<T> replacement, final TxnDoc<T> deleted, final int offset);


    protected void assertPartialOrdered(final List<ExpectedLoggable> expectedPartialOrderedJournalEntries, final List<Loggable> actualJournalEntries) throws AssertionError {
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
    protected void checkpointJournalAndSwitchFile() throws TransactionException {
        final Journal journal = existEmbeddedServer.getBrokerPool().getJournalManager().get().journal;

        //set Journal#journalMinSize = 0, so that switch files will always happen
        final long existingMinReplaceValue = journal.journalSizeMin;
        journal.journalSizeMin = 0;

        // checkpoint the journal and switch file
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        synchronized (pool) {
            pool.getTransactionManager().checkpoint(true);
        }

        //restore the Journal#journalMinSize to its previous value
        journal.journalSizeMin = existingMinReplaceValue;
    }

    protected List<Loggable> readLatestJournalEntries() throws IOException, LogException {
        final Configuration configuration = existEmbeddedServer.getBrokerPool().getConfiguration();
        final Path journalDir = (Path) Optional.ofNullable(configuration.getProperty(Journal.PROPERTY_RECOVERY_JOURNAL_DIR))
                .orElse(configuration.getProperty(BrokerPool.PROPERTY_DATA_DIR));

        final short lastNum;
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
     * @return a Tuple(txnId, docLocation), where txnId is of the transaction which stored the document, and docLocation
     *     is an identifier to the document in the database.
     */
    private TxnDoc<T> store(final boolean commitAndClose, final Path file) throws EXistException, PermissionDeniedException,
            IOException, SAXException, LockException, InterruptedException {
        return store(commitAndClose, file, FileUtils.fileName(file));
    }

    /**
     * Store a document into the database.
     *
     * @param commitAndClose true if the transaction should be committed. false will leave the transaction
     *      unfinished (i.e. neither committed, aborted, or closed)
     * @param file The file containing the data to store in the document
     * @param dbFilename the name to use when storing the file in the database
     *
     * @return a Tuple(txnId, docLocation), where txnId is of the transaction which stored the document, and docLocation
     *     is an identifier to the document in the database.
     */
    protected TxnDoc<T> store(final boolean commitAndClose, final Path file, final String dbFilename) throws EXistException,
            PermissionDeniedException, IOException, SAXException, LockException, InterruptedException {
        return store(commitAndClose, new FileInputSource(file), dbFilename);
    }

    /**
     * Store a document into the database.
     *
     * @param commitAndClose true if the transaction should be committed. false will leave the transaction
     *      unfinished (i.e. neither committed, aborted, or closed)
     * @param data The data to store in the document
     * @param dbFilename the name to use when storing the file in the database
     *
     * @return a Tuple(txnId, docLocation), where txnId is of the transaction which stored the document, and docLocation
     *     is an identifier to the document in the database.
     */
    protected TxnDoc<T> store(final boolean commitAndClose, final InputSource data, final String dbFilename) throws EXistException,
            PermissionDeniedException, IOException, SAXException, LockException, InterruptedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();

        return runSync(new BrokerTask<>(existEmbeddedServer.getBrokerPool(), (broker, transaction) -> {

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            final T docId = storeAndVerify(broker, transaction, root, data, dbFilename);

            if(commitAndClose) {
                transaction.commit();
                transaction.close();
            } else {
                broker.removeCurrentTransaction(transaction);
            }

            return new TxnDoc<>(transaction.getId(), docId);
        }));
    }

    /**
     * Store a document into the database and verify its correctness.
     *
     * @param broker The database broker
     * @param transaction The database transaction
     * @param collection The Collection into which the document should be stored
     * @param data The content for the document to store in the database
     * @param dbFilename The name to store the document as in the database
     *
     * @return the path to the document stored in the database.
     */
    protected abstract T storeAndVerify(final DBBroker broker, final Txn transaction, final Collection collection,
            final InputSource data, final String dbFilename) throws EXistException, PermissionDeniedException,
            IOException, SAXException, LockException;

    /**
     * Calculate the doc location for a file
     *
     * @param content the content
     * @param fileName the name of the file
     *
     * @return the doc location
     */
    private T calcDocLocation(final Path content, final String fileName) throws IOException {
        return calcDocLocation(content, TestConstants.TEST_COLLECTION_URI, fileName);
    }

    /**
     * Calculate the doc location for a file
     *
     * @param content the content
     * @param collectionUri the URI of the collection that the file would be accessible from
     * @param fileName the name of the file
     *
     * @return the doc location
     */
    protected abstract T calcDocLocation(final Path content, final XmldbURI collectionUri, final String fileName)
            throws IOException;

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
     * @return a Tuple(id, path), where id is of the transaction which deleted the document, and path
     *     is the path of the deleted document from the database.
     */
    private TxnDoc<T> delete(final boolean commitAndClose, final Path file)
            throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, InterruptedException {
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
    private TxnDoc<T> delete(final boolean commitAndClose, final String dbFilename)
            throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, InterruptedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            // the following transaction will not be committed. It may thus be rolled back by recovery
            final Txn transaction = transact.beginTransaction();

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            final T docLocation = delete(broker, transaction, root, dbFilename);

            if(commitAndClose) {
                transaction.commit();
                transaction.close();
            }

            return new TxnDoc<>(transaction.getId(), docLocation);
        }
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

    /**
     * Delete a document from the database.
     *
     * @param broker The database broker
     * @param transaction The database transaction
     * @param collection The Collection from which the document should be removed
     * @param dbFilename The name of the document in the database to delete
     *
     * @return the path to the document stored in the database.
     */
    protected abstract T delete(final DBBroker broker, final Txn transaction, final Collection collection,
        final String dbFilename) throws PermissionDeniedException, LockException, IOException, TriggerException;

    protected void flushJournal() {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        pool.getJournalManager().get().flush(true, false);
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

    protected ExpectedStartStorePartialTextNode StartStorePartialTextNode(final long transactionId, final long page, final DLN nodeId, final String partialText) {
        return new ExpectedStartStorePartialTextNode(transactionId, page, nodeId, partialText);
    }

    protected ExpectedStorePartialTextNode StorePartialTextNode(final long transactionId, final long page, final String partialText) {
        return new ExpectedStorePartialTextNode(transactionId, page, partialText);
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
                final VariableByteInputStream vis = new VariableByteInputStream(new UnsynchronizedByteArrayInputStream(o.getValue()));
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
            return "STORED INDEX VALUE T-" + transactionId + " collectionCreateDoc(txnId=" + transactionId + ", page=" + page + ", docLocation=" + docId + ", uri=" + docUri + ")";
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
                final VariableByteInputStream vis = new VariableByteInputStream(new UnsynchronizedByteArrayInputStream(o.getOldData(), o.getOffset(), o.getLen()));
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
            return "REMOVED INDEX VALUE T-" + transactionId + " collectionDeleteDoc(txnId=" + transactionId + ", page=" + page + ", docLocation=" + docId + ", uri=" + docUri + ")";
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

    protected abstract static class AbstractExpectedWriteOverflowPage extends ExpectedLoggable {
        protected final long page;

        public AbstractExpectedWriteOverflowPage(final long transactionId, final long page) {
            super(transactionId);
            this.page = page;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != WriteOverflowPageLoggable.class) return false;

            final WriteOverflowPageLoggable that = (WriteOverflowPageLoggable) o;
            return that.transactionId == transactionId
                    && that.getPageNum() == page
                    && equalsWriteOverflowPage(that);
        }

        protected abstract boolean equalsWriteOverflowPage(final WriteOverflowPageLoggable o);

        @Override
        public String toString() {
            return "WRITE OVERFLOW T-" + transactionId;
        }
    }

    protected class ExpectedStartStorePartialTextNode extends AbstractExpectedWriteOverflowPage {
        private final DLN nodeId;
        private final String partialText;

        public ExpectedStartStorePartialTextNode(final long transactionId, final long pageId, final DLN nodeId, final String partialText) {
            super(transactionId, pageId);
            this.nodeId = nodeId;
            this.partialText = partialText;
        }

        @Override
        protected boolean equalsWriteOverflowPage(final WriteOverflowPageLoggable o) {
            int pos = 0;
            final byte[] thatData = o.getValue().getData();
            final byte thatSignature = thatData[pos++];

            final int thatDlnLen = ByteConversion.byteToShort(thatData, pos);
            pos += 2;
            final NodeId thatNodeId = new DLN(thatDlnLen, thatData, pos);
            pos += thatNodeId.size();

            final String thatPartialText = UTF8.decode(thatData, pos, thatData.length - pos).toString();

            // check it is a TextImpl, the nodeId and the text matches
            return thatSignature == (Signatures.Char << 0x5)
                    && thatNodeId.equals(nodeId)
                    && thatPartialText.equals(partialText);
        }

        @Override
        public String toString() {
            return "WRITE OVERFLOW T-" + transactionId + " storeStartPartialText(txnId=" + transactionId + ", page=" + page + ", nodeId=" + nodeId.toString() + ", partialText=" + partialText + ")";
        }
    }

    protected class ExpectedStorePartialTextNode extends AbstractExpectedWriteOverflowPage {
        private final String partialText;

        public ExpectedStorePartialTextNode(final long transactionId, final long pageId, final String partialText) {
            super(transactionId, pageId);
            this.partialText = partialText;
        }

        @Override
        protected boolean equalsWriteOverflowPage(final WriteOverflowPageLoggable o) {
            final byte[] thatData = o.getValue().getData();
            final String thatPartialText = UTF8.decode(thatData).toString();

            // check the text matches
            return thatPartialText.equals(partialText);
        }

        @Override
        public String toString() {
            return "WRITE OVERFLOW T-" + transactionId + " storePartialText(txnId=" + transactionId + ", page=" + page + ", partialText=" + partialText + ")";
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
            super.addAll(Arrays.asList(items));
            return this;
        }
    }

    protected static class TxnDoc<T> {
        final long transactionId;
        final T docLocation;

        protected TxnDoc(final long transactionId, final T docLocation) {
            this.transactionId = transactionId;
            this.docLocation = docLocation;
        }
    }

    private int runSyncId = 0;
    private <T> T runSync(final BrokerTask<T> brokerTask) throws InterruptedException, LockException, SAXException, PermissionDeniedException, EXistException, IOException {
        final String brokerTaskName = "AbstractJournalTest#runSync-" + runSyncId++;
        final Thread thread = new Thread(brokerTask, brokerTaskName);
        thread.start();
        thread.join();
        return brokerTask.resultOrThow();
    }

    private static class BrokerTask<T> implements Runnable {
        private final BiFunction5E<DBBroker, Txn, T, EXistException, PermissionDeniedException, IOException, SAXException, LockException> task;
        private final BrokerPool pool;
        private volatile Supplier5E<T, EXistException, PermissionDeniedException, IOException, SAXException, LockException> result = null;

        public BrokerTask(final BrokerPool pool, final BiFunction5E<DBBroker, Txn, T, EXistException, PermissionDeniedException, IOException, SAXException, LockException> task) {
            this.pool = pool;
            this.task = task;
        }

        @Override
        public void run() {
            final TransactionManager transact = pool.getTransactionManager();
            try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
                final Txn transaction = transact.beginTransaction();

                final T localResult = task.apply(broker, transaction);
                this.result = () -> localResult;
            } catch (final EXistException | PermissionDeniedException | IOException | SAXException | LockException e) {
                this.result = () -> { throw e; };
            }
        }

        /**
         * Return the result of throw an exception if present
         */
        public T resultOrThow() throws LockException, SAXException, PermissionDeniedException, EXistException, IOException {
            if (result != null) {
                return result.get();
            }

            return null;
        }
    }
}
