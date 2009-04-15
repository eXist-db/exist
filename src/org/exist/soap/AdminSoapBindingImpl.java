package org.exist.soap;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.QName;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
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
import java.util.Iterator;
import java.util.Vector;

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
        } catch (Exception e) {
            throw new RuntimeException("failed to initialize broker pool");
        }
    }
    
    public java.lang.String connect(java.lang.String userId, java.lang.String password) throws java.rmi.RemoteException {
        User u = pool.getSecurityManager().getUser(userId);
        if (u == null)
            throw new RemoteException("user " + userId + " does not exist");
        if (!u.validate(password))
            throw new RemoteException("the supplied password is invalid");
        LOG.debug("user " + userId + " connected");
        return SessionManager.getInstance().createSession(u);
    }
    
    public void disconnect(java.lang.String sessionId) throws java.rmi.RemoteException {
        SessionManager manager = SessionManager.getInstance();
        Session session = manager.getSession(sessionId);
        if (session != null) {
            LOG.debug("disconnecting session " + sessionId);
            manager.disconnect(sessionId);
        }
    }
    
    public boolean createCollection(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
    	try {
    		return createCollection(sessionId,XmldbURI.xmldbUriFor(path));
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public boolean createCollection(java.lang.String sessionId, XmldbURI path) throws java.rmi.RemoteException {
        Session session = getSession(sessionId);
        DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
            LOG.debug("creating collection " + path);
            org.exist.collections.Collection coll =
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
        } catch (Exception e) {
            transact.abort(txn);
            LOG.debug(e.getMessage(), e);
            throw new RemoteException(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    
    public boolean removeCollection(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
    	try {
    		return removeCollection(sessionId,XmldbURI.xmldbUriFor(path));
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public boolean removeCollection(java.lang.String sessionId, XmldbURI path) throws java.rmi.RemoteException {
        Session session = getSession(sessionId);
        DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
            Collection collection = broker.getCollection(path);
            if(collection == null) {
                transact.abort(txn);
                return false;
            }
            boolean removed = broker.removeCollection(txn, collection);
            transact.commit(txn);
            return removed;
        } catch (Exception e) {
            transact.abort(txn);
            LOG.debug(e.getMessage(), e);
            throw new RemoteException(e.getMessage(), e);
        } finally {
            pool.release(broker);
        }
    }
    
    public boolean removeDocument(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
    	try {
    		return removeDocument(sessionId,XmldbURI.xmldbUriFor(path));
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid document URI",e);
    	}
    }
    public boolean removeDocument(java.lang.String sessionId, XmldbURI path) throws java.rmi.RemoteException {
        Session session = getSession(sessionId);
        DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
            XmldbURI collectionUri = path.removeLastSegment();
            XmldbURI docUri = path.lastSegment();
            if (collectionUri==null || docUri==null) {
                transact.abort(txn);
                throw new EXistException("Illegal document path");
            }
            Collection collection = broker.getCollection(collectionUri);
            if (collection == null) {
                transact.abort(txn);
                throw new EXistException(
                        "Collection " + collectionUri + " not found");
            }
            DocumentImpl doc = collection.getDocument(broker, docUri);
            if(doc == null)
                throw new EXistException("Document " + docUri + " not found");
            
            if(doc.getResourceType() == DocumentImpl.BINARY_FILE)
                collection.removeBinaryResource(txn, broker, doc);
            else
                collection.removeXMLResource(txn, broker, docUri);
            
            transact.commit(txn);
            return true;
        } catch (Exception e) {
            transact.abort(txn);
            LOG.debug(e.getMessage(), e);
            throw new RemoteException(e.getMessage(), e);
        } finally {
            pool.release(broker);
        }
    }
    
    public void store(java.lang.String sessionId, byte[] data, java.lang.String encoding, java.lang.String path, boolean replace) throws java.rmi.RemoteException {
    	try {
    		store(sessionId,data,encoding,XmldbURI.xmldbUriFor(path),replace);
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid document URI",e);
    	}
    }
    public void store(java.lang.String sessionId, byte[] data, java.lang.String encoding, XmldbURI path, boolean replace) throws java.rmi.RemoteException {
        Session session = getSession(sessionId);
        DBBroker broker = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
            XmldbURI collectionUri = path.removeLastSegment();
            XmldbURI docUri = path.lastSegment();
            if (collectionUri==null || docUri==null) {
                transact.abort(txn);
                throw new EXistException("Illegal document path");
            }
            Collection collection = broker.getCollection(collectionUri);
            if (collection == null) {
                transact.abort(txn);
                throw new EXistException("Collection " + collectionUri + " not found");
            }
            if(!replace) {
                DocumentImpl old = collection.getDocument(broker, docUri);
                if(old != null) {
                    transact.abort(txn);
                    throw new RemoteException("Document exists and overwrite is not allowed");
                }
            }
            long startTime = System.currentTimeMillis();
// TODO check XML/Binary resource
//          IndexInfo info = collection.validate(txn, broker, path, new InputSource(new ByteArrayInputStream(data)));
            IndexInfo info = collection.validateXMLResource(txn, broker, docUri, new InputSource(new ByteArrayInputStream(data)));
            info.getDocument().getMetadata().setMimeType(MimeType.XML_TYPE.getName());
            collection.store(txn, broker, info, new InputSource(new ByteArrayInputStream(data)), false);
            transact.commit(txn);
            LOG.debug(
                    "parsing "
                    + path
                    + " took "
                    + (System.currentTimeMillis() - startTime)
                    + "ms.");
        } catch (Exception e) {
            transact.abort(txn);
            LOG.debug(e.getMessage(), e);
            throw new RemoteException(e.getMessage(), e);
        } finally {
            pool.release(broker);
        }
    }
    
    private Session getSession(String id) throws java.rmi.RemoteException {
        Session session = SessionManager.getInstance().getSession(id);
        if (session == null)
            throw new java.rmi.RemoteException(
                    "Session is invalid or timed out");
        return session;
    }
    
    public int xupdate(java.lang.String sessionId, java.lang.String collectionName, java.lang.String xupdate) throws java.rmi.RemoteException {
    	try {
    		return xupdate(sessionId,XmldbURI.xmldbUriFor(collectionName), xupdate);
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public int xupdate(java.lang.String sessionId, XmldbURI collectionName, java.lang.String xupdate) throws java.rmi.RemoteException {
        DBBroker broker = null;
        Session session = getSession(sessionId);
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
            Collection collection = broker.getCollection(collectionName);
            if (collection == null) {
                transact.abort(transaction);
                throw new RemoteException(
                        "collection " + collectionName + " not found");
            }
            DocumentSet docs =
                    collection.allDocs(broker, new DefaultDocumentSet(), true, true);
            XUpdateProcessor processor =
                    new XUpdateProcessor(broker, docs, AccessContext.SOAP);
            Modification modifications[] =
                    processor.parse(new InputSource(new StringReader(xupdate)));
            long mods = 0;
            for (int i = 0; i < modifications.length; i++) {
                mods += modifications[i].process(transaction);
                broker.flush();
            }
            transact.commit(transaction);
            return (int) mods;
        } catch (ParserConfigurationException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (IOException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (EXistException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (SAXException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (PermissionDeniedException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (XPathException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (LockException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } finally {
            pool.release(broker);
        }
    }
    
    public int xupdateResource(java.lang.String sessionId, java.lang.String documentName, java.lang.String xupdate) throws java.rmi.RemoteException {
    	try {
    		return xupdateResource(sessionId,XmldbURI.xmldbUriFor(documentName), xupdate);
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid document URI",e);
    	}
    }
    public int xupdateResource(java.lang.String sessionId, XmldbURI documentName, java.lang.String xupdate) throws java.rmi.RemoteException {
        DBBroker broker = null;
        Session session = getSession(sessionId);
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
// TODO check XML/Binary resource
//            DocumentImpl doc = (DocumentImpl)broker.getDocument(documentName);
            DocumentImpl doc = (DocumentImpl)broker.getXMLResource(documentName);
            if (doc == null) {
                transact.abort(transaction);
                throw new RemoteException(
                        "document " + documentName + " not found");
            }
            if(!doc.getPermissions().validate(broker.getUser(), Permission.READ)) {
                transact.abort(transaction);
                throw new RemoteException("Not allowed to read resource");
            }
            MutableDocumentSet docs = new DefaultDocumentSet();
            docs.add(doc);
            XUpdateProcessor processor =
                    new XUpdateProcessor(broker, docs, AccessContext.SOAP);
            Modification modifications[] =
                    processor.parse(new InputSource(new StringReader(xupdate)));
            long mods = 0;
            for (int i = 0; i < modifications.length; i++) {
                mods += modifications[i].process(transaction);
                broker.flush();
            }
            transact.commit(transaction);
            return (int) mods;
        } catch (ParserConfigurationException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (IOException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (EXistException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (SAXException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (PermissionDeniedException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (XPathException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } catch (LockException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage(), e);
        } finally {
            pool.release(broker);
        }
        
    }
    
    public void storeBinary(java.lang.String sessionId, byte[] data, java.lang.String path, java.lang.String mimeType, boolean replace) throws java.rmi.RemoteException {
    	try {
    		storeBinary(sessionId,data,XmldbURI.xmldbUriFor(path), mimeType, replace);
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid document URI",e);
    	}
    }
    public void storeBinary(java.lang.String sessionId, byte[] data, XmldbURI path, java.lang.String mimeType, boolean replace) throws java.rmi.RemoteException {
        DBBroker broker = null;
        Session session = getSession(sessionId);
        Collection collection = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
            XmldbURI collectionUri = path.removeLastSegment();
            XmldbURI docUri = path.lastSegment();
            if (collectionUri==null || docUri==null) {
                transact.abort(txn);
                throw new EXistException("Illegal document path");
            }
            collection = broker.openCollection(collectionUri, Lock.WRITE_LOCK);
            if (collection == null)
                throw new EXistException("Collection " + collectionUri
                        + " not found");
            if (!replace) {
                DocumentImpl old = collection.getDocument(broker, docUri);
                if (old != null)
                    throw new PermissionDeniedException(
                            "Old document exists and overwrite is not allowed");
            }
            LOG.debug("Storing binary resource to collection " + collection.getURI());
            
            /*DocumentImpl doc = */
            collection.addBinaryResource(txn, broker, docUri, data, mimeType);
//            if (created != null)
//                doc.setCreated(created.getTime());
//            if (modified != null)
//                doc.setLastModified(modified.getTime());
            transact.commit(txn);
        } catch (Exception e) {
            transact.abort(txn);
            throw new RemoteException(e.getMessage(), e);
        } finally {
            if(collection != null)
                collection.release(Lock.WRITE_LOCK);
            pool.release(broker);
        }
//        documentCache.clear();
//        return doc != null;
    }
    
    public byte[] getBinaryResource(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
    	try {
    		return getBinaryResource(sessionId,XmldbURI.xmldbUriFor(path));
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid document URI",e);
    	}
    }
    public byte[] getBinaryResource(java.lang.String sessionId, XmldbURI name) throws java.rmi.RemoteException {
        DBBroker broker = null;
        Session session = getSession(sessionId);
        DocumentImpl doc = null;
        try {
            broker = pool.get(session.getUser());
// TODO check XML/Binary resource
//            doc = (DocumentImpl) broker.openXmlDocument(name, Lock.READ_LOCK);
            doc = broker.getXMLResource(name, Lock.READ_LOCK);
            if (doc == null)
                throw new EXistException("Resource " + name + " not found");
            if (doc.getResourceType() != DocumentImpl.BINARY_FILE)
                throw new EXistException("Document " + name
                        + " is not a binary resource");
            if(!doc.getPermissions().validate(session.getUser(), Permission.READ))
                throw new PermissionDeniedException("Insufficient privileges to read resource");
            InputStream is = broker.getBinaryResource((BinaryDocument) doc);
            byte [] data = new byte[(int)broker.getBinaryResourceSize((BinaryDocument) doc)];
            is.read(data);
            is.close();
            return data;
        } catch (Exception ex) {
            throw new RemoteException(ex.getMessage());
        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.READ_LOCK);
            pool.release(broker);
        }
    }
    
    public org.exist.soap.CollectionDesc getCollectionDesc(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
    	try {
    		return getCollectionDesc(sessionId,XmldbURI.xmldbUriFor(path));
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public org.exist.soap.CollectionDesc getCollectionDesc(java.lang.String sessionId, XmldbURI collectionName) throws java.rmi.RemoteException {
        DBBroker broker = null;
        Session session = getSession(sessionId);
        Collection collection = null;
        try {
            broker = pool.get(session.getUser());
            if (collectionName == null)
                collectionName = XmldbURI.ROOT_COLLECTION_URI;
            
            collection = broker.openCollection(collectionName, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("collection " + collectionName
                        + " not found!");
            CollectionDesc desc = new CollectionDesc();
            Vector docs = new Vector();
            Vector collections = new Vector();
            if (collection.getPermissions().validate(session.getUser(), Permission.READ)) {
                DocumentImpl doc;
//              Hashtable hash;
                Permission perms;
                for (Iterator i = collection.iterator(broker); i.hasNext(); ) {
                    doc = (DocumentImpl) i.next();
                    perms = doc.getPermissions();
                    DocumentDesc dd = new DocumentDesc();
//                    hash = new Hashtable(4);
                    dd.setName(doc.getFileURI().toString());
                    dd.setOwner(perms.getOwner());
                    dd.setGroup(perms.getOwnerGroup());
                    dd.setPermissions(perms.getPermissions());
                    dd.setType(doc.getResourceType() == DocumentImpl.BINARY_FILE
                            ? DocumentType.BinaryResource
                            : DocumentType.XMLResource);
                    docs.addElement(dd);
                }
                for (Iterator i = collection.collectionIterator(); i.hasNext(); )
                    collections.addElement(((XmldbURI)i.next()).toString());
            }
            Permission perms = collection.getPermissions();
            desc.setCollections(new Strings((String[]) collections.toArray(new String[collections.size()])));
            desc.setDocuments(new DocumentDescs((DocumentDesc[])docs.toArray(new DocumentDesc[docs.size()])));
            desc.setName(collection.getURI().toString());
            desc.setCreated(collection.getCreationTime());
            desc.setOwner(perms.getOwner());
            desc.setGroup(perms.getOwnerGroup());
            desc.setPermissions(perms.getPermissions());
            return desc;
        } catch (Exception ex){
            throw new RemoteException(ex.getMessage());
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            pool.release(broker);
        }
    }
    
    public void setPermissions(java.lang.String sessionId, java.lang.String resource, java.lang.String owner, java.lang.String ownerGroup, int permissions) throws java.rmi.RemoteException {
    	try {
    		setPermissions(sessionId,XmldbURI.xmldbUriFor(resource),owner,ownerGroup,permissions);
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public void setPermissions(java.lang.String sessionId, XmldbURI resource, java.lang.String owner, java.lang.String ownerGroup, int permissions) throws java.rmi.RemoteException {
        DBBroker broker = null;
        Session session = getSession(sessionId);
        Collection collection = null;
        DocumentImpl doc = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = pool.get(session.getUser());
            org.exist.security.SecurityManager manager = pool
                    .getSecurityManager();
            collection = broker.openCollection(resource, Lock.WRITE_LOCK);
            if (collection == null) {
// TODO check XML/Binary resource
//                doc = (DocumentImpl) broker.openDocument(resource, Lock.WRITE_LOCK);
                doc = (DocumentImpl) broker.getXMLResource(resource, Lock.WRITE_LOCK);
                if (doc == null)
                    throw new RemoteException("document or collection "
                            + resource + " not found");
                LOG.debug("changing permissions on document " + resource);
                Permission perm = doc.getPermissions();
                if (perm.getOwner().equals(session.getUser().getName())
                || manager.hasAdminPrivileges(session.getUser())) {
                    if (owner != null) {
                        perm.setOwner(owner);
                        perm.setGroup(ownerGroup);
                    }
                    perm.setPermissions(permissions);
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
            Permission perm = collection.getPermissions();
            if (perm.getOwner().equals(session.getUser().getName())
            || manager.hasAdminPrivileges(session.getUser())) {
                perm.setPermissions(permissions);
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
        } catch (IOException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage());
        } catch (PermissionDeniedException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage());
        } catch (TransactionException e) {
            throw new RemoteException(e.getMessage());
        } catch (EXistException e) {
            throw new RemoteException(e.getMessage());
        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            pool.release(broker);
        }
    }
    
    private void moveOrCopyResource(String sessionId, String docPath, String destinationPath,
            String newName, boolean move)
    		throws RemoteException {
    	try {
    		moveOrCopyResource(sessionId,XmldbURI.xmldbUriFor(docPath),XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName),move);
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    private void moveOrCopyResource(String sessionId, XmldbURI docPath, XmldbURI destinationPath,
            XmldbURI newName, boolean move)
            throws RemoteException {
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        Session session = getSession(sessionId);
        Collection collection = null;
        Collection destination = null;
        DocumentImpl doc = null;
        try {
            broker = pool.get(session.getUser());
            XmldbURI collectionUri = docPath.removeLastSegment();
            XmldbURI docUri = docPath.lastSegment();
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
                broker.moveResource(transaction, doc, destination, newName);
            else
// TODO check XML/Binary resource
//                broker.copyResource(transaction, doc, destination, newName);
                broker.copyResource(transaction, doc, destination, newName);
            transact.commit(transaction);
//            documentCache.clear();
            return;
        } catch (LockException e) {
            transact.abort(transaction);
            throw new RemoteException("Could not acquire lock on document " + docPath);
        } catch (PermissionDeniedException e) {
            transact.abort(transaction);
            throw new RemoteException("Could not move/copy document " + docPath);
        } catch (IOException e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage());
        } catch (TransactionException e) {
            throw new RemoteException("Error commiting transaction " + e.getMessage());
        } catch (EXistException e) {
            throw new RemoteException(e.getMessage());
        } finally {
            if(destination != null)
                destination.release(Lock.WRITE_LOCK);
            if(doc != null)
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            if(collection != null)
                collection.release(move ? Lock.WRITE_LOCK : Lock.READ_LOCK);
            pool.release(broker);
        }
    }
    
    private boolean moveOrCopyCollection(String sessionId, String collectionPath, String destinationPath,
            String newName, boolean move)
    		throws EXistException, PermissionDeniedException, RemoteException {
    	try {
    		return moveOrCopyCollection(sessionId,XmldbURI.xmldbUriFor(collectionPath),XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName),move);
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    private boolean moveOrCopyCollection(String sessionId, XmldbURI collectionPath, XmldbURI destinationPath,
    		XmldbURI newName, boolean move)
            throws EXistException, PermissionDeniedException, RemoteException {
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        Session session = getSession(sessionId);
        Collection collection = null;
        Collection destination = null;
        try {
            User user = session.getUser();
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
                broker.moveCollection(transaction, collection, destination, newName);
            else
                broker.copyCollection(transaction, collection, destination, newName);
            transact.commit(transaction);
//            documentCache.clear();
            return true;
        } catch (IOException e) {
        	transact.abort(transaction);
            throw new RemoteException(e.getMessage());            
        } catch (LockException e) {
        	transact.abort(transaction);
            throw new PermissionDeniedException(e.getMessage());
        } finally {
            if(collection != null)
                collection.release(move ? Lock.WRITE_LOCK : Lock.READ_LOCK);
            if(destination != null)
                destination.release(Lock.WRITE_LOCK);
            pool.release(broker);
        }
    }
    
    public void copyResource(java.lang.String sessionId, java.lang.String docPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException {
        moveOrCopyResource(sessionId,docPath,destinationPath,newName,false);
    }
    
    public void copyCollection(java.lang.String sessionId, java.lang.String collectionPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException {
        try {
            moveOrCopyCollection(sessionId,collectionPath,destinationPath,newName,false);
        } catch (RemoteException e) {
            throw new RemoteException(e.getMessage());
        } catch (EXistException e) {
            throw new RemoteException(e.getMessage());
        } catch (PermissionDeniedException e) {
            throw new RemoteException(e.getMessage());
        }
    }
    
    public void setUser(java.lang.String sessionId, java.lang.String name, java.lang.String password, org.exist.soap.Strings groups, java.lang.String home) throws java.rmi.RemoteException {
        if (password.length() == 0)
            password = null;
        Session session = getSession(sessionId);
        User user = session.getUser();
        
        org.exist.security.SecurityManager manager = pool.getSecurityManager();
        if(name.equals(org.exist.security.SecurityManager.GUEST_USER) &&
                (!manager.hasAdminPrivileges(user)))
            throw new RemoteException(
                    "guest user cannot be modified");
        User u;
        if (!manager.hasUser(name)) {
            if (!manager.hasAdminPrivileges(user))
                throw new RemoteException(
                        "not allowed to create user");
            u = new User(name);
            u.setPasswordDigest(password);
        } else {
            u = manager.getUser(name);
            if (!(u.getName().equals(user.getName()) || manager
                    .hasAdminPrivileges(user)))
                throw new RemoteException(
                        "you are not allowed to change this user");
            u.setPasswordDigest(password);
        }
        for (int i = 0; i < groups.getElements().length; i++ ) {
            if (!u.hasGroup(groups.getElements()[i])) {
                if(!manager.hasAdminPrivileges(user))
                    throw new RemoteException(
                            "User is not allowed to add groups");
                u.addGroup(groups.getElements()[i]);
            }
        }
        if (home != null) {
        	try {
                u.setHome(XmldbURI.xmldbUriFor(home));
        	} catch(URISyntaxException e) {
        		throw new RemoteException("Invalid collection URI",e);
        	}
        }
        manager.setUser(u);
    }
    
    public org.exist.soap.UserDesc getUser(java.lang.String sessionId, java.lang.String user) throws java.rmi.RemoteException {
        User u = pool.getSecurityManager().getUser(user);
        if (u == null)
            throw new RemoteException("user " + user + " does not exist");
        UserDesc desc = new UserDesc();
        desc.setName(u.getName());
/*
        Vector groups = new Vector();
        for (Iterator i = u.getGroups(); i.hasNext(); )
            groups.addElement(i.next());
        desc.setGroups(new Strings((String[])groups.toArray(new String[groups.size()])));
 */
        desc.setGroups(new Strings(u.getGroups()));
        if (u.getHome() != null) {
            desc.setHome(u.getHome().toString());
        }
        return desc;
    }
    
    public void removeUser(java.lang.String sessionId, java.lang.String name) throws java.rmi.RemoteException {
        User user = getSession(sessionId).getUser();
        org.exist.security.SecurityManager manager = pool
                .getSecurityManager();
        if (!manager.hasAdminPrivileges(user))
            throw new RemoteException(
                    "you are not allowed to remove users");
        
        try {
            manager.deleteUser(name);
        } catch (PermissionDeniedException e) {
            throw new RemoteException(e.getMessage());
        }
    }
    
    public org.exist.soap.UserDescs getUsers(java.lang.String sessionId) throws java.rmi.RemoteException {
        User users[] = pool.getSecurityManager().getUsers();
        UserDesc[] r = new UserDesc[users.length];
        for (int i = 0; i < users.length; i++) {
            r[i] = new UserDesc();
            r[i].setName(users[i].getName());
/*
            Vector groups = new Vector();
            for (Iterator j = users[i].getGroups(); j.hasNext(); )
                groups.addElement(j.next());
            r[i].setGroups(new Strings((String[])groups.toArray(new String[groups.size()])));
 */
            r[i].setGroups(new Strings(users[i].getGroups()));
            if (users[i].getHome() != null)
                r[i].setHome(users[i].getHome().toString());
        }
        return new UserDescs(r);
    }
    
    public org.exist.soap.Strings getGroups(java.lang.String sessionId) throws java.rmi.RemoteException {
        String[] groups = pool.getSecurityManager().getGroups();
        Vector v = new Vector(groups.length);
        for (int i = 0; i < groups.length; i++) {
            v.addElement(groups[i]);
        }
        return new Strings((String[])v.toArray(new String[v.size()]));
    }
    
    public void moveCollection(java.lang.String sessionId, java.lang.String collectionPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException {
        try {
            moveOrCopyCollection(sessionId,collectionPath,destinationPath,newName,true);
        } catch (RemoteException e) {
            throw new RemoteException(e.getMessage());
        } catch (EXistException e) {
            throw new RemoteException(e.getMessage());
        } catch (PermissionDeniedException e) {
            throw new RemoteException(e.getMessage());
        }
    }
    
    public void moveResource(java.lang.String sessionId, java.lang.String docPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException {
        moveOrCopyResource(sessionId,docPath,destinationPath,newName,true);
    }
    
    public void lockResource(java.lang.String sessionId, java.lang.String path, java.lang.String userName) throws java.rmi.RemoteException {
    	try {
    		lockResource(sessionId,XmldbURI.xmldbUriFor(path),userName);
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public void lockResource(java.lang.String sessionId, XmldbURI path, java.lang.String userName) throws java.rmi.RemoteException {
        DBBroker broker = null;
        Session session = getSession(sessionId);
        User user = session.getUser();
        DocumentImpl doc = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = pool.get(user);
// TODO check XML/Binary resource
//            doc = (DocumentImpl) broker.openDocument(path, Lock.WRITE_LOCK);
            doc = broker.getXMLResource(path, Lock.WRITE_LOCK);
            if (doc == null) {
                throw new EXistException("Resource "
                        + path + " not found");
            }
            if (!doc.getPermissions().validate(user, Permission.UPDATE))
                throw new PermissionDeniedException("User is not allowed to lock resource " + path);
            org.exist.security.SecurityManager manager = pool.getSecurityManager();
            if (!(userName.equals(user.getName()) || manager.hasAdminPrivileges(user)))
                throw new PermissionDeniedException("User " + user.getName() + " is not allowed " +
                        "to lock the resource for user " + userName);
            User lockOwner = doc.getUserLock();
            if(lockOwner != null && (!lockOwner.equals(user)) && (!manager.hasAdminPrivileges(user)))
                throw new PermissionDeniedException("Resource is already locked by user " +
                        lockOwner.getName());
            User lo = manager.getUser(userName);
            doc.setUserLock(lo);
// TODO check XML/Binary resource
//            broker.storeDocument(transaction, doc);
            broker.storeXMLResource(transaction, doc);
            transact.commit(transaction);
            return;
        } catch (Exception e) {
            transact.abort(transaction);
            throw new RemoteException(e.getMessage());
        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            pool.release(broker);
        }
    }
    
    public void unlockResource(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
    	try {
    		unlockResource(sessionId,XmldbURI.xmldbUriFor(path));
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public void unlockResource(java.lang.String sessionId, XmldbURI path) throws java.rmi.RemoteException {
        DBBroker broker = null;
        Session session = getSession(sessionId);
        User user = session.getUser();
        DocumentImpl doc = null;
        try {
            broker = pool.get(user);
// TODO check XML/Binary resource
//            doc = (DocumentImpl) broker.openDocument(path, Lock.WRITE_LOCK);
            doc = (DocumentImpl) broker.getXMLResource(path, Lock.WRITE_LOCK);
            if (doc == null)
                throw new EXistException("Resource "
                        + path + " not found");
            if (!doc.getPermissions().validate(user, Permission.UPDATE))
                throw new PermissionDeniedException("User is not allowed to lock resource " + path);
            org.exist.security.SecurityManager manager = pool.getSecurityManager();
            User lockOwner = doc.getUserLock();
            if(lockOwner != null && (!lockOwner.equals(user)) && (!manager.hasAdminPrivileges(user)))
                throw new PermissionDeniedException("Resource is already locked by user " +
                        lockOwner.getName());
            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();
            doc.setUserLock(null);
// TODO check XML/Binary resource
//            broker.storeDocument(transaction, doc);
            broker.storeXMLResource(transaction, doc);
            transact.commit(transaction);
            return;
        } catch (Exception ex){
            throw new RemoteException(ex.getMessage());
        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.WRITE_LOCK);
            pool.release(broker);
        }
    }
    
    public java.lang.String hasUserLock(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
    	try {
    		return hasUserLock(sessionId,XmldbURI.xmldbUriFor(path));
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public java.lang.String hasUserLock(java.lang.String sessionId, XmldbURI path) throws java.rmi.RemoteException {
        DBBroker broker = null;
        Session session = getSession(sessionId);
        User user = session.getUser();
        DocumentImpl doc = null;
        try {
            broker = pool.get(user);
// TODO check XML/Binary resource
//            doc = (DocumentImpl) broker.openDocument(path, Lock.READ_LOCK);
            doc = (DocumentImpl) broker.getXMLResource(path, Lock.READ_LOCK);
            if (doc == null)
                throw new EXistException("Resource "
                        + path + " not found");
            if(!doc.getPermissions().validate(user, Permission.READ))
                throw new PermissionDeniedException("Insufficient privileges to read resource");
            if (doc == null)
                throw new EXistException("Resource " + path + " not found");
            User u = doc.getUserLock();
            return u == null ? "" : u.getName();
        } catch (Exception ex) {
            throw new RemoteException(ex.getMessage());
        } finally {
            if(doc != null)
                doc.getUpdateLock().release(Lock.READ_LOCK);
            pool.release(broker);
        }
    }
    
    public org.exist.soap.Permissions getPermissions(java.lang.String sessionId, java.lang.String resource) throws java.rmi.RemoteException {
    	try {
    		return getPermissions(sessionId,XmldbURI.xmldbUriFor(resource));
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public org.exist.soap.Permissions getPermissions(java.lang.String sessionId, XmldbURI resource) throws java.rmi.RemoteException {
        DBBroker broker = null;
        Session session = getSession(sessionId);
        User user = session.getUser();
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
	            		doc = (DocumentImpl) broker.getXMLResource(resource, Lock.READ_LOCK);
		                if (doc == null)
		                    throw new EXistException("document or collection " + resource + " not found");
		                perm = doc.getPermissions();
	            	} finally {
	            		if (doc != null)
	            			doc.getUpdateLock().release(Lock.READ_LOCK);
	            	}
	            } else {
	                perm = collection.getPermissions();
	            }
	            Permissions p = new Permissions();
	            p.setOwner(perm.getOwner());
	            p.setGroup(perm.getOwnerGroup());
	            p.setPermissions(perm.getPermissions());
	            return p;
            } finally {
            	if (collection != null)
            		collection.release(Lock.READ_LOCK);            	
            }
        } catch (Exception ex) {
            throw new RemoteException(ex.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    
    public org.exist.soap.EntityPermissionsList listCollectionPermissions(java.lang.String sessionId, java.lang.String name) throws java.rmi.RemoteException {
    	try {
    		return listCollectionPermissions(sessionId,XmldbURI.xmldbUriFor(name));
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public org.exist.soap.EntityPermissionsList listCollectionPermissions(java.lang.String sessionId, XmldbURI name) throws java.rmi.RemoteException {
        DBBroker broker = null;
        Session session = getSession(sessionId);
        User user = session.getUser();
        Collection collection = null;
        try {
            broker = pool.get(user);
            collection = broker.openCollection(name, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("Collection " + name + " not found");
            if (!collection.getPermissions().validate(user, Permission.READ))
                throw new PermissionDeniedException(
                        "not allowed to read collection " + name);
            EntityPermissions[] result = new EntityPermissions[collection.getChildCollectionCount()];
            XmldbURI child, path;
            Collection childColl;
            Permission perm;
            int cnt = 0;
            for (Iterator i = collection.collectionIterator(); i.hasNext(); ) {
                child = (XmldbURI) i.next();
                path = name.append(child);
                childColl = broker.getCollection(path);
                perm = childColl.getPermissions();
                result[cnt] = new EntityPermissions();
                result[cnt].setName(child.toString());
                result[cnt].setOwner(perm.getOwner());
                result[cnt].setGroup(perm.getOwnerGroup());
                result[cnt].setPermissions(perm.getPermissions());
                cnt++;
            }
            return new EntityPermissionsList(result);
        } catch (Exception ex) {
            throw new RemoteException(ex.getMessage());
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            pool.release(broker);
        }
    }
    
    public org.exist.soap.EntityPermissionsList listDocumentPermissions(java.lang.String sessionId, java.lang.String name) throws java.rmi.RemoteException {
    	try {
    		return listDocumentPermissions(sessionId,XmldbURI.xmldbUriFor(name));
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public org.exist.soap.EntityPermissionsList listDocumentPermissions(java.lang.String sessionId, XmldbURI name) throws java.rmi.RemoteException {
        DBBroker broker = null;
        Session session = getSession(sessionId);
        User user = session.getUser();
        Collection collection = null;
        try {
            broker = pool.get(user);
            collection = broker.openCollection(name, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("Collection " + name + " not found");
            if (!collection.getPermissions().validate(user, Permission.READ))
                throw new PermissionDeniedException(
                        "not allowed to read collection " + name);
            EntityPermissions[] result = new EntityPermissions[collection.getDocumentCount()];
            DocumentImpl doc;
            Permission perm;
            int cnt = 0;
            for (Iterator i = collection.iterator(broker); i.hasNext(); ) {
                doc = (DocumentImpl) i.next();
                perm = doc.getPermissions();
                result[cnt] = new EntityPermissions();
                result[cnt].setName(doc.getFileURI().toString());
                result[cnt].setOwner(perm.getOwner());
                result[cnt].setGroup(perm.getOwnerGroup());
                result[cnt].setPermissions(perm.getPermissions());
                cnt++;
            }
            return new EntityPermissionsList(result);
        } catch (Exception ex) {
            throw new RemoteException(ex.getMessage());
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            pool.release(broker);
        }
    }
    
    public org.exist.soap.IndexedElements getIndexedElements(java.lang.String sessionId, java.lang.String collectionName, boolean inclusive) throws java.rmi.RemoteException {
    	try {
    		return getIndexedElements(sessionId,XmldbURI.xmldbUriFor(collectionName),inclusive);
    	} catch(URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public org.exist.soap.IndexedElements getIndexedElements(java.lang.String sessionId, XmldbURI collectionName, boolean inclusive) throws java.rmi.RemoteException {
        DBBroker broker = null;
        Session session = getSession(sessionId);
        User user = session.getUser();
        Collection collection = null;
        try {
            broker = pool.get(user);
            collection = broker.openCollection(collectionName, Lock.READ_LOCK);
            if (collection == null)
                throw new EXistException("collection " + collectionName
                        + " not found");
            Occurrences occurrences[] = broker.getElementIndex().scanIndexedElements(collection,
                    inclusive);
            IndexedElement[] result = new IndexedElement[occurrences.length];
            for (int i = 0; i < occurrences.length; i++) {
                QName qname = (QName)occurrences[i].getTerm();
                result[i] = new IndexedElement(qname.getLocalName(),qname.getNamespaceURI(),
                        qname.getPrefix() == null ? "" : qname.getPrefix(),
                        occurrences[i].getOccurrences());
            }
            return new IndexedElements(result);
        } catch (Exception ex) {
            throw new RemoteException(ex.getMessage());
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            pool.release(broker);
        }
    }
    
}
