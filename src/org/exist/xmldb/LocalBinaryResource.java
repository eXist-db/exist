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
package org.exist.xmldb;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.exist.EXistException;
import org.exist.dom.BLOBDocument;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

/**
 * @author wolf
 */
public class LocalBinaryResource implements BinaryResource {

	protected User user;
	protected BrokerPool pool;
	protected LocalCollection parent;
	protected String docId;
	protected BLOBDocument blob = null;
	protected byte[] rawData = null;
	
	/**
	 * 
	 */
	public LocalBinaryResource(User user, BrokerPool pool, LocalCollection collection, String docId) {
		super();
		this.user = user;
		this.pool = pool;
		this.parent = collection;
		if (docId.indexOf('/') > -1)
			docId = docId.substring(docId.lastIndexOf('/') + 1);
		this.docId = docId;
	}

	public LocalBinaryResource(User user, BrokerPool pool, LocalCollection collection, BLOBDocument blob) {
		this(user, pool, collection, blob.getFileName());
		this.blob = blob;
	}
	
	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#getParentCollection()
	 */
	public Collection getParentCollection() throws XMLDBException {
		return parent;
	}

	public BLOBDocument getBlob() {
		return blob;
	}
	
	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#getId()
	 */
	public String getId() throws XMLDBException {
		return docId;
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#getResourceType()
	 */
	public String getResourceType() throws XMLDBException {
		return "BinaryResource";
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#getContent()
	 */
	public Object getContent() throws XMLDBException {
		if(rawData == null && blob != null) {
			DBBroker broker = null;
			try {
				broker = pool.get(user);
				rawData = broker.getBinaryResourceData(blob);
			} catch(EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					"error while loading binary resource " + getId(), e);
			} finally {
				pool.release(broker);
			}
		}
		return rawData;
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#setContent(java.lang.Object)
	 */
	public void setContent(Object value) throws XMLDBException {
		if(value instanceof File) {
			readFile((File)value);
		} else if(value instanceof byte[])
			rawData = (byte[])value;
		else
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"don't know how to handle value of type " + value.getClass().getName());
	}

	private void readFile(File file) throws XMLDBException {
		try {
			FileInputStream is = new FileInputStream(file);
			ByteArrayOutputStream bos = new ByteArrayOutputStream(2048);
			byte[] temp = new byte[1024];
			int count = 0;
			while((count = is.read(temp)) > -1) {
				bos.write(temp, 0, count);
			}
			rawData = bos.toByteArray();
		} catch (FileNotFoundException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"file " + file.getAbsolutePath() + " could not be found", e);
		} catch (IOException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"IO exception while reading file " + file.getAbsolutePath(), e);
		}
	}

}
