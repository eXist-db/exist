/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
package org.exist.dom;

import org.exist.collections.Collection;
import org.exist.security.Group;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.btree.Paged.Page;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.xmldb.XmldbURI;

import java.io.EOFException;
import java.io.IOException;

/**
 * Represents a binary resource. Binary resources are just stored
 * as binary data in a single overflow page. However, class BinaryDocument
 * extends {@link org.exist.dom.DocumentImpl} and thus provides the 
 * same interface.
 * 
 * @author wolf
 */
public class BinaryDocument extends DocumentImpl {

    private long pageNr = Page.NO_PAGE;
    
    private long realSize = 0L;
    
    public BinaryDocument(BrokerPool pool) {
        super(pool, null, null);
    } 
    
    public BinaryDocument(BrokerPool pool, Collection collection) {
        super(pool, collection);
    }

    public BinaryDocument(BrokerPool pool, XmldbURI fileURI) {
        super(pool, null, fileURI);
    }

    public BinaryDocument(BrokerPool pool, Collection collection, XmldbURI fileURI) {
        super(pool, collection, fileURI);
    }

    /* (non-Javadoc)
     * @see org.exist.dom.DocumentImpl#getResourceType()
     */
    @Override
    public byte getResourceType() {
        return BINARY_FILE;
    }
    
    public void setPage(long page) {
        this.pageNr = page;
    }

    public long getPage() {
        return pageNr;
    }

    @Override
    public long getContentLength() {
        return realSize;
    }

    public void setContentLength(long length) {
        this.realSize = length;
    }

    @Override
    public void write(VariableByteOutputStream ostream) throws IOException {
        ostream.writeInt(getDocId());
        ostream.writeUTF(getFileURI().toString());
        ostream.writeLong(pageNr);

        ostream.writeInt(permissions.getOwner().getId());
        ostream.writeInt(permissions.getOwnerGroup().getId());

        ostream.writeByte((byte) permissions.getPermissions());
        ostream.writeLong(realSize);
        getMetadata().write(getBrokerPool(), ostream);
    }

    @Override
    public void read(VariableByteInput istream) throws IOException, EOFException {
        setDocId(istream.readInt());
        setFileURI(XmldbURI.create(istream.readUTF()));
        pageNr = istream.readLong();
        final SecurityManager secman = getBrokerPool().getSecurityManager();
        final int uid = istream.readInt();
        final int groupId = istream.readInt();
        final int perm = (istream.readByte() & 0777);
        if (secman == null) {
            permissions.setOwner(SecurityManager.DBA_USER);
            permissions.setGroup(SecurityManager.DBA_GROUP);
        } else {
            permissions.setOwner(secman.getAccount(uid));
            Group group = secman.getGroup(groupId);
            if (group != null)
                permissions.setGroup(group.getName());
        }
        permissions.setPermissions(perm);
        realSize = istream.readLong();
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.read(getBrokerPool(), istream);
        setMetadata(metadata);
    }
}
