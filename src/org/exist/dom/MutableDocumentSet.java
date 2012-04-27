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

    void addCollection(Collection collection);
    
    void clear();
}
