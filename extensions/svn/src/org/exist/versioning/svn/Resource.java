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
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringBufferInputStream;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.dom.LockToken;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
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
    protected XmldbURI collectionPath = null;
    
    protected boolean initialized = false;
    
    private Collection collection = null;
    private DocumentImpl resource = null;
	
    public Resource(XmldbURI uri) {
		super(uri.toString());
		
		this.uri = uri;
	}

	public Resource(String uri) {
		this(XmldbURI.create(uri));
	}

	public Resource(File file, String child) {
		this((Resource)file, child);
	}

	public Resource(Resource resource, String child) {
		this(resource.uri.append(child));
	}

	public Resource(String parent, String child) {
		this(XmldbURI.create(parent).append(child));
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
    
    public boolean mkdir() {
    	DBBroker broker = null; 
		BrokerPool db = null;
		TransactionManager tm;

		try {
			try {
				db = BrokerPool.getInstance();
				broker = db.get(null);
			} catch (EXistException e) {
				return false;
			}
	
	        Collection collection = broker.getCollection(uri.toCollectionPathURI());
	        if (collection != null) return true;
	
	        Collection parent_collection = broker.getCollection(uri.toCollectionPathURI().removeLastSegment());
	        if (parent_collection == null) return false;
	
	        tm = db.getTransactionManager();
			Txn transaction = tm.beginTransaction();
			
			try {
				Collection child = broker.getOrCreateCollection(transaction, uri.toCollectionPathURI());
				broker.saveCollection(transaction, child);
				tm.commit(transaction);
			} catch (Exception e) {
	    		tm.abort(transaction);
				return false;
			}
		} finally {
			if (db != null)
				db.release(broker);
		}
    	
    	return true;
    }

    public boolean mkdirs() {
    	DBBroker broker = null; 
		BrokerPool db = null;
		TransactionManager tm;

		try {
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
				Collection child = broker.getOrCreateCollection(transaction, uri.toCollectionPathURI());
				broker.saveCollection(transaction, child);
				tm.commit(transaction);
			} catch (Exception e) {
	    		tm.abort(transaction);
				return false;
			}
		} finally {
			if (db != null)
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
			try {
				db = BrokerPool.getInstance();
				broker = db.get(null);
			} catch (EXistException e) {
				return false;
			}
	
			tm = db.getTransactionManager();
			Txn transaction = null;
	
	        org.exist.collections.Collection destination = null;
	        org.exist.collections.Collection source = null;
	        XmldbURI newName;
			try {
	     		source = broker.openCollection(uri.removeLastSegment(), Lock.WRITE_LOCK);
	    		if(source == null) {
	    			return false;
	            }
	    		DocumentImpl doc = source.getDocument(broker, uri.lastSegment());
	            if(doc == null) {
	                return false;
	            }
	            destination = broker.openCollection(destinationPath.removeLastSegment(), Lock.WRITE_LOCK);
	            if(destination == null) {
	                return false;
	            }
	            
	            newName = destinationPath.lastSegment();
	
	            transaction = tm.beginTransaction();
	            broker.copyResource(transaction, doc, destination, newName);
	            tm.commit(transaction);
	            return true;
	            
	        } catch ( Exception e ) {
	        	if (transaction != null) tm.abort(transaction);
	        	return false;
	        } finally {
	        	if(source != null) source.release(Lock.WRITE_LOCK);
	        	if(destination != null) destination.release(Lock.WRITE_LOCK);
	        }
        } finally {
        	if (db != null)
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
			try {
				db = BrokerPool.getInstance();
				broker = db.get(null);
			} catch (EXistException e) {
				return false;
			}
	
			tm = db.getTransactionManager();
	        Txn transaction = null;
	        try {
	            collection = broker.openCollection(uri.removeLastSegment(), Lock.NO_LOCK);
	            if (collection == null) {
	                return false;
	            }
	            // keep the write lock in the transaction
	            //transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
	
	            DocumentImpl doc = collection.getDocument(broker, uri.lastSegment());
	            if (doc == null) {
	            	return true;
	            }
	            
	            transaction = tm.beginTransaction();
	            if(doc.getResourceType() == DocumentImpl.BINARY_FILE)
	                collection.removeBinaryResource(transaction, broker, doc);
	            else
	                collection.removeXMLResource(transaction, broker, uri.lastSegment());
	            tm.commit(transaction);
	            return true;
	
	        } catch (Exception e) {
	        	if (transaction != null) tm.abort(transaction);
	            return false;
	        }	            
        } finally {
        	if (db != null)
        		db.release(broker);
        }
    }

    public boolean createNewFile() throws IOException {
    	DBBroker broker = null; 
		BrokerPool db = null;
		TransactionManager tm;

		try {
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
					resource.getUpdateLock().release(Lock.READ_LOCK);
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
				if (mimeType.isXMLType()) {
					// store as xml resource
					String str = "<empty/>"; 
					IndexInfo info = collection.validateXMLResource(transaction, broker, fileName, str);
					info.getDocument().getMetadata().setMimeType(mimeType.getName());
					collection.store(transaction, broker, info, str, false);
	
				} else {
					// store as binary resource
					is = new StringBufferInputStream("");
	
					collection.addBinaryResource(transaction, broker, fileName, is,
							mimeType.getName(), 0L , new Date(), new Date());
	
				}
				tm.commit(transaction);
			} catch (Exception e) {
				tm.abort(transaction);
				throw new IOException(e);
			} finally {
				SVNFileUtil.closeFile(is);
	
				if (resource != null)
					resource.getUpdateLock().release(Lock.READ_LOCK);
			}
		} finally {
			if (db != null)
				db.release(broker);
		}
		
		return true;
    }


    private synchronized void init() throws IOException {
    	if (initialized) return;
    	
    	DBBroker broker = null; 
		BrokerPool db = null;

		try {
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
						//may be, it's collection ... checking ...
						collection = broker.getCollection(uri);
						if (collection == null) {
							throw new IOException("Resource not found: "+uri);
						}
					} else {
						collection = resource.getCollection();
					}
				}
			} catch (Exception e) {
				throw new IOException(e);
			} finally {
				if (resource != null)
					resource.getUpdateLock().release(Lock.READ_LOCK);
			}
		} finally {
			if (db != null)
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
    
    private Subject getBrokerUser() throws IOException {
    	DBBroker broker = null; 
		BrokerPool db = null;

		try {
			db = BrokerPool.getInstance();
			broker = db.get(null);
			
			return broker.getSubject();
		} catch (EXistException e) {
			throw new IOException(e);
		} finally {
			if (db != null)
				db.release(broker);
		}
    }
    
    public Reader getReader() throws IOException {
    	InputStream is = getConnection().getInputStream();
        BufferedInputStream bis = new BufferedInputStream(is);
        return new InputStreamReader(bis);
    }

    private URLConnection connection = null;
    
    private URLConnection getConnection() throws IOException {
    	if (connection == null) {
			BrokerPool db = null;
			DBBroker broker = null;
			try {
				db = BrokerPool.getInstance();
				broker = db.get(null);
				Subject subject = broker.getSubject();
				
				URL url = new URL("xmldb:exist://jsessionid:"+subject.getSessionId()+"@"+ uri.toString());
				connection = url.openConnection();
			} catch (IllegalArgumentException e) {
				throw new IOException(e); 
			} catch (MalformedURLException e) {
				throw new IOException(e); 
			} catch (EXistException e) {
				throw new IOException(e); 
			} finally {
				if (db != null)
					db.release(broker);
			}
    	}
    	return connection;
    }

    public InputStream getInputStream() throws IOException {
    	return getConnection().getInputStream();
    }

    public Writer getWriter() throws IOException {
    	return new BufferedWriter(new OutputStreamWriter(getOutputStream(false)));
    }

    public OutputStream getOutputStream() throws IOException {
    	return getOutputStream(false);
    }

    public OutputStream getOutputStream(boolean append) throws IOException {
    	//XXX: code append
    	if (append)
    		System.err.println("BUG: OutputStream in append mode!");
    	return getConnection().getOutputStream();
    }

    public DocumentImpl getDocument() throws IOException {
		init();
		
		return resource;
	}

	public Collection getCollection() throws IOException {
		if (!initialized) {
	    	DBBroker broker = null; 
			BrokerPool db = null;

			try {
				try {
					db = BrokerPool.getInstance();
					broker = db.get(null);
				} catch (EXistException e) {
					throw new IOException(e);
				}
	
				try {
					if (uri.endsWith("/")) {
						collection = broker.getCollection(uri);
					} else {
						collection = broker.getCollection(uri);
						if (collection == null)
							collection = broker.getCollection(uri.removeLastSegment());
					}
					if (collection == null)
						throw new IOException("Collection not found: "+uri);
					
					return collection;
				} catch (Exception e) {
					throw new IOException(e);
				}
			} finally {
				if (db != null)
					db.release(broker);
			}
		}

		if (resource == null)
			return collection;
		else
			return resource.getCollection();
			
	}

    public File[] listFiles() {
    	if (!isDirectory())
    		return null;
    	
    	if (collection == null)
    		return null;
    	
    	DBBroker broker = null; 
		BrokerPool db = null;

		try {
			try {
				db = BrokerPool.getInstance();
				broker = db.get(null);
			} catch (EXistException e) {
				return null;
			}
	
	    	try {
	        	collection.getLock().acquire(Lock.READ_LOCK);
	
	        	File[] children = new File[collection.getChildCollectionCount() + 
	                                       collection.getDocumentCount()];
	            
	            //collections
	            int j = 0;
	            for (Iterator<XmldbURI> i = collection.collectionIterator(); i.hasNext(); j++)
	            	children[j] = new Resource(i.next());
	
	            //collections
	            List<XmldbURI> allresources = new ArrayList<XmldbURI>();
	            DocumentImpl doc = null;
	            for (Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
	                doc = i.next();
	                
	                // Include only when (1) locktoken is present or (2)
	                // locktoken indicates that it is not a null resource
	                LockToken lock = doc.getMetadata().getLockToken();
	                if(lock==null || (!lock.isNullResource()) ){
	                    allresources.add( doc.getURI() );
	                }
	            }
	            
	            // Copy content of list into String array.
	            for(Iterator<XmldbURI> i = allresources.iterator(); i.hasNext(); j++){
	            	children[j] = new Resource(i.next());
	            }
	            
	            return children;
	        } catch (LockException e) {
	        	//throw new IOException("Failed to acquire lock on collection '" + uri + "'");
	    		return null;
		    } finally {
		        collection.release(Lock.READ_LOCK);
		    }
	    } finally {
	    	if (db != null)
	    		db.release(broker);
	    }
    }
    
    public long length() {
    	return 0;
    	
//		try {
//			init();
//		} catch (IOException e) {
//			return 0;
//		}
//
//    	if (resource != null)
//    		return resource.getContentLength();
//    	
//    	return 0;
    }
    
    public String getPath() {
    	return uri.toString();
    }
}
