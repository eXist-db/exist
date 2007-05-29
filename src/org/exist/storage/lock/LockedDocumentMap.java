package org.exist.storage.lock;

import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;

/**
 * This map is used by the XQuery engine to track how many read locks were
 * acquired for a document during query execution.
 */
public class LockedDocumentMap extends Int2ObjectHashMap {

    public LockedDocumentMap() {
        super(29, 1.75);
    }

    public void add(DocumentImpl doc) {
        LockedDocument entry = (LockedDocument) get(doc.getDocId());
        if (entry == null) {
            entry = new LockedDocument(doc);
            put(doc.getDocId(), entry);
        }
        entry.locksAcquired++;
    }

    public void unlock() {
	    LockedDocument d;
	    Lock dlock;
        for(int idx = 0; idx < tabSize; idx++) {
	        if(values[idx] == null || values[idx] == REMOVED)
	            continue;
	        d = (LockedDocument) values[idx];
            unlockDocument(d); 
        }
	}

    public LockedDocumentMap unlockSome(DocumentSet keep) {
        LockedDocument d;
        Lock dlock;
        for(int idx = 0; idx < tabSize; idx++) {
	        if(values[idx] == null || values[idx] == REMOVED)
	            continue;
	        d = (LockedDocument) values[idx];
            if (!keep.containsKey(d.document.getDocId())) {
                values[idx] = REMOVED;
                unlockDocument(d);
            }
        }
        return this;
    }

    private void unlockDocument(LockedDocument d) {
        Lock dlock;
        dlock = d.document.getUpdateLock();
        dlock.release(Lock.READ_LOCK, d.locksAcquired);
//        for (int i = 0; i < d.locksAcquired; i++) {
//            dlock.release(Lock.READ_LOCK);
//        }
        if (dlock.isLockedForRead(Thread.currentThread())) {
            System.out.println("Thread is still LOCKED: " + Thread.currentThread().getName());
        }
    }

    private static class LockedDocument {
        private DocumentImpl document;
        private int locksAcquired = 0;

        public LockedDocument(DocumentImpl document) {
            this.document = document;
        }
    }
}
