/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
 *  http://exist-db.org
 *  http://exist.sourceforge.net
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
 *
 *  $Id$
 */
package org.exist.dom.persistent;

import java.io.IOException;

import org.exist.ResourceMetadata;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.util.MimeType;
import org.w3c.dom.DocumentType;

public class DocumentMetadata implements ResourceMetadata {

    public static final byte NO_DOCTYPE = 0;
    public static final byte HAS_DOCTYPE = 1;

    public static final byte NO_LOCKTOKEN = 0;
    public static final byte HAS_LOCKTOKEN = 2;

    /**
     * the mimeType of the document
     */
    private String mimeType = MimeType.XML_TYPE.getName();

    /**
     * the creation time of this document
     */
    private long created = 0;

    /**
     * time of the last modification
     */
    private long lastModified = 0;

    /**
     * the number of data pages occupied by this document
     */
    private int pageCount = 0;

    /**
     * contains the user id if a user lock is held on this resource
     */
    private int userLock = 0;

    /**
     * the document's doctype declaration - if specified.
     */
    private DocumentType docType = null;

    /**
     * TODO associated lock token - if available
     */
    private LockToken lockToken = null;

    protected transient int splitCount = 0;

    private boolean isReferenced = false;

    public DocumentMetadata() {
        //Nothing to do
    }

    public DocumentMetadata(final DocumentMetadata other) {
        this.mimeType = other.mimeType;
        this.created = other.created;
        this.lastModified = other.lastModified;
    }

    public void copyOf(final DocumentMetadata other) {
        setCreated(other.getCreated());
        setLastModified(other.getLastModified());
        setMimeType(other.getMimeType());
        setDocType(other.getDocType());
    }

    @Override
    public long getCreated() {
        return created;
    }

    public void setCreated(final long created) {
        this.created = created;
        if(lastModified == 0) {
            this.lastModified = created;
        }
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(final long lastModified) {
        this.lastModified = lastModified;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(final String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * @return the number of pages currently occupied by this document.
     *
     */
    public int getPageCount() {
        return pageCount;
    }

    /**
     * Set the number of pages currently occupied by this document.
     *
     * @param pageCount number of pages currently occupied by this document
     *
     */
    public void setPageCount(final int pageCount) {
        this.pageCount = pageCount;
    }

    public void incPageCount() {
        ++pageCount;
    }

    public void decPageCount() {
        --pageCount;
    }

    public void write(final SymbolTable symbolTable, final VariableByteOutputStream ostream) throws IOException {
        ostream.writeLong(created);
        ostream.writeLong(lastModified);
        ostream.writeInt(symbolTable.getMimeTypeId(mimeType));
        ostream.writeInt(pageCount);
        ostream.writeInt(userLock);

        if(docType != null) {
            ostream.writeByte(HAS_DOCTYPE);
            ((DocumentTypeImpl) docType).write(ostream);
        } else {
            ostream.writeByte(NO_DOCTYPE);
        }

        // TODO added by dwes
        if(lockToken != null) {
            ostream.writeByte(HAS_LOCKTOKEN);
            lockToken.write(ostream);
        } else {
            ostream.writeByte(NO_LOCKTOKEN);
        }
    }

    public void read(final SymbolTable symbolTable, final VariableByteInput istream) throws IOException {
        created = istream.readLong();
        lastModified = istream.readLong();
        final int mimeTypeSymbolsIndex = istream.readInt();
        mimeType = symbolTable.getMimeType(mimeTypeSymbolsIndex);
        pageCount = istream.readInt();
        userLock = istream.readInt();
        if(istream.readByte() == HAS_DOCTYPE) {
            docType = DocumentTypeImpl.read(istream);
        } else {
            docType = null;
        }
        // TODO added by dwes
        if(istream.readByte() == HAS_LOCKTOKEN) {
            lockToken = LockToken.read(istream);
        } else {
            lockToken = null;
        }
    }

    public int getUserLock() {
        return userLock;
    }

    public void setUserLock(final int userLock) {
        this.userLock = userLock;
    }

    public LockToken getLockToken() {
        return lockToken;
    }

    public void setLockToken(final LockToken token) {
        lockToken = token;
    }

    public DocumentType getDocType() {
        return docType;
    }

    public void setDocType(final DocumentType docType) {
        this.docType = docType;
    }

    /**
     * Increase the page split count of this document. The number
     * of pages that have been split during inserts serves as an
     * indicator for the fragmentation
     */
    public void incSplitCount() {
        splitCount++;
    }

    public int getSplitCount() {
        return splitCount;
    }

    public void setSplitCount(final int count) {
        splitCount = count;
    }

    public boolean isReferenced() {
        return isReferenced;
    }

    public void setReferenced(final boolean referenced) {
        isReferenced = referenced;
    }
}
