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

import org.apache.commons.io.input.CountingInputStream;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.*;
import org.exist.storage.blob.BlobId;
import org.exist.storage.blob.StoreBlobFileLoggable;
import org.exist.storage.blob.UpdateBlobRefCountLoggable;
import org.exist.storage.txn.Txn;
import org.exist.util.FileInputSource;
import org.exist.util.FileUtils;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.StreamableDigest;
import org.exist.xmldb.XmldbURI;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test expectations to check that the correct entries
 * are written to the journal during
 * various Binary operations.
 *
 * Actual JUnit test cases are defined in the
 * subclass {@link AbstractJournalTest}.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class JournalBinaryTest extends AbstractJournalTest<JournalBinaryTest.BinaryDocLocator> {

    @ClassRule
    public static final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private static Path testFile1 = null;
    private static Path testFile2 = null;

    @BeforeClass
    public static void storeTempBinaryDocs() throws IOException {
        testFile1 = temporaryFolder.getRoot().toPath().resolve("blob1.bin");
        Files.write(testFile1, List.of("blob1"), CREATE_NEW);

        testFile2 = temporaryFolder.getRoot().toPath().resolve("blob2.bin");
        Files.write(testFile2, List.of("blob2"), CREATE_NEW);
    }

    @Override
    protected List<ExpectedLoggable> store_expected(final TxnDoc<BinaryDocLocator> stored, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;

        return Arrays.asList(
                Start(stored.transactionId),
                CollectionNextDocId(stored.transactionId, 1, docId),
                StoreBlobFile(stored.transactionId, stored.docLocation.blobId),
                UpdateBlobRefCount(stored.transactionId, stored.docLocation.blobId, 0, 1),
                CollectionCreateDoc(stored.transactionId, 1, docId, stored.docLocation.dbLocation),
                Commit(stored.transactionId)
        );
    }


    @Override
    protected List<ExpectedLoggable> storeWithoutCommit_expected(final TxnDoc<BinaryDocLocator> stored) {
        final int docId = FIRST_USABLE_DOC_ID + 0;

        return Arrays.asList(
                Start(stored.transactionId),
                CollectionNextDocId(stored.transactionId, 1, docId),
                StoreBlobFile(stored.transactionId, stored.docLocation.blobId),
                UpdateBlobRefCount(stored.transactionId, stored.docLocation.blobId, 0, 1),
                CollectionCreateDoc(stored.transactionId, 1, docId, stored.docLocation.dbLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeThenDelete_expected(final TxnDoc<BinaryDocLocator> stored, final TxnDoc<BinaryDocLocator> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;

        return Arrays.asList(
                Start(stored.transactionId),
                CollectionNextDocId(stored.transactionId, 1, docId),
                StoreBlobFile(stored.transactionId, stored.docLocation.blobId),
                UpdateBlobRefCount(stored.transactionId, stored.docLocation.blobId, 0, 1),
                CollectionCreateDoc(stored.transactionId, 1, docId, stored.docLocation.dbLocation),
                Commit(stored.transactionId),

                Start(deleted.transactionId),
                UpdateBlobRefCount(deleted.transactionId, deleted.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation.dbLocation),
                Commit(deleted.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeWithoutCommitThenDelete_expected(final TxnDoc<BinaryDocLocator> stored, final TxnDoc<BinaryDocLocator> deleted) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        return Arrays.asList(
                Start(stored.transactionId),
                CollectionNextDocId(stored.transactionId, 1, docId),
                StoreBlobFile(stored.transactionId, stored.docLocation.blobId),
                UpdateBlobRefCount(stored.transactionId, stored.docLocation.blobId, 0, 1),
                CollectionCreateDoc(stored.transactionId, 1, docId, stored.docLocation.dbLocation),

                Start(deleted.transactionId),
                UpdateBlobRefCount(deleted.transactionId, deleted.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation.dbLocation),
                Commit(deleted.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeThenDeleteWithoutCommit_expected(final TxnDoc<BinaryDocLocator> stored, final TxnDoc<BinaryDocLocator> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;

        return Arrays.asList(
                Start(stored.transactionId),
                CollectionNextDocId(stored.transactionId, 1, docId),
                StoreBlobFile(stored.transactionId, stored.docLocation.blobId),
                UpdateBlobRefCount(stored.transactionId, stored.docLocation.blobId, 0, 1),
                CollectionCreateDoc(stored.transactionId, 1, docId, stored.docLocation.dbLocation),
                Commit(stored.transactionId),

                Start(deleted.transactionId),
                UpdateBlobRefCount(deleted.transactionId, deleted.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation.dbLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeWithoutCommitThenDeleteWithoutCommit_expected(final TxnDoc<BinaryDocLocator> stored, final TxnDoc<BinaryDocLocator> deleted) {
        final int docId = FIRST_USABLE_DOC_ID + 0;

        return Arrays.asList(
                Start(stored.transactionId),
                CollectionNextDocId(stored.transactionId, 1, docId),
                StoreBlobFile(stored.transactionId, stored.docLocation.blobId),
                UpdateBlobRefCount(stored.transactionId, stored.docLocation.blobId, 0, 1),
                CollectionCreateDoc(stored.transactionId, 1, docId, stored.docLocation.dbLocation),

                Start(deleted.transactionId),
                UpdateBlobRefCount(deleted.transactionId, deleted.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation.dbLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> delete_expected(final TxnDoc<BinaryDocLocator> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;

        return Arrays.asList(
                Start(deleted.transactionId),
                UpdateBlobRefCount(deleted.transactionId, deleted.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation.dbLocation),
                Commit(deleted.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> deleteWithoutCommit_expected(final TxnDoc<BinaryDocLocator> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;

        return Arrays.asList(
                Start(deleted.transactionId),
                UpdateBlobRefCount(deleted.transactionId, deleted.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation.dbLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceSameContent_expected(final TxnDoc<BinaryDocLocator> replaced, final int offset, final boolean overridesStore) {
        final int docId = FIRST_USABLE_DOC_ID + offset;

        return Arrays.asList(
                Start(replaced.transactionId),
                CollectionNextDocId(replaced.transactionId, 1, docId + 1),
                UpdateBlobRefCount(replaced.transactionId, replaced.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(replaced.transactionId, 1, docId, replaced.docLocation.dbLocation),
                UpdateBlobRefCount(replaced.transactionId, replaced.docLocation.blobId, 0, 1),
                CollectionCreateDoc(replaced.transactionId, 1, docId + 1, replaced.docLocation.dbLocation),
                Commit(replaced.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceDifferentContent_expected(final TxnDoc<BinaryDocLocator> original, final TxnDoc<BinaryDocLocator> replacement, final int offset, final boolean overridesStore) {
        final int docId = FIRST_USABLE_DOC_ID + offset;

        return Arrays.asList(
                Start(replacement.transactionId),
                CollectionNextDocId(replacement.transactionId, 1, docId + 1),
                UpdateBlobRefCount(replacement.transactionId, original.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(replacement.transactionId, 1, docId, original.docLocation.dbLocation),
                StoreBlobFile(replacement.transactionId, replacement.docLocation.blobId),
                UpdateBlobRefCount(replacement.transactionId, replacement.docLocation.blobId, 0, 1),
                CollectionCreateDoc(replacement.transactionId, 1, docId + 1, replacement.docLocation.dbLocation),
                Commit(replacement.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceSameContentWithoutCommit_expected(final TxnDoc<BinaryDocLocator> replaced, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;

        return Arrays.asList(
                Start(replaced.transactionId),
                CollectionNextDocId(replaced.transactionId, 1, docId + 1),
                UpdateBlobRefCount(replaced.transactionId, replaced.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(replaced.transactionId, 1, docId, replaced.docLocation.dbLocation),
                UpdateBlobRefCount(replaced.transactionId, replaced.docLocation.blobId, 0, 1),
                CollectionCreateDoc(replaced.transactionId, 1, docId + 1, replaced.docLocation.dbLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceDifferentContentWithoutCommit_expected(final TxnDoc<BinaryDocLocator> original, final TxnDoc<BinaryDocLocator> replacement, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;

        return Arrays.asList(
                Start(replacement.transactionId),
                CollectionNextDocId(replacement.transactionId, 1, docId + 1),
                UpdateBlobRefCount(replacement.transactionId, original.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(replacement.transactionId, 1, docId, original.docLocation.dbLocation),
                StoreBlobFile(replacement.transactionId, replacement.docLocation.blobId),
                UpdateBlobRefCount(replacement.transactionId, replacement.docLocation.blobId, 0, 1),
                CollectionCreateDoc(replacement.transactionId, 1, docId + 1, replacement.docLocation.dbLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceSameContentThenDelete_expected(final TxnDoc<BinaryDocLocator> replaced, final TxnDoc<BinaryDocLocator> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;

        return Arrays.asList(
                Start(replaced.transactionId),
                CollectionNextDocId(replaced.transactionId, 1, docId + 1),
                UpdateBlobRefCount(replaced.transactionId, replaced.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(replaced.transactionId, 1, docId, replaced.docLocation.dbLocation),
                UpdateBlobRefCount(replaced.transactionId, replaced.docLocation.blobId, 0, 1),
                CollectionCreateDoc(replaced.transactionId, 1, docId + 1, replaced.docLocation.dbLocation),
                Commit(replaced.transactionId),

                Start(deleted.transactionId),
                UpdateBlobRefCount(deleted.transactionId, deleted.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(deleted.transactionId, 1, docId + 1, deleted.docLocation.dbLocation),
                Commit(deleted.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceDifferentContentThenDelete_expected(final TxnDoc<BinaryDocLocator> original,
            final TxnDoc<BinaryDocLocator> replacement, final TxnDoc<BinaryDocLocator> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;

        return Arrays.asList(
                Start(replacement.transactionId),
                CollectionNextDocId(replacement.transactionId, 1, docId + 1),
                UpdateBlobRefCount(replacement.transactionId, original.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(replacement.transactionId, 1, docId, original.docLocation.dbLocation),
                StoreBlobFile(replacement.transactionId, replacement.docLocation.blobId),
                UpdateBlobRefCount(replacement.transactionId, replacement.docLocation.blobId, 0, 1),
                CollectionCreateDoc(replacement.transactionId, 1, docId + 1, replacement.docLocation.dbLocation),
                Commit(replacement.transactionId),

                Start(deleted.transactionId),
                UpdateBlobRefCount(deleted.transactionId, deleted.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(deleted.transactionId, 1, docId + 1, deleted.docLocation.dbLocation),
                Commit(deleted.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceSameContentWithoutCommitThenDelete_expected(final TxnDoc<BinaryDocLocator> replaced, final TxnDoc<BinaryDocLocator> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;

        return Arrays.asList(
                Start(replaced.transactionId),
                CollectionNextDocId(replaced.transactionId, 1, docId + 1),
                UpdateBlobRefCount(replaced.transactionId, replaced.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(replaced.transactionId, 1, docId, replaced.docLocation.dbLocation),
                UpdateBlobRefCount(replaced.transactionId, replaced.docLocation.blobId, 0, 1),
                CollectionCreateDoc(replaced.transactionId, 1, docId + 1, replaced.docLocation.dbLocation),

                Start(deleted.transactionId),
                UpdateBlobRefCount(deleted.transactionId, deleted.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(deleted.transactionId, 1, docId + 1, deleted.docLocation.dbLocation),
                Commit(deleted.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceDifferentContentWithoutCommitThenDelete_expected(final TxnDoc<BinaryDocLocator> original, final TxnDoc<BinaryDocLocator> replacement, final TxnDoc<BinaryDocLocator> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;

        return Arrays.asList(
                Start(replacement.transactionId),
                CollectionNextDocId(replacement.transactionId, 1, docId + 1),
                UpdateBlobRefCount(replacement.transactionId, original.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(replacement.transactionId, 1, docId, original.docLocation.dbLocation),
                StoreBlobFile(replacement.transactionId, replacement.docLocation.blobId),
                UpdateBlobRefCount(replacement.transactionId, replacement.docLocation.blobId, 0, 1),
                CollectionCreateDoc(replacement.transactionId, 1, docId + 1, replacement.docLocation.dbLocation),

                Start(deleted.transactionId),
                UpdateBlobRefCount(deleted.transactionId, deleted.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(deleted.transactionId, 1, docId + 1, deleted.docLocation.dbLocation),
                Commit(deleted.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceSameContentThenDeleteWithoutCommit_expected(final TxnDoc<BinaryDocLocator> replaced, final TxnDoc<BinaryDocLocator> deleted, final int offset, final boolean overridesStore) {
        final int docId = FIRST_USABLE_DOC_ID + offset;

        return Arrays.asList(
                Start(replaced.transactionId),
                CollectionNextDocId(replaced.transactionId, 1, docId + 1),
                UpdateBlobRefCount(replaced.transactionId, replaced.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(replaced.transactionId, 1, docId, replaced.docLocation.dbLocation),
                UpdateBlobRefCount(replaced.transactionId, replaced.docLocation.blobId, 0, 1),
                CollectionCreateDoc(replaced.transactionId, 1, docId + 1, replaced.docLocation.dbLocation),
                Commit(replaced.transactionId),

                Start(deleted.transactionId),
                UpdateBlobRefCount(deleted.transactionId, deleted.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(deleted.transactionId, 1, docId + 1, deleted.docLocation.dbLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceDifferentContentThenDeleteWithoutCommit_expected(final TxnDoc<BinaryDocLocator> original, final TxnDoc<BinaryDocLocator> replacement, final TxnDoc<BinaryDocLocator> deleted, final int offset, final boolean overridesStore) {
        final int docId = FIRST_USABLE_DOC_ID + offset;

        return Arrays.asList(
                Start(replacement.transactionId),
                CollectionNextDocId(replacement.transactionId, 1, docId + 1),
                UpdateBlobRefCount(replacement.transactionId, original.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(replacement.transactionId, 1, docId, original.docLocation.dbLocation),
                StoreBlobFile(replacement.transactionId, replacement.docLocation.blobId),
                UpdateBlobRefCount(replacement.transactionId, replacement.docLocation.blobId, 0, 1),
                CollectionCreateDoc(replacement.transactionId, 1, docId + 1, replacement.docLocation.dbLocation),
                Commit(replacement.transactionId),

                Start(deleted.transactionId),
                UpdateBlobRefCount(deleted.transactionId, deleted.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(deleted.transactionId, 1, docId + 1, deleted.docLocation.dbLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceSameContentWithoutCommitThenDeleteWithoutCommit_expected(final TxnDoc<BinaryDocLocator> replaced, final TxnDoc<BinaryDocLocator> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;
        return Arrays.asList(
                Start(replaced.transactionId),
                CollectionNextDocId(replaced.transactionId, 1, docId + 1),
                UpdateBlobRefCount(replaced.transactionId, replaced.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(replaced.transactionId, 1, docId, replaced.docLocation.dbLocation),
                UpdateBlobRefCount(replaced.transactionId, replaced.docLocation.blobId, 0, 1),
                CollectionCreateDoc(replaced.transactionId, 1, docId + 1, replaced.docLocation.dbLocation),

                Start(deleted.transactionId),
                UpdateBlobRefCount(deleted.transactionId, deleted.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(deleted.transactionId, 1, docId + 1, deleted.docLocation.dbLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceDifferentContentWithoutCommitThenDeleteWithoutCommit_expected(final TxnDoc<BinaryDocLocator> original, final TxnDoc<BinaryDocLocator> replacement, final TxnDoc<BinaryDocLocator> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;
        return Arrays.asList(
                Start(replacement.transactionId),
                CollectionNextDocId(replacement.transactionId, 1, docId + 1),
                UpdateBlobRefCount(replacement.transactionId, original.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(replacement.transactionId, 1, docId, original.docLocation.dbLocation),
                StoreBlobFile(replacement.transactionId, replacement.docLocation.blobId),
                UpdateBlobRefCount(replacement.transactionId, replacement.docLocation.blobId, 0, 1),
                CollectionCreateDoc(replacement.transactionId, 1, docId + 1, replacement.docLocation.dbLocation),

                Start(deleted.transactionId),
                UpdateBlobRefCount(deleted.transactionId, deleted.docLocation.blobId, 1, 0),
                CollectionDeleteDoc(deleted.transactionId, 1, docId + 1, deleted.docLocation.dbLocation)
        );
    }

    @Override
    protected Path getTestFile1() throws IOException {
        return testFile1;
    }

    @Override
    protected Path getTestFile2() throws IOException {
        return testFile2;
    }

    @Override
    protected BinaryDocLocator storeAndVerify(final DBBroker broker, final Txn transaction, final Collection collection,
            final InputSource data, final String dbFilename) throws EXistException, PermissionDeniedException, IOException,
            SAXException, LockException {

        assertTrue(data instanceof FileInputSource);

        broker.storeDocument(transaction, XmldbURI.create(dbFilename), data, MimeType.BINARY_TYPE, collection);
        final BinaryDocument doc = (BinaryDocument) collection.getDocument(broker, XmldbURI.create(dbFilename));
        assertNotNull(doc);
        assertEquals(Files.size(((FileInputSource)data).getFile()), doc.getContentLength());

        return new BinaryDocLocator(collection.getURI().append(dbFilename).getRawCollectionPath(), doc.getBlobId());
    }

    @Override
    protected BinaryDocLocator calcDocLocation(final Path content, final XmldbURI collectionUri, final String fileName)
            throws IOException {
        final StreamableDigest streamableDigest = DigestType.BLAKE_256.newStreamableDigest();
        FileUtils.digest(content, streamableDigest);

        return new BinaryDocLocator(collectionUri.append(fileName).getRawCollectionPath(),
                new BlobId(streamableDigest.getMessageDigest()));
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

    @Override
    protected BinaryDocLocator delete(final DBBroker broker, final Txn transaction, final Collection collection,
            final String dbFilename) throws PermissionDeniedException, LockException, IOException, TriggerException {
        final DocumentImpl doc = collection.getDocument(broker, XmldbURI.create(dbFilename));
        if(doc != null) {
            collection.removeResource(transaction, broker, doc);
        }

        return new BinaryDocLocator(doc.getURI().getRawCollectionPath(), ((BinaryDocument)doc).getBlobId());
    }

    private final static String FS_SUBDIR = "fs";

    private ExpectedStoreBlobFile StoreBlobFile(final long transactionId, final BlobId blobId) {
        return new ExpectedStoreBlobFile(transactionId, blobId);
    }

    private ExpectedUpdateBlobRefCount UpdateBlobRefCount(final long transactionId, final BlobId blobId,
            final int currentCount, final int newCount) {
        return new ExpectedUpdateBlobRefCount(transactionId, blobId, currentCount, newCount);
    }

    private static class ExpectedStoreBlobFile extends ExpectedLoggable {
        private final BlobId blobId;

        public ExpectedStoreBlobFile(final long transactionId, final BlobId blobId) {
            super(transactionId);
            this.blobId = blobId;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != StoreBlobFileLoggable.class) return false;

            final StoreBlobFileLoggable that = (StoreBlobFileLoggable) o;
            return that.transactionId == transactionId
                    && that.getBlobId().equals(blobId);
        }

        @Override
        public String toString() {
            return "STORE BLOB FILE T-" + transactionId + " blobId=" + blobId;
        }
    }

    private static class ExpectedUpdateBlobRefCount extends ExpectedLoggable {
        private final BlobId blobId;
        private final int currentCount;
        private final int newCount;

        public ExpectedUpdateBlobRefCount(final long transactionId, final BlobId blobId, final int currentCount, final int newCount) {
            super(transactionId);
            this.blobId = blobId;
            this.currentCount = currentCount;
            this.newCount = newCount;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || o.getClass() != UpdateBlobRefCountLoggable.class) return false;

            final UpdateBlobRefCountLoggable that = (UpdateBlobRefCountLoggable) o;
            return that.transactionId == transactionId
                    && that.getBlobId().equals(blobId)
                    && that.getCurrentCount() == currentCount
                    && that.getNewCount() == newCount;
        }

        @Override
        public String toString() {
            return "UPDATE BLOB REF COUNT T-" + transactionId + " blobId=" + blobId + " v=" + currentCount + " w=" + newCount;
        }
    }

    public static class BinaryDocLocator {
        final String dbLocation;
        final BlobId blobId;

        public BinaryDocLocator(final String dbLocation, final BlobId blobId) {
            this.dbLocation = dbLocation;
            this.blobId = blobId;
        }
    }
}
