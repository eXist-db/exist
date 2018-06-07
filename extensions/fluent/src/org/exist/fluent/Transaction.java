package org.exist.fluent;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.LockManager;
import org.exist.storage.txn.*;
import org.exist.util.LockException;

/**
 * A transaction on the database.  This can be either a top-level transaction, or a "nested"
 * transaction.  Nested transactions do nothing on commit and abort.  All transactions will
 * only execute a commit or abort once, all further invocations do nothing.  This makes it
 * convenient to put abort in a finally clause -- it will only have an effect if no commit was
 * reached beforehand.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
class Transaction implements AutoCloseable {
	private final TransactionManager txManager;
	private final LockManager lockManager;
	final Txn tx;
	DBBroker broker;
	private final Database db;
	
	/**
	 * Begin a new transaction.
	 *
	 * @param txManager the manager to use
	 */
	Transaction(TransactionManager txManager, LockManager lockManager, Database db) {
		this.txManager = txManager;
		this.lockManager = lockManager;
		this.tx = txManager.beginTransaction();
		this.db = db;
		this.broker = db == null ? null : db.acquireBroker();
	}
	
	/**
	 * Join an existing transaction, do nothing on commit or abort.
	 *
	 * @param tx the transaction to join
	 */
	Transaction(TransactionManager txManager, Transaction tx, LockManager lockManager, Database db) {
		this.txManager = txManager;
		this.lockManager = lockManager;
		this.tx = tx == null ? txManager.beginTransaction() : tx.tx;
		this.db = db;
		this.broker = db == null ? null : db.acquireBroker();
	}

	Transaction(TransactionManager txManager, Txn tx, LockManager lockManager, Database db) {
		this.txManager = txManager;
		this.lockManager = lockManager;
		this.tx = tx == null ? txManager.beginTransaction() : tx;
		this.db = db;
		this.broker = db == null ? null : db.acquireBroker();
	}
	
	void commit() {
		if (tx.getState() == Txn.State.COMMITTED) return;
		try {
			if (tx != null && txManager != null) {
                try {
                    txManager.commit(tx);
                } catch (TransactionException e) {
                    throw new DatabaseException(e);
                }
            }
		} finally {
			if (broker != null) {
                db.releaseBroker(broker);
                broker = null;
            }
		}
	}

    @Override
    public void close() {
        if (tx != null && txManager != null) {
            txManager.close(tx);
        }

        if (broker != null) {
            db.releaseBroker(broker);
            broker = null;
        }
	}

	void lockWrite(final DocumentImpl doc) {
		try {
			tx.acquireDocumentLock(() -> lockManager.acquireDocumentWriteLock(doc.getURI()));
		} catch (LockException e) {
			throw new DatabaseException(e);
		}
	}

	void lockRead(final DocumentImpl doc) {
		try {
			tx.acquireDocumentLock(() -> lockManager.acquireDocumentReadLock(doc.getURI()));
		} catch (LockException e) {
			throw new DatabaseException(e);
		}		
	}
}
