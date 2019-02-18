package org.exist.storage.txn;

public class CountingTxnListener implements TxnListener {

    private int commit = 0;
    private int abort = 0;

    @Override
    public void commit() {
        commit++;
    }

    @Override
    public void abort() {
        abort++;
    }

    public int getCommit() {
        return commit;
    }

    public int getAbort() {
        return abort;
    }
}
