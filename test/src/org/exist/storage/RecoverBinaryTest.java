/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-17 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.exist.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.input.CountingInputStream;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class RecoverBinaryTest extends AbstractRecoverTest {

    @Override
    protected Path getTestFile1() throws IOException {
        return resolveTestFile("LICENSE");
    }

    @Override
    protected Path getTestFile2() throws IOException {
        return resolveTestFile("README.md");
    }

    @Override
    protected void storeAndVerify(final DBBroker broker, final Txn transaction, final Collection collection,
            final Path file, final String dbFilename) throws EXistException, PermissionDeniedException, IOException,
            TriggerException, LockException {

        final byte[] data = Files.readAllBytes(file);
        final BinaryDocument doc = collection.addBinaryResource(transaction, broker, XmldbURI.create(dbFilename), data, "application/octet-stream");

        assertNotNull(doc);
        assertEquals(Files.size(file), doc.getContentLength());
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
}
