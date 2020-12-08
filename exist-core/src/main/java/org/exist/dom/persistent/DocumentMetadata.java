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

import org.exist.ResourceMetadata;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.w3c.dom.DocumentType;

import java.io.IOException;

/**
 * @deprecated Will be removed in eXist-db 6.0.0. Metadata methods
 * are now directly accessible from {@link org.exist.dom.persistent.DocumentImpl}.
 */
@Deprecated
public class DocumentMetadata implements ResourceMetadata {

    private final DocumentImpl doc;

    DocumentMetadata(final DocumentImpl doc) {
        this.doc = doc;
    }

    @Deprecated
    public void copyOf(final DocumentMetadata other) {
        setCreated(other.getCreated());
        setLastModified(other.getLastModified());
        setMimeType(other.getMimeType());
        setDocType(other.getDocType());
    }

    @Override
    public long getCreated() {
        return doc.getCreated();
    }

    @Deprecated
    public void setCreated(final long created) {
        doc.setCreated(created);
    }

    @Deprecated
    public long getLastModified() {
        return doc.getLastModified();
    }

    @Deprecated
    public void setLastModified(final long lastModified) {
        doc.setLastModified(lastModified);
    }

    @Deprecated
    public String getMimeType() {
        return doc.getMimeType();
    }

    @Deprecated
    public void setMimeType(final String mimeType) {
        doc.setMimeType(mimeType);
    }

    @Deprecated
    public int getPageCount() {
        return doc.getPageCount();
    }

    @Deprecated
    public void setPageCount(final int pageCount) {
        doc.setPageCount(pageCount);
    }

    @Deprecated
    public void incPageCount() {
        doc.incPageCount();
    }

    @Deprecated
    public void decPageCount() {
        doc.decPageCount();
    }

    @Deprecated
    public void write(final SymbolTable symbolTable, final VariableByteOutputStream ostream) throws IOException {
        doc.writeDocumentAttributes(symbolTable, ostream);
    }

    @Deprecated
    public void read(final SymbolTable symbolTable, final VariableByteInput istream) throws IOException {
        final long created = istream.readLong();
        final long lastModified = istream.readLong();
        final int mimeTypeSymbolsIndex = istream.readInt();
        final String mimeType = symbolTable.getMimeType(mimeTypeSymbolsIndex);
        final int pageCount = istream.readInt();
        final int userLock = istream.readInt();
        final DocumentTypeImpl docType;
        if (istream.readByte() == DocumentImpl.HAS_DOCTYPE) {
            docType = DocumentTypeImpl.read(istream);
        } else {
            docType = null;
        }
        final LockToken lockToken;
        if (istream.readByte() == DocumentImpl.HAS_LOCKTOKEN) {
            lockToken = LockToken.read(istream);
        } else {
            lockToken = null;
        }

        doc.setCreated(created);
        doc.setLastModified(lastModified);
        doc.setMimeType(mimeType);
        doc.setPageCount(pageCount);
        doc.setUserLock(userLock);
        doc.setDocType(docType);
        doc.setLockToken(lockToken);
    }

    @Deprecated
    public int getUserLock() {
        return doc.getUserLockInternal();
    }

    @Deprecated
    public void setUserLock(final int userLock) {
        doc.setUserLock(userLock);
    }

    @Deprecated
    public LockToken getLockToken() {
        return doc.getLockToken();
    }

    @Deprecated
    public void setLockToken(final LockToken token) {
        doc.setLockToken(token);
    }

    @Deprecated
    public DocumentType getDocType() {
        return doc.getDocType();
    }

    @Deprecated
    public void setDocType(final DocumentType docType) {
        doc.setDocType(docType);
    }

    @Deprecated
    public void incSplitCount() {
        doc.incSplitCount();
    }

    @Deprecated
    public int getSplitCount() {
        return doc.getSplitCount();
    }

    @Deprecated
    public void setSplitCount(final int count) {
        doc.setSplitCount(count);
    }

    @Deprecated
    public boolean isReferenced() {
        return doc.isReferenced();
    }

    @Deprecated
    public void setReferenced(final boolean referenced) {
        doc.setReferenced(referenced);
    }
}