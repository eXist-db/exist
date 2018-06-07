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

import org.apache.commons.io.input.CountingInputStream;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.*;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class JournalBinaryTest extends AbstractJournalTest {

    @BeforeClass
    public static void prepare() {
        /*
         * NOTE: we `ONLY` disable this so we can write our tests as single-threaded which is much simpler,
         * that writing multi-threaded tests.
         */
        System.setProperty(DBBroker.PROP_DISABLE_SINGLE_THREAD_OVERLAPPING_TRANSACTION_CHECKS, "true");
    }

    @After
    public void tearDown() {
        BrokerPool.FORCE_CORRUPTION = false;
    }

    @AfterClass
    public static void cleanup() {
        System.clearProperty(DBBroker.PROP_DISABLE_SINGLE_THREAD_OVERLAPPING_TRANSACTION_CHECKS);
    }

    @Override
    protected List<ExpectedLoggable> store_expected(final long storedTxnId, final String storedDbPath) {
        return Arrays.asList(
                Start(storedTxnId),
                CreateBinary(storedTxnId, storedDbPath),
                Commit(storedTxnId)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeWithoutCommit_expected(final long storedTxnId, final String storedDbPath) {
        return Arrays.asList(
                Start(storedTxnId),
                CreateBinary(storedTxnId, storedDbPath)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeThenDelete_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath) {
        return Arrays.asList(
                Start(storedTxnId),
                CreateBinary(storedTxnId, storedDbPath),
                Commit(storedTxnId),

                Start(deletedTxnId),
                RenameBinary(deletedTxnId, deletedDbPath),     // this is a delete!
                Commit(deletedTxnId)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeWithoutCommitThenDelete_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath) {
        return Arrays.asList(
                Start(storedTxnId),
                CreateBinary(storedTxnId, storedDbPath),

                Start(deletedTxnId),
                RenameBinary(deletedTxnId, deletedDbPath),     // this is a delete!
                Commit(deletedTxnId)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeThenDeleteWithoutCommit_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath) {
        return Arrays.asList(
                Start(storedTxnId),
                CreateBinary(storedTxnId, storedDbPath),
                Commit(storedTxnId),

                Start(deletedTxnId),
                RenameBinary(deletedTxnId, deletedDbPath)     // this is a delete!
        );
    }

    @Override
    protected List<ExpectedLoggable> storeWithoutCommitThenDeleteWithoutCommit_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath) {
        return Arrays.asList(
                Start(storedTxnId),
                CreateBinary(storedTxnId, storedDbPath),

                Start(deletedTxnId),
                RenameBinary(deletedTxnId, deletedDbPath)     // this is a delete!
        );
    }

    @Override
    protected List<ExpectedLoggable> delete_expected(final long deletedTxnId, final String deletedDbPath) {
        return Arrays.asList(
                Start(deletedTxnId),
                RenameBinary(deletedTxnId, deletedDbPath),     // this is a delete!
                Commit(deletedTxnId)
        );
    }

    @Override
    protected List<ExpectedLoggable> deleteWithoutCommit_expected(final long deletedTxnId, final String deletedDbPath) {
        return Arrays.asList(
                Start(deletedTxnId),
                RenameBinary(deletedTxnId, deletedDbPath)     // this is a delete!
        );
    }

    @Override
    protected List<ExpectedLoggable> replace_expected(final long replacedTxnId, final String replacedDbPath) {
        return Arrays.asList(
                Start(replacedTxnId),
//                ReplaceBinary(replacedTxnId, replacedDbPath),
                RenameBinary(replacedTxnId, replacedDbPath),
                CreateBinary(replacedTxnId, replacedDbPath),
                Commit(replacedTxnId)
        );
    }


    @Override
    protected List<ExpectedLoggable> replaceWithoutCommit_expected(final long replacedTxnId, final String replacedDbPath) {
        return Arrays.asList(
                Start(replacedTxnId),
//                ReplaceBinary(replacedTxnId, replacedDbPath)
                RenameBinary(replacedTxnId, replacedDbPath),
                CreateBinary(replacedTxnId, replacedDbPath)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceThenDelete_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath) {
        return Arrays.asList(
                Start(replacedTxnId),
//                ReplaceBinary(replacedTxnId, replacedDbPath),
                RenameBinary(replacedTxnId, replacedDbPath),
                CreateBinary(replacedTxnId, replacedDbPath),
                Commit(replacedTxnId),

                Start(deletedTxnId),
                RenameBinary(deletedTxnId, deletedDbPath),     // this is a delete!
                Commit(deletedTxnId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceWithoutCommitThenDelete_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath) {
        return Arrays.asList(
                Start(replacedTxnId),
//                ReplaceBinary(replacedTxnId, replacedDbPath),
                RenameBinary(replacedTxnId, replacedDbPath),
                CreateBinary(replacedTxnId, replacedDbPath),

                Start(deletedTxnId),
                RenameBinary(deletedTxnId, deletedDbPath),     // this is a delete!
                Commit(deletedTxnId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceThenDeleteWithoutCommit_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath) {
        return Arrays.asList(
                Start(replacedTxnId),
//                ReplaceBinary(replacedTxnId, replacedDbPath),
                RenameBinary(replacedTxnId, replacedDbPath),
                CreateBinary(replacedTxnId, replacedDbPath),
                Commit(replacedTxnId),

                Start(deletedTxnId),
                RenameBinary(deletedTxnId, deletedDbPath)     // this is a delete!
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceWithoutCommitThenDeleteWithoutCommit_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath) {
        return Arrays.asList(
                Start(replacedTxnId),
//                ReplaceBinary(replacedTxnId, replacedDbPath),
                RenameBinary(replacedTxnId, replacedDbPath),
                CreateBinary(replacedTxnId, replacedDbPath),

                Start(deletedTxnId),
                RenameBinary(deletedTxnId, deletedDbPath)     // this is a delete!
        );
    }

    @Override
    protected Path getTestFile1() throws IOException {
        return resolveTestFile("LICENSE");
    }

    @Override
    protected Path getTestFile2() throws IOException {
        return resolveTestFile("README.md");
    }

    @Override
    protected XmldbURI storeAndVerify(final DBBroker broker, final Txn transaction, final Collection collection,
            final Path file, final String dbFilename) throws EXistException, PermissionDeniedException, IOException,
            TriggerException, LockException {

        final byte[] data = Files.readAllBytes(file);
        final BinaryDocument doc = collection.addBinaryResource(transaction, broker, XmldbURI.create(dbFilename), data, "application/octet-stream");

        assertNotNull(doc);
        assertEquals(Files.size(file), doc.getContentLength());

        return collection.getURI().append(dbFilename);
    }

    @Override
    protected void readAndVerify(final DBBroker broker, final DocumentImpl doc, final Path file,
            final String dbFilename) throws IOException {

        final BinaryDocument binDoc = (BinaryDocument)doc;

        // verify the size, to ensure it is the correct content
        final long expectedSize = Files.size(file);
        assertEquals(expectedSize, binDoc.getContentLength());

        // check the actual content too!
        final byte[] bdata = new byte[(int) binDoc.getContentLength()];
        try (final CountingInputStream cis = new CountingInputStream(broker.getBinaryResource(binDoc))) {
            final int read = cis.read(bdata);
            assertEquals(bdata.length, read);

            final String data = new String(bdata);
            assertNotNull(data);

            assertEquals(expectedSize, cis.getByteCount());
        }
    }

    private final static String FS_SUBDIR = "fs";

    private ExpectedCreateBinary CreateBinary(final long transactionId, final String createdDbFile) {
        return new ExpectedCreateBinary(transactionId, createdDbFile);
    }

    private ExpectedRenameBinary RenameBinary(final long transactionId, final String renamedDbFile) {
        return new ExpectedRenameBinary(transactionId, renamedDbFile);
    }

    private class ExpectedCreateBinary extends ExpectedLoggable {
        private final String createdDbFile;
        private final Path dataDir;

        public ExpectedCreateBinary(final long transactionId, final String createdDbFile) {
            super(transactionId);
            this.createdDbFile = createdDbFile;
            this.dataDir = (Path)existEmbeddedServer.getBrokerPool().getConfiguration().getProperty(BrokerPool.PROPERTY_DATA_DIR);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != CreateBinaryLoggable.class) return false;

            final Path createdFile = dataDir.resolve(FS_SUBDIR + createdDbFile);

            final CreateBinaryLoggable that = (CreateBinaryLoggable) o;
            return that.transactionId == transactionId
                    && that.getCreatedFile().equals(createdFile);
        }

        public String toString() {
            return "CREATE BINARY T-" + transactionId + " v=null" + " w=" + dataDir.resolve(FS_SUBDIR).resolve(createdDbFile);
        }
    }

    private class ExpectedRenameBinary extends ExpectedLoggable {
        private final String renamedDbFile;
        private final Path dataDir;

        public ExpectedRenameBinary(final long transactionId, final String renamedDbFile) {
            super(transactionId);
            this.renamedDbFile = renamedDbFile;
            this.dataDir = (Path)existEmbeddedServer.getBrokerPool().getConfiguration().getProperty(BrokerPool.PROPERTY_DATA_DIR);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != RenameBinaryLoggable.class) return false;

            final Path renamedFile = dataDir.resolve(FS_SUBDIR + renamedDbFile);

            final RenameBinaryLoggable that = (RenameBinaryLoggable) o;
            return that.transactionId == transactionId
                    && that.getRenamedFile().equals(renamedFile);
        }

        public String toString() {
            return "RENAMED BINARY T-" + transactionId + " v=null" + " w=" + dataDir.resolve(FS_SUBDIR).resolve(renamedDbFile);
        }
    }

//    private class ExpectedDeleteBinary extends ExpectedLoggable {
//        private final String deletedDbFile;
//        private final Path dataDir;
//
//        public ExpectedDeleteBinary(final long transactionId, final String deletedDbFile) {
//            super(transactionId);
//            this.deletedDbFile = deletedDbFile;
//            this.dataDir = (Path)existEmbeddedServer.getBrokerPool().getConfiguration().getProperty(BrokerPool.PROPERTY_DATA_DIR);
//        }
//
//        @Override
//        public boolean equals(final Object o) {
//            if (this == o) return true;
//            if (o == null || o.getClass() != DeleteBinaryLoggable.class) return false;
//
//            final Path deletedFile = dataDir.resolve(FS_SUBDIR + deletedDbFile);
//
//            final DeleteBinaryLoggable that = (DeleteBinaryLoggable) o;
//            return that.transactionId == transactionId
//                    && that.getDeletedFile().equals(deletedFile);
//        }
//
//        public String toString() {
//            return "CREATE BINARY T-" + transactionId + " v=null" + " w=" + dataDir.resolve(FS_SUBDIR).resolve(deletedDbFile);
//        }
//    }
}
