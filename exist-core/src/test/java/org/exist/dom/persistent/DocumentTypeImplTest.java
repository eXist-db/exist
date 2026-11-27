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
package org.exist.dom.persistent;

import org.exist.storage.io.VariableByteArrayInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A document type is stored behind a flag byte of its document: {@link DocumentImpl#HAS_DOCTYPE} for name, system id
 * and public id, as it has always been written, and {@link DocumentImpl#HAS_DOCTYPE_WITH_SUBSET} for those and the
 * declarations of the internal subset. A database written before the declarations were kept must read as it did.
 */
public class DocumentTypeImplTest {

    private static final String SUBSET = "<!ATTLIST c id ID #IMPLIED>";

    private static VariableByteArrayInput written(final DocumentTypeImpl docType) throws IOException {
        try (final VariableByteOutputStream out = new VariableByteOutputStream()) {
            docType.write(out);
            return new VariableByteArrayInput(out.toByteArray());
        }
    }

    @Test
    void aDocumentTypeWithoutDeclarationsIsWrittenAsBefore() throws IOException {
        final DocumentTypeImpl docType = new DocumentTypeImpl(null, "r", "-//X//DTD//EN", "r.dtd");
        try (final VariableByteOutputStream out = new VariableByteOutputStream()) {
            docType.write(out);
            // the layout of the old format: three strings, nothing after them
            try (final VariableByteOutputStream old = new VariableByteOutputStream()) {
                old.writeUTF("r");
                old.writeUTF("r.dtd");
                old.writeUTF("-//X//DTD//EN");
                assertEquals(old.toByteArray().length, out.toByteArray().length);
            }
        }

        final DocumentTypeImpl read = DocumentTypeImpl.read(written(docType));
        assertEquals("r", read.getName());
        assertEquals("-//X//DTD//EN", read.getPublicId());
        assertEquals("r.dtd", read.getSystemId());
        assertNull(read.getInternalSubset());
    }

    @Test
    void aDocumentTypeWithDeclarationsKeepsThem() throws IOException {
        final DocumentTypeImpl docType = new DocumentTypeImpl(null, "r", null, null, SUBSET);

        final DocumentTypeImpl read = DocumentTypeImpl.readWithInternalSubset(written(docType));
        assertEquals("r", read.getName());
        assertNull(read.getPublicId());
        assertNull(read.getSystemId());
        assertEquals(SUBSET, read.getInternalSubset());
    }

    @Test
    void theDeclarationsAreWrittenAfterTheOldFields() throws IOException {
        final DocumentTypeImpl docType = new DocumentTypeImpl(null, "r", "-//X//DTD//EN", "r.dtd", SUBSET);

        // a reader of the old layout finds the old fields first
        final VariableByteArrayInput input = written(docType);
        assertEquals("r", input.readUTF());
        assertEquals("r.dtd", input.readUTF());
        assertEquals("-//X//DTD//EN", input.readUTF());
        assertEquals(SUBSET, input.readUTF());
    }
}
