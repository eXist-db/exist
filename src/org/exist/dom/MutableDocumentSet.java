package org.exist.dom;

import org.exist.collections.Collection;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.util.LockException;

/**
 * 
 */
public interface MutableDocumentSet extends DocumentSet {

    void add(DocumentImpl doc);

    void add(DocumentImpl doc, boolean checkDuplicates);

    void addAll(DocumentSet other);
    
    void addAll(DBBroker broker, Collection collection, String[] paths, boolean checkPermissions);

    void addAll(DBBroker broker, Collection collection, String[] paths, LockedDocumentMap lockMap, int lockType) throws LockException;

    void addCollection(Collection collection);
    
    void clear();
}
