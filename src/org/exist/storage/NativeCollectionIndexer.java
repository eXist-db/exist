/*
 * eXist Open Source Native XML Database 
 * 
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Library General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any
 * later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.storage;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.dbxml.core.DBException;
import org.dbxml.core.data.Value;
import org.exist.collections.Collection;
import org.exist.collections.CollectionCache;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.store.CollectionStore;
import org.exist.util.ByteArray;
import org.exist.util.ByteConversion;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.exist.util.ReadOnlyException;

/**
 * Class which encapsulates the CollectionStore functionallity
 * with some goodies.
 */
final class NativeCollectionIndexer {
    /**
     * Temporary DBBroker instance.
     */
    private DBBroker broker = null;
    
    /**
     * Is any of the databases read-only?
     */
    private boolean readOnly = false;
    
    /**
     * The broker pool.
     */
    private BrokerPool pool = null;
    
    /**
     * The underlying native db.
     */
    private CollectionStore collectionsDb = null;
    
    /**
     * The Log4J logger.
     */
    private static final Logger LOG = Logger.getLogger(NativeCollectionIndexer.class);

    /**
     * Create a new NativeCollectionIndexer. The CollectionStore
     * must be initialized when calling this constructor.
     * 
     * Refactor note: currently, DBBroker is used here, but
     * this should <b>not</b> be the case in future.
     *  
     * @param pool broker pool to use
     * @param broker the broker to use
     * @param collectionsDb initialized collectionsDb
     */
    public NativeCollectionIndexer(BrokerPool pool, DBBroker broker, CollectionStore collectionsDb) {
        this.pool = pool;
        this.collectionsDb = collectionsDb;
        this.broker = broker;
    }

    /**
     * Set read-only if any of the backend datastores
     * are set read-only.
     * 
     * @param readOnly true, if one backend db is read-only
     */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
    
    
    public void reloadCollection(Collection collection) {
        Value key = null;
        if (collection.getAddress() == -1)
            try {
                key = new Value(collection.getName().getBytes("UTF-8"));
            } catch (UnsupportedEncodingException uee) {
                key = new Value(collection.getName().getBytes());
            }
        VariableByteInput is = null;
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.READ_LOCK);
            try {
                if (collection.getAddress() == -1) {
                    is = collectionsDb.getAsStream(key);
                } else {
                    is = collectionsDb.getAsStream(collection.getAddress());
                }
            } catch (IOException ioe) {
                LOG.warn(ioe.getMessage(), ioe);
            }
            if (is == null) {
                LOG.warn("Collection data not found for collection " + collection.getName());
                return;
            }
            
            try {
                collection.read(broker, is);
            } catch (IOException ioe) {
                LOG.warn(ioe);
            }
        } catch (LockException e) {
            LOG.warn("failed to acquire lock on collections.dbx");
        } finally {
            lock.release();
        }
    }
    
    /**
     * Release the collection id assigned to a collection so it can be
     * reused later.
     * 
     * @param id
     * @throws PermissionDeniedException
     */
    public void freeCollection(short id) throws PermissionDeniedException {
//      LOG.debug("freeing collection " + id);
        Value key = new Value("__free_collection_id");
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            Value value = collectionsDb.get(key);
            if (value != null) {
                byte[] data = value.getData();
                byte[] ndata = new byte[data.length + 2];
                System.arraycopy(data, 0, ndata, 2, data.length);
                ByteConversion.shortToByte(id, ndata, 0);
                collectionsDb.put(key, ndata, true);
            } else {
                byte[] data = new byte[2];
                ByteConversion.shortToByte(id, data, 0);
                collectionsDb.put(key, data, true);
            }
        } catch (LockException e) {
            LOG.warn("failed to acquire lock on collections store", e);
        } catch (ReadOnlyException e) {
            throw new PermissionDeniedException(NewNativeBroker.DATABASE_IS_READ_ONLY);
        } finally {
            lock.release();
        }
    }
    
    ///////////////////// exported DB METHODS
    public void sync(int syncEvent) throws DBException {
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            collectionsDb.flush();
        } catch (LockException e) {
            LOG.warn("failed to acquire lock on collections store", e);
        } finally {
            lock.release();
        }
    }
    
    public void printStatistics() {
        collectionsDb.printStatistics();
    }
    
    public boolean close() throws DBException {
        return collectionsDb.close();
    }
    
    public void remove(Value key) throws ReadOnlyException {
        collectionsDb.remove(key);
    }
    
    public long append(Value key, ByteArray value) throws ReadOnlyException,
        IOException {
        return collectionsDb.append(key, value);
    }
    
    public Lock getLock() {
        return collectionsDb.getLock();
    }
    ///////////////////// DB METHODS
    
    public int getNextDocId(Collection collection) {
        int nextDocId;
        try {
            nextDocId = getFreeDocId();
        } catch (ReadOnlyException e1) {
            return 1;
        }
        if(nextDocId > -1)
            return nextDocId;
        else
            nextDocId = 1;
        
        Value key = new Value("__next_doc_id");
        Value data;
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            data = collectionsDb.get(key);
            if (data != null) {
                nextDocId = ByteConversion.byteToInt(data.getData(), 0);
                ++nextDocId;
            }
            byte[] d = new byte[4];
            ByteConversion.intToByte(nextDocId, d, 0);
            try {
                collectionsDb.put(key, d, true);
            } catch (ReadOnlyException e) {
                LOG.debug("database read-only");
                return -1;
            }
        } catch (LockException e) {
            LOG.warn("failed to acquire lock on collections store", e);
        } finally {
            lock.release();
        }
        return nextDocId;
    }
    
    public void saveCollection(Collection collection) throws PermissionDeniedException {
        if (readOnly)
            throw new PermissionDeniedException(NewNativeBroker.DATABASE_IS_READ_ONLY);
        pool.getCollectionsCache().add(collection);
        Lock lock = null;
        try {
            lock = collectionsDb.getLock();
            lock.acquire(Lock.WRITE_LOCK);

            if (collection.getId() < 0)
                collection.setId(getNextCollectionId());

            Value name;
            try {
                name = new Value(collection.getName().getBytes("UTF-8"));
            } catch (UnsupportedEncodingException uee) {
                LOG.debug(uee);
                name = new Value(collection.getName().getBytes());
            }
            try {
                final VariableByteOutputStream ostream = new VariableByteOutputStream(8);
                collection.write(broker, ostream);
                final long addr = collectionsDb.put(name, ostream.data());
                if (addr < 0) {
                    LOG.debug(
                        "could not store collection data for " + collection.getName());
                    return;
                }
                collection.setAddress(addr);
                ostream.close();
            } catch (IOException ioe) {
                LOG.debug(ioe);
            }
        } catch (ReadOnlyException e) {
            LOG.warn(NewNativeBroker.DATABASE_IS_READ_ONLY);
        } catch (LockException e) {
            LOG.warn("could not acquire lock for collections store", e);
        } finally {
            lock.release();
        }
    }
    
    /**
     *  get collection object If the collection does not yet exists, it is
     *  created automatically.
     *
     *@param  name                           the collection's name
     *@param  user                           Description of the Parameter
     *@return                                The orCreateCollection value
     *@exception  PermissionDeniedException  Description of the Exception
     *@author=@author
     */
    public Collection getOrCreateCollection(String name)
        throws PermissionDeniedException {
        //      final long start = System.currentTimeMillis();
        name = normalizeCollectionName(name);
        if (name.length() > 0 && name.charAt(0) != '/')
            name = "/" + name;

        if (!name.startsWith(NewNativeBroker.ROOT_COLLECTION))
            name = NewNativeBroker.ROOT_COLLECTION + name;

        if (name.endsWith("/") && name.length() > 1)
            name = name.substring(0, name.length() - 1);
        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            try {
                StringTokenizer tok = new StringTokenizer(name, "/");
                String temp = tok.nextToken();
                String path = NewNativeBroker.ROOT_COLLECTION;
                Collection sub;
                Collection current = openCollection(NewNativeBroker.ROOT_COLLECTION, -1L, Lock.NO_LOCK);
                if (current == null) {
                    LOG.debug("creating root collection /db");
                    current = new Collection(collectionsDb, NewNativeBroker.ROOT_COLLECTION);
                    current.getPermissions().setPermissions(0777);
                    current.getPermissions().setOwner(broker.getUser());
                    current.getPermissions().setGroup(broker.getUser().getPrimaryGroup());
                    current.setId(getNextCollectionId());
                    current.setCreationTime(System.currentTimeMillis());
                    saveCollection(current);
                }
                while (tok.hasMoreTokens()) {
                    temp = tok.nextToken();
                    path = path + "/" + temp;
                    if (current.hasSubcollection(temp)) {
                        current = openCollection(path, -1L, Lock.NO_LOCK);
                    } else {
                        if (!current.getPermissions().validate(broker.getUser(), Permission.WRITE)) {
                            LOG.debug("permission denied to create collection " + path);
                            throw new PermissionDeniedException("not allowed to write to collection");
                        }
                        LOG.debug("creating collection " + path);
                        sub = new Collection(collectionsDb, path);
                        sub.getPermissions().setOwner(broker.getUser());
                        sub.getPermissions().setGroup(broker.getUser().getPrimaryGroup());
                        sub.setId(getNextCollectionId());
                        sub.setCreationTime(System.currentTimeMillis());
                        current.addCollection(sub);
                        saveCollection(current);
                        current = sub;
                    }
                }
                //          LOG.debug("getOrCreateCollection took " + 
                //              (System.currentTimeMillis() - start) + "ms.");
                return current;
            } catch(ReadOnlyException e) {
                throw new PermissionDeniedException(NewNativeBroker.DATABASE_IS_READ_ONLY);
            }
        }
    }
    
    /**
     *  Get collection object. If the collection does not exist, null is
     *  returned.
     *
     *@param  name  Description of the Parameter
     *@return       The collection value
     */
    public Collection openCollection(String name, long addr, int lockMode) {
        //  final long start = System.currentTimeMillis();
        name = normalizeCollectionName(name);
        if (name.length() > 0 && name.charAt(0) != '/')
            name = "/" + name;

        if (!name.startsWith(NewNativeBroker.ROOT_COLLECTION))
            name = NewNativeBroker.ROOT_COLLECTION + name;

        if (name.endsWith("/") && name.length() > 1)
            name = name.substring(0, name.length() - 1);

        CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            Collection collection = collectionsCache.get(name);
            if(collection == null) {
//              LOG.debug("loading collection " + name);
                VariableByteInput is = null;
                Lock lock = collectionsDb.getLock();
                try {
                    lock.acquire(Lock.READ_LOCK);
                    
                    collection = new Collection(collectionsDb, name);
                    Value key = null;
                    if (addr == -1) {
                        try {
                            key = new Value(name.getBytes("UTF-8"));
                        } catch (UnsupportedEncodingException uee) {
                            key = new Value(name.getBytes());
                        }
                    }
                    try {
                        if (addr < 0) {
                            is = collectionsDb.getAsStream(key);
                        } else {
                            is = collectionsDb.getAsStream(addr);
                        }
                        if (is == null)
                            return null;
                        collection.read(broker, is);
                    } catch (IOException ioe) {
                        LOG.warn(ioe.getMessage(), ioe);
                    }
                } catch (LockException e) {
                    LOG.warn("failed to acquire lock on collections.dbx");
                    return null;
                } finally {
                    lock.release();
                }
            }
            if(lockMode != Lock.NO_LOCK) {
                try {
//                  LOG.debug("acquiring lock on " + collection.getName());
                    collection.getLock().acquire(lockMode);
//                  LOG.debug("lock acquired");
                } catch (LockException e1) {
                    LOG.warn("Could not acquire lock on collection " + name);
                }
            }
            collectionsCache.add(collection);
            //          LOG.debug(
            //              "loading collection "
            //                  + name
            //                  + " took "
            //                  + (System.currentTimeMillis() - start)
            //                  + "ms.");
            return collection;
        }
    }
    
    public void moveCollection(Collection collection, Collection destination, String newName) 
    throws PermissionDeniedException, LockException {
        if (readOnly)
            throw new PermissionDeniedException(NewNativeBroker.DATABASE_IS_READ_ONLY);
        if(collection.getId() == destination.getId())
            throw new PermissionDeniedException("Cannot move collection to itself");
        if(collection.getName().equals(NewNativeBroker.ROOT_COLLECTION))
            throw new PermissionDeniedException("Cannot move the db root collection");
        if(!collection.getPermissions().validate(broker.getUser(), Permission.WRITE))
            throw new PermissionDeniedException("Insufficient privileges to move collection " +
                    collection.getName());
        if(!destination.getPermissions().validate(broker.getUser(), Permission.WRITE))
            throw new PermissionDeniedException("Insufficient privileges on target collection " +
                    destination.getName());
        if(newName == null) {
            int p = collection.getName().lastIndexOf('/');
            newName = collection.getName().substring(p + 1);
        }
        if(newName.indexOf('/') > -1)
            throw new PermissionDeniedException("New collection name is illegal (may not contain a '/')");
            // check if another collection with the same name exists at the destination
        Collection old = openCollection(destination.getName() + '/' + newName, -1, Lock.WRITE_LOCK);
        if(old != null) {
            try {
                broker.removeCollection(old);
            } finally {
                old.release();
            }
        }
        String name = collection.getName();
        final CollectionCache collectionsCache = pool.getCollectionsCache();
        synchronized(collectionsCache) {
            Collection parent = openCollection(collection.getParentPath(), -1L, Lock.WRITE_LOCK);
            if(parent != null) {
                try {
                    parent.removeCollection(name.substring(name.lastIndexOf("/") + 1));
                } finally {
                    parent.release();
                }
            }
            Lock lock = null;
            try {
                lock = collectionsDb.getLock();
                lock.acquire(Lock.WRITE_LOCK);
                
                collectionsCache.remove(collection);
                Value key;
                try {
                    key = new Value(name.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException uee) {
                    key = new Value(name.getBytes());
                }   
                collectionsDb.remove(key);
                
                collection.setName(destination.getName() + '/' + newName);
                collection.setCreationTime(System.currentTimeMillis());
                
                destination.addCollection(collection);
                if(parent != null)
                    saveCollection(parent);
                if(parent != destination)
                    saveCollection(destination);
                saveCollection(collection);
            } catch (ReadOnlyException e) {
                throw new PermissionDeniedException(NewNativeBroker.DATABASE_IS_READ_ONLY);
            } finally {
                lock.release();
            }
            String childName;
            Collection child;
            for(Iterator i = collection.collectionIterator(); i.hasNext(); ) {
                childName = (String)i.next();
                child = openCollection(name + '/' + childName, -1L, Lock.WRITE_LOCK);
                if(child == null)
                    LOG.warn("Child collection " + childName + " not found");
                else {
                    try {
                        moveCollection(child, collection, childName);
                    } finally {
                        child.release();
                    }
                }
            }
        }
    }
    
    private final static String normalizeCollectionName(String name) {
        StringBuffer out = new StringBuffer();
        for (int i = 0; i < name.length(); i++)
            if (name.charAt(i) == '/'
                && name.length() > i + 1
                && name.charAt(i + 1) == '/')
                i++;
            else
                out.append(name.charAt(i));

        return out.toString();
    }

    /**
     * Get the next available unique collection id.
     * 
     * @return
     * @throws ReadOnlyException
     */
    private short getNextCollectionId() throws ReadOnlyException {
        short nextCollectionId = getFreeCollectionId();
        if(nextCollectionId > -1)
            return nextCollectionId;
        Value key = new Value("__next_collection_id");
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            Value data = collectionsDb.get(key);
            if (data != null) {
                nextCollectionId = ByteConversion.byteToShort(data.getData(), 0);
                ++nextCollectionId;
            }
            byte[] d = new byte[2];
            ByteConversion.shortToByte(nextCollectionId, d, 0);
            collectionsDb.put(key, d, true);
        } catch (LockException e) {
            LOG.warn("failed to acquire lock on collections store", e);
            return -1;
        } finally {
            lock.release();
        }
        return nextCollectionId;
    }

    /**
     * Get the next free collection id. If a collection is removed, its collection id
     * is released so it can be reused.
     * 
     * @return
     * @throws ReadOnlyException
     */
    private short getFreeCollectionId() throws ReadOnlyException {
        short freeCollectionId = -1;
        Value key = new Value("__free_collection_id");
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            Value value = collectionsDb.get(key);
            if (value != null) {
                byte[] data = value.getData();
                freeCollectionId = ByteConversion.byteToShort(data, data.length - 2);
//              LOG.debug("reusing collection id: " + freeCollectionId);
                if(data.length - 2 > 0) {
                    byte[] ndata = new byte[data.length - 2];
                    System.arraycopy(data, 0, ndata, 0, ndata.length);
                    collectionsDb.put(key, ndata, true);
                } else
                    collectionsDb.remove(key);
            }
        } catch (LockException e) {
            LOG.warn("failed to acquire lock on collections store", e);
            return -1;
        } finally {
            lock.release();
        }
        return freeCollectionId;
    }
    
    /**
     * Get the next unused document id. If a document is removed, its doc id is
     * released, so it can be reused.
     * 
     * @return
     * @throws ReadOnlyException
     */
    private int getFreeDocId() throws ReadOnlyException {
        int freeDocId = -1;
        Value key = new Value("__free_doc_id");
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            Value value = collectionsDb.get(key);
            if (value != null) {
                byte[] data = value.getData();
                freeDocId = ByteConversion.byteToInt(data, data.length - 4);
//              LOG.debug("reusing document id: " + freeDocId);
                if(data.length - 4 > 0) {
                    byte[] ndata = new byte[data.length - 4];
                    System.arraycopy(data, 0, ndata, 0, ndata.length);
                    collectionsDb.put(key, ndata, true);
                } else
                    collectionsDb.remove(key);
            }
        } catch (LockException e) {
            LOG.warn("failed to acquire lock on collections store", e);
            return -1;
        } finally {
            lock.release();
        }
        return freeDocId;
    }
    
    /**
     * Release the document id reserved for a document so it
     * can be reused.
     * 
     * @param id
     * @throws PermissionDeniedException
     */
    public void freeDocument(int id) throws PermissionDeniedException {
//      LOG.debug("freeing document " + id);
        Value key = new Value("__free_doc_id");
        Lock lock = collectionsDb.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            Value value = collectionsDb.get(key);
            if (value != null) {
                byte[] data = value.getData();
                byte[] ndata = new byte[data.length + 4];
                System.arraycopy(data, 0, ndata, 4, data.length);
                ByteConversion.intToByte(id, ndata, 0);
                collectionsDb.put(key, ndata, true);
            } else {
                byte[] data = new byte[4];
                ByteConversion.intToByte(id, data, 0);
                collectionsDb.put(key, data, true);
            }
        } catch (LockException e) {
            LOG.warn("failed to acquire lock on collections store", e);
        } catch (ReadOnlyException e) {
            throw new PermissionDeniedException(NewNativeBroker.DATABASE_IS_READ_ONLY);
        } finally {
            lock.release();
        }
    }
}
