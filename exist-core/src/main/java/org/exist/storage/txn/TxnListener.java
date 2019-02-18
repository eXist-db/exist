package org.exist.storage.txn;

public interface TxnListener {

    public void commit();

    public void abort();
}
