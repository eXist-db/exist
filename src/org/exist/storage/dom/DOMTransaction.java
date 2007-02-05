package org.exist.storage.dom;

import org.apache.log4j.Logger;
import org.exist.dom.DocumentImpl;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.MultiReadReentrantLock;
import org.exist.util.LockException;
import org.exist.util.ReadOnlyException;


/**
 * DOMTransaction controls access to the DOM file
 * 
 * This implements a wrapper around the code passed in
 * method start(). The class acquires a lock on the
 * file, enters the locked code block and calls start.
 * 
 * @author wolf
 *
 */
public abstract class DOMTransaction {

	private final static Logger LOG = Logger.getLogger(DOMTransaction.class);
	
	private Object ownerObject;
    private DOMFile file;
    private DocumentImpl document = null;
    private int mode;

    /**
     * @deprecated : use other constructors
     * @param owner
     * @param f
     */
    public DOMTransaction(Object owner, DOMFile f) {
    	ownerObject = owner;
    	file = f;
    	mode = Lock.READ_LOCK;
    }

	public DOMTransaction(Object owner, DOMFile f, int mode) {
		this(owner, f);
		this.mode = mode;
	}
	
	public DOMTransaction(Object owner, DOMFile f, int mode, DocumentImpl doc) {
		this(owner, f, mode);
		this.document = doc;		
	}
	
    public abstract Object start() throws ReadOnlyException;

    public Object run() {
    	Lock lock = file.getLock();
        try {
            // try to acquire a lock on the file
            try {
                lock.acquire( mode );
            } catch( LockException e ) {
            	System.out.println("Failed to acquire read lock on " + file.getFile().getName());
                return null;
            }
    	    file.setOwnerObject(ownerObject);
    	    file.setCurrentDocument(document);
            return start();
    	} catch( ReadOnlyException e ) {
    		LOG.warn(e.getMessage(), e);
        } finally {
			lock.release(mode);
        }
        return null;
    }
}
