package org.exist.storage.txn;

import java.nio.ByteBuffer;

import org.exist.storage.DBBroker;
import org.exist.storage.journal.AbstractLoggable;
import org.exist.storage.journal.LogEntryTypes;

public class TxnAbort extends AbstractLoggable {

	public TxnAbort(long transactionId) {
        this(null, transactionId);
    }
    
    public TxnAbort(DBBroker broker, long transactionId) {
        super(LogEntryTypes.TXN_ABORT, transactionId);
    }
    
	public void write(ByteBuffer out) {
	}

	public void read(ByteBuffer in) {

	}

	public int getLogSize() {
		return 0;
	}

	public String dump() {
		return super.dump() + " - transaction " + transactId + " aborted.";
	}
}
