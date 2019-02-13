package org.exist.fluent;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Lock.LockMode;
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
	final DBBroker broker;
	private final Database db;
	private boolean complete;
	
	/**
	 * Begin a new transaction.
	 *
	 * @param txManager the manager to use
	 */
	Transaction(TransactionManager txManager, Database db) {
		this.txManager = txManager;
		this.tx = txManager.beginTransaction();
		this.db = db;
		this.broker = db == null ? null : db.acquireBroker();
		complete = false;
	}
	
	/**
	 * Join an existing transaction, do nothing on commit or abort.
	 *
	 * @param tx the transaction to join
	 */
	Transaction(Transaction tx, Database db) {
		this.txManager = null;
		this.tx = tx.tx;
		this.db = db;
		this.broker = db == null ? null : db.acquireBroker();
		complete = true;
	}
	
	void commit() {
		if (complete) return;
		try {
			if (tx != null && txManager != null) try {
				txManager.commit(tx);
				complete = true;
			} catch (TransactionException e) {
				throw new DatabaseException(e);
			}
		} finally {
			if (broker != null) db.releaseBroker(broker);
		}
	}
	
	void abortIfIncomplete() {
		if (complete) return;
		if (tx != null && txManager != null) txManager.abort(tx);
		if (broker != null) db.releaseBroker(broker);
		complete = true;
	}
	
	void lockWrite(DocumentImpl doc) {
		try {
			tx.acquireLock(doc.getUpdateLock(), LockMode.WRITE_LOCK);
		} catch (LockException e) {
			throw new DatabaseException(e);
		}
	}
	
	void lockRead(DocumentImpl doc) {
		try {
			tx.acquireLock(doc.getUpdateLock(), LockMode.READ_LOCK);
		} catch (LockException e) {
			throw new DatabaseException(e);
		}		
	}
}
