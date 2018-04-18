/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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
package org.exist.storage;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public abstract class AbstractRecoverTest {

    private static boolean COMMIT = true;
    private static boolean NO_COMMIT = false;

    private static boolean MUST_EXIST = true;
    private static boolean MUST_NOT_EXIST = false;

    /**
     * We set useTemporaryStorage=true for ExistEmbeddedServer
     * so that each test runs on its own data directory.
     */
    @Rule
    public final ExistEmbeddedServer existEmbeddedServer =
            new ExistEmbeddedServer(true, true);

    @Test
    public void storeAndLoad() throws LockException, TriggerException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException {
        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        store(COMMIT, testFile);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_EXIST, testFile);
    }

    @Test
    public void storeAndLoad_isRepeatable() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException {
        storeAndLoad();
        existEmbeddedServer.restart();

        storeAndLoad();
        existEmbeddedServer.restart();

        storeAndLoad();
    }

    @Test
    public void storeWithoutCommitAndLoad() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException {
        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        store(NO_COMMIT, testFile);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_NOT_EXIST, testFile);
    }

    @Test
    public void storeWithoutCommitAndLoad_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        storeWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        storeWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        storeWithoutCommitAndLoad();
    }

    @Test
    public void storeThenDeleteAndLoad() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException {
        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        store(COMMIT, testFile);
        delete(COMMIT, testFile);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_NOT_EXIST, testFile);
    }

    @Test
    public void storeThenDeleteAndLoad_isRepeatable() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException {
        storeThenDeleteAndLoad();
        existEmbeddedServer.restart();

        storeThenDeleteAndLoad();
        existEmbeddedServer.restart();

        storeThenDeleteAndLoad();
    }

    @Test
    public void storeWithoutCommitThenDeleteAndLoad() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException {
        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        store(NO_COMMIT, testFile);
        delete(COMMIT, testFile);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_NOT_EXIST, testFile);
    }

    @Test
    public void storeWithoutCommitThenDeleteAndLoad_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        storeWithoutCommitThenDeleteAndLoad();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDeleteAndLoad();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDeleteAndLoad();
    }

    @Test
    public void storeThenDeleteWithoutCommitAndLoad() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException {
        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        store(COMMIT, testFile);
        delete(NO_COMMIT, testFile);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_EXIST, testFile);
    }

    @Test
    public void storeThenDeleteWithoutCommitAndLoad_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        storeThenDeleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        storeThenDeleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        storeThenDeleteWithoutCommitAndLoad();
    }

    @Test
    public void storeWithoutCommitThenDeleteWithoutCommitAndLoad() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        store(NO_COMMIT, testFile);
        delete(NO_COMMIT, testFile);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_NOT_EXIST, testFile);
    }

    @Test
    public void storeWithoutCommitThenDeleteWithoutCommitAndLoad_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        storeWithoutCommitThenDeleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDeleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDeleteWithoutCommitAndLoad();
    }

    @Test
    public void deleteAndLoad() throws LockException, TriggerException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException {
        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = false;
        store(COMMIT, testFile);

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = true;
        delete(COMMIT, testFile);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_NOT_EXIST, testFile);
    }

    @Test
    public void deleteAndLoad_isRepeatable() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException {
        deleteAndLoad();
        existEmbeddedServer.restart();

        deleteAndLoad();
        existEmbeddedServer.restart();

        deleteAndLoad();
    }

    @Test
    public void deleteWithoutCommitAndLoad() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException {
        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = false;
        store(COMMIT, testFile);

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = true;
        delete(NO_COMMIT, testFile);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_EXIST, testFile);
    }

    @Test
    public void deleteWithoutCommitAndLoad_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        deleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        deleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        deleteWithoutCommitAndLoad();
    }

    @Test
    public void replaceAndLoad() throws LockException, TriggerException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException {
        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);
        
        BrokerPool.FORCE_CORRUPTION = false;
        store(COMMIT, testFile);

        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        store(COMMIT, testFile2, testFilename);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_EXIST, testFile2, testFilename);
    }

    @Test
    public void replaceAndLoad_isRepeatable() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException {
        replaceAndLoad();
        existEmbeddedServer.restart();

        replaceAndLoad();
        existEmbeddedServer.restart();

        replaceAndLoad();
    }

    @Test
    public void replaceWithoutCommitAndLoad() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException {
        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        store(COMMIT, testFile);

        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        store(NO_COMMIT, testFile2, testFilename);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_EXIST, testFile);
    }

    @Test
    public void replaceWithoutCommitAndLoad_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        replaceWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        replaceWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        replaceWithoutCommitAndLoad();
    }

    @Test
    public void replaceThenDeleteAndLoad() throws LockException, TriggerException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException {
        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        store(COMMIT, testFile);

        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        store(COMMIT, testFile2, testFilename);
        delete(COMMIT, testFilename);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_NOT_EXIST, testFile2, testFilename);
    }

    @Test
    public void replaceThenDeleteAndLoad_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        replaceThenDeleteAndLoad();
        existEmbeddedServer.restart();

        replaceThenDeleteAndLoad();
        existEmbeddedServer.restart();

        replaceThenDeleteAndLoad();
    }

    @Test
    public void replaceWithoutCommitThenDeleteAndLoad() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        store(COMMIT, testFile);

        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        store(NO_COMMIT, testFile2, testFilename);
        delete(COMMIT, testFilename);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_NOT_EXIST, testFile);
    }

    @Test
    public void replaceWithoutCommitThenDeleteAndLoad_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        replaceWithoutCommitThenDeleteAndLoad();
        existEmbeddedServer.restart();

        replaceWithoutCommitThenDeleteAndLoad();
        existEmbeddedServer.restart();

        replaceWithoutCommitThenDeleteAndLoad();
    }

    @Test
    public void replaceThenDeleteWithoutCommitAndLoad() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        store(COMMIT, testFile);

        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        store(COMMIT, testFile2, testFilename);
        delete(NO_COMMIT, testFilename);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_EXIST, testFile2, testFilename);
    }

    @Test
    public void replaceThenDeleteWithoutCommitAndLoad_isRepeatable() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        replaceThenDeleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        replaceThenDeleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        replaceThenDeleteWithoutCommitAndLoad();
    }

    @Test
    public void replaceWithoutCommitThenDeleteWithoutCommitAndLoad() throws LockException, TriggerException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        final Path testFile = getTestFile1();
        final String testFilename = FileUtils.fileName(testFile);

        BrokerPool.FORCE_CORRUPTION = false;
        store(COMMIT, testFile);

        existEmbeddedServer.restart();

        final Path testFile2 = getTestFile2();

        // replace testFile with testFile2
        BrokerPool.FORCE_CORRUPTION = true;
        store(NO_COMMIT, testFile2, testFilename);
        delete(NO_COMMIT, testFilename);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_EXIST, testFile);
    }

    @Test
    public void replaceWithoutCommitThenDeleteWithoutCommitAndLoad_isRepeatable() throws LockException,
            TriggerException, PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        replaceWithoutCommitThenDeleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        replaceWithoutCommitThenDeleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        replaceWithoutCommitThenDeleteWithoutCommitAndLoad();
    }

    protected abstract Path getTestFile1() throws IOException;
    protected abstract Path getTestFile2() throws IOException;

    /**
     * Store a document into the database.
     *
     * @param commitAndClose true if the transaction should be committed. false will leave the transaction
     *      unfinished (i.e. neither committed, aborted, or closed)
     * @param file The file that to store
     */
    private void store(final boolean commitAndClose, final Path file) throws EXistException, PermissionDeniedException,
            IOException, TriggerException, LockException {
        store(commitAndClose, file, FileUtils.fileName(file));
    }

    /**
     * Store a document into the database.
     *
     * @param commitAndClose true if the transaction should be committed. false will leave the transaction
     *      unfinished (i.e. neither committed, aborted, or closed)
     * @param file The file that to store
     * @param dbFilename the name to use when storing the file in the database
     */
    private void store(final boolean commitAndClose, final Path file, final String dbFilename) throws EXistException,
            PermissionDeniedException, IOException, TriggerException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            final Txn transaction = transact.beginTransaction();

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            storeAndVerify(broker, transaction, root, file, dbFilename);

            if(commitAndClose) {
                transaction.commit();
                transaction.close();
            }
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
     */
    protected abstract void storeAndVerify(final DBBroker broker, final Txn transaction, final Collection collection,
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
            final DocumentImpl doc = broker.getXMLResource(uri, LockMode.READ_LOCK);

            if(!shouldExist) {
                assertNull("Document should not exist in the database: " + uri, doc);
            } else {
                assertNotNull("Document does not exist in the database: " + uri, doc);

                readAndVerify(broker, doc, file, dbFilename);
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
     */
    private void delete(final boolean commitAndClose, final Path file)
            throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        delete(commitAndClose, FileUtils.fileName(file));
    }

    /**
     * Delete a document from the database.
     *
     * @param commitAndClose true if the transaction should be committed. false will leave the transaction
     *      unfinished (i.e. neither committed, aborted, or closed)
     * @param dbFilename The name of the file that was previously stored, that should be deleted
     */
    private void delete(final boolean commitAndClose, final String dbFilename)
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
}
