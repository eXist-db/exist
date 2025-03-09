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

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.numbering.DLN;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeType;
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
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class JournalXmlTest extends AbstractJournalTest<String> {

    private static final int TEXT_PAGE_SIZE = 4032;

    @ClassRule
    public static final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private static Path testFile1 = null;
    private static Path testFile2 = null;

    @BeforeClass
    public static void storeTempXmlDocs() throws IOException {
        testFile1 = temporaryFolder.getRoot().toPath().resolve("JournalXmlTest.doc1.xml");
        Files.write(testFile1, List.of("<element1>text1</element1>"), CREATE_NEW);

        testFile2 = temporaryFolder.getRoot().toPath().resolve("JournalXmlTest.doc2.xml");
        Files.write(testFile2, List.of("<element2>text2</element2>"), CREATE_NEW);
    }

    @Test
    public void largeJournalEntry_nonCorrupt() throws EXistException, LockException, SAXException, PermissionDeniedException, IOException, InterruptedException {
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
        final TxnDoc<String> stored = store(COMMIT, source, "large-non-corrupt.xml");
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored, 0, largeText),
                readLatestJournalEntries());
    }

    @Test
    public void largeJournalEntry_corrupt() throws EXistException, LockException, SAXException, PermissionDeniedException, IOException, InterruptedException {
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
        final TxnDoc<String> stored = store(COMMIT, source, "large-non-corrupt.xml");
        flushJournal();

        // shutdown the broker pool (without destroying the data dir)
        existEmbeddedServer.getBrokerPool().shutdown();

        // reset the corruption flag back to normal
        BrokerPool.FORCE_CORRUPTION = false;

        // check journal entries written for store
        assertPartialOrdered(
                store_expected(stored, 0, largeText),
                readLatestJournalEntries());
    }

    @Override
    protected List<ExpectedLoggable> store_expected(final TxnDoc<String> stored, final int offset) {
        return store_expected(stored, offset, "text1");
    }

    private List<ExpectedLoggable> store_expected(final TxnDoc<String> stored, final int offset, final String text) {
        final int docId = FIRST_USABLE_DOC_ID + offset;
        final long pageNum = FIRST_USABLE_PAGE + offset;

        if (text.length() < TEXT_PAGE_SIZE) {
            return Arrays.asList(
                    Start(stored.transactionId),
                    CollectionNextDocId(stored.transactionId, 1, docId),
                    StoreElementNode(stored.transactionId, pageNum, 1),
                    StoreTextNode(stored.transactionId, pageNum, text),
                    CollectionCreateDoc(stored.transactionId, 1, docId, stored.docLocation),
                    Commit(stored.transactionId)
            );
        } else {
            final int textNodeHeaderLen = 5;
            long textPageNum = pageNum + 1;

            final ExtendedArrayList<ExpectedLoggable> expected = List(
                    Start(stored.transactionId),
                    CollectionNextDocId(stored.transactionId, 1, docId),
                    StoreElementNode(stored.transactionId, pageNum, 1),

                    // first entry for large text node
                    StartStorePartialTextNode(stored.transactionId, textPageNum++, new DLN("1.1"), text.substring(0, TEXT_PAGE_SIZE - textNodeHeaderLen))
            );

            for (int i = TEXT_PAGE_SIZE - textNodeHeaderLen; i < text.length(); i += TEXT_PAGE_SIZE) {
                int partialTextEndOffset = i + TEXT_PAGE_SIZE;
                if (partialTextEndOffset > text.length()) {
                    partialTextEndOffset = i + (text.length() - i);
                }
                final String partialText = text.substring(i, partialTextEndOffset);
                expected.add(StorePartialTextNode(stored.transactionId, textPageNum++, partialText));
            }


            return expected.add(
                    CollectionCreateDoc(stored.transactionId, 1, docId, stored.docLocation),
                    Commit(stored.transactionId)
            );
        }
    }


    @Override
    protected List<ExpectedLoggable> storeWithoutCommit_expected(final TxnDoc<String> stored) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final long pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(stored.transactionId),
                CollectionNextDocId(stored.transactionId, 1, docId),
                StoreElementNode(stored.transactionId, pageNum, 1),
                StoreTextNode(stored.transactionId, pageNum, "text1"),
                CollectionCreateDoc(stored.transactionId, 1, docId, stored.docLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeThenDelete_expected(final TxnDoc<String> stored, final TxnDoc<String> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;
        final long pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(stored.transactionId),
                CollectionNextDocId(stored.transactionId, 1, docId),
                StoreElementNode(stored.transactionId, pageNum, 1),
                StoreTextNode(stored.transactionId, pageNum, "text1"),
                CollectionCreateDoc(stored.transactionId, 1, docId, stored.docLocation),
                Commit(stored.transactionId),

                Start(deleted.transactionId),
                DeleteElementNode(deleted.transactionId, pageNum, 1),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation),
                Commit(deleted.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeWithoutCommitThenDelete_expected(final TxnDoc<String> stored, final TxnDoc<String> deleted) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final long pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(stored.transactionId),
                CollectionNextDocId(stored.transactionId, 1, docId),
                StoreElementNode(stored.transactionId, pageNum, 1),
                StoreTextNode(stored.transactionId, pageNum, "text1"),
                CollectionCreateDoc(stored.transactionId, 1, docId, stored.docLocation),

                Start(deleted.transactionId),
                DeleteElementNode(deleted.transactionId, pageNum, 1),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation),
                Commit(deleted.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeThenDeleteWithoutCommit_expected(final TxnDoc<String> stored, final TxnDoc<String> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;
        final long pageNum = FIRST_USABLE_PAGE + offset;

        return Arrays.asList(
                Start(stored.transactionId),
                CollectionNextDocId(stored.transactionId, 1, docId),
                StoreElementNode(stored.transactionId, pageNum, 1),
                StoreTextNode(stored.transactionId, pageNum, "text1"),
                CollectionCreateDoc(stored.transactionId, 1, docId, stored.docLocation),
                Commit(stored.transactionId),

                Start(deleted.transactionId),
                DeleteElementNode(deleted.transactionId, pageNum, 1),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> storeWithoutCommitThenDeleteWithoutCommit_expected(final TxnDoc<String> stored, final TxnDoc<String> deleted) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final long pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(stored.transactionId),
                CollectionNextDocId(stored.transactionId, 1, docId),
                StoreElementNode(stored.transactionId, pageNum, 1),
                StoreTextNode(stored.transactionId, pageNum, "text1"),
                CollectionCreateDoc(stored.transactionId, 1, docId, stored.docLocation),

                Start(deleted.transactionId),
                DeleteElementNode(deleted.transactionId, pageNum, 1),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> delete_expected(final TxnDoc<String> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + offset;
        final int pageNum = FIRST_USABLE_PAGE + offset;

        return Arrays.asList(
                Start(deleted.transactionId),
                DeleteElementNode(deleted.transactionId, pageNum, 1),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation),
                Commit(deleted.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> deleteWithoutCommit_expected(final TxnDoc<String> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(deleted.transactionId),
                DeleteElementNode(deleted.transactionId, pageNum, 1),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceSameContent_expected(final TxnDoc<String> replaced, final int offset, final boolean overridesStore) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        final ExtendedArrayList<ExpectedLoggable> expected = List(
                Start(replaced.transactionId),

                DeleteElementNode(replaced.transactionId, pageNum, 1),
                CollectionDeleteDoc(replaced.transactionId, 1, docId, replaced.docLocation),

                StoreElementNode(replaced.transactionId, pageNum, 1)
        );

        if (overridesStore) {
            expected.add(StoreTextNode(replaced.transactionId, pageNum, "text1"));
        } else {
            expected.add(StoreTextNode(replaced.transactionId, pageNum, "text1"));
        }

        return expected.add(
            CollectionCreateDoc(replaced.transactionId, 1, docId, replaced.docLocation),
            Commit(replaced.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceDifferentContent_expected(final TxnDoc<String> original, final TxnDoc<String> replacement, final int offset, final boolean overridesStore) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        final ExtendedArrayList<ExpectedLoggable> expected = List(
                Start(replacement.transactionId),

                DeleteElementNode(replacement.transactionId, pageNum, 1),
                CollectionDeleteDoc(replacement.transactionId, 1, docId, replacement.docLocation),

                StoreElementNode(replacement.transactionId, pageNum, 1)
        );

        if (overridesStore) {
            expected.add(StoreTextNode(replacement.transactionId, pageNum, "text1"));
        } else {
            expected.add(StoreTextNode(replacement.transactionId, pageNum, "text2"));
        }

        return expected.add(
                CollectionCreateDoc(replacement.transactionId, 1, docId, replacement.docLocation),
                Commit(replacement.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceSameContentWithoutCommit_expected(final TxnDoc<String> replaced, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(replaced.transactionId),

                DeleteElementNode(replaced.transactionId, pageNum, 1),
                CollectionDeleteDoc(replaced.transactionId, 1, docId, replaced.docLocation),

                StoreElementNode(replaced.transactionId, pageNum, 1),
                StoreTextNode(replaced.transactionId, pageNum, "text1"),
                CollectionCreateDoc(replaced.transactionId, 1, docId, replaced.docLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceDifferentContentWithoutCommit_expected(final TxnDoc<String> original, final TxnDoc<String> replacement, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(replacement.transactionId),

                DeleteElementNode(replacement.transactionId, pageNum, 1),
                CollectionDeleteDoc(replacement.transactionId, 1, docId, replacement.docLocation),

                StoreElementNode(replacement.transactionId, pageNum, 1),
                StoreTextNode(replacement.transactionId, pageNum, "text2"),
                CollectionCreateDoc(replacement.transactionId, 1, docId, replacement.docLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceSameContentThenDelete_expected(final TxnDoc<String> replaced, final TxnDoc<String> deleted, int offset) {
        if (offset >= 2) {
            offset = offset / 2;
        }

        final int docId = FIRST_USABLE_DOC_ID + offset;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(replaced.transactionId),
                DeleteElementNode(replaced.transactionId, pageNum, 1),
                CollectionDeleteDoc(replaced.transactionId, 1, docId, replaced.docLocation),
                StoreElementNode(replaced.transactionId, pageNum, 1),
                StoreTextNode(replaced.transactionId, pageNum, "text1"),
                CollectionCreateDoc(replaced.transactionId, 1, docId, replaced.docLocation),
                Commit(replaced.transactionId),

                Start(deleted.transactionId),
                DeleteElementNode(deleted.transactionId, pageNum, 1),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation),
                Commit(deleted.transactionId)
        );
    }

    //NOTE special case for XML tests of replaceThenDelete_isRepeatable
    @Override
    protected List<ExpectedLoggable> store_expected_for_replaceThenDelete(final TxnDoc<String> stored, int offset) {
        if (offset >= 2) {
            offset = offset / 2;
        }
        final int docId = FIRST_USABLE_DOC_ID + offset;
        final long pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(stored.transactionId),
                CollectionNextDocId(stored.transactionId, 1, docId),
                StoreElementNode(stored.transactionId, pageNum, 1),
                StoreTextNode(stored.transactionId, pageNum, "text1"),
                CollectionCreateDoc(stored.transactionId, 1, docId, stored.docLocation),
                Commit(stored.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceDifferentContentThenDelete_expected(final TxnDoc<String> original, final TxnDoc<String> replacement, final TxnDoc<String> deleted, int offset) {
        if (offset >= 2) {
            offset = offset / 2;
        }

        final int docId = FIRST_USABLE_DOC_ID + offset;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(replacement.transactionId),
                DeleteElementNode(replacement.transactionId, pageNum, 1),
                CollectionDeleteDoc(replacement.transactionId, 1, docId, replacement.docLocation),
                StoreElementNode(replacement.transactionId, pageNum, 1),
                StoreTextNode(replacement.transactionId, pageNum, "text2"),
                CollectionCreateDoc(replacement.transactionId, 1, docId, replacement.docLocation),
                Commit(replacement.transactionId),

                Start(deleted.transactionId),
                DeleteElementNode(deleted.transactionId, pageNum, 1),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation),
                Commit(deleted.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceSameContentWithoutCommitThenDelete_expected(final TxnDoc<String> replaced, final TxnDoc<String> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(replaced.transactionId),
                DeleteElementNode(replaced.transactionId, pageNum, 1),
                CollectionDeleteDoc(replaced.transactionId, 1, docId, replaced.docLocation),
                StoreElementNode(replaced.transactionId, pageNum, 1),
                StoreTextNode(replaced.transactionId, pageNum, "text1"),
                CollectionCreateDoc(replaced.transactionId, 1, docId, replaced.docLocation),

                Start(deleted.transactionId),
                DeleteElementNode(deleted.transactionId, pageNum, 1),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation),
                Commit(deleted.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceDifferentContentWithoutCommitThenDelete_expected(final TxnDoc<String> original, final TxnDoc<String> replacement, final TxnDoc<String> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(replacement.transactionId),
                DeleteElementNode(replacement.transactionId, pageNum, 1),
                CollectionDeleteDoc(replacement.transactionId, 1, docId, replacement.docLocation),
                StoreElementNode(replacement.transactionId, pageNum, 1),
                StoreTextNode(replacement.transactionId, pageNum, "text2"),
                CollectionCreateDoc(replacement.transactionId, 1, docId, replacement.docLocation),

                Start(deleted.transactionId),
                DeleteElementNode(deleted.transactionId, pageNum, 1),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation),
                Commit(deleted.transactionId)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceSameContentThenDeleteWithoutCommit_expected(final TxnDoc<String> replaced, final TxnDoc<String> deleted, final int offset, final boolean overridesStore) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        final ExtendedArrayList<ExpectedLoggable> expected = List(
                Start(replaced.transactionId),
                DeleteElementNode(replaced.transactionId, pageNum, 1),
                CollectionDeleteDoc(replaced.transactionId, 1, docId, replaced.docLocation),
                StoreElementNode(replaced.transactionId, pageNum, 1)
        );

        if (overridesStore) {
            expected.add(StoreTextNode(replaced.transactionId, pageNum, "text1"));
        } else {
            expected.add(StoreTextNode(replaced.transactionId, pageNum, "text1"));
        }

        return expected.add(
                CollectionCreateDoc(replaced.transactionId, 1, docId, replaced.docLocation),
                Commit(replaced.transactionId)
        ).add(

                Start(deleted.transactionId),
                DeleteElementNode(deleted.transactionId, pageNum, 1),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceDifferentContentThenDeleteWithoutCommit_expected(final TxnDoc<String> original, final TxnDoc<String> replacement, final TxnDoc<String> deleted, final int offset, final boolean overridesStore) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        final ExtendedArrayList<ExpectedLoggable> expected = List(
                Start(replacement.transactionId),
                DeleteElementNode(replacement.transactionId, pageNum, 1),
                CollectionDeleteDoc(replacement.transactionId, 1, docId, replacement.docLocation),
                StoreElementNode(replacement.transactionId, pageNum, 1)
        );

        if (overridesStore) {
            expected.add(StoreTextNode(replacement.transactionId, pageNum, "text1"));
        } else {
            expected.add(StoreTextNode(replacement.transactionId, pageNum, "text2"));
        }

        return expected.add(
                CollectionCreateDoc(replacement.transactionId, 1, docId, replacement.docLocation),
                Commit(replacement.transactionId)
        ).add(

                Start(deleted.transactionId),
                DeleteElementNode(deleted.transactionId, pageNum, 1),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceSameContentWithoutCommitThenDeleteWithoutCommit_expected(final TxnDoc<String> replaced, final TxnDoc<String> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(replaced.transactionId),
                DeleteElementNode(replaced.transactionId, pageNum, 1),
                CollectionDeleteDoc(replaced.transactionId, 1, docId, replaced.docLocation),
                StoreElementNode(replaced.transactionId, pageNum, 1),
                StoreTextNode(replaced.transactionId, pageNum, "text1"),
                CollectionCreateDoc(replaced.transactionId, 1, docId, replaced.docLocation),

                Start(deleted.transactionId),
                DeleteElementNode(deleted.transactionId, pageNum, 1),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation)
        );
    }

    @Override
    protected List<ExpectedLoggable> replaceDifferentContentWithoutCommitThenDeleteWithoutCommit_expected(final TxnDoc<String> original, final TxnDoc<String> replacement, final TxnDoc<String> deleted, final int offset) {
        final int docId = FIRST_USABLE_DOC_ID + 0;
        final int pageNum = FIRST_USABLE_PAGE + 0;

        return Arrays.asList(
                Start(replacement.transactionId),
                DeleteElementNode(replacement.transactionId, pageNum, 1),
                CollectionDeleteDoc(replacement.transactionId, 1, docId, replacement.docLocation),
                StoreElementNode(replacement.transactionId, pageNum, 1),
                StoreTextNode(replacement.transactionId, pageNum, "text2"),
                CollectionCreateDoc(replacement.transactionId, 1, docId, replacement.docLocation),

                Start(deleted.transactionId),
                DeleteElementNode(deleted.transactionId, pageNum, 1),
                CollectionDeleteDoc(deleted.transactionId, 1, docId, deleted.docLocation)
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
    protected String storeAndVerify(final DBBroker broker, final Txn transaction, final Collection collection,
            final InputSource data, final String dbFilename) throws EXistException, PermissionDeniedException, IOException,
            SAXException, LockException {

        broker.storeDocument(transaction, XmldbURI.create(dbFilename), data, MimeType.XML_TYPE, collection);

        assertNotNull(collection.getDocument(broker, XmldbURI.create(dbFilename)));

        return collection.getURI().append(dbFilename).getRawCollectionPath();
    }

    @Override
    protected String calcDocLocation(final Path content, final XmldbURI collectionUri, final String fileName)
            throws IOException {
        return collectionUri.append(fileName).getRawCollectionPath();
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

    @Override
    protected String delete(final DBBroker broker, final Txn transaction, final Collection collection,
            final String dbFilename) throws PermissionDeniedException, LockException, IOException, TriggerException {
        final DocumentImpl doc = collection.getDocument(broker, XmldbURI.create(dbFilename));
        if(doc != null) {
            collection.removeResource(transaction, broker, doc);
        }

        return doc.getURI().getRawCollectionPath();
    }
}
