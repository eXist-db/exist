/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

import java.io.EOFException;
import java.io.IOException;

import org.exist.collections.Collection;
import org.exist.security.Group;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.util.VariableByteInputStream;
import org.exist.util.VariableByteOutputStream;

/**
 * @author wolf
 */
public class BLOBDocument extends DocumentImpl {

	private long pageNr = -1;
	
	public BLOBDocument(DBBroker broker, Collection collection) {
		super(broker, collection);
	}

	public BLOBDocument(DBBroker broker, String docName, Collection collection) {
		super(broker, docName, collection);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.dom.DocumentImpl#getResourceType()
	 */
	public byte getResourceType() {
		return BINARY_FILE;
	}
	
	public void setPage(long page) {
		this.pageNr = page;
	}

	public long getPage() {
		return pageNr;
	}

	public void write(VariableByteOutputStream ostream) throws IOException {
		ostream.writeByte(getResourceType());
		ostream.writeInt(docId);
		ostream.writeUTF(fileName.substring(collection.getName().length() + 1));
		ostream.writeLong(address);
		ostream.writeLong(pageNr);
		SecurityManager secman = broker.getBrokerPool().getSecurityManager();
		if (secman == null) {
			ostream.writeInt(1);
			ostream.writeInt(1);
		} else {
			User user = secman.getUser(permissions.getOwner());
			Group group = secman.getGroup(permissions.getOwnerGroup());
			ostream.writeInt(user.getUID());
			ostream.writeInt(group.getId());
		}
		ostream.writeByte((byte) permissions.getPermissions());
	}

	public void read(VariableByteInputStream istream)
		throws IOException, EOFException {
		docId = istream.readInt();
		fileName = collection.getName() + '/' + istream.readUTF();
		address = istream.readLong();
		pageNr = istream.readLong();
		final SecurityManager secman =
			broker.getBrokerPool().getSecurityManager();
		final int uid = istream.readInt();
		final int gid = istream.readInt();
		final int perm = (istream.readByte() & 0777);
		if (secman == null) {
			permissions.setOwner(SecurityManager.DBA_USER);
			permissions.setGroup(SecurityManager.DBA_GROUP);
		} else {
			permissions.setOwner(secman.getUser(uid));
			permissions.setGroup(secman.getGroup(gid).getName());
		}
		permissions.setPermissions(perm);
	}
	
	public byte[] serialize() {
		final VariableByteOutputStream ostream = new VariableByteOutputStream(7);
		try {
			ostream.writeLong(created);
			ostream.writeLong(lastModified);
			final byte[] data = ostream.toByteArray();
			ostream.close();
			return data;
		} catch (IOException e) {
			LOG.warn("io error while writing document data", e);
			return null;
		}
	}
	
	public void deserialize(byte[] data) {
		VariableByteInputStream istream = new VariableByteInputStream(data);
		try {
			created = istream.readLong();
			lastModified = istream.readLong();
		} catch(IOException e) {
			LOG.warn("IO error while reading document metadata", e);
		}
	}
}
