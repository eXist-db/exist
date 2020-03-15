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

import org.exist.dom.persistent.DocumentTypeImpl;
import org.exist.dom.persistent.DocumentMetadata;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.w3c.dom.DocumentType;

/**
 *
 * @author aretter
 */
public class DocumentMetadataTest {

    @Test
    public void copyOf_copiesFields() {
        final long created = System.currentTimeMillis();
        final long lastModified = System.currentTimeMillis() + 100;
        final String mimeType = "application/pdf";
        final DocumentType docType = new DocumentTypeImpl("concept", "-//OASIS//DTD DITA Concept//EN", "http://docs.oasis-open.org/dita/v1.1/OS/dtd/concept.dtd");

        DocumentMetadata other = new DocumentMetadata();
        other.setCreated(created);
        other.setLastModified(lastModified);
        other.setMimeType(mimeType);
        other.setDocType(docType);

        DocumentMetadata meta = new DocumentMetadata();
        meta.copyOf(other);

        assertEquals(created, meta.getCreated());
        assertEquals(lastModified, meta.getLastModified());
        assertEquals(mimeType, meta.getMimeType());
        assertEquals(docType.getName(), meta.getDocType().getName());
        assertEquals(docType.getPublicId(), meta.getDocType().getPublicId());
        assertEquals(docType.getSystemId(), meta.getDocType().getSystemId());
    }
}
