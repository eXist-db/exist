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
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.numbering.DLN;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.transform.Source;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Test expectations to check that the correct entries
 * are written to the journal during
 * various XML operations.
 *
 * Actual JUnit test cases are defined in the
 * subclass {@link AbstractJournalTest}.
 *
 * @author Adam Retter <adam@evolvedbinary.com>
 */
public class JournalXmlTest extends AbstractJournalTest {

    private static final int TEXT_PAGE_SIZE = 4032;

    @ClassRule
    public static final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private static Path testFile1 = null;
    private static Path testFile2 = null;

    @BeforeClass
    public static void storeTempXmlDocs() throws IOException {
        testFile1 = temporaryFolder.getRoot().toPath().resolve("JournalXmlTest.doc1.xml");
        Files.write(testFile1, Arrays.asList("<element1>text1</element1>"), CREATE_NEW);

        testFile2 = temporaryFolder.getRoot().toPath().resolve("JournalXmlTest.doc2.xml");
        Files.write(testFile2, Arrays.asList("<element2>text2</element2>"), CREATE_NEW);
    }

    @Test
    public void largeJournalEntry_nonCorrupt() throws IllegalAccessException, EXistException, NoSuchFieldException, LockException, SAXException, PermissionDeniedException, IOException, InterruptedException {
        checkpointJournalAndSwitchFile();

        // generate a string filled with random a-z characters which is larger than the journal buffer
        final byte[] buf = new byte[Journal.BUFFER_SIZE * 3]; // 3 * the journal buffer size
        final Random random = new Random();
        for (int i = 0; i < buf.length; i++) {
            final byte singleByteChar = (byte)('a' + random.nextInt('z' - 'a' - 1));
            buf[i] = singleByteChar;
        }
        final String largeText = new String(buf, UTF_8);
        final String xml = "<large-text>" + largeText + "</large-text>";
        final InputSource source = new StringInputSource(xml);
        source.setEncoding("UTF-8");

        BrokerPool.FORCE_CORRUPTION = false;
        final Tuple2<Long, String> stored = store(COMMIT, source, "large-non-corrupt.xml");
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored._1, stored._2, 0, largeText),
                readLatestJournalEntries());
    }

    @Test
    public void largeJournalEntry_corrupt() throws IllegalAccessException, EXistException, NoSuchFieldException, LockException, SAXException, PermissionDeniedException, IOException, InterruptedException {
        checkpointJournalAndSwitchFile();

        // generate a string filled with random a-z characters which is larger than the journal buffer
        final byte[] buf = new byte[Journal.BUFFER_SIZE * 3]; // 3 * the journal buffer size
        final Random random = new Random();
        for (int i = 0; i < buf.length; i++) {
            final byte singleByteChar = (byte)('a' + random.nextInt('z' - 'a' - 1));
            buf[i] = singleByteChar;
        }
        final String largeText = new String(buf, UTF_8);
        final String xml = "<large-text>" + largeText + "</large-text>";
        final InputSource source = new StringInputSource(xml);
        source.setEncoding("UTF-8");

        BrokerPool.FORCE_CORRUPTION = true;
        final Tuple2<Long, String> stored = store(COMMIT, source, "large-non-corrupt.xml");
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored._1, stored._2, 0, largeText),
                readLatestJournalEntries());
    }

    @Override
    protected List<ExpectedLoggable> store_expected(final long storedTxnId, final String storedDbPath, final int offset) {
        return store_expected(storedTxnId, storedDbPath, offset, "text1");
    }

    private List<ExpectedLoggable> store_expected(final long storedTxnId, final String storedDbPath, final int offset, final String text) {
        final int docId = FIRST_USABLE_DOC_ID + offset;
        final long pageNum = FIRST_USABLE_PAGE + offset;

        if (text.length() < TEXT_PAGE_SIZE) {
            return Arrays.asList(
                    Start(storedTxnId),
                    CollectionNextDocId(storedTxnId, 1, docId),
                    StoreElementNode(storedTxnId, pageNum, 1),
                    StoreTextNode(storedTxnId, pageNum, text),
                    CollectionCreateDoc(storedTxnId, 1, docId, storedDbPath),
                    Commit(storedTxnId)
            );
        } else {
            final int textNodeHeaderLen = 5;
            long textPageNum = pageNum + 1;

            final ExtendedArrayList<ExpectedLoggable> expected = List(
                    Start(storedTxnId),
                    CollectionNextDocId(storedTxnId, 1, docId),
                    StoreElementNode(storedTxnId, pageNum, 1),

                    // first entry for large text node
                    StartStorePartialTextNode(storedTxnId, textPageNum++, new DLN("1.1"), text.substring(0, TEXT_PAGE_SIZE - textNodeHeaderLen))
            );

            for (int i = TEXT_PAGE_SIZE - textNodeHeaderLen; i < text.length(); i += TEXT_PAGE_SIZE) {
                int partialTextEndOffset = i + TEXT_PAGE_SIZE;
                if (partialTextEndOffset > text.length()) {
                    partialTextEndOffset = i + (text.length() - i);
                }
                final String partialText = text.substring(i, partialTextEndOffset);
                expected.add(StorePartialTextNode(storedTxnId, textPageNum++, partialText));
            }


            return expected.add(
                    CollectionCreateDoc(storedTxnId, 1, docId, storedDbPath),
                    Commit(storedTxnId)
            );
        }
    }


    @Override
    protected List<ExpectedLoggable> storeWithoutCommit_expected(final long storedTxnId, final String storedDbPath) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final long pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(storedTxnId),
                CollectionNextDocId(storedTxnId, 1, docId),
                StoreElementNode(storedTxnId, pageNum, 1),
                StoreTextNode(storedTxnId, pageNum, "text1"),
                CollectionCreateDoc(storedTxnId, 1, docId, storedDbPath)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeThenDelete_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;
        final long pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(storedTxnId),
                CollectionNextDocId(storedTxnId, 1, docId),
                StoreElementNode(storedTxnId, pageNum, 1),
                StoreTextNode(storedTxnId, pageNum, "text1"),
                CollectionCreateDoc(storedTxnId, 1, docId, storedDbPath),
                Commit(storedTxnId),

                Start(deletedTxnId),
                DeleteElementNode(deletedTxnId, pageNum, 1),
                CollectionDeleteDoc(deletedTxnId, 1, docId, deletedDbPath),
                Commit(deletedTxnId)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeWithoutCommitThenDelete_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final long pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(storedTxnId),
                CollectionNextDocId(storedTxnId, 1, docId),
                StoreElementNode(storedTxnId, pageNum, 1),
                StoreTextNode(storedTxnId, pageNum, "text1"),
                CollectionCreateDoc(storedTxnId, 1, docId, storedDbPath),

                Start(deletedTxnId),
                DeleteElementNode(deletedTxnId, pageNum, 1),
                CollectionDeleteDoc(deletedTxnId, 1, docId, deletedDbPath),
                Commit(deletedTxnId)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeThenDeleteWithoutCommit_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;
        final long pageNum = FIRST_USABLE_PAGE + offset;

        return Arrays.asList(
                Start(storedTxnId),
                CollectionNextDocId(storedTxnId, 1, docId),
                StoreElementNode(storedTxnId, pageNum, 1),
                StoreTextNode(storedTxnId, pageNum, "text1"),
                CollectionCreateDoc(storedTxnId, 1, docId, storedDbPath),
                Commit(storedTxnId),

                Start(deletedTxnId),
                DeleteElementNode(deletedTxnId, pageNum, 1),
                CollectionDeleteDoc(deletedTxnId, 1, docId, deletedDbPath)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeWithoutCommitThenDeleteWithoutCommit_expected(final long storedTxnId, final String storedDbPath, final long deletedTxnId, final String deletedDbPath) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final long pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(storedTxnId),
                CollectionNextDocId(storedTxnId, 1, docId),
                StoreElementNode(storedTxnId, pageNum, 1),
                StoreTextNode(storedTxnId, pageNum, "text1"),
                CollectionCreateDoc(storedTxnId, 1, docId, storedDbPath),

                Start(deletedTxnId),
                DeleteElementNode(deletedTxnId, pageNum, 1),
                CollectionDeleteDoc(deletedTxnId, 1, docId, deletedDbPath)
        );
    }

    @Override
    protected List<ExpectedLoggable> delete_expected(final long deletedTxnId, final String deletedDbPath, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;
        final int pageNum = FIRST_USABLE_PAGE + offset;

        return Arrays.asList(
                Start(deletedTxnId),
                DeleteElementNode(deletedTxnId, pageNum, 1),
                CollectionDeleteDoc(deletedTxnId, 1, docId, deletedDbPath),
                Commit(deletedTxnId)
        );
    }

    @Override
    protected List<ExpectedLoggable> deleteWithoutCommit_expected(final long deletedTxnId, final String deletedDbPath, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(deletedTxnId),
                DeleteElementNode(deletedTxnId, pageNum, 1),
                CollectionDeleteDoc(deletedTxnId, 1, docId, deletedDbPath)
        );
    }

    @Override
    protected List<ExpectedLoggable> replace_expected(final long replacedTxnId, final String replacedDbPath, final int offset, final boolean overridesStore) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        final ExtendedArrayList<ExpectedLoggable> expected = List(
                Start(replacedTxnId),

                DeleteElementNode(replacedTxnId, pageNum, 1),
                CollectionDeleteDoc(replacedTxnId, 1, docId, replacedDbPath),

                StoreElementNode(replacedTxnId, pageNum, 1)
        );

        if (overridesStore) {
            expected.add(StoreTextNode(replacedTxnId, pageNum, "text1"));
        } else {
            expected.add(StoreTextNode(replacedTxnId, pageNum, "text2"));
        }

        return expected.add(
            CollectionCreateDoc(replacedTxnId, 1, docId, replacedDbPath),
            Commit(replacedTxnId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceWithoutCommit_expected(final long replacedTxnId, final String replacedDbPath, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(replacedTxnId),

                DeleteElementNode(replacedTxnId, pageNum, 1),
                CollectionDeleteDoc(replacedTxnId, 1, docId, replacedDbPath),

                StoreElementNode(replacedTxnId, pageNum, 1),
                StoreTextNode(replacedTxnId, pageNum, "text2"),
                CollectionCreateDoc(replacedTxnId, 1, docId, replacedDbPath)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceThenDelete_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath, int offset) {
        if (offset >= 2) {
            offset = offset / 2;
        }

        final int docId = FIRST_USABLE_DOC_ID + offset;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(replacedTxnId),
                DeleteElementNode(replacedTxnId, pageNum, 1),
                CollectionDeleteDoc(replacedTxnId, 1, docId, replacedDbPath),
                StoreElementNode(replacedTxnId, pageNum, 1),
                StoreTextNode(replacedTxnId, pageNum, "text2"),
                CollectionCreateDoc(replacedTxnId, 1, docId, replacedDbPath),
                Commit(replacedTxnId),

                Start(deletedTxnId),
                DeleteElementNode(deletedTxnId, pageNum, 1),
                CollectionDeleteDoc(deletedTxnId, 1, docId, deletedDbPath),
                Commit(deletedTxnId)
        );
    }

    //NOTE special case for XML tests of replaceThenDelete_isRepeatable
    @Override
    protected List<ExpectedLoggable> store_expected_for_replaceThenDelete(final long storedTxnId, final String storedDbPath, int offset) {
        if (offset >= 2) {
            offset = offset / 2;
        }
        final int docId = FIRST_USABLE_DOC_ID + offset;
        final long pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(storedTxnId),
                CollectionNextDocId(storedTxnId, 1, docId),
                StoreElementNode(storedTxnId, pageNum, 1),
                StoreTextNode(storedTxnId, pageNum, "text1"),
                CollectionCreateDoc(storedTxnId, 1, docId, storedDbPath),
                Commit(storedTxnId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceWithoutCommitThenDelete_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(replacedTxnId),
                DeleteElementNode(replacedTxnId, pageNum, 1),
                CollectionDeleteDoc(replacedTxnId, 1, docId, replacedDbPath),
                StoreElementNode(replacedTxnId, pageNum, 1),
                StoreTextNode(replacedTxnId, pageNum, "text2"),
                CollectionCreateDoc(replacedTxnId, 1, docId, replacedDbPath),

                Start(deletedTxnId),
                DeleteElementNode(deletedTxnId, pageNum, 1),
                CollectionDeleteDoc(deletedTxnId, 1, docId, deletedDbPath),
                Commit(deletedTxnId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceThenDeleteWithoutCommit_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath, final int offset, final boolean overridesStore) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        final ExtendedArrayList<ExpectedLoggable> expected = List(
                Start(replacedTxnId),
                DeleteElementNode(replacedTxnId, pageNum, 1),
                CollectionDeleteDoc(replacedTxnId, 1, docId, replacedDbPath),
                StoreElementNode(replacedTxnId, pageNum, 1)
        );

        if (overridesStore) {
            expected.add(StoreTextNode(replacedTxnId, pageNum, "text1"));
        } else {
            expected.add(StoreTextNode(replacedTxnId, pageNum, "text2"));
        }

        return expected.add(
                CollectionCreateDoc(replacedTxnId, 1, docId, replacedDbPath),
                Commit(replacedTxnId)
        ).add(

                Start(deletedTxnId),
                DeleteElementNode(deletedTxnId, pageNum, 1),
                CollectionDeleteDoc(deletedTxnId, 1, docId, deletedDbPath)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceWithoutCommitThenDeleteWithoutCommit_expected(final long replacedTxnId, final String replacedDbPath, final long deletedTxnId, final String deletedDbPath, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(replacedTxnId),
                DeleteElementNode(replacedTxnId, pageNum, 1),
                CollectionDeleteDoc(replacedTxnId, 1, docId, replacedDbPath),
                StoreElementNode(replacedTxnId, pageNum, 1),
                StoreTextNode(replacedTxnId, pageNum, "text2"),
                CollectionCreateDoc(replacedTxnId, 1, docId, replacedDbPath),

                Start(deletedTxnId),
                DeleteElementNode(deletedTxnId, pageNum, 1),
                CollectionDeleteDoc(deletedTxnId, 1, docId, deletedDbPath)
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
    protected XmldbURI storeAndVerify(final DBBroker broker, final Txn transaction, final Collection collection,
            final InputSource data, final String dbFilename) throws EXistException, PermissionDeniedException, IOException,
            SAXException, LockException {

        final IndexInfo indexInfo = collection.validateXMLResource(transaction, broker, XmldbURI.create(dbFilename), data);
        collection.store(transaction, broker, indexInfo, data);

        assertNotNull(collection.getDocument(broker, XmldbURI.create(dbFilename)));

        return collection.getURI().append(dbFilename);
    }

    @Override
    protected void readAndVerify(final DBBroker broker, final DocumentImpl doc, final Path file,
            final String dbFilename) throws IOException {

        // check the actual content too!
        final Source expected = Input.fromFile(file.toFile()).build();
        final Source actual = Input.fromDocument(doc).build();

        final Diff diff = DiffBuilder
                .compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }
}
