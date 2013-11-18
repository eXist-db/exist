package org.exist.soap;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.QName;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.Account;
import org.exist.security.internal.AccountImpl;
import org.exist.security.internal.aider.UserAider;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.Occurrences;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *  Provides the actual implementations for the methods defined in
 * {@link org.exist.soap.Admin}.
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 */
public class AdminSoapBindingImpl implements org.exist.soap.Admin {
    
    private static Logger LOG = Logger.getLogger(Admin.class.getName());
    
    private BrokerPool pool;
    
    /**  Constructor for the AdminSoapBindingImpl object */
    public AdminSoapBindingImpl() {
        try {
            pool = BrokerPool.getInstance();
        } catch (final Exception e) {
            throw new RuntimeException("failed to initialize broker pool");
        }
    }
    
    @Override
    public java.lang.String connect(java.lang.String userId, java.lang.String password) throws java.rmi.RemoteException {
    	try {
    		final Subject u = pool.getSecurityManager().authenticate(userId, password);

            LOG.debug("user " + userId + " connected");
            
            return SessionManager.getInstance().createSession(u);
		} catch (final Exception e) {
            throw new RemoteException(e.getMessage());
		}
    }
    
    @Override
    public void disconnect(java.lang.String sessionId) throws java.rmi.RemoteException {
        final SessionManager manager = SessionManager.getInstance();
        final Session session = manager.getSession(sessionId);
        if (session != null) {
            LOG.debug("disconnecting session " + sessionId);
            manager.disconnect(sessionId);
        }
    }
    
    @Override
    public boolean createCollection(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
    	try {
    		return createCollection(sessionId,XmldbURI.xmldbUriFor(path));
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public boolean createCollection(java.lang.String sessionId, XmldbURI path) throws java.rmi.RemoteException {
        final Session session = getSession(sessionId);
        DBBroker broker = null;
        final TransactionManager transact = pool.getTransactionManager();
        final Txn txn = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
            LOG.debug("creating collection " + path);
            final org.exist.collections.Collection coll =
                    broker.getOrCreateCollection(txn, path);
            if (coll == null) {
                LOG.debug("failed to create collection");
                return false;
            }
            broker.saveCollection(txn, coll);
            transact.commit(txn);
            broker.flush();
            broker.sync(Sync.MINOR_SYNC);
            return true;
        } catch (final Exception e) {
            transact.abort(txn);
            LOG.debug(e.getMessage(), e);
            throw new RemoteException(e.getMessage());
        } finally {
            transact.close(txn);
            pool.release(broker);
        }
    }
    
    @Override
    public boolean removeCollection(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
    	try {
    		return removeCollection(sessionId,XmldbURI.xmldbUriFor(path));
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public boolean removeCollection(java.lang.String sessionId, XmldbURI path) throws java.rmi.RemoteException {
        final Session session = getSession(sessionId);
        DBBroker broker = null;
        final TransactionManager transact = pool.getTransactionManager();
        final Txn txn = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
            final Collection collection = broker.getCollection(path);
            if(collection == null) {
                transact.abort(txn);
                return false;
            }
            final boolean removed = broker.removeCollection(txn, collection);
            transact.commit(txn);
            return removed;
        } catch (final Exception e) {
            transact.abort(txn);
            LOG.debug(e.getMessage(), e);
            throw new RemoteException(e.getMessage(), e);
        } finally {
            transact.close(txn);
            pool.release(broker);
        }
    }
    
    @Override
    public boolean removeDocument(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
    	try {
    		return removeDocument(sessionId,XmldbURI.xmldbUriFor(path));
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid document URI",e);
    	}
    }
    public boolean removeDocument(java.lang.String sessionId, XmldbURI path) throws java.rmi.RemoteException {
        final Session session = getSession(sessionId);
        DBBroker broker = null;
        final TransactionManager transact = pool.getTransactionManager();
        final Txn txn = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
            final XmldbURI collectionUri = path.removeLastSegment();
            final XmldbURI docUri = path.lastSegment();
            if (collectionUri==null || docUri==null) {
                transact.abort(txn);
                throw new EXistException("Illegal document path");
            }
            final Collection collection = broker.getCollection(collectionUri);
            if (collection == null) {
                transact.abort(txn);
                throw new EXistException(
                        "Collection " + collectionUri + " not found");
            }
            final DocumentImpl doc = collection.getDocument(broker, docUri);
            if(doc == null)
                {throw new EXistException("Document " + docUri + " not found");}
            
            if(doc.getResourceType() == DocumentImpl.BINARY_FILE)
                {collection.removeBinaryResource(txn, broker, doc);}
            else
                {collection.removeXMLResource(txn, broker, docUri);}
            
            transact.commit(txn);
            return true;
        } catch (final Exception e) {
            transact.abort(txn);
            LOG.debug(e.getMessage(), e);
            throw new RemoteException(e.getMessage(), e);
        } finally {
            transact.close(txn);
            pool.release(broker);
        }
    }
    
    @Override
    public void store(java.lang.String sessionId, byte[] data, java.lang.String encoding, java.lang.String path, boolean replace) throws java.rmi.RemoteException {
    	try {
    		store(sessionId,data,encoding,XmldbURI.xmldbUriFor(path),replace);
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid document URI",e);
    	}
    }
    public void store(java.lang.String sessionId, byte[] data, java.lang.String encoding, XmldbURI path, boolean replace) throws java.rmi.RemoteException {
        final Session session = getSession(sessionId);
        DBBroker broker = null;
        final TransactionManager transact = pool.getTransactionManager();
        final Txn txn = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
            final XmldbURI collectionUri = path.removeLastSegment();
            final XmldbURI docUri = path.lastSegment();
            if (collectionUri==null || docUri==null) {
                transact.abort(txn);
                throw new EXistException("Illegal document path");
            }
            final Collection collection = broker.getCollection(collectionUri);
            if (collection == null) {
                transact.abort(txn);
                throw new EXistException("Collection " + collectionUri + " not found");
            }
            if(!replace) {
                final DocumentImpl old = collection.getDocument(broker, docUri);
                if(old != null) {
                    transact.abort(txn);
                    throw new RemoteException("Document exists and overwrite is not allowed");
                }
            }
            final long startTime = System.currentTimeMillis();
// TODO check XML/Binary resource
//          IndexInfo info = collection.validate(txn, broker, path, new InputSource(new ByteArrayInputStream(data)));
            final IndexInfo info = collection.validateXMLResource(txn, broker, docUri, new InputSource(new ByteArrayInputStream(data)));
            info.getDocument().getMetadata().setMimeType(MimeType.XML_TYPE.getName());
            collection.store(txn, broker, info, new InputSource(new ByteArrayInputStream(data)), false);
            transact.commit(txn);
            LOG.debug(
                    "parsing "
                    + path
                    + " took "
                    + (System.currentTimeMillis() - startTime)
                    + "ms.");
        } catch (final Exception e) {
            transact.abort(txn);
            LOG.debug(e.getMessage(), e);
            throw new RemoteException(e.getMessage(), e);
        } finally {
            transact.close(txn);
            pool.release(broker);
        }
    }
    
    private Session getSession(String id) throws java.rmi.RemoteException {
        final Session session = SessionManager.getInstance().getSession(id);
        if (session == null)
            {throw new java.rmi.RemoteException(
                    "Session is invalid or timed out");}
        return session;
    }
    
    @Override
    public int xupdate(java.lang.String sessionId, java.lang.String collectionName, java.lang.String xupdate) throws java.rmi.RemoteException {
    	try {
    		return xupdate(sessionId,XmldbURI.xmldbUriFor(collectionName), xupdate);
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public int xupdate(java.lang.String sessionId, XmldbURI collectionName, java.lang.String xupdate) throws java.rmi.RemoteException {
        DBBroker broker = null;
        final Session session = getSession(sessionId);
        final TransactionManager transact = pool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
            final Collection collection = broker.getCollection(collectionName);
            if (collection == null) {
                transact.abort(transaction);
                throw new RemoteException(
                        "collection " + collectionName + " not found");
            }
            final DocumentSet docs =
                    collection.allDocs(broker, new DefaultDocumentSet(), true);
            final XUpdateProcessor processor =
                    new XUpdateProcessor(broker, docs, AccessContext.SOAP);
            final Modification modifications[] =
                    processor.parse(new InputSource(new StringReader(xupdate)));
            long mods = 0;
            for (int i = 0; i < modifications.length; i++) {
                mods += modifications[i].process(transaction);
                broker.flush();
            }
            transact.commit(transaction);
            return (int) mods;
        } catch (final ParserConfigurationException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (final IOException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (final EXistException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (final SAXException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (final XPathException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (final LockException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } finally {
            transact.close(transaction);
            pool.release(broker);
        }
    }
    
    @Override
    public int xupdateResource(java.lang.String sessionId, java.lang.String documentName, java.lang.String xupdate) throws java.rmi.RemoteException {
    	try {
    		return xupdateResource(sessionId,XmldbURI.xmldbUriFor(documentName), xupdate);
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid document URI",e);
    	}
    }
    public int xupdateResource(java.lang.String sessionId, XmldbURI documentName, java.lang.String xupdate) throws java.rmi.RemoteException {
        DBBroker broker = null;
        final Session session = getSession(sessionId);
        final TransactionManager transact = pool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
// TODO check XML/Binary resource
//            DocumentImpl doc = (DocumentImpl)broker.getDocument(documentName);
            final DocumentImpl doc = broker.getXMLResource(documentName, Permission.READ);
            if (doc == null) {
                transact.abort(transaction);
                throw new RemoteException(
                        "document " + documentName + " not found");
            }
            final MutableDocumentSet docs = new DefaultDocumentSet();
            docs.add(doc);
            final XUpdateProcessor processor =
                    new XUpdateProcessor(broker, docs, AccessContext.SOAP);
            final Modification modifications[] =
                    processor.parse(new InputSource(new StringReader(xupdate)));
            long mods = 0;
            for (int i = 0; i < modifications.length; i++) {
                mods += modifications[i].process(transaction);
                broker.flush();
            }
            transact.commit(transaction);
            return (int) mods;
        } catch (final ParserConfigurationException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (final IOException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (final EXistException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (final SAXException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (final XPathException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (final LockException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } finally {
            transact.close(transaction);
            pool.release(broker);
        }
        
    }
    
    @Override
    public void storeBinary(java.lang.String sessionId, byte[] data, java.lang.String path, java.lang.String mimeType, boolean replace) throws java.rmi.RemoteException {
    	try {
    		storeBinary(sessionId,data,XmldbURI.xmldbUriFor(path), mimeType, replace);
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid document URI",e);
    	}
    }
    public void storeBinary(java.lang.String sessionId, byte[] data, XmldbURI path, java.lang.String mimeType, boolean replace) throws java.rmi.RemoteException {
        DBBroker broker = null;
        final Session session = getSession(sessionId);
        Collection collection = null;
        final TransactionManager transact = pool.getTransactionManager();
        final Txn txn = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
            final XmldbURI collectionUri = path.removeLastSegment();
            final XmldbURI docUri = path.lastSegment();
            if (collectionUri==null || docUri==null) {
                transact.abort(txn);
                throw new EXistException("Illegal document path");
            }
            collection = broker.openCollection(collectionUri, Lock.WRITE_LOCK);
            if (collection == null)
                {throw new EXistException("Collection " + collectionUri
                        + " not found");}
            if (!replace) {
                final DocumentImpl old = collection.getDocument(broker, docUri);
                if (old != null)
                    {throw new PermissionDeniedException(
                            "Old document exists and overwrite is not allowed");}
            }
            LOG.debug("Storing binary resource to collection " + collection.getURI());
            
            /*DocumentImpl doc = */
            collection.addBinaryResource(txn, broker, docUri, data, mimeType);
//            if (created != null)
//                doc.setCreated(created.getTime());
//            if (modified != null)
//                doc.setLastModified(modified.getTime());
            transact.commit(txn);
        } catch (final Exception e) {
            transact.abort(txn);
            throw new RemoteException(e.getMessage(), e);
        } finally {
            transact.close(txn);
            if(collection != null)
                {collection.release(Lock.WRITE_LOCK);}
            pool.release(broker);
        }
//        documentCache.clear();
//        return doc != null;
    }
    
    @Override
    public byte[] getBinaryResource(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
    	try {
    		return getBinaryResource(sessionId,XmldbURI.xmldbUriFor(path));
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid document URI",e);
    	}
    }
    public byte[] getBinaryResource(java.lang.String sessionId, XmldbURI name) throws java.rmi.RemoteException {
        DBBroker broker = null;
        final Session session = getSession(sessionId);
        DocumentImpl doc = null;
        try {
            broker = pool.get(session.getUser());
            // TODO check XML/Binary resource
            doc = broker.getXMLResource(name, Lock.READ_LOCK);
            if (doc == null)
                {throw new EXistException("Resource " + name + " not found");}
            if (doc.getResourceType() != DocumentImpl.BINARY_FILE)
                {throw new EXistException("Document " + name
                        + " is not a binary resource");}
            if(!doc.getPermissions().validate(session.getUser(), Permission.READ))
                {throw new PermissionDeniedException("Insufficient privileges to read resource");}
            final InputStream is = broker.getBinaryResource((BinaryDocument) doc);
            final long resourceSize = broker.getBinaryResourceSize((BinaryDocument) doc);
            if(resourceSize > Integer.MAX_VALUE)
                {throw new RemoteException("Resource too big to be read using this port.");}
            final byte [] data = new byte[(int)resourceSize];
            is.read(data);
            is.close();
            return data;
        } catch (final Exception ex) {
            throw new RemoteException(ex.getMessage());
        } finally {
            if(doc != null)
                {doc.getUpdateLock().release(Lock.READ_LOCK);}
            pool.release(broker);
        }
    }
    
    @Override
    public org.exist.soap.CollectionDesc getCollectionDesc(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
    	try {
    		return getCollectionDesc(sessionId,XmldbURI.xmldbUriFor(path));
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public org.exist.soap.CollectionDesc getCollectionDesc(java.lang.String sessionId, XmldbURI collectionName) throws java.rmi.RemoteException {
        DBBroker broker = null;
        final Session session = getSession(sessionId);
        Collection collection = null;
        try {
            broker = pool.get(session.getUser());
            if (collectionName == null)
                {collectionName = XmldbURI.ROOT_COLLECTION_URI;}
            
            collection = broker.openCollection(collectionName, Lock.READ_LOCK);
            if (collection == null)
                {throw new EXistException("collection " + collectionName
                        + " not found!");}
            final CollectionDesc desc = new CollectionDesc();
            final List<DocumentDesc> docs = new ArrayList<DocumentDesc>();
            final List<String> collections = new ArrayList<String>();
            if (collection.getPermissionsNoLock().validate(session.getUser(), Permission.READ)) {
                DocumentImpl doc;
//              Hashtable hash;
                Permission perms;
                for (final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
                    doc = i.next();
                    perms = doc.getPermissions();
                    final DocumentDesc dd = new DocumentDesc();
//                    hash = new Hashtable(4);
                    dd.setName(doc.getFileURI().toString());
                    dd.setOwner(perms.getOwner().getName());
                    dd.setGroup(perms.getGroup().getName());
                    dd.setPermissions(perms.getMode());
                    dd.setType(doc.getResourceType() == DocumentImpl.BINARY_FILE
                            ? DocumentType.BinaryResource
                            : DocumentType.XMLResource);
                    docs.add(dd);
                }
                for(final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext();) {
                    collections.add(i.next().toString());
                }
            }
            Permission perms = collection.getPermissionsNoLock();
            desc.setCollections(new Strings(collections.toArray(new String[collections.size()])));
            desc.setDocuments(new DocumentDescs(docs.toArray(new DocumentDesc[docs.size()])));
            desc.setName(collection.getURI().toString());
            desc.setCreated(collection.getCreationTime());
            desc.setOwner(perms.getOwner().getName());
            desc.setGroup(perms.getGroup().getName());
            desc.setPermissions(perms.getMode());
            return desc;
        } catch (final Exception ex){
            throw new RemoteException(ex.getMessage());
        } finally {
            if(collection != null)
                {collection.release(Lock.READ_LOCK);}
            pool.release(broker);
        }
    }
    
    @Override
    public void setPermissions(java.lang.String sessionId, java.lang.String resource, java.lang.String owner, java.lang.String ownerGroup, int permissions) throws java.rmi.RemoteException {
    	try {
    		setPermissions(sessionId,XmldbURI.xmldbUriFor(resource),owner,ownerGroup,permissions);
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public void setPermissions(java.lang.String sessionId, XmldbURI resource, java.lang.String owner, java.lang.String ownerGroup, int permissions) throws java.rmi.RemoteException {
        DBBroker broker = null;
        final Session session = getSession(sessionId);
        Collection collection = null;
        DocumentImpl doc = null;
        final TransactionManager transact = pool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
            final org.exist.security.SecurityManager manager = pool.getSecurityManager();
            collection = broker.openCollection(resource, Lock.WRITE_LOCK);
            if (collection == null) {
                // TODO check XML/Binary resource
                doc = broker.getXMLResource(resource, Lock.WRITE_LOCK);
                if (doc == null)
                    {throw new RemoteException("document or collection "
                            + resource + " not found");}
                LOG.debug("changing permissions on document " + resource);
                final Permission perm = doc.getPermissions();
                if (perm.getOwner().equals(session.getUser())
                || manager.hasAdminPrivileges(session.getUser())) {
                    if (owner != null) {
                        perm.setOwner(owner);
                        perm.setGroup(ownerGroup);
                    }
                    perm.setMode(permissions);
// TODO check XML/Binary resource
//                    broker.storeDocument(transaction, doc);
                    broker.storeXMLResource(transaction, doc);
                    transact.commit(transaction);
                    broker.flush();
                    return;
//                    return true;
                }
                transact.abort(transaction);
                throw new PermissionDeniedException("not allowed to change permissions");
            }
            LOG.debug("changing permissions on collection " + resource);
            final Permission perm = collection.getPermissionsNoLock();
            if (perm.getOwner().equals(session.getUser())
            || manager.hasAdminPrivileges(session.getUser())) {
                perm.setMode(permissions);
                if (owner != null) {
                    perm.setOwner(owner);
                    perm.setGroup(ownerGroup);
                }
                transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
                broker.saveCollection(transaction, collection);
                transact.commit(transaction);
                broker.flush();
                return;
            }
            transact.abort(transaction);
            throw new PermissionDeniedException("not allowed to change permissions");
        } catch (final IOException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage());
        } catch (final PermissionDeniedException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage());
        } catch (final TransactionException e) {
            throw new RemoteException(e.getMessage());
        } catch (final EXistException e) {
            throw new RemoteException(e.getMessage());
        } catch (final TriggerException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage());
		} finally {
            transact.close(transaction);
            if(doc != null)
                {doc.getUpdateLock().release(Lock.WRITE_LOCK);}
            pool.release(broker);
        }
    }
    
    private void moveOrCopyResource(String sessionId, String docPath, String destinationPath,
            String newName, boolean move)
    		throws RemoteException {
    	try {
    		moveOrCopyResource(sessionId,XmldbURI.xmldbUriFor(docPath),XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName),move);
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    private void moveOrCopyResource(String sessionId, XmldbURI docPath, XmldbURI destinationPath,
            XmldbURI newName, boolean move)
            throws RemoteException {
        final TransactionManager transact = pool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        final Session session = getSession(sessionId);
        Collection collection = null;
        Collection destination = null;
        DocumentImpl doc = null;
        try {
            broker = pool.get(session.getUser());
            final XmldbURI collectionUri = docPath.removeLastSegment();
            final XmldbURI docUri = docPath.lastSegment();
            if (collectionUri==null || docUri==null) {
                transact.abort(transaction);
                throw new EXistException("Illegal document path");
            }
            collection = broker.openCollection(collectionUri, move ? Lock.WRITE_LOCK : Lock.READ_LOCK);
            if (collection == null) {
                transact.abort(transaction);
                throw new RemoteException("Collection " + collectionUri
                        + " not found");
            }
            doc = collection.getDocumentWithLock(broker, docUri, Lock.WRITE_LOCK);
            if(doc == null) {
                transact.abort(transaction);
                throw new RemoteException("Document " + docUri + " not found");
            }
            
            // get destination collection
            destination = broker.openCollection(destinationPath, Lock.WRITE_LOCK);
            if(destination == null) {
                transact.abort(transaction);
                throw new RemoteException("Destination collection " + destinationPath + " not found");
            }
            if(move)
// TODO check XML/Binary resource
//                broker.moveResource(transaction, doc, destination, newName);
                {broker.moveResource(transaction, doc, destination, newName);}
            else
// TODO check XML/Binary resource
//                broker.copyResource(transaction, doc, destination, newName);
                {broker.copyResource(transaction, doc, destination, newName);}
            transact.commit(transaction);
//            documentCache.clear();
            return;
        } catch (final LockException e) {
            transact.abort(transaction);
            throw new RemoteException("Could not acquire lock on document " + docPath);
        } catch (final PermissionDeniedException e) {
            transact.abort(transaction);
            throw new RemoteException("Could not move/copy document " + docPath);
        } catch (final IOException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage());
        } catch (final TransactionException e) {
            throw new RemoteException("Error commiting transaction " + e.getMessage());
        } catch (final EXistException e) {
            throw new RemoteException(e.getMessage());
        } catch (final TriggerException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage());
		} finally {
            transact.close(transaction);
            if(destination != null)
                {destination.release(Lock.WRITE_LOCK);}
            if(doc != null)
                {doc.getUpdateLock().release(Lock.WRITE_LOCK);}
            if(collection != null)
                {collection.release(move ? Lock.WRITE_LOCK : Lock.READ_LOCK);}
            pool.release(broker);
        }
    }
    
    private boolean moveOrCopyCollection(String sessionId, String collectionPath, String destinationPath,
            String newName, boolean move)
    		throws EXistException, PermissionDeniedException, RemoteException {
    	try {
    		return moveOrCopyCollection(sessionId,XmldbURI.xmldbUriFor(collectionPath),XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName),move);
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    private boolean moveOrCopyCollection(String sessionId, XmldbURI collectionPath, XmldbURI destinationPath,
    		XmldbURI newName, boolean move)
            throws EXistException, PermissionDeniedException, RemoteException {
        final TransactionManager transact = pool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        final Session session = getSession(sessionId);
        Collection collection = null;
        Collection destination = null;
        try {
            final Subject user = session.getUser();
            broker = pool.get(user);
            // get source document
            collection = broker.openCollection(collectionPath, move ? Lock.WRITE_LOCK : Lock.READ_LOCK);
            if (collection == null) {
                transact.abort(transaction);
                throw new EXistException("Collection " + collectionPath
                        + " not found");
            }
            // get destination collection
            destination = broker.openCollection(destinationPath, Lock.WRITE_LOCK);
            if(destination == null) {
                transact.abort(transaction);
                throw new EXistException("Destination collection " + destinationPath + " not found");
            }
            if(move)
                {broker.moveCollection(transaction, collection, destination, newName);}
            else
                {broker.copyCollection(transaction, collection, destination, newName);}
            transact.commit(transaction);
//            documentCache.clear();
            return true;
        } catch (final IOException e) {
        	transact.abort(transaction);
            throw new RemoteException(e.getMessage());            
        } catch (final LockException e) {
        	transact.abort(transaction);
            throw new PermissionDeniedException(e.getMessage());
        } catch (final TriggerException e) {
        	transact.abort(transaction);
            throw new RemoteException(e.getMessage());            
		} finally {
            transact.close(transaction);
            if(collection != null)
                {collection.release(move ? Lock.WRITE_LOCK : Lock.READ_LOCK);}
            if(destination != null)
                {destination.release(Lock.WRITE_LOCK);}
            pool.release(broker);
        }
    }
    
    @Override
    public void copyResource(java.lang.String sessionId, java.lang.String docPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException {
        moveOrCopyResource(sessionId,docPath,destinationPath,newName,false);
    }
    
    @Override
    public void copyCollection(java.lang.String sessionId, java.lang.String collectionPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException {
        try {
            moveOrCopyCollection(sessionId,collectionPath,destinationPath,newName,false);
        } catch (final RemoteException e) {
            throw new RemoteException(e.getMessage());
        } catch (final EXistException e) {
            throw new RemoteException(e.getMessage());
        } catch (final PermissionDeniedException e) {
            throw new RemoteException(e.getMessage());
        }
    }
    
    @Override
    public void setUser(java.lang.String sessionId, java.lang.String name, java.lang.String password, org.exist.soap.Strings groups, java.lang.String home) throws java.rmi.RemoteException {
        if (password.length() == 0) {
            password = null;
        }
        
        final Session session = getSession(sessionId);
        final Subject user = session.getUser();
        
        final org.exist.security.SecurityManager manager = pool.getSecurityManager();
        if(name.equals(org.exist.security.SecurityManager.GUEST_USER) && (!manager.hasAdminPrivileges(user))) {
            throw new RemoteException("guest user cannot be modified");
        }
        
        DBBroker broker = null;
        try {
            broker = pool.get(user);

		    Account u;
		    if(!manager.hasAccount(name)) {
		        if(!manager.hasAdminPrivileges(user)) {
		            throw new RemoteException("not allowed to create user");
		        }
		      
		        u = new UserAider(name);
		        ((UserAider)u).setPasswordDigest(password);
		    } else {
		        u = manager.getAccount(name);
		        if(!(u.getName().equals(user.getName()) || manager.hasAdminPrivileges(user))) {
		            throw new RemoteException("you are not allowed to change this user");
		        }
		        ((AccountImpl)u).setPassword(password);
                    }
        
            for(final String groupName : groups.getElements()) {
                if (!u.hasGroup(groupName)) {

                    if(!manager.hasAdminPrivileges(user)) {
                        throw new RemoteException("User is not allowed to add groups");
                    }

                    if(!manager.hasGroup(groupName)){
                        manager.addGroup(groupName);
                    }
                    u.addGroup(groupName);
                }
            }
            
            manager.addAccount(u);
            
        } catch (final PermissionDeniedException e) {
            throw new RemoteException(e.getMessage());
        } catch (final EXistException e) {
            throw new RemoteException(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    
    @Override
    public org.exist.soap.UserDesc getUser(java.lang.String sessionId, java.lang.String user) throws java.rmi.RemoteException {
        final Session session = getSession(sessionId);
        
        DBBroker broker = null;
        try {
            broker = pool.get(session.getUser());

	        final Account u = pool.getSecurityManager().getAccount(user);
	        if (u == null)
	            {throw new RemoteException("user " + user + " does not exist");}
	        final UserDesc desc = new UserDesc();
	        desc.setName(u.getName());
	/*
	        Vector groups = new Vector();
	        for (Iterator i = u.getGroups(); i.hasNext(); )
	            groups.addElement(i.next());
	        desc.setGroups(new Strings((String[])groups.toArray(new String[groups.size()])));
	 */
	        desc.setGroups(new Strings(u.getGroups()));
	        return desc;
        } catch (final EXistException e) {
            throw new RemoteException(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    
    @Override
    public void removeUser(java.lang.String sessionId, java.lang.String name) throws java.rmi.RemoteException {
        final Subject user = getSession(sessionId).getUser();
        final org.exist.security.SecurityManager manager = pool
                .getSecurityManager();
        if (!manager.hasAdminPrivileges(user))
            {throw new RemoteException(
                    "you are not allowed to remove users");}
        
        DBBroker broker = null;
        try {
        	broker = pool.get(user);
        	
            manager.deleteAccount(name);
        } catch (final Exception e) {
            throw new RemoteException(e.getMessage());
		} finally {
			pool.release(broker);
		}
    }
    
    @Override
    public org.exist.soap.UserDescs getUsers(java.lang.String sessionId) throws java.rmi.RemoteException {
    	final java.util.Collection<Account> users = pool.getSecurityManager().getUsers();
        final UserDesc[] r = new UserDesc[users.size()];
        int i = 0;
        for (final Account user : users) {
            r[i] = new UserDesc();
            r[i].setName(user.getName());
/*
            Vector groups = new Vector();
            for (Iterator j = users[i].getGroups(); j.hasNext(); )
                groups.addElement(j.next());
            r[i].setGroups(new Strings((String[])groups.toArray(new String[groups.size()])));
 */
            r[i].setGroups(new Strings(user.getGroups()));
            
            i++;
        }
        return new UserDescs(r);
    }
    
    @Override
    public org.exist.soap.Strings getGroups(java.lang.String sessionId) throws java.rmi.RemoteException {
        final java.util.Collection<Group> roles = pool.getSecurityManager().getGroups();
        final List<String> v = new ArrayList<String>(roles.size());
        for (final Group role : roles) {
            v.add(role.getName());
        }
        return new Strings(v.toArray(new String[v.size()]));
    }
    
    @Override
    public void moveCollection(java.lang.String sessionId, java.lang.String collectionPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException {
        try {
            moveOrCopyCollection(sessionId,collectionPath,destinationPath,newName,true);
        } catch (final RemoteException e) {
            throw new RemoteException(e.getMessage());
        } catch (final EXistException e) {
            throw new RemoteException(e.getMessage());
        } catch (final PermissionDeniedException e) {
            throw new RemoteException(e.getMessage());
        }
    }
    
    @Override
    public void moveResource(java.lang.String sessionId, java.lang.String docPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException {
        moveOrCopyResource(sessionId,docPath,destinationPath,newName,true);
    }
    
    @Override
    public void lockResource(java.lang.String sessionId, java.lang.String path, java.lang.String userName) throws java.rmi.RemoteException {
    	try {
    		lockResource(sessionId,XmldbURI.xmldbUriFor(path),userName);
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public void lockResource(java.lang.String sessionId, XmldbURI path, java.lang.String userName) throws java.rmi.RemoteException {
        DBBroker broker = null;
        final Session session = getSession(sessionId);
        final Subject user = session.getUser();
        DocumentImpl doc = null;
        final TransactionManager transact = pool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
        try {
            broker = pool.get(user);
// TODO check XML/Binary resource
//            doc = (DocumentImpl) broker.openDocument(path, Lock.WRITE_LOCK);
            doc = broker.getXMLResource(path, Lock.WRITE_LOCK);
            if (doc == null) {
                throw new EXistException("Resource "
                        + path + " not found");
            }
            if (!doc.getPermissions().validate(user, Permission.WRITE))
                {throw new PermissionDeniedException("User is not allowed to lock resource " + path);}
            
            final org.exist.security.SecurityManager manager = pool.getSecurityManager();
            if (!(userName.equals(user.getName()) || manager.hasAdminPrivileges(user)))
                {throw new PermissionDeniedException("User " + user.getName() + " is not allowed " +
                        "to lock the resource for user " + userName);}
            final Account lockOwner = doc.getUserLock();
            if(lockOwner != null && (!lockOwner.equals(user)) && (!manager.hasAdminPrivileges(user)))
                {throw new PermissionDeniedException("Resource is already locked by user " +
                        lockOwner.getName());}
            final Account lo = manager.getAccount(userName);
            doc.setUserLock(lo);
// TODO check XML/Binary resource
//            broker.storeDocument(transaction, doc);
            broker.storeXMLResource(transaction, doc);
            transact.commit(transaction);
            return;
        } catch (final Exception e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage());
        } finally {
            transact.close(transaction);
            if(doc != null)
                {doc.getUpdateLock().release(Lock.WRITE_LOCK);}
            pool.release(broker);
        }
    }
    
    @Override
    public void unlockResource(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
    	try {
    		unlockResource(sessionId,XmldbURI.xmldbUriFor(path));
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public void unlockResource(java.lang.String sessionId, XmldbURI path) throws java.rmi.RemoteException {
        DBBroker broker = null;
        final Session session = getSession(sessionId);
        final Subject user = session.getUser();
        DocumentImpl doc = null;
        final TransactionManager transact = pool.getTransactionManager();
        Txn transaction = null;
        try {
            broker = pool.get(user);
            // TODO check XML/Binary resource
            doc = broker.getXMLResource(path, Lock.WRITE_LOCK);
            if (doc == null)
                {throw new EXistException("Resource "
                        + path + " not found");}
            if (!doc.getPermissions().validate(user, Permission.WRITE))
                {throw new PermissionDeniedException("User is not allowed to lock resource " + path);}
            final org.exist.security.SecurityManager manager = pool.getSecurityManager();
            final Account lockOwner = doc.getUserLock();
            if(lockOwner != null && (!lockOwner.equals(user)) && (!manager.hasAdminPrivileges(user)))
                {throw new PermissionDeniedException("Resource is already locked by user " +
                        lockOwner.getName());}
            transaction = transact.beginTransaction();
            doc.setUserLock(null);
// TODO check XML/Binary resource
//            broker.storeDocument(transaction, doc);
            broker.storeXMLResource(transaction, doc);
            transact.commit(transaction);
            return;
        } catch (final Exception ex){
            transact.abort(transaction);
            throw new RemoteException(ex.getMessage());
        } finally {
            transact.close(transaction);
            if(doc != null)
                {doc.getUpdateLock().release(Lock.WRITE_LOCK);}
            pool.release(broker);
        }
    }
    
    @Override
    public java.lang.String hasUserLock(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
    	try {
    		return hasUserLock(sessionId,XmldbURI.xmldbUriFor(path));
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public java.lang.String hasUserLock(java.lang.String sessionId, XmldbURI path) throws java.rmi.RemoteException {
        DBBroker broker = null;
        final Session session = getSession(sessionId);
        final Subject user = session.getUser();
        DocumentImpl doc = null;
        try {
            broker = pool.get(user);
// TODO check XML/Binary resource
//            doc = (DocumentImpl) broker.openDocument(path, Lock.READ_LOCK);
            doc = broker.getXMLResource(path, Lock.READ_LOCK);
            if (doc == null)
                {throw new EXistException("Resource "
                        + path + " not found");}
            if(!doc.getPermissions().validate(user, Permission.READ))
                {throw new PermissionDeniedException("Insufficient privileges to read resource");}
            final Account u = doc.getUserLock();
            return u == null ? "" : u.getName();
        } catch (final Exception ex) {
            throw new RemoteException(ex.getMessage());
        } finally {
            if(doc != null)
                {doc.getUpdateLock().release(Lock.READ_LOCK);}
            pool.release(broker);
        }
    }
    
    @Override
    public org.exist.soap.Permissions getPermissions(java.lang.String sessionId, java.lang.String resource) throws java.rmi.RemoteException {
    	try {
    		return getPermissions(sessionId,XmldbURI.xmldbUriFor(resource));
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public org.exist.soap.Permissions getPermissions(java.lang.String sessionId, XmldbURI resource) throws java.rmi.RemoteException {
        DBBroker broker = null;
        final Session session = getSession(sessionId);
        final Subject user = session.getUser();
        try {
            broker = pool.get(user);
            Collection collection = null;
            try {
            	collection = broker.openCollection(resource, Lock.READ_LOCK);
	            Permission perm = null;
	            if (collection == null) {
					// TODO check XML/Binary resource
					// DocumentImpl doc = (DocumentImpl) broker.openDocument(resource, Lock.READ_LOCK);
	            	DocumentImpl doc = null;
	            	try {
	            	    doc = broker.getXMLResource(resource, Lock.READ_LOCK);
		                if (doc == null)
		                    {throw new EXistException("document or collection " + resource + " not found");}
		                perm = doc.getPermissions();
	            	} finally {
	            		if (doc != null)
	            			{doc.getUpdateLock().release(Lock.READ_LOCK);}
	            	}
	            } else {
	                perm = collection.getPermissionsNoLock();
	            }
	            final Permissions p = new Permissions();
	            p.setOwner(perm.getOwner().getName());
	            p.setGroup(perm.getGroup().getName());
	            p.setPermissions(perm.getMode());
	            return p;
            } finally {
            	if (collection != null)
            		{collection.release(Lock.READ_LOCK);}            	
            }
        } catch (final Exception ex) {
            throw new RemoteException(ex.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    
    @Override
    public org.exist.soap.EntityPermissionsList listCollectionPermissions(java.lang.String sessionId, java.lang.String name) throws java.rmi.RemoteException {
    	try {
    		return listCollectionPermissions(sessionId,XmldbURI.xmldbUriFor(name));
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public org.exist.soap.EntityPermissionsList listCollectionPermissions(java.lang.String sessionId, XmldbURI name) throws java.rmi.RemoteException {
        DBBroker broker = null;
        final Session session = getSession(sessionId);
        final Subject user = session.getUser();
        Collection collection = null;
        try {
            broker = pool.get(user);
            collection = broker.openCollection(name, Lock.READ_LOCK);
            if (collection == null)
                {throw new EXistException("Collection " + name + " not found");}
            if (!collection.getPermissionsNoLock().validate(user, Permission.READ))
                {throw new PermissionDeniedException(
                        "not allowed to read collection " + name);}
            final EntityPermissions[] result = new EntityPermissions[collection.getChildCollectionCount(broker)];
            XmldbURI child, path;
            Collection childColl;
            Permission perm;
            int cnt = 0;
            for (final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext(); ) {
                child = i.next();
                path = name.append(child);
                childColl = broker.getCollection(path);
                perm = childColl.getPermissionsNoLock();
                result[cnt] = new EntityPermissions();
                result[cnt].setName(child.toString());
                result[cnt].setOwner(perm.getOwner().getName());
                result[cnt].setGroup(perm.getGroup().getName());
                result[cnt].setPermissions(perm.getMode());
                cnt++;
            }
            return new EntityPermissionsList(result);
        } catch (final Exception ex) {
            throw new RemoteException(ex.getMessage());
        } finally {
            if(collection != null)
                {collection.release(Lock.READ_LOCK);}
            pool.release(broker);
        }
    }
    
    @Override
    public org.exist.soap.EntityPermissionsList listDocumentPermissions(java.lang.String sessionId, java.lang.String name) throws java.rmi.RemoteException {
    	try {
    		return listDocumentPermissions(sessionId,XmldbURI.xmldbUriFor(name));
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public org.exist.soap.EntityPermissionsList listDocumentPermissions(java.lang.String sessionId, XmldbURI name) throws java.rmi.RemoteException {
        DBBroker broker = null;
        final Session session = getSession(sessionId);
        final Subject user = session.getUser();
        Collection collection = null;
        try {
            broker = pool.get(user);
            collection = broker.openCollection(name, Lock.READ_LOCK);
            if (collection == null)
                {throw new EXistException("Collection " + name + " not found");}
            if (!collection.getPermissionsNoLock().validate(user, Permission.READ))
                {throw new PermissionDeniedException(
                        "not allowed to read collection " + name);}
            final EntityPermissions[] result = new EntityPermissions[collection.getDocumentCount(broker)];
            DocumentImpl doc;
            Permission perm;
            int cnt = 0;
            for (final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
                doc = i.next();
                perm = doc.getPermissions();
                result[cnt] = new EntityPermissions();
                result[cnt].setName(doc.getFileURI().toString());
                result[cnt].setOwner(perm.getOwner().getName());
                result[cnt].setGroup(perm.getGroup().getName());
                result[cnt].setPermissions(perm.getMode());
                cnt++;
            }
            return new EntityPermissionsList(result);
        } catch (final Exception ex) {
            throw new RemoteException(ex.getMessage());
        } finally {
            if(collection != null)
                {collection.release(Lock.READ_LOCK);}
            pool.release(broker);
        }
    }
    
    @Override
    public org.exist.soap.IndexedElements getIndexedElements(java.lang.String sessionId, java.lang.String collectionName, boolean inclusive) throws java.rmi.RemoteException {
    	try {
    		return getIndexedElements(sessionId,XmldbURI.xmldbUriFor(collectionName),inclusive);
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public org.exist.soap.IndexedElements getIndexedElements(java.lang.String sessionId, XmldbURI collectionName, boolean inclusive) throws java.rmi.RemoteException {
        DBBroker broker = null;
        final Session session = getSession(sessionId);
        final Subject user = session.getUser();
        Collection collection = null;
        try {
            broker = pool.get(user);
            collection = broker.openCollection(collectionName, Lock.READ_LOCK);
            if (collection == null)
                {throw new EXistException("collection " + collectionName
                        + " not found");}
            final Occurrences occurrences[] = broker.getElementIndex().scanIndexedElements(collection,
                    inclusive);
            final IndexedElement[] result = new IndexedElement[occurrences.length];
            for (int i = 0; i < occurrences.length; i++) {
                final QName qname = (QName)occurrences[i].getTerm();
                result[i] = new IndexedElement(qname.getLocalName(),qname.getNamespaceURI(),
                        qname.getPrefix() == null ? "" : qname.getPrefix(),
                        occurrences[i].getOccurrences());
            }
            return new IndexedElements(result);
        } catch (final Exception ex) {
            throw new RemoteException(ex.getMessage());
        } finally {
            if(collection != null)
                {collection.release(Lock.READ_LOCK);}
            pool.release(broker);
        }
    }
    
}