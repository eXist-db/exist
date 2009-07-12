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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import org.exist.EXistException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.util.EXistInputSource;
import org.exist.util.LockException;
import org.xml.sax.InputSource;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;

/**
 * @author wolf
 */
public class LocalBinaryResource extends AbstractEXistResource implements ExtendedResource, BinaryResource, EXistResource {

	protected InputSource inputSource = null;
	protected File file = null;
	protected byte[] rawData = null;
	private boolean isExternal=false;
	
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

	public Object getExtendedContent() throws XMLDBException {
		if(file!=null)
			return file;
		if(inputSource!=null)
			return inputSource;
		
		DBBroker broker = null;
		BinaryDocument blob = null;
		InputStream rawDataStream = null;
		try {
			broker = pool.get(user);
			blob = (BinaryDocument)getDocument(broker, Lock.READ_LOCK);
			if(!blob.getPermissions().validate(user, Permission.READ))
			    throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
			    	"Permission denied to read resource");
			
			rawDataStream = broker.getBinaryResource(blob);
		} catch(EXistException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"error while loading binary resource " + getId(), e);
		} catch(IOException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"error while loading binary resource " + getId(), e);
		} finally {
			if(blob!=null)
				parent.getCollection().releaseDocument(blob, Lock.READ_LOCK);
			if(broker!=null)
				pool.release(broker);
		}
		
		return rawDataStream;
	}
	
	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#getContent()
	 */
	public Object getContent() throws XMLDBException {
		Object res=getExtendedContent();
		if(res!=null) {
			if(res instanceof File) {
				return readFile((File)res);
			} else if(res instanceof InputSource) {
				return readFile((InputSource)res);
			} else if(res instanceof InputStream) {
				return readFile((InputStream)res);
			}
		}
		
		return res;
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#setContent(java.lang.Object)
	 */
	public void setContent(Object value) throws XMLDBException {
		if(value instanceof File) {
			file=(File)value;
			isExternal=true;
		} else if(value instanceof InputSource) {
			inputSource=(InputSource)value;
			isExternal=true;
		} else if(value instanceof byte[]) {
			rawData = (byte[])value;
			isExternal=true;
		} else if(value instanceof String) {
			rawData = ((String)value).getBytes();
			isExternal=true;
		} else
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"don't know how to handle value of type " + value.getClass().getName());
	}
	
	public InputStream getStreamContent() throws XMLDBException {
		InputStream retval=null;
		if(file!=null) {
			try {
				retval=new FileInputStream(file);
			} catch(FileNotFoundException fnfe) {
				// Cannot fire it :-(
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, fnfe.getMessage(), fnfe);
			}
		} else if(inputSource!=null) {
			retval=inputSource.getByteStream();
		} else if(rawData!=null) {
			retval=new ByteArrayInputStream(rawData);
		} else {
			DBBroker broker = null;
			BinaryDocument blob = null;
			try {
				broker = pool.get(user);
				blob = (BinaryDocument)getDocument(broker, Lock.READ_LOCK);
				if(!blob.getPermissions().validate(user, Permission.READ))
				    throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
				    	"Permission denied to read resource");
				
				retval = broker.getBinaryResource(blob);
			} catch(EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					"error while loading binary resource " + getId(), e);
			} catch(IOException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					"error while loading binary resource " + getId(), e);
			} finally {
				if(blob!=null)
					parent.getCollection().releaseDocument(blob, Lock.READ_LOCK);
				if(broker!=null)
					pool.release(broker);
			}
		}
		
		return retval;
	}
	
	public void getContentIntoAFile(File tmpfile) throws XMLDBException {
		try {
			FileOutputStream fos=new FileOutputStream(tmpfile);
			BufferedOutputStream bos=new BufferedOutputStream(fos);
			getContentIntoAStream(bos);
			bos.close();
			fos.close();
		} catch(IOException ioe) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					"error while loading binary resource " + getId(), ioe);
		}
	}
	
	public void getContentIntoAStream(OutputStream os) throws XMLDBException {
		DBBroker broker = null;
		BinaryDocument blob = null;
		boolean doClose=false;
		try {
			broker = pool.get(user);
			blob = (BinaryDocument)getDocument(broker, Lock.READ_LOCK);
			if(!blob.getPermissions().validate(user, Permission.READ))
				throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
					"Permission denied to read resource");
			
			// Improving the performance a bit for files!
			if(os instanceof FileOutputStream) {
				os = new BufferedOutputStream(os,655360);
				doClose=true;
			}
			
			broker.readBinaryResource(blob, os);
		} catch(EXistException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"error while loading binary resource " + getId(), e);
		} catch(IOException ioe) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					"error while loading binary resource " + getId(), ioe);
		} finally {
			if(blob!=null)
				parent.getCollection().releaseDocument(blob, Lock.READ_LOCK);
			if(broker!=null)
				pool.release(broker);
			if(doClose) {
				try {
					os.close();
				} catch(IOException ioe) {
					// IgnoreIT(R)
				}
			}
		}
	}
	
	public void freeLocalResources()
	{
		if(!isExternal && file!=null) {
			file=null;
		}
	}
	
	public long getStreamLength() throws XMLDBException {
		long retval=-1;
		if(file!=null) {
			retval=file.length();
		} else if(inputSource!=null && inputSource instanceof EXistInputSource) {
			retval=((EXistInputSource)inputSource).getByteStreamLength();
		} else if(rawData!=null) {
			retval=rawData.length;
		} else {
			DBBroker broker = null;
			BinaryDocument blob = null;
			try {
				broker = pool.get(user);
				blob = (BinaryDocument)getDocument(broker, Lock.READ_LOCK);
				retval=blob.getContentLength();
			} catch(EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
					"error while loading binary resource " + getId(), e);
			} finally {
				if(blob!=null)
					parent.getCollection().releaseDocument(blob, Lock.READ_LOCK);
				
				if(broker!=null)
					pool.release(broker);
			}
		}
		
		return retval;
	}
	
	private byte[] readFile(File file) throws XMLDBException {
		try {
			return readFile(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"file " + file.getAbsolutePath() + " could not be found", e);
		}
	}

	private byte[] readFile(InputSource is) throws XMLDBException {
		return readFile(is.getByteStream());
	}

	private byte[] readFile(InputStream is) throws XMLDBException {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream(2048);
			byte[] temp = new byte[1024];
			int count = 0;
			while((count = is.read(temp)) > -1) {
				bos.write(temp, 0, count);
			}
			return bos.toByteArray();
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
