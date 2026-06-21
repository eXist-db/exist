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
 * A file-backed binary value must honor the shared-reference contract that
 * {@link org.exist.xquery.XQueryContext}'s {@code enterEnclosedExpr()} / {@code exitEnclosedExpr()}
 * rely on: {@code incrementSharedReferences()} protects the value while it is shared out of an enclosed
 * expression, and {@code close()} must only release the underlying channel once the shared references
 * are exhausted (mirroring {@link org.exist.xquery.value.BinaryValueFromInputStream}).
 *
 * <p>Regression for the multipart-upload failure: an uploaded file's {@link BinaryValueFromFile} (from
 * {@code request:get-uploaded-file-data}) had its channel closed by {@code exitEnclosedExpr()} before a
 * deferred {@code xmldb:store} could read it, surfacing as
 * "error while obtaining length of binary value ..." caused by "Underlying channel has been closed".</p>
 */
public class BinaryValueFromFileSharedReferenceTest {

    /**
     * Models {@code enterEnclosedExpr()} (incrementSharedReferences) followed by {@code exitEnclosedExpr()}
     * (close) on a value that escapes the enclosed expression: it must remain open and readable, and only
     * be released by the final cleanup {@code close()}.
     */
    @Test
    public void survivesEnclosedExpressionWhenShared() throws Exception {
        final byte[] content = "multipart upload payload".getBytes(UTF_8);
        final Path file = Files.createTempFile("bvff-shared", ".bin");
        try {
            Files.write(file, content);
            final MockBinaryValueManager manager = new MockBinaryValueManager();
            final BinaryValue bin = BinaryValueFromFile.getInstance(manager, new Base64BinaryValueType(), file);

            // enterEnclosedExpr(): the value is referenced from outside the enclosed expression
            bin.incrementSharedReferences();
            // exitEnclosedExpr(): closes (decrements a reference on) each registered binary value
            bin.close();

            // having escaped the enclosed expression, the value must still be readable
            assertFalse("a binary value shared out of an enclosed expression must not be closed", bin.isClosed());
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
     * A value that is never shared out of an enclosed expression is released by a single {@code close()},
     * preserving the eager-cleanup behavior of {@code exitEnclosedExpr()}.
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
