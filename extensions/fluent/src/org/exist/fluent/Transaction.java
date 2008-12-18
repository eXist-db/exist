package org.exist.fluent;

import org.exist.dom.DocumentImpl;
import org.exist.storage.lock.Lock;
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
class Transaction {
	private final TransactionManager txManager;
	final Txn tx;
	private boolean complete;
	
	/**
	 * Begin a new transaction.
	 *
	 * @param txManager the manager to use
	 */
	Transaction(TransactionManager txManager) {
		this.txManager = txManager;
		this.tx = txManager.beginTransaction();
		complete = false;
	}
	
	/**
	 * Join an existing transaction, do nothing on commit or abort.
	 *
	 * @param tx the transaction to join
	 */
	Transaction(Txn tx) {
		this.txManager = null;
		this.tx = tx;
		complete = true;
	}
	
	void commit() {
		if (complete) return;
		if (tx != null && txManager != null) try {
			txManager.commit(tx);
			complete = true;
		} catch (TransactionException e) {
			throw new DatabaseException(e);
		}
	}
	
	void abortIfIncomplete() {
		if (complete) return;
		if (tx != null && txManager != null) txManager.abort(tx);
		complete = true;
	}
	
	void lockWrite(DocumentImpl doc) {
		try {
			tx.acquireLock(doc.getUpdateLock(), Lock.WRITE_LOCK);
		} catch (LockException e) {
			throw new DatabaseException(e);
		}
	}
	
	void lockRead(DocumentImpl doc) {
		try {
			tx.acquireLock(doc.getUpdateLock(), Lock.READ_LOCK);
		} catch (LockException e) {
			throw new DatabaseException(e);
		}		
	}
}
