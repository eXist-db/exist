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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.input.CountingInputStream;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.Txn;
import org.exist.util.FileInputSource;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class RecoverBinaryTest extends AbstractRecoverTest {

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
    protected Path getTestFile1() throws IOException {
        return testFile1;
    }

    @Override
    protected Path getTestFile2() throws IOException {
        return testFile2;
    }

    @Override
    protected void storeAndVerify(final DBBroker broker, final Txn transaction, final Collection collection,
            final InputSource data, final String dbFilename) throws EXistException, PermissionDeniedException, IOException,
            SAXException, LockException {

        final Path file = ((FileInputSource)data).getFile();

        broker.storeDocument(transaction, XmldbURI.create(dbFilename), new FileInputSource(file), MimeType.BINARY_TYPE, collection);
        final BinaryDocument doc = (BinaryDocument) collection.getDocument(broker, XmldbURI.create(dbFilename));

        assertNotNull(doc);
        assertEquals(Files.size(file), doc.getContentLength());
    }

    @Override
    protected void readAndVerify(final DBBroker broker, final DocumentImpl doc, final InputSource data,
            final String dbFilename) throws IOException {

        final Path file = ((FileInputSource)data).getFile();

        final BinaryDocument binDoc = (BinaryDocument)doc;

        // verify the size, to ensure it is the correct content
        final long expectedSize = Files.size(file);
        assertEquals(expectedSize, binDoc.getContentLength());

        // check the actual content too!
        final byte[] bdata = new byte[(int) binDoc.getContentLength()];
        try (final CountingInputStream cis = new CountingInputStream(broker.getBinaryResource(binDoc))) {
            final int read = cis.read(bdata);
            assertEquals(bdata.length, read);

            final String content = new String(bdata);
            assertNotNull(content);

            assertEquals(expectedSize, cis.getByteCount());
        }
    }
}
