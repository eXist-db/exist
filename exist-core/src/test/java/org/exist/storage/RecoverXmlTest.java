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

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.journal.Journal;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
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
import java.io.Reader;
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
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class RecoverXmlTest extends AbstractRecoverTest {

    @ClassRule
    public static final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private static Path testFile1 = null;
    private static Path testFile2 = null;

    @BeforeClass
    public static void storeTempXmlDocs() throws IOException {
        testFile1 = temporaryFolder.getRoot().toPath().resolve("RecoverXmlTest.doc1.xml");
        Files.write(testFile1, List.of("<?xml version=\"1.0\" encoding=\"UTF-8\"?><element1>text1</element1>"), CREATE_NEW);

        testFile2 = temporaryFolder.getRoot().toPath().resolve("RecoverXmlTest.doc2.xml");
        Files.write(testFile2, List.of("<?xml version=\"1.0\" encoding=\"UTF-8\"?><element2>text2</element2>"), CREATE_NEW);
    }

    @Test
    public void storeLargeAndLoad() throws LockException, SAXException, PermissionDeniedException, EXistException,
            IOException, DatabaseConfigurationException, InterruptedException {
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
        store(COMMIT, source, "large.xml");
        flushJournal();

        existEmbeddedServer.restart();

        BrokerPool.FORCE_CORRUPTION = false;
        read(MUST_EXIST, source, "large.xml");
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
    protected void storeAndVerify(final DBBroker broker, final Txn transaction, final Collection collection,
            final InputSource data, final String dbFilename) throws EXistException, PermissionDeniedException,
            IOException, LockException {
        final XmldbURI docUri = XmldbURI.create(dbFilename);
        try {
            broker.storeDocument(transaction, docUri, data, MimeType.XML_TYPE, collection);

        } catch (final SAXException e) {
            throw new IOException(e);
        }


        final DocumentImpl doc = broker.getResource(collection.getURI().append(docUri), Permission.READ);
        assertNotNull(doc);

        final Source expected;
        if (data instanceof FileInputSource) {
            expected = Input.fromFile(((FileInputSource)data).getFile().toFile()).build();
        } else if(data instanceof StringInputSource) {
            try (final Reader reader = data.getCharacterStream()) {
                expected = Input.fromString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + readAll(reader)).build();
            }
        } else {
            throw new IllegalStateException();
        }
        final Source actual = Input.fromDocument(doc).build();

        final Diff diff = DiffBuilder.compare(expected).withTest(actual)
                .checkForIdentical()
                .build();

        assertFalse("XML identical: " + diff.toString(), diff.hasDifferences());
    }

    private final String readAll(final Reader reader) throws IOException {
        final StringBuilder builder = new StringBuilder();
        final char buf[] = new char[4096];
        int read = -1;
        while ((read = reader.read(buf)) > -1) {
            builder.append(buf, 0, read);
        }
        return builder.toString();
    }

    @Override
    protected void readAndVerify(final DBBroker broker, final DocumentImpl doc, final InputSource data,
            final String dbFilename) throws IOException {

        final Source expected;
        if (data instanceof FileInputSource) {
            expected = Input.fromFile(((FileInputSource)data).getFile().toFile()).build();
        } else if(data instanceof StringInputSource) {
            try (final Reader reader = data.getCharacterStream()) {
                expected = Input.fromString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + readAll(reader)).build();
            }
        } else {
            throw new IllegalStateException();
        }

        final Source actual = Input.fromDocument(doc).build();

        final Diff diff = DiffBuilder.compare(expected).withTest(actual)
                .checkForIdentical()
                .build();

        assertFalse("XML identical: " + diff.toString(), diff.hasDifferences());
    }
}
