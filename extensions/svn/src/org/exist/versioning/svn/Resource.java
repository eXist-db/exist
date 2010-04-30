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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

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
import org.exist.xmldb.XmldbURI;

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
    	//XXX: code !!!
    	
    	return false;
    }
    
    public boolean setReadOnly() {
    	//XXX: code !!!
    	
    	return false;
    }
    
    public boolean createNewFile() throws IOException {
    	System.out.println("*******************************************");
    	System.out.println("               createNewFile               ");
    	System.out.println("*******************************************");
    	
    	return false;
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
				if (resource == null)
					throw new IOException("Resource not found: "+uri);
				
				collection = resource.getCollection();
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

    public Writer getWriter() throws IOException {
    	return new ResourceWriter(this, File.createTempFile("eXist", "resource"));
    }

	public DocumentImpl getDocument() {
		return resource;
	}

	public Collection getCollection() {
		if (resource == null)
			return collection;
		else
			return resource.getCollection();
			
	}
}
