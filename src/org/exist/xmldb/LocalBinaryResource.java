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
package org.exist.xmldb;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;

import org.exist.EXistException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.util.LockException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

/**
 * @author wolf
 */
public class LocalBinaryResource extends AbstractEXistResource implements BinaryResource {

	protected byte[] rawData = null;
	
	protected Date datecreated= null;
	protected Date datemodified= null;
	
	/**
	 * 
	 */
	public LocalBinaryResource(User user, BrokerPool pool, LocalCollection collection, XmldbURI docId) {
		super(user, pool, collection, docId, null);
	}
	
	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#getParentCollection()
	 */
	public Collection getParentCollection() throws XMLDBException {
		return parent;
	}
	
	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#getId()
	 */
	public String getId() throws XMLDBException {
		return docId.toString();
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
		if(rawData == null) {
			DBBroker broker = null;
			BinaryDocument blob = null;
			try {
				broker = pool.get(user);
				blob = (BinaryDocument)getDocument(broker, Lock.READ_LOCK);
				if(!blob.getPermissions().validate(user, Permission.READ))
				    throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
				    	"Permission denied to read resource");
				rawData = broker.getBinaryResource(blob);
			} catch(EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					"error while loading binary resource " + getId(), e);
			} finally {
			    parent.getCollection().releaseDocument(blob, Lock.READ_LOCK);
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
		else if(value instanceof String)
			rawData = ((String)value).getBytes();
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

	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#getCreationTime()
	 */
	public Date getCreationTime() throws XMLDBException {
        if (isNewResource)
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "The resource has not yet been stored");
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			BinaryDocument blob = (BinaryDocument)getDocument(broker, Lock.NO_LOCK);
			if (!blob.getPermissions().validate(user, Permission.READ))
				throw new XMLDBException(
						ErrorCodes.PERMISSION_DENIED,
				"permission denied to read resource");
			return new Date(blob.getMetadata().getCreated());
		} catch (EXistException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
		} finally {
			pool.release(broker);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#getLastModificationTime()
	 */
	public Date getLastModificationTime() throws XMLDBException {
        if (isNewResource)
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "The resource has not yet been stored");
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			BinaryDocument blob = (BinaryDocument)getDocument(broker, Lock.NO_LOCK);
			if (!blob.getPermissions().validate(user, Permission.READ))
				throw new XMLDBException(
						ErrorCodes.PERMISSION_DENIED,
				"permission denied to read resource");
			return new Date(blob.getMetadata().getLastModified());
		} catch (EXistException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
		} finally {
			pool.release(broker);
		}
	}

    /* (non-Javadoc)
     * @see org.exist.xmldb.AbstractEXistResource#getMimeType()
     */
    public String getMimeType() throws XMLDBException {
        if (isNewResource)
            return mimeType;
        DBBroker broker = null;
        try {
            broker = pool.get(user);
            BinaryDocument blob = (BinaryDocument)getDocument(broker, Lock.NO_LOCK);
            if (!blob.getPermissions().validate(user, Permission.READ))
                throw new XMLDBException(
                        ErrorCodes.PERMISSION_DENIED,
                "permission denied to read resource");
            mimeType = blob.getMetadata().getMimeType();
            return mimeType;
        } catch (EXistException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        } finally {
            pool.release(broker);
        }
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#getPermissions()
	 */
	public Permission getPermissions() throws XMLDBException {
        if (isNewResource)
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "The resource has not yet been stored");
	    DBBroker broker = null;
	    try {
	        broker = pool.get(user);
		    DocumentImpl document = getDocument(broker, Lock.NO_LOCK);
			return document != null ? document.getPermissions() : null;
	    } catch (EXistException e) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
        } finally {
	        pool.release(broker);
	    }
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#getContentLength()
	 */
	public int getContentLength() throws XMLDBException {
        if (isNewResource)
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "The resource has not yet been stored");
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			DocumentImpl document = getDocument(broker, Lock.NO_LOCK);
			if (!document.getPermissions().validate(user, Permission.READ))
				throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
						"permission denied to read resource");
			return document.getContentLength();
		} catch (EXistException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(),
					e);
		} finally {
			pool.release(broker);
		}
	}
	
	protected DocumentImpl getDocument(DBBroker broker, int lock) throws XMLDBException {
	    DocumentImpl document = null;
	    if(lock != Lock.NO_LOCK)
            try {
                document = parent.getCollection().getDocumentWithLock(broker, docId, lock);
            } catch (LockException e) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
                        "Failed to acquire lock on document " + docId);
            }
        else
	        document = parent.getCollection().getDocument(broker, docId);
	    if (document == null)
	        throw new XMLDBException(ErrorCodes.INVALID_RESOURCE);
	    if (document.getResourceType() != DocumentImpl.BINARY_FILE)
	        throw new XMLDBException(ErrorCodes.WRONG_CONTENT_TYPE, "Document " + docId + 
	                " is not a binary resource");
	    return document;
	}
	
	protected DocumentImpl openDocument(DBBroker broker, int lockMode) throws XMLDBException {
	    DocumentImpl document = super.openDocument(broker, lockMode);
	    if (document.getResourceType() != DocumentImpl.BINARY_FILE) {
	    	closeDocument(document, lockMode);
	        throw new XMLDBException(ErrorCodes.WRONG_CONTENT_TYPE, "Document " + docId + 
	                " is not a binary resource");
	    }
	    return document;
	}
}
