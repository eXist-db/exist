/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
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
package org.exist.xquery.value;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A file-backed binary value must honor the shared-reference contract that lending relies on:
 * {@code incrementSharedReferences()} protects the value while something else holds it, and
 * {@code close()} must only release the underlying channel once the shared references are exhausted
 * (mirroring {@link org.exist.xquery.value.BinaryValueFromInputStream}).
 *
 * <p>Scope lifetime no longer uses this: a value is released by the frame that created it (see
 * {@link org.exist.xquery.value.BinaryValueManager}). Reference counting is now for genuine lending -
 * {@code xmldb:store} hands its value to a {@code Resource} whose {@code close()} closes what it was
 * given, while the query that produced the value may still need to read it afterwards.</p>
 *
 * <p>Regression for the multipart-upload failure: an uploaded file's {@link BinaryValueFromFile} (from
 * {@code request:get-uploaded-file-data}) had its channel closed before a deferred {@code xmldb:store}
 * could read it, surfacing as "error while obtaining length of binary value ..." caused by
 * "Underlying channel has been closed".</p>
 */
public class BinaryValueFromFileSharedReferenceTest {

    /**
     * Models lending the value out (incrementSharedReferences) and the borrower closing it: the value
     * must remain open and readable, and only be released by the owner's final {@code close()}.
     */
    @Test
    public void survivesBorrowerCloseWhenShared() throws Exception {
        final byte[] content = "multipart upload payload".getBytes(UTF_8);
        final Path file = Files.createTempFile("bvff-shared", ".bin");
        try {
            Files.write(file, content);
            final MockBinaryValueManager manager = new MockBinaryValueManager();
            final BinaryValue bin = BinaryValueFromFile.getInstance(manager, new Base64BinaryValueType(), file);

            // lent to something that closes what it is given, e.g. xmldb:store's Resource
            bin.incrementSharedReferences();
            // the borrower is done with it: this releases only the borrower's reference
            bin.close();

            // the owner still holds it, so it must still be readable
            assertFalse("a binary value that is still lent out must not be closed", bin.isClosed());
            try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                bin.streamBinaryTo(baos);
                assertArrayEquals(content, baos.toByteArray());
            }

            // the final cleanup releases it for real
            bin.close();
            assertTrue("once the last reference is released the value is closed", bin.isClosed());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /**
     * A value that was never lent out is released by a single {@code close()}, which is what lets a
     * frame release the values it owns as soon as it is left.
     */
    @Test
    public void closesWhenNotShared() throws Exception {
        final Path file = Files.createTempFile("bvff-unshared", ".bin");
        try {
            Files.write(file, "payload".getBytes(UTF_8));
            final MockBinaryValueManager manager = new MockBinaryValueManager();
            final BinaryValue bin = BinaryValueFromFile.getInstance(manager, new Base64BinaryValueType(), file);

            assertFalse(bin.isClosed());
            bin.close();
            assertTrue("a single owner's close() releases the value", bin.isClosed());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
