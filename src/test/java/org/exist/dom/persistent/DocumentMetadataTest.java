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
