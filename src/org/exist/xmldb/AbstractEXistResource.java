/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist-db.org
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

import java.util.Date;

import org.exist.dom.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.LockException;
import org.w3c.dom.DocumentType;
import org.xml.sax.ext.LexicalHandler;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * Abstract base implementation of interface EXistResource.
 */
public abstract class AbstractEXistResource implements EXistResource {

	protected User user;
	protected BrokerPool pool;
	protected LocalCollection parent;
	protected XmldbURI docId = null;
	protected String mimeType = null;
    protected boolean isNewResource = false;
    
	public AbstractEXistResource(User user, BrokerPool pool, LocalCollection parent, XmldbURI docId, String mimeType) {
		this.user = user;
		this.pool = pool;
		this.parent = parent;
		docId = docId.lastSegment();
		this.docId = docId;
        this.mimeType = mimeType;
	}
	
	/**
	 * 
	 * @param user
	 * @param pool
	 * @param parent
	 * @param docId
	 * @param mimeType
	 * 
	 * @deprecated Use the XmldbURI constructor instead
	 */
	public AbstractEXistResource(User user, BrokerPool pool, LocalCollection parent, String docId, String mimeType) {
		this(user, pool, parent, XmldbURI.create(docId), mimeType);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#getCreationTime()
	 */
	public abstract Date getCreationTime() throws XMLDBException;

	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#getLastModificationTime()
	 */
	public abstract Date getLastModificationTime() throws XMLDBException;

	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#getPermissions()
	 */
	public abstract Permission getPermissions() throws XMLDBException;
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#setLexicalHandler(org.xml.sax.ext.LexicalHandler)
	 */
	public void setLexicalHandler(LexicalHandler handler) {
	}
	
    public void setMimeType(String mime) {
        this.mimeType = mime;
    }
    
    public String getMimeType() throws XMLDBException {
        return mimeType;
    }
    
	protected DocumentImpl openDocument(DBBroker broker, int lockMode) throws XMLDBException {
	    DocumentImpl document = null;
	    org.exist.collections.Collection parentCollection = null;
	    try {
	    	parentCollection = parent.getCollectionWithLock(lockMode);
		    if(parentCollection == null)
		    	throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + parent.getPath() + " not found");
	        try {
	        	document = parentCollection.getDocumentWithLock(broker, docId, lockMode);
	        } catch (LockException e) {
	        	throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
	        			"Failed to acquire lock on document " + docId);
	        }
		    if (document == null) {
		        throw new XMLDBException(ErrorCodes.INVALID_RESOURCE);
		    }
	//	    System.out.println("Opened document " + document.getName() + " mode = " + lockMode);
		    return document;
	    } finally {
	    	if(parentCollection != null)
	    		parentCollection.release(lockMode);
	    }
	}
	
	protected void closeDocument(DocumentImpl doc, int lockMode) throws XMLDBException {
		if(doc == null)
			return;
//		System.out.println("Closed " + doc.getName() + " mode = " + lockMode);
		doc.getUpdateLock().release(lockMode);
	}

    public  DocumentType getDocType() throws XMLDBException {
    	return null;
        }

    public void setDocType(DocumentType doctype) throws XMLDBException {
		
    }
}
