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
package org.exist.storage;

import com.evolvedbinary.j8fu.function.BiConsumer5E;
import com.evolvedbinary.j8fu.function.Runnable5E;
import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileInputSource;
import org.exist.util.FileUtils;
import org.exist.util.LockException;
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
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public abstract class AbstractRecoverTest {

    protected static final boolean COMMIT = true;
    protected static final boolean NO_COMMIT = false;

    protected static final boolean MUST_EXIST = true;
    protected static final boolean MUST_NOT_EXIST = false;

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
    public void storeAndLoad() throws LockException, SAXException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException, InterruptedException {
        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        store(COMMIT, testFile);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_EXIST, testFile);
    }

    @Test
    public void storeAndLoad_isRepeatable() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, InterruptedException {
        storeAndLoad();
        existEmbeddedServer.restart();

        storeAndLoad();
        existEmbeddedServer.restart();

        storeAndLoad();
    }

    @Test
    public void storeWithoutCommitAndLoad() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, InterruptedException {
        final Path testFile = getTestFile1();

        BrokerPool.FORCE_CORRUPTION = true;
        store(NO_COMMIT, testFile);
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_NOT_EXIST, testFile);
    }

    @Test
    public void storeWithoutCommitAndLoad_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, InterruptedException {
        storeWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        storeWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        storeWithoutCommitAndLoad();
    }

    @Test
    public void storeThenDeleteAndLoad() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, InterruptedException {
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
    public void storeThenDeleteAndLoad_isRepeatable() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, InterruptedException {
        storeThenDeleteAndLoad();
        existEmbeddedServer.restart();

        storeThenDeleteAndLoad();
        existEmbeddedServer.restart();

        storeThenDeleteAndLoad();
    }

    @Test
    public void storeWithoutCommitThenDeleteAndLoad() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, InterruptedException {
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
    public void storeWithoutCommitThenDeleteAndLoad_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, InterruptedException {
        storeWithoutCommitThenDeleteAndLoad();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDeleteAndLoad();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDeleteAndLoad();
    }

    @Test
    public void storeThenDeleteWithoutCommitAndLoad() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, InterruptedException {
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
    public void storeThenDeleteWithoutCommitAndLoad_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, InterruptedException {
        storeThenDeleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        storeThenDeleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        storeThenDeleteWithoutCommitAndLoad();
    }

    @Test
    public void storeWithoutCommitThenDeleteWithoutCommitAndLoad() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, InterruptedException {
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
    public void storeWithoutCommitThenDeleteWithoutCommitAndLoad_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, InterruptedException {
        storeWithoutCommitThenDeleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDeleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        storeWithoutCommitThenDeleteWithoutCommitAndLoad();
    }

    @Test
    public void deleteAndLoad() throws LockException, SAXException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException, InterruptedException {
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
    public void deleteAndLoad_isRepeatable() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, InterruptedException {
        deleteAndLoad();
        existEmbeddedServer.restart();

        deleteAndLoad();
        existEmbeddedServer.restart();

        deleteAndLoad();
    }

    @Test
    public void deleteWithoutCommitAndLoad() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, InterruptedException {
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
    public void deleteWithoutCommitAndLoad_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, InterruptedException {
        deleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        deleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        deleteWithoutCommitAndLoad();
    }

    @Test
    public void replaceAndLoad() throws LockException, SAXException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException, InterruptedException {
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
    public void replaceAndLoad_isRepeatable() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, InterruptedException {
        replaceAndLoad();
        existEmbeddedServer.restart();

        replaceAndLoad();
        existEmbeddedServer.restart();

        replaceAndLoad();
    }

    @Test
    public void replaceWithoutCommitAndLoad() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, InterruptedException {
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
    public void replaceWithoutCommitAndLoad_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, InterruptedException {
        replaceWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        replaceWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        replaceWithoutCommitAndLoad();
    }

    @Test
    public void replaceThenDeleteAndLoad() throws LockException, SAXException, PermissionDeniedException,
            EXistException, IOException, DatabaseConfigurationException, InterruptedException {
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
    public void replaceThenDeleteAndLoad_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, InterruptedException {
        replaceThenDeleteAndLoad();
        existEmbeddedServer.restart();

        replaceThenDeleteAndLoad();
        existEmbeddedServer.restart();

        replaceThenDeleteAndLoad();
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
    public void replaceWithoutCommitThenDeleteAndLoad() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, InterruptedException {
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
    public void replaceWithoutCommitThenDeleteAndLoad_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, InterruptedException {
        replaceWithoutCommitThenDeleteAndLoad();
        existEmbeddedServer.restart();

        replaceWithoutCommitThenDeleteAndLoad();
        existEmbeddedServer.restart();

        replaceWithoutCommitThenDeleteAndLoad();
    }

    @Test
    public void replaceThenDeleteWithoutCommitAndLoad() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, InterruptedException {
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
    public void replaceThenDeleteWithoutCommitAndLoad_isRepeatable() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, InterruptedException {
        replaceThenDeleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        replaceThenDeleteWithoutCommitAndLoad();
        existEmbeddedServer.restart();

        replaceThenDeleteWithoutCommitAndLoad();
    }

    @Test
    public void replaceWithoutCommitThenDeleteWithoutCommitAndLoad() throws LockException, SAXException,
            PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, InterruptedException {
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
            SAXException, PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException, InterruptedException {
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
    protected void store(final boolean commitAndClose, final Path file) throws EXistException, PermissionDeniedException,
            IOException, SAXException, LockException, InterruptedException {
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
            PermissionDeniedException, IOException, SAXException, LockException, InterruptedException {
        store(commitAndClose, new FileInputSource(file), dbFilename);
    }

    /**
     * Store a document into the database.
     *
     * @param commitAndClose true if the transaction should be committed. false will leave the transaction
     *      unfinished (i.e. neither committed, aborted, or closed)
     * @param data The data to store in the document
     * @param dbFilename the name to use when storing the file in the database
     */
    protected void store(final boolean commitAndClose, final InputSource data, final String dbFilename) throws EXistException,
            PermissionDeniedException, IOException, SAXException, LockException, InterruptedException {


        runSync(new BrokerTask(existEmbeddedServer.getBrokerPool(), (broker, transaction) -> {
            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            storeAndVerify(broker, transaction, root, data, dbFilename);

            if(commitAndClose) {
                transaction.commit();
                transaction.close();
            } else {
                broker.removeCurrentTransaction(transaction);
            }
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
     */
    protected abstract void storeAndVerify(final DBBroker broker, final Txn transaction, final Collection collection,
            final InputSource data, final String dbFilename) throws EXistException, PermissionDeniedException,
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
        read(shouldExist, new FileInputSource(file), dbFilename);
    }

    /**
     * Read a document from the database.
     *
     * @param shouldExist true if the document should exist in the database, false if the document should not exist
     * @param data The data that was previously stored
     * @param dbFilename The name of the file to read from the database
     */
    protected void read(final boolean shouldExist, final InputSource data, final String dbFilename)
            throws EXistException, PermissionDeniedException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XmldbURI uri = TestConstants.TEST_COLLECTION_URI.append(dbFilename);

            try( final LockedDocument doc = broker.getXMLResource(uri, LockMode.READ_LOCK)) {

                if (!shouldExist) {
                    assertNull("Document should not exist in the database: " + uri, doc);
                } else {
                    assertNotNull("Document does not exist in the database: " + uri, doc);

                    readAndVerify(broker, doc.getDocument(), data, dbFilename);
                }
            }
        }
    }

    /**
     * Read and Verify that the document from the database is correct.
     *
     * @param broker The database broker.
     * @param doc The document from the database.
     * @param data The data that was previously stored
     * @param dbFilename The name of the file read from the database
     */
    protected abstract void readAndVerify(final DBBroker broker, final DocumentImpl doc,
            final InputSource data, final String dbFilename) throws EXistException, PermissionDeniedException, IOException;

    /**
     * Delete a document from the database.
     *
     * @param commitAndClose true if the transaction should be committed. false will leave the transaction
     *      unfinished (i.e. neither committed, aborted, or closed)
     * @param file The file that was previously stored, that should be deleted
     */
    private void delete(final boolean commitAndClose, final Path file)
            throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, InterruptedException {
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
            throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, InterruptedException {

        runSync(new BrokerTask(existEmbeddedServer.getBrokerPool(), (broker, transaction) -> {
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
            } else {
                broker.removeCurrentTransaction(transaction);
            }
        }));
    }

    protected void flushJournal() {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        pool.getJournalManager().get().flush(true, false);
    }

    protected Path resolveTestFile(final String fileName) throws IOException {
        final Path path = TestUtils.getEXistHome().orElseGet(() -> Paths.get(".")).resolve(fileName);
        if(!Files.exists(path)) {
            throw new IOException("No such test file: " + path.toAbsolutePath());
        }
        return path;
    }

    private int runSyncId = 0;
    private void runSync(final BrokerTask brokerTask) throws InterruptedException, LockException, SAXException, PermissionDeniedException, EXistException, IOException {
        final String brokerTaskName = "AbstractRecoveryTest#runSync-" + runSyncId++;
        final Thread thread = new Thread(brokerTask, brokerTaskName);
        thread.start();
        thread.join();
        brokerTask.throwIfException();
    }

    private static class BrokerTask implements Runnable {
        private final BiConsumer5E<DBBroker, Txn, EXistException, PermissionDeniedException, IOException, SAXException, LockException> task;
        private final BrokerPool pool;
        private volatile Runnable5E<EXistException, PermissionDeniedException, IOException, SAXException, LockException> exception = null;

        public BrokerTask(final BrokerPool pool, final BiConsumer5E<DBBroker, Txn, EXistException, PermissionDeniedException, IOException, SAXException, LockException> task) {
            this.pool = pool;
            this.task = task;
        }

        @Override
        public void run() {
            final TransactionManager transact = pool.getTransactionManager();
            try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
                final Txn transaction = transact.beginTransaction();

                task.accept(broker, transaction);
            } catch (final EXistException | PermissionDeniedException | IOException | SAXException | LockException e) {
                this.exception = () -> { throw e; };
            }
        }

        /**
         * If an exception is present, throw it
         */
        public void throwIfException() throws LockException, SAXException, PermissionDeniedException, EXistException, IOException {
            if (exception != null) {
                exception.run();
            }
        }
    }
}
