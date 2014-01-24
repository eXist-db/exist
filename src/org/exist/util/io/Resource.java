/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2012 The eXist Project
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
package org.exist.util.io;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.Collection.CollectionEntry;
import org.exist.collections.IndexInfo;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentMetadata;
import org.exist.dom.LockToken;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.FileInputSource;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;

import static org.exist.security.Permission.*;

/**
 * eXist's resource. It extend java.io.File
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Resource extends File {

	private static final long serialVersionUID = -3450182389919974961L;

    public static final char separatorChar = '/';

    //  default output properties for the XML serialization
    public final static Properties XML_OUTPUT_PROPERTIES = new Properties();

    static {
        XML_OUTPUT_PROPERTIES.setProperty(OutputKeys.INDENT, "yes");
        XML_OUTPUT_PROPERTIES.setProperty(OutputKeys.ENCODING, "UTF-8");
        XML_OUTPUT_PROPERTIES.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        XML_OUTPUT_PROPERTIES.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
        XML_OUTPUT_PROPERTIES.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
    }
    
    public final static int DEFAULT_COLLECTION_PERM = 0777;
    public final static int DEFAULT_RESOURCE_PERM = 0644;


    private static final SecureRandom random = new SecureRandom();
    static File generateFile(String prefix, String suffix, File dir) {
        long n = random.nextLong();
        if (n == Long.MIN_VALUE) {
            n = 0;      // corner case
        } else {
            n = Math.abs(n);
        }
        return new Resource(dir, prefix + Long.toString(n) + suffix);
    }

    public static File createTempFile(String prefix, String suffix, File directory) throws IOException {
        if (prefix.length() < 3)
            {throw new IllegalArgumentException("Prefix string too short");}
        if (suffix == null)
            {suffix = ".tmp";}
        
        return generateFile(prefix, suffix, directory);
    }

	protected XmldbURI uri;
    
    protected boolean initialized = false;
    
    private Collection collection = null;
    private DocumentImpl resource = null;
    
    File file = null;
	
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
//		this(child.startsWith("/db") ? XmldbURI.create(child) : resource.uri.append(child));
	}

	public Resource(String parent, String child) {
		this(XmldbURI.create(parent).append(child));
//		this(child.startsWith("/db") ? XmldbURI.create(child) : XmldbURI.create(parent).append(child));
	}

	public Resource getParentFile() {
    	final XmldbURI parentPath = uri.removeLastSegment();
		if (parentPath == XmldbURI.EMPTY_URI) {
			if (uri.startsWith(XmldbURI.DB))
				return null;
			
			return new Resource(XmldbURI.DB);
		}

		return new Resource(parentPath);
    }

    public Resource getAbsoluteFile() {
    	return this; //UNDERSTAND: is it correct?
    }

    public File getCanonicalFile() throws IOException {
    	return this;
//        String canonPath = getCanonicalPath();
//        return new File(canonPath, fs.prefixLength(canonPath));
    }
    
    public String getName() {
    	return uri.lastSegment().toString();
    }
    
    private void closeFile(InputStream is) {
        if (is == null) {
            return;
        }
        try {
            is.close();
        } catch (final IOException e) {
            //
        }
    }
    
    public boolean mkdir() {
    	DBBroker broker = null; 
		BrokerPool db = null;
		TransactionManager tm;

		try {
			try {
				db = BrokerPool.getInstance();
				broker = db.get(null);
			} catch (final EXistException e) {
				return false;
			}
	
	        final Collection collection = broker.getCollection(uri.toCollectionPathURI());
	        if (collection != null) {return true;}
	
	        final Collection parent_collection = broker.getCollection(uri.toCollectionPathURI().removeLastSegment());
	        if (parent_collection == null) {return false;}
	
	        tm = db.getTransactionManager();
			final Txn transaction = tm.beginTransaction();
			
			try {
				final Collection child = broker.getOrCreateCollection(transaction, uri.toCollectionPathURI());
				broker.saveCollection(transaction, child);
				tm.commit(transaction);
			} catch (final Exception e) {
	    		tm.abort(transaction);
				return false;
			} finally {
                tm.close(transaction);
            }
        } catch (final Exception e) {
			return false;
			
		} finally {
			if (db != null)
				{db.release(broker);}
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
			} catch (final EXistException e) {
				return false;
			}
	
	        final Collection collection = broker.getCollection(uri.toCollectionPathURI());
	        if (collection != null) {return true;}
	
			tm = db.getTransactionManager();
			final Txn transaction = tm.beginTransaction();
			
			try {
				final Collection child = broker.getOrCreateCollection(transaction, uri.toCollectionPathURI());
				broker.saveCollection(transaction, child);
				tm.commit(transaction);
			} catch (final Exception e) {
	    		tm.abort(transaction);
				return false;
			} finally {
                tm.close(transaction);
            }

		} catch (final Exception e) {
			return false;
		
		} finally {
			if (db != null)
				{db.release(broker);}
		}
    	
    	return true;
    }

    public boolean isDirectory() {
    	try {
			init();
		} catch (final IOException e) {
			return false;
		}
    	
    	return (resource == null);
    }

    public boolean isFile() {
    	try {
			init();
		} catch (final IOException e) {
			return false;
		}

		return (resource != null);
    }

    public boolean exists() {
    	try {
			init();
		} catch (final IOException e) {
			return false;
		}
		
		return ((collection != null) || (resource != null));
    	
    }
    
    public boolean _renameTo(File dest) {
    	final XmldbURI destinationPath = ((Resource)dest).uri;

    	DBBroker broker = null; 
		BrokerPool db = null;
		TransactionManager tm;

		try {
			try {
				db = BrokerPool.getInstance();
				broker = db.get(null);
			} catch (final EXistException e) {
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
	    		final DocumentImpl doc = source.getDocument(broker, uri.lastSegment());
	            if(doc == null) {
	                return false;
	            }
	            destination = broker.openCollection(destinationPath.removeLastSegment(), Lock.WRITE_LOCK);
	            if(destination == null) {
	                return false;
	            }
	            
	            newName = destinationPath.lastSegment();
	
	            transaction = tm.beginTransaction();
	            broker.moveResource(transaction, doc, destination, newName);
	            tm.commit(transaction);
	            return true;
	            
	        } catch ( final Exception e ) {
	        	e.printStackTrace();
	        	if (transaction != null) {tm.abort(transaction);}
	        	return false;
	        } finally {
                tm.close(transaction);
	        	if(source != null) {source.release(Lock.WRITE_LOCK);}
	        	if(destination != null) {destination.release(Lock.WRITE_LOCK);}
	        }
        } finally {
        	if (db != null)
        		{db.release( broker );}
        }
    }
    
    public boolean renameTo(File dest) {
    	
//    	System.out.println("rename from "+uri+" to "+dest.getPath());
    	
        final XmldbURI destinationPath = ((Resource)dest).uri;

        DBBroker broker = null; 
        BrokerPool db = null;
        TransactionManager tm;

        try {
            try {
                db = BrokerPool.getInstance();
                broker = db.get(null);
            } catch (final EXistException e) {
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
                final DocumentImpl doc = source.getDocument(broker, uri.lastSegment());
                if(doc == null) {
                    return false;
                }
                destination = broker.openCollection(destinationPath.removeLastSegment(), Lock.WRITE_LOCK);
                if(destination == null) {
                    return false;
                }
                
                newName = destinationPath.lastSegment();
    
                transaction = tm.beginTransaction();
                moveResource(broker, transaction, doc, source, destination, newName);

//                resource = null;
//                collection = null;
//                initialized = false;
//                uri = ((Resource)dest).uri;

                tm.commit(transaction);
                return true;
                
            } catch ( final Exception e ) {
                e.printStackTrace();
                if (transaction != null) {tm.abort(transaction);}
                return false;
            } finally {
                tm.close(transaction);
                if(source != null) {source.release(Lock.WRITE_LOCK);}
                if(destination != null) {destination.release(Lock.WRITE_LOCK);}
            }
        } finally {
            if (db != null)
                {db.release( broker );}
        }
    }
    
    private synchronized File serialize(final DBBroker broker, final DocumentImpl doc) throws IOException {
    	if (file != null)
    		{throw new IOException(doc.getFileURI().toString()+" locked.");}
    		
    	try {
			final Serializer serializer = broker.getSerializer();
			serializer.setUser(broker.getSubject());
			serializer.setProperties(XML_OUTPUT_PROPERTIES);
			
            file = File.createTempFile("eXist-resource-", ".xml");
            file.deleteOnExit();

            final Writer w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
			
			serializer.serialize(doc, w);
			w.flush();
			w.close();
			
			return file;
			
    	} catch (final Exception e) {
    		throw new IOException(e);
    	}
    }
    
    protected void freeFile() throws IOException {
    	
    	if (isXML()) {
	    	if (file == null) {
	    	    //XXX: understand why can't throw exception
	    	    //throw new IOException();
	    	    return;
    	    }
	    	
	    	file.delete();
	    	
	    	file = null;
    	}
    }
    
    protected synchronized void uploadTmpFile() throws IOException {
    	if (file == null)
    		{throw new IOException();}
    	
        DBBroker broker = null; 
        BrokerPool db = null;
        TransactionManager tm = null;
        Txn txn = null;

        try {
            try {
                db = BrokerPool.getInstance();
                broker = db.get(null);
            } catch (final EXistException e) {
                throw new IOException(e);
            }
    
            tm = db.getTransactionManager();
            txn = tm.beginTransaction();

            FileInputSource is = new FileInputSource(file);
	        
            final IndexInfo info = collection.validateXMLResource(txn, broker, uri.lastSegment(), is);
//	        info.getDocument().getMetadata().setMimeType(mimeType.getName());
	
	        is = new FileInputSource(file);
	        collection.store(txn, broker, info, is, false);

            tm.commit(txn);
            
        } catch ( final Exception e ) {
            e.printStackTrace();
            if (txn != null) {tm.abort(txn);}
	    } finally {
            tm.close(txn);
	        if (db != null)
	            {db.release( broker );}
	    }
    }

    private void moveResource(DBBroker broker, Txn txn, DocumentImpl doc, Collection source, Collection destination, XmldbURI newName) throws PermissionDeniedException, LockException, IOException, SAXException, EXistException {
        
        final MimeTable mimeTable = MimeTable.getInstance();
        
        final boolean isXML = mimeTable.isXMLContent(newName.toString());
        
        final MimeType mimeType = mimeTable.getContentTypeFor(newName);
        
    	if ( mimeType != null && !mimeType.getName().equals( doc.getMetadata().getMimeType()) ) {
            doc.getMetadata().setMimeType(mimeType.getName());
            broker.storeXMLResource(txn, doc);

            doc = source.getDocument(broker, uri.lastSegment());
        }
        
    	if (isXML) {
            if (doc.getResourceType() == DocumentImpl.XML_FILE) {
            	//XML to XML
                //move to same type as it
                broker.moveResource(txn, doc, destination, newName);

            } else {
                //convert BINARY to XML
                
                final File file = broker.getBinaryFile((BinaryDocument) doc);

                FileInputSource is = new FileInputSource(file);
                
                final IndexInfo info = destination.validateXMLResource(txn, broker, newName, is);
                info.getDocument().getMetadata().setMimeType(mimeType.getName());

                is = new FileInputSource(file);
                destination.store(txn, broker, info, is, false);
                
                source.removeBinaryResource(txn, broker, doc);
            }
        } else {
            if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
            	//BINARY to BINARY

            	//move to same type as it
                broker.moveResource(txn, doc, destination, newName);
                
            } else {
                //convert XML to BINARY
                // xml file
                final Serializer serializer = broker.getSerializer();
                serializer.setUser(broker.getSubject());
                serializer.setProperties(XML_OUTPUT_PROPERTIES);
                
                File tempFile = null;
                FileInputStream is = null;
				try {
                    tempFile = File.createTempFile("eXist-resource-", ".xml");
                    tempFile.deleteOnExit();
                    
                    final Writer w = new OutputStreamWriter(new FileOutputStream(tempFile), "UTF-8");

                    serializer.serialize(doc, w);
                    w.flush();
                    w.close();
                    
                    is = new FileInputStream(tempFile);
                    
                    final DocumentMetadata meta = doc.getMetadata();
                    
                    final Date created = new Date(meta.getCreated());
                    final Date lastModified = new Date(meta.getLastModified());
    
                    BinaryDocument binary = destination.validateBinaryResource(txn, broker, newName, is, mimeType.getName(), -1, created, lastModified);
                    
                    binary = destination.addBinaryResource(txn, broker, binary, is, mimeType.getName(), -1, created, lastModified);
                    
                    source.removeXMLResource(txn, broker, doc.getFileURI());
                    
                } finally {
                	if (is != null)
                		{is.close();}

                	if (tempFile != null)
                		{tempFile.delete();}
                }
            }
        }
    }
    
    public boolean delete() {
    	DBBroker broker = null; 
		BrokerPool db = null;
		TransactionManager tm;

		try {
			try {
				db = BrokerPool.getInstance();
				broker = db.get(null);
			} catch (final EXistException e) {
				return false;
			}
	
			tm = db.getTransactionManager();
	        Txn txn = null;
	        try {
	            collection = broker.openCollection(uri.removeLastSegment(), Lock.NO_LOCK);
	            if (collection == null) {
	                return false;
	            }
	            // keep the write lock in the transaction
	            //transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
	
	            final DocumentImpl doc = collection.getDocument(broker, uri.lastSegment());
	            if (doc == null) {
	            	return true;
	            }
	            
	            txn = tm.beginTransaction();
	            if(doc.getResourceType() == DocumentImpl.BINARY_FILE)
	                {collection.removeBinaryResource(txn, broker, doc);}
	            else
	                {collection.removeXMLResource(txn, broker, uri.lastSegment());}
	            
	            tm.commit(txn);
	            return true;
	
	        } catch (final Exception e) {
	        	if (txn != null) {tm.abort(txn);}
	            return false;
	        } finally {
                tm.close(txn);
            }
        } finally {
        	if (db != null)
        		{db.release(broker);}

            resource = null;
            collection = null;
            initialized = false;
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
			} catch (final EXistException e) {
				throw new IOException(e);
			}
			
//			if (!uri.startsWith("/db"))
//				uri = XmldbURI.DB.append(uri);
//	
			try {
				if (uri.endsWith("/"))
					{throw new IOException("It collection, but should be resource: "+uri);}
			} catch (final Exception e) {
				throw new IOException(e);
			}
			
			final XmldbURI collectionURI = uri.removeLastSegment();
			collection = broker.getCollection(collectionURI);
			if (collection == null)
				{throw new IOException("Collection not found: "+collectionURI);}
			
			final XmldbURI fileName = uri.lastSegment();
	
//			try {
//				resource = broker.getXMLResource(uri, Lock.READ_LOCK);
//			} catch (final PermissionDeniedException e1) {
//			} finally {
//				if (resource != null) {
//					resource.getUpdateLock().release(Lock.READ_LOCK);
//					collection = resource.getCollection();
//					initialized = true;
//					
//					return false;
//				}
//			}
//			
			try {
				resource = broker.getResource(uri, Lock.READ_LOCK);
			} catch (final PermissionDeniedException e1) {
			} finally {
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
			final Txn transaction = tm.beginTransaction();
	
			InputStream is = null;
			try {
				if (mimeType.isXMLType()) {
					// store as xml resource
					final String str = "<empty/>"; 
					final IndexInfo info = collection.validateXMLResource(transaction, broker, fileName, str);
					info.getDocument().getMetadata().setMimeType(mimeType.getName());
					info.getDocument().getPermissions().setMode(DEFAULT_RESOURCE_PERM);
					collection.store(transaction, broker, info, str, false);
	
				} else {
					// store as binary resource
					is = new ByteArrayInputStream("".getBytes("UTF-8"));
					
					final BinaryDocument blob = new BinaryDocument(db, collection, fileName);
	
					blob.getPermissions().setMode(DEFAULT_RESOURCE_PERM);

					collection.addBinaryResource(transaction, broker, blob, is,
							mimeType.getName(), 0L , new Date(), new Date());
	
				}
				tm.commit(transaction);
			} catch (final Exception e) {
				tm.abort(transaction);
				throw new IOException(e);
			} finally {
                tm.close(transaction);
				closeFile(is);
	
				if (resource != null)
					{resource.getUpdateLock().release(Lock.READ_LOCK);}
			}
			
		} catch (final Exception e) {
			return false;
			
		} finally {
			if (db != null)
				{db.release(broker);}
		}
		
		return true;
    }


    private synchronized void init() throws IOException {
    	if (initialized) {
    		collection = null;
    		resource = null;
    		initialized = false;
		}
    	
    	DBBroker broker = null; 
		BrokerPool db = null;

		try {
			try {
				db = BrokerPool.getInstance();
				broker = db.get(null);
			} catch (final EXistException e) {
				throw new IOException(e);
			}
	
			try {
				//collection
				if (uri.endsWith("/")) {
					collection = broker.getCollection(uri);
					if (collection == null)
						{throw new IOException("Resource not found: "+uri);}
					
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
			} catch (final IOException e) {
				throw e;
			} catch (final Exception e) {
				throw new IOException(e);
			} finally {
				if (resource != null)
					{resource.getUpdateLock().release(Lock.READ_LOCK);}
			}
		} finally {
			if (db != null)
				{db.release(broker);}
		}
		
		initialized = true;
    }
    
    private Permission getPermission() throws IOException {
    	init();
    	
    	if (resource != null) {return resource.getPermissions();}

    	if (collection != null) {return collection.getPermissionsNoLock();}
    	
    	throw new IOException("this never should happen");
    }
    
    private Subject getBrokerUser() throws IOException {
    	DBBroker broker = null; 
		BrokerPool db = null;

		try {
			db = BrokerPool.getInstance();
			broker = db.get(null);
			
			return broker.getSubject();
		} catch (final EXistException e) {
			throw new IOException(e);
		} finally {
			if (db != null)
				{db.release(broker);}
		}
    }
    
    public Reader getReader() throws IOException {
    	final InputStream is = getConnection().getInputStream();
        final BufferedInputStream bis = new BufferedInputStream(is);
        return new InputStreamReader(bis);
    }
    
    public BufferedReader getBufferedReader() throws IOException {
    	return new BufferedReader(getReader());
    }

    private URLConnection connection = null;
    
    private URLConnection getConnection() throws IOException {
    	if (connection == null) {
			BrokerPool db = null;
			DBBroker broker = null;
			try {
				db = BrokerPool.getInstance();
				broker = db.get(null);
				final Subject subject = broker.getSubject();
				
				final URL url = new URL("xmldb:exist://jsessionid:"+subject.getSessionId()+"@"+ uri.toString());
				connection = url.openConnection();
			} catch (final IllegalArgumentException e) {
				throw new IOException(e); 
			} catch (final MalformedURLException e) {
				throw new IOException(e); 
			} catch (final EXistException e) {
				throw new IOException(e); 
			} finally {
				if (db != null)
					{db.release(broker);}
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
    		{System.err.println("BUG: OutputStream in append mode!");}
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
				} catch (final EXistException e) {
					throw new IOException(e);
				}
	
				try {
					if (uri.endsWith("/")) {
						collection = broker.getCollection(uri);
					} else {
						collection = broker.getCollection(uri);
						if (collection == null)
							{collection = broker.getCollection(uri.removeLastSegment());}
					}
					if (collection == null)
						{throw new IOException("Collection not found: "+uri);}
					
					return collection;
				} catch (final Exception e) {
					throw new IOException(e);
				}
			} finally {
				if (db != null)
					{db.release(broker);}
			}
		}

		if (resource == null)
			{return collection;}
		else
			{return resource.getCollection();}
	}
	
    public String[] list() {
    	
    	if (isDirectory()) {
    		
        	DBBroker broker = null; 
    		BrokerPool db = null;

    		try {
    			try {
    				db = BrokerPool.getInstance();
    				broker = db.get(null);
    			} catch (final EXistException e) {
                	return new String[0];
    			}

    	    	final List<String> list = new ArrayList<String>();
    			for (final CollectionEntry entry : collection.getEntries(broker)) {
    				list.add(entry.getUri().lastSegment().toString());
    			}
    	    
    			return list.toArray(new String[list.size()]);

    		} catch (final PermissionDeniedException e) {
            	return new String[0];

			} finally {
            	if (db != null)
            		{db.release( broker );}
            }
    	}
    	
    	return new String[0];
    }

//    public String[] list(FilenameFilter filter) {
//    	throw new IllegalAccessError("not implemeted");
//    }
    
    public File[] listFiles() {
    	if (!isDirectory())
    		{return null;}
    	
    	if (collection == null)
    		{return null;}
    	
    	DBBroker broker = null; 
		BrokerPool db = null;

		try {
			try {
				db = BrokerPool.getInstance();
				broker = db.get(null);
			} catch (final EXistException e) {
				return null;
			}
	
	    	try {
	        	collection.getLock().acquire(Lock.READ_LOCK);
	
	        	final File[] children = new File[collection.getChildCollectionCount(broker) + 
	                                       collection.getDocumentCount(broker)];
	            
	            //collections
	            int j = 0;
	            for (final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext(); j++)
	            	children[j] = new Resource(collection.getURI().append(i.next()));
	
	            //collections
	            final List<XmldbURI> allresources = new ArrayList<XmldbURI>();
	            DocumentImpl doc = null;
	            for (final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
	                doc = i.next();
	                
	                // Include only when (1) locktoken is present or (2)
	                // locktoken indicates that it is not a null resource
	                final LockToken lock = doc.getMetadata().getLockToken();
	                if(lock==null || (!lock.isNullResource()) ){
	                    allresources.add( doc.getURI() );
	                }
	            }
	            
	            // Copy content of list into String array.
	            for(final Iterator<XmldbURI> i = allresources.iterator(); i.hasNext(); j++){
	            	children[j] = new Resource(i.next());
	            }
	            
	            return children;
	        } catch (final LockException e) {
	        	//throw new IOException("Failed to acquire lock on collection '" + uri + "'");
	    		return null;
	        
	        } catch (final Exception e) {
	        	return null;
	        	
		    } finally {
		        collection.release(Lock.READ_LOCK);
		    }
	    
		} catch (final Exception e) {
			return null;
			
		} finally {
	    	if (db != null)
	    		{db.release(broker);}
	    }
    }
    
    public File[] listFiles(FilenameFilter filter) {
    	throw new IllegalAccessError("not implemeted");
    }
    
    public File[] listFiles(FileFilter filter) {
    	throw new IllegalAccessError("not implemeted");
    }
    
    public synchronized long length() {
		try {
			init();
		} catch (final IOException e) {
			return 0L;
		}

    	if (resource != null) {
    		//report size for binary resource only
    		if (resource instanceof BinaryDocument) {
        		return resource.getContentLength();
			}
    	}
    	
    	return 0L;
    }
    
    private static XmldbURI normalize(final XmldbURI uri) {
        return uri.startsWith(XmldbURI.ROOT_COLLECTION_URI)?
                uri:
                uri.prepend(XmldbURI.ROOT_COLLECTION_URI);
    }

    public String getPath() {
    	return normalize(uri).toString();// uri.toString();
    }

    public String getAbsolutePath() {
    	return normalize(uri).toString();// uri.toString();
    }
    
    public boolean isXML() throws IOException {
		init();

    	if (resource != null) {
    		if (resource instanceof BinaryDocument) {
        		return false;
			} else {
				return true;
			}
    	}
    	
    	return false;
    }

	protected File getFile() throws FileNotFoundException {
		if (isDirectory())
			{throw new FileNotFoundException("unsupported operation for collection.");}

		DocumentImpl doc;
		try {
			if (!exists())
				{createNewFile();}
			
			doc = getDocument();
		} catch (final IOException e) {
			throw new FileNotFoundException(e.getMessage());
		}
		
    	DBBroker broker = null; 
		BrokerPool db = null;

		try {
			try {
				db = BrokerPool.getInstance();
				broker = db.get(null);
			} catch (final EXistException e) {
				throw new FileNotFoundException(e.getMessage());
			}
	
			if (doc instanceof BinaryDocument) {
				return broker.getBinaryFile(((BinaryDocument)doc));
				
			} else {
				return serialize(broker, doc);
			}

		} catch (final Exception e) {
			throw new FileNotFoundException(e.getMessage());
			
		} finally {
	    	if (db != null)
	    		{db.release(broker);}
	    }
//		throw new FileNotFoundException("unsupported operation for "+doc.getClass()+".");
	}
	
    public boolean setReadOnly() {
    	try {
			modifyMetadata(new ModifyMetadata() {

				@Override
				public void modify(DocumentImpl resource) throws IOException {
					Permission perm = resource.getPermissions();
					try {
                        perm.setMode(perm.getMode() | (READ << 6) & ~(WRITE << 6));
                    } catch (PermissionDeniedException e) {
                        throw new IOException(e);
                    }
				}

				@Override
				public void modify(Collection collection) throws IOException {
					Permission perm = collection.getPermissionsNoLock();
					try {
                        perm.setMode(perm.getMode() | (READ << 6) & ~(WRITE << 6));
                    } catch (PermissionDeniedException e) {
                        throw new IOException(e);
                    }
				}
				
			});
		} catch (IOException e) {
			return false;
		}

    	return true;
    }
    
    public boolean setExecutable(boolean executable, boolean ownerOnly) {
    	try {
			modifyMetadata(new ModifyMetadata() {

				@Override
				public void modify(DocumentImpl resource) throws IOException {
					Permission perm = resource.getPermissions();
					try {
                        perm.setMode(perm.getMode() | (EXECUTE << 6));
                    } catch (PermissionDeniedException e) {
                        throw new IOException(e);
                    }
				}

				@Override
				public void modify(Collection collection) throws IOException {
					Permission perm = collection.getPermissionsNoLock();
					try {
                        perm.setMode(perm.getMode() | (EXECUTE << 6));
                    } catch (PermissionDeniedException e) {
                        throw new IOException(e);
                    }
				}
				
			});
		} catch (IOException e) {
			return false;
		}

    	return true;
    }
    
    public boolean canExecute() {
		try {
			return getPermission().validate(getBrokerUser(), EXECUTE);
		} catch (final IOException e) {
			return false;
		}
    }

    
    public boolean canRead() {
		try {
			return getPermission().validate(getBrokerUser(), READ);
		} catch (final IOException e) {
			return false;
		}
    }
    
    long lastModified = 0L;
    
    public boolean setLastModified(final long time) {
    	lastModified = time;
    	try {
			modifyMetadata(new ModifyMetadata() {

				@Override
				public void modify(DocumentImpl resource) throws IOException {
					resource.getMetadata().setLastModified(time);
				}

				@Override
				public void modify(Collection collection) throws IOException {
					throw new IOException("LastModified can't be set for collection.");
				}
				
			});
		} catch (IOException e) {
			return false;
		}

    	return true;
    }
    
    public long lastModified() {
    	try {
			init();
		} catch (final IOException e) {
			return lastModified;
		}
    	
    	if (resource != null) {
    		return resource.getMetadata().getLastModified();
    	}

    	if (collection != null) {
	    	//TODO: need lastModified for collection
	    	return collection.getCreationTime();
    	}
    	return lastModified;
    }
    
    interface ModifyMetadata {
    	public void modify(DocumentImpl resource) throws IOException;

		public void modify(Collection collection) throws IOException;
    }
    
    private void modifyMetadata(ModifyMetadata method) throws IOException {
//    	if (initialized) {return;}
    	
		DBBroker broker = null; 
		BrokerPool db = null;

		try {
			try {
				db = BrokerPool.getInstance();
				broker = db.get(null);
			} catch (final EXistException e) {
				throw new IOException(e);
			}
	
			final TransactionManager tm = db.getTransactionManager();
			Txn txn = null;
			
			try {
				//collection
				if (uri.endsWith("/")) {
					collection = broker.getCollection(uri);
					if (collection == null)
						{throw new IOException("Resource not found: "+uri);}
					
				//resource
				} else {
					resource = broker.getXMLResource(uri, Lock.READ_LOCK);
					if (resource == null) {
						//may be, it's collection ... checking ...
						collection = broker.getCollection(uri);
						if (collection == null) {
							throw new IOException("Resource not found: "+uri);
						}
						
						txn = tm.beginTransaction();

						method.modify(collection);
						broker.saveCollection(txn, collection);
						
						tm.commit(txn);

					} else {
						collection = resource.getCollection();

						txn = tm.beginTransaction();
						
						method.modify(resource);
			            broker.storeMetadata(txn, resource);
						
						tm.commit(txn);
					}
				}
			} catch (final IOException e) {
				if (txn != null) {
					tm.abort(txn);
				}
				throw e;
			} catch (final Exception e) {
				if (txn != null) {
					tm.abort(txn);
				}
				throw new IOException(e);
			} finally {
                tm.close(txn);
				if (resource != null)
					{resource.getUpdateLock().release(Lock.READ_LOCK);}
			}
		} finally {
			if (db != null)
				{db.release(broker);}
		}
		
		initialized = true;
    }
}
