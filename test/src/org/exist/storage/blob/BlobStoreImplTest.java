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

import com.evolvedbinary.j8fu.Try;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.Database;
import org.exist.storage.txn.Txn;
import org.exist.util.FileUtils;
import org.exist.util.crypto.digest.DigestInputStream;
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.MessageDigest;
import org.exist.util.crypto.digest.StreamableDigest;
import org.exist.util.io.FastByteArrayInputStream;
import org.exist.util.io.FastByteArrayOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.evolvedbinary.j8fu.Try.TaggedTryUnchecked;
import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.bouncycastle.util.Arrays.reverse;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class BlobStoreImplTest {

    private static final DigestType DIGEST_TYPE = DigestType.BLAKE_256;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private final Random random = new Random();

    private static BlobStore newBlobStore(final Path blobDbx, final Path blobDir) {
        final Database database = createNiceMock(Database.class);
        expect(database.getThreadGroup()).andReturn(Thread.currentThread().getThreadGroup());
        expect(database.getId()).andReturn("BlobStoreTest").times(2);
        expect(database.getJournalManager()).andReturn(Optional.empty()).anyTimes();
        replay(database);

        return new BlobStoreImpl(database, blobDbx, blobDir, DIGEST_TYPE);
    }

    @Test
    public void addUnique() throws IOException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final List<Tuple2<byte[], MessageDigest>> testFiles = Arrays.asList(
                generateTestFile(),
                generateTestFile(),
                generateTestFile(),
                generateTestFile(),
                generateTestFile()
        );

        try (final BlobStore blobStore = newBlobStore(blobDbx, blobDir)) {
            blobStore.open();

            for (final Tuple2<byte[], MessageDigest> testFile : testFiles) {
                addAndVerify(blobStore, testFile);
            }
        }

        // should be 1 entry per unique test file in the blob.dbx, each entry is the digest and then the reference count
        final long expectedBlobDbxLen = calculateBlobStoreSize(testFiles.size());
        final long actualBlobDbxLen = Files.size(blobDbx);
        assertEquals(expectedBlobDbxLen, actualBlobDbxLen);
    }

    @Test
    public void addDuplicates() throws IOException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> testFile1 = generateTestFile();
        final Tuple2<byte[], MessageDigest> testFile2 = generateTestFile();

        try (final BlobStore blobStore = newBlobStore(blobDbx, blobDir)) {
            blobStore.open();

            addAndVerify(blobStore, testFile1);
            addAndVerify(blobStore, testFile2);
            addAndVerify(blobStore, testFile1);
            addAndVerify(blobStore, testFile2);
        }

        // should be 1 entry per unique test file in the blob.dbx, each entry is the digest and then the reference count
        // i.e. only 2 entries!
        final long expectedBlobDbxLen = calculateBlobStoreSize(2);
        final long actualBlobDbxLen = Files.size(blobDbx);
        assertEquals(expectedBlobDbxLen, actualBlobDbxLen);
    }

    @Test
    public void getNonExistent() throws IOException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> testFile = generateTestFile();

        try (final BlobStore blobStore = newBlobStore(blobDbx, blobDir)) {
            blobStore.open();

            // store a blob
            final BlobId storedId = addAndVerify(blobStore, testFile);

            final BlobId nonExistent = new BlobId(reverse(storedId.getId()));

            // try and retrieve a blob by id that does not exist
            assertNull(blobStore.get(null, nonExistent));
        }
    }

    @Test
    public void get() throws IOException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> testFile1 = generateTestFile();
        final Tuple2<byte[], MessageDigest> testFile2 = generateTestFile();

        try (final BlobStore blobStore = newBlobStore(blobDbx, blobDir)) {
            blobStore.open();

            final BlobId testFileId1 = addAndVerify(blobStore, testFile1);
            final BlobId testFileId2 = addAndVerify(blobStore, testFile2);

            getAndVerify(blobStore, testFileId1, testFile1);
            getAndVerify(blobStore, testFileId2, testFile2);
        }
    }

    @Test
    public void with() throws IOException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> testFile1 = generateTestFile();
        final Tuple2<byte[], MessageDigest> testFile2 = generateTestFile();

        try (final BlobStore blobStore = newBlobStore(blobDbx, blobDir)) {
            blobStore.open();

            final BlobId testFileId1 = addAndVerify(blobStore, testFile1);
            final BlobId testFileId2 = addAndVerify(blobStore, testFile2);

            final MessageDigest gotTestFile1Digest = blobStore.with(null, testFileId1, BlobStoreImplTest::digest).get();
            assertArrayEquals(testFile1._2.getValue(), gotTestFile1Digest.getValue());

            final MessageDigest gotTestFile2Digest = blobStore.with(null, testFileId2, BlobStoreImplTest::digest).get();
            assertArrayEquals(testFile2._2.getValue(), gotTestFile2Digest.getValue());
        }
    }

    @Test
    public void removeUnique() throws IOException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final List<Tuple2<byte[], MessageDigest>> testFiles = Arrays.asList(
                generateTestFile(),
                generateTestFile(),
                generateTestFile(),
                generateTestFile(),
                generateTestFile()
        );

        try (final BlobStore blobStore = newBlobStore(blobDbx, blobDir)) {
            blobStore.open();

            final List<BlobId> addedBlobIds = new ArrayList<>();
            for (final Tuple2<byte[], MessageDigest> testFile : testFiles) {
                addedBlobIds.add(addAndVerify(blobStore, testFile));
            }

            // remove each added blob
            for (final BlobId addedBlobId : addedBlobIds) {
                blobStore.remove(null, addedBlobId);
            }

            // check that each blob was removed
            for (final BlobId addedBlobId : addedBlobIds) {
                assertNull(blobStore.get(null, addedBlobId));
            }
        }
    }

    @Test
    public void removeDuplicates() throws IOException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> testFile1 = generateTestFile();
        final Tuple2<byte[], MessageDigest> testFile2 = generateTestFile();

        try (final BlobStore blobStore = newBlobStore(blobDbx, blobDir)) {
            blobStore.open();

            BlobId testFile1Id = addAndVerify(blobStore, testFile1);
            BlobId testFile2Id = addAndVerify(blobStore, testFile2);

            // add again to increase reference count
            testFile1Id = addAndVerify(blobStore, testFile1);
            testFile2Id = addAndVerify(blobStore, testFile2);

            // remove second reference
            blobStore.remove(null, testFile1Id);
            blobStore.remove(null, testFile2Id);

            // should still exist with one more reference
            getAndVerify(blobStore, testFile1Id, testFile1);
            getAndVerify(blobStore, testFile2Id, testFile2);

            // remove first reference
            blobStore.remove(null, testFile1Id);
            blobStore.remove(null, testFile2Id);

            // should no longer exist as all references were removed
            assertNull(blobStore.get(null, testFile1Id));
            assertNull(blobStore.get(null, testFile2Id));
        }
    }

    @Test
    public void compactPersistentReferences() throws IOException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> testFile1 = generateTestFile();
        final Tuple2<byte[], MessageDigest> testFile2 = generateTestFile();
        final Tuple2<byte[], MessageDigest> testFile3 = generateTestFile();

        try (final BlobStore blobStore = newBlobStore(blobDbx, blobDir)) {
            blobStore.open();

            final BlobId testFile1Id = addAndVerify(blobStore, testFile1);
            final BlobId testFile2Id = addAndVerify(blobStore, testFile2);
            BlobId testFile3Id = addAndVerify(blobStore, testFile3);

            // add a second reference for testFile3
            testFile3Id = addAndVerify(blobStore, testFile3);

            // remove testFile2
            blobStore.remove(null, testFile2Id);

            // remove one of the two references to testFile3
            blobStore.remove(null, testFile3Id);
        }

        // should be 1 entry per unique test file in the blob.dbx, each entry is the digest and then the reference count
        // i.e. only 3 entries!
        long expectedBlobDbxLen = calculateBlobStoreSize(3);
        long actualBlobDbxLen = Files.size(blobDbx);
        assertEquals(expectedBlobDbxLen, actualBlobDbxLen);

        // reopen and close the blob store, this should call {@link BlobStoreImpl#compactPersistentReferences}
        try (final BlobStore blobStore = newBlobStore(blobDbx, blobDir)) {
            blobStore.open();
        }

        // after compaction, should only be 2 entries, because testFile2 was removed. testFile3 is still present as it has one reference remaining
        expectedBlobDbxLen = calculateBlobStoreSize(2);
        actualBlobDbxLen = Files.size(blobDbx);
        assertEquals(expectedBlobDbxLen, actualBlobDbxLen);
    }

    /**
     * A blind copy is where the same resource
     * is added to the blob store twice, if the
     * operator was aware of the blob store then
     * they could call the more efficient
     * {@link BlobStore#copy(Txn, BlobId)}.
     */
    @Test
    public void blindCopy() throws IOException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> testFile1 = generateTestFile();

        try (final BlobStore blobStore = newBlobStore(blobDbx, blobDir)) {
            blobStore.open();

            final BlobId testFile1Id = addAndVerify(blobStore, testFile1);

            // attempt copy
            try (final InputStream src = blobStore.get(null, testFile1Id)) {
                final Tuple2<BlobId, Long> copiedTestFileId = blobStore.add(null, src);

                assertArrayEquals(testFile1Id.getId(), copiedTestFileId._1.getId());
            }
        }
    }

    @Test
    public void copy() throws IOException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> testFile1 = generateTestFile();

        try (final BlobStore blobStore = newBlobStore(blobDbx, blobDir)) {
            blobStore.open();

            // store
            final BlobId testFile1Id = addAndVerify(blobStore, testFile1);

            // copy
            final BlobId copyBlobId = blobStore.copy(null, testFile1Id);

            // check the two copies are identical
            final Tuple2<byte[], MessageDigest> testFile1Data;
            try (final InputStream testFile1Is = blobStore.get(null, testFile1Id)) {
                testFile1Data = readAll(testFile1Is);
            }
            final Tuple2<byte[], MessageDigest> copyBlobData;
            try (final InputStream copyBlobIs = blobStore.get(null, copyBlobId)) {
                copyBlobData = readAll(copyBlobIs);
            }

            assertEquals(testFile1Data._2, copyBlobData._2);
            assertArrayEquals(testFile1Data._1, copyBlobData._1);
        }
    }

    @Test
    public void copyRemove() throws IOException {
        final Path blobDbx = temporaryFolder.getRoot().toPath().resolve("blob.dbx");
        final Path blobDir = temporaryFolder.newFolder("blob").toPath();

        final Tuple2<byte[], MessageDigest> testFile1 = generateTestFile();

        try (final BlobStore blobStore = newBlobStore(blobDbx, blobDir)) {
            blobStore.open();

            // store
            final BlobId testFile1Id = addAndVerify(blobStore, testFile1);

            // copy
            final BlobId copyBlobId = blobStore.copy(null, testFile1Id);

            // assert that copies are identical... this is true for the deduplicating BLOB Store
            assertEquals(testFile1Id, copyBlobId);

            // remove the "original"
            blobStore.remove(null, testFile1Id);

            // assert that the copy is still present
            final Tuple2<byte[], MessageDigest> copyBlobData;
            try (final InputStream copyBlobIs = blobStore.get(null, copyBlobId)) {
                assertNotNull(copyBlobIs);
                copyBlobData = readAll(copyBlobIs);
            }
            assertEquals(testFile1._2, copyBlobData._2);
            assertArrayEquals(testFile1._1, copyBlobData._1);

            //remove the "copy"
            blobStore.remove(null, copyBlobId);

            // assert that the copy is no longer present
            try (final InputStream copyBlobIs = blobStore.get(null, copyBlobId)) {
                assertNull(copyBlobIs);
            }
        }
    }

    private long calculateBlobStoreSize(final int numRecords) {
        return BlobStoreImpl.BLOB_STORE_HEADER_LEN + (numRecords * (DIGEST_TYPE.getDigestLengthBytes() + BlobStoreImpl.REFERENCE_COUNT_LEN));
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

    private BlobId addAndVerify(final BlobStore blobStore, final Tuple2<byte[], MessageDigest> blob) throws IOException {
        final Tuple2<BlobId, Long> actualBlob;
        try (final InputStream is = new FastByteArrayInputStream(blob._1)) {
            actualBlob = blobStore.add(null, is);
        }
        assertNotNull(actualBlob);
        assertArrayEquals(blob._2.getValue(), actualBlob._1.getId());
        return actualBlob._1;
    }

    private void getAndVerify(final BlobStore blobStore, final BlobId blobId, final Tuple2<byte[], MessageDigest> expectedBlob) throws IOException {
        InputStream is = null;
        try {
            is = blobStore.get(null, blobId);
            assertNotNull(is);

            final Tuple2<byte[], MessageDigest> actualBlob = readAll(is);
            assertArrayEquals(expectedBlob._1, actualBlob._1);
            assertArrayEquals(expectedBlob._2.getValue(), actualBlob._2.getValue());

        } finally {
            if (is != null) {
                is.close();
            }
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

    private static Try<MessageDigest, IOException> digest(final Path path) {
        final StreamableDigest streamableDigest = DIGEST_TYPE.newStreamableDigest();
        return TaggedTryUnchecked(IOException.class, () -> {
            FileUtils.digest(path, streamableDigest);
            return streamableDigest.copyMessageDigest();
        });
    }
}
