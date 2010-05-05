/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.versioning.svn;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringBufferInputStream;
import java.io.Writer;
import java.util.Date;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.source.DBSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * eXist's resource. It extend java.io.File
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Resource extends File {

	private static final long serialVersionUID = -3450182389919974961L;

    public static final char separatorChar = '/';

    protected XmldbURI uri;
    
    private boolean initialized = false;
    
    private Collection collection = null;
    private DocumentImpl resource = null;
	
	public Resource(String uri) {
		super(uri);
		
		this.uri = XmldbURI.create(uri);
	}

    public Resource(XmldbURI uri) {
		super(uri.toString());
		
		this.uri = uri;
	}

	public Resource(File file, String child) {
		this((Resource)file, child);
	}

	public Resource(Resource resource, String child) {
		super(resource.uri.append(child).toString());

		this.uri = resource.uri.append(child);
	}

    public Resource getParentFile() {
    	XmldbURI parentPath = uri.removeLastSegment();
		if (parentPath == XmldbURI.EMPTY_URI) return null;

		return new Resource(parentPath);
    }

    public Resource getAbsoluteFile() {
    	return this; //UNDERSTAND: is it correct?
    }

    public String getName() {
    	return uri.lastSegment().toString();
    }
    
    public boolean mkdirs() {
    	DBBroker broker = null; 
		BrokerPool db = null;
		TransactionManager tm;

		try {
			db = BrokerPool.getInstance();
			broker = db.get(null);
		} catch (EXistException e) {
			return false;
		}

        Collection collection = broker.getCollection(uri.toCollectionPathURI());
        if (collection != null) return true;

		tm = db.getTransactionManager();
		Txn transaction = tm.beginTransaction();
		
		try {
			Collection child = broker.getOrCreateCollection(transaction, uri);
			broker.saveCollection(transaction, child);
			tm.commit(transaction);
		} catch (Exception e) {
    		tm.abort(transaction);
			return false;
		} finally {
			db.release(broker);
		}
    	
    	return true;
    }

    public boolean isDirectory() {
    	try {
			init();
		} catch (IOException e) {
			return false;
		}
    	
    	return (resource == null);
    }

    public boolean isFile() {
    	try {
			init();
		} catch (IOException e) {
			return false;
		}

		return (resource != null);
    }

    public boolean exists() {
    	try {
			init();
		} catch (IOException e) {
			return false;
		}
		
		return ((collection != null) || (resource != null));
    	
    }
    
    public boolean canRead() {
		try {
			return getPermission().validate(getBrokerUser(), Permission.READ);
		} catch (IOException e) {
			return false;
		}
    }
    
    public boolean renameTo(File dest) {
    	XmldbURI destinationPath = ((Resource)dest).uri;

    	DBBroker broker = null; 
		BrokerPool db = null;
		TransactionManager tm;

		try {
			db = BrokerPool.getInstance();
			broker = db.get(null);
		} catch (EXistException e) {
			return false;
		}

		tm = db.getTransactionManager();
        Txn transaction = tm.beginTransaction();

        org.exist.collections.Collection destination = null;
        org.exist.collections.Collection source = null;
        XmldbURI newName;
		try {
     		source = broker.openCollection(uri.removeLastSegment(), Lock.WRITE_LOCK);
    		if(source == null) {
    			tm.abort(transaction);
    			return false;
            }
    		DocumentImpl doc = source.getDocument(broker, uri.lastSegment());
            if(doc == null) {
            	tm.abort(transaction);
                return false;
            }
            destination = broker.openCollection(destinationPath.removeLastSegment(), Lock.WRITE_LOCK);
            if(destination == null) {
            	tm.abort(transaction);
                return false;
            }
            
            newName = destinationPath.lastSegment();

            broker.copyResource(transaction, doc, destination, newName);
            tm.commit(transaction);
            return true;
            
        } catch ( Exception e ) {
        	tm.abort(transaction);
        	return false;
        } finally {
        	if(source != null) source.release(Lock.WRITE_LOCK);
        	if(destination != null) destination.release(Lock.WRITE_LOCK);
            db.release( broker );
        }
    }
    
    public boolean setReadOnly() {
    	//XXX: code !!!
    	
    	return true;
    }
    
    public boolean delete() {
    	DBBroker broker = null; 
		BrokerPool db = null;
		TransactionManager tm;

		try {
			db = BrokerPool.getInstance();
			broker = db.get(null);
		} catch (EXistException e) {
			return false;
		}

		tm = db.getTransactionManager();
        Txn transaction = tm.beginTransaction();
        try {
            collection = broker.openCollection(uri.removeLastSegment(), Lock.WRITE_LOCK);
            if (collection == null) {
            	tm.abort(transaction);
                return false;
            }
            // keep the write lock in the transaction
            transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);

            DocumentImpl doc = collection.getDocument(broker, uri.lastSegment());
            if (doc == null) {
            	tm.abort(transaction);
            	return false;
            }
            
            if(doc.getResourceType() == DocumentImpl.BINARY_FILE)
                collection.removeBinaryResource(transaction, broker, doc);
            else
                collection.removeXMLResource(transaction, broker, uri.lastSegment());
            tm.commit(transaction);
            return true;

        } catch (Throwable e) {
            return false;
            
        } finally {
            db.release(broker);
        }
    }

    public boolean createNewFile() throws IOException {
    	DBBroker broker = null; 
		BrokerPool db = null;
		TransactionManager tm;

		try {
			db = BrokerPool.getInstance();
			broker = db.get(null);
		} catch (EXistException e) {
			throw new IOException(e);
		}

		try {
			if (uri.endsWith("/"))
				throw new IOException("It collection, but should be resource: "+uri);
		} catch (Exception e) {
			throw new IOException(e);
		}
		
		XmldbURI collectionURI = uri.removeLastSegment();
		collection = broker.getCollection(collectionURI);
		if (collection == null)
			throw new IOException("Collection not found: "+collectionURI);
		
		XmldbURI fileName = uri.lastSegment();

		try {
			resource = broker.getXMLResource(uri, Lock.READ_LOCK);
		} catch (PermissionDeniedException e1) {
			if (resource != null) {
				collection = resource.getCollection();
				initialized = true;
				
				return false;
			}
		}
		
		MimeType mimeType = MimeTable.getInstance().getContentTypeFor(fileName);

		if (mimeType == null) {
			mimeType = MimeType.BINARY_TYPE;
		}
		
		tm = db.getTransactionManager();
		Txn transaction = tm.beginTransaction();

		InputStream is = null;
		try {
//			if (mimeType.isXMLType()) {
//				// store as xml resource
//				is = new FileInputStream(temp);
//				IndexInfo info = collection.validateXMLResource(
//						transaction, broker, fileName, new InputSource(new InputStreamReader(is)));
//				is.close();
//				info.getDocument().getMetadata().setMimeType(mimeType.getName());
//				is = new FileInputStream(temp);
//				collection.store(transaction, broker, info, new InputSource(new InputStreamReader(is)), false);
//				is.close();
//
//			} else {
				// store as binary resource
				is = new StringBufferInputStream("");

				collection.addBinaryResource(transaction, broker, fileName, is,
						mimeType.getName(), (int) 0, new Date(), new Date());

//			}
			tm.commit(transaction);
		} catch (Exception e) {
			tm.abort(transaction);
			throw new IOException(e);
		} finally {
			SVNFileUtil.closeFile(is);

			db.release(broker);
		}
		
		return true;
    }


    private synchronized void init() throws IOException {
    	if (initialized) return;
    	
    	DBBroker broker = null; 
		BrokerPool db = null;

		try {
			db = BrokerPool.getInstance();
			broker = db.get(null);
		} catch (EXistException e) {
			throw new IOException(e);
		}

		try {
			//collection
			if (uri.endsWith("/")) {
				collection = broker.getCollection(uri);
				if (collection == null)
					throw new IOException("Resource not found: "+uri);
				
			//resource
			} else {
				resource = broker.getXMLResource(uri, Lock.READ_LOCK);
				if (resource == null) {
					//may be, it's collection ... cheking ...
					collection = broker.getCollection(uri);
					if (collection == null)
						throw new IOException("Resource not found: "+uri);
				} else {
					collection = resource.getCollection();
				}
			}
		} catch (Exception e) {
			throw new IOException(e);
		} finally {
			db.release(broker);
		}
		
		initialized = true;
    }
    
    private Permission getPermission() throws IOException {
    	init();
    	
    	if (isFile()) return collection.getPermissions();

    	if (isDirectory()) return resource.getPermissions();
    	
    	throw new IOException("this never should happen");
    }
    
    private User getBrokerUser() throws IOException {
    	DBBroker broker = null; 
		BrokerPool db = null;

		try {
			db = BrokerPool.getInstance();
			broker = db.get(null);
			
			return broker.getUser();
		} catch (EXistException e) {
			throw new IOException(e);
		} finally {
			db.release(broker);
		}
    }
    
    public Reader getReader() throws IOException {
    	DBBroker broker = null; 
		BrokerPool db = null;

		try {
			db = BrokerPool.getInstance();
			broker = db.get(null);
		} catch (EXistException e) {
			throw new IOException(e);
		}

		try {
    	
			//UNDERSTAND: read as dba ???
			BinaryDocument resource = (BinaryDocument) broker.getXMLResource(uri, Lock.READ_LOCK);
			
			if (resource == null)
				throw new IOException("Can't find resource.");
			
			DBSource source = new DBSource(broker, resource, false);
			return source.getReader();

		} catch (PermissionDeniedException e) {
			throw new IOException(e);
		} finally {
			db.release(broker);
		}
    }

    public InputStream getInputStream() throws IOException {
    	DBBroker broker = null; 
		BrokerPool db = null;

		try {
			db = BrokerPool.getInstance();
			broker = db.get(null);
		} catch (EXistException e) {
			throw new IOException(e);
		}

		try {
    	
			//UNDERSTAND: read as dba ???
			BinaryDocument resource = (BinaryDocument) broker.getXMLResource(uri, Lock.READ_LOCK);
			
			if (resource == null)
				throw new IOException("Can't find resource.");
			
			InputStream is = broker.getBinaryResource(resource);
	        return new BufferedInputStream(is);
		} catch (PermissionDeniedException e) {
			throw new IOException(e);
		} finally {
			db.release(broker);
		}
    }

    public Writer getWriter() throws IOException {
    	return new ResourceWriter(this, File.createTempFile("eXist", "resource"));
    }

    public ResourceOutputStream getOutputStream() throws IOException {
    	return getOutputStream(false);
    }
    
    public ResourceOutputStream getOutputStream(boolean append) throws IOException {
    	//XXX: code append
    	return new ResourceOutputStream(this, File.createTempFile("eXist", "resource"));
    }

    public DocumentImpl getDocument() throws IOException {
		init();
		
		return resource;
	}

	public Collection getCollection() throws IOException {
		init();

		if (resource == null)
			return collection;
		else
			return resource.getCollection();
			
	}
}
