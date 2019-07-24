package org.exist.storage.txn;

import org.exist.EXistException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class ReusableTxnTest {

    final TransactionManagerTestHelper helper = new TransactionManagerTestHelper();

    @Test
    public void commitTransactionHasNoEffect() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn realTransaction = transact.beginTransaction();
        final CountingTxnListener realListener = new CountingTxnListener();
        realTransaction.registerListener(realListener);

        final Txn transaction = new Txn.ReusableTxn(realTransaction);
        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        transaction.commit();

        assertEquals(Txn.State.STARTED, transaction.getState());
        assertEquals(0, listener.getCommit());
        assertEquals(0, listener.getAbort());

        assertEquals(Txn.State.STARTED, realTransaction.getState());
        assertEquals(0, realListener.getCommit());
        assertEquals(0, realListener.getAbort());

        helper.verifyMocks();
    }

    @Test
    public void commitAndCloseTransactionHasNoEffect() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn realTransaction = transact.beginTransaction();
        final CountingTxnListener realListener = new CountingTxnListener();
        realTransaction.registerListener(realListener);

        final Txn transaction = new Txn.ReusableTxn(realTransaction);
        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        transaction.commit();
        transaction.close();

        assertEquals(Txn.State.STARTED, transaction.getState());
        assertEquals(0, listener.getCommit());
        assertEquals(0, listener.getAbort());

        assertEquals(Txn.State.STARTED, realTransaction.getState());
        assertEquals(0, realListener.getCommit());
        assertEquals(0, realListener.getAbort());

        helper.verifyMocks();
    }

    @Test
    public void abortTransaction() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn realTransaction = transact.beginTransaction();
        final CountingTxnListener realListener = new CountingTxnListener();
        realTransaction.registerListener(realListener);

        final Txn transaction = new Txn.ReusableTxn(realTransaction);
        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        transaction.abort();

        assertEquals(Txn.State.ABORTED, transaction.getState());
        assertEquals(0, listener.getCommit());
        assertEquals(1, listener.getAbort());

        assertEquals(Txn.State.ABORTED, realTransaction.getState());
        assertEquals(0, realListener.getCommit());
        assertEquals(1, realListener.getAbort());

        helper.verifyMocks();
    }

    @Test
    public void abortAndCloseTransaction() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn realTransaction = transact.beginTransaction();
        final CountingTxnListener realListener = new CountingTxnListener();
        realTransaction.registerListener(realListener);

        final Txn transaction = new Txn.ReusableTxn(realTransaction);
        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        transaction.abort();
        transaction.close();

        assertEquals(Txn.State.CLOSED, transaction.getState());
        assertEquals(0, listener.getCommit());
        assertEquals(1, listener.getAbort());

        assertEquals(Txn.State.CLOSED, realTransaction.getState());
        assertEquals(0, realListener.getCommit());
        assertEquals(1, realListener.getAbort());

        helper.verifyMocks();
    }

    @Test
    public void repeatedAbortOnlyAbortsTransactionOnce() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn realTransaction = transact.beginTransaction();
        final CountingTxnListener realListener = new CountingTxnListener();
        realTransaction.registerListener(realListener);

        final Txn transaction = new Txn.ReusableTxn(realTransaction);
        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        //call 3 times, abort count should be one!
        transaction.abort();
        transaction.abort();
        transaction.abort();

        assertEquals(Txn.State.ABORTED, transaction.getState());

        assertEquals(0, listener.getCommit());
        assertEquals(1, listener.getAbort());

        assertEquals(Txn.State.ABORTED, realTransaction.getState());

        assertEquals(0, realListener.getCommit());
        assertEquals(1, realListener.getAbort());

        helper.verifyMocks();
    }

    @Test
    public void closeWithoutCommitAbortsTransaction() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn realTransaction = transact.beginTransaction();
        final CountingTxnListener realListener = new CountingTxnListener();
        realTransaction.registerListener(realListener);

        final Txn transaction = new Txn.ReusableTxn(realTransaction);
        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        transaction.close();

        assertEquals(Txn.State.CLOSED, transaction.getState());

        assertEquals(0, listener.getCommit());
        assertEquals(1, listener.getAbort());

        assertEquals(Txn.State.CLOSED, realTransaction.getState());

        assertEquals(0, realListener.getCommit());
        assertEquals(1, realListener.getAbort());

        helper.verifyMocks();
    }

    @Test
    public void repeatedCloseWithoutCommitOnlyAbortsTransactionOnce() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn realTransaction = transact.beginTransaction();
        final CountingTxnListener realListener = new CountingTxnListener();
        realTransaction.registerListener(realListener);

        final Txn transaction = new Txn.ReusableTxn(realTransaction);
        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        //call 3 times, abort count should be one!
        transaction.close();
        transaction.close();
        transaction.close();

        assertEquals(Txn.State.CLOSED, transaction.getState());

        assertEquals(0, listener.getCommit());
        assertEquals(1, listener.getAbort());

        assertEquals(Txn.State.CLOSED, realTransaction.getState());

        assertEquals(0, realListener.getCommit());
        assertEquals(1, realListener.getAbort());

        helper.verifyMocks();
    }

    @Test
    public void closeWithoutCommitOnMultipleReusableTransactionOnlyAbortsTransactionOnce() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn realTransaction = transact.beginTransaction();
        final CountingTxnListener realListener = new CountingTxnListener();
        realTransaction.registerListener(realListener);

        final Txn transaction1 = new Txn.ReusableTxn(realTransaction);
        final CountingTxnListener listener1 = new CountingTxnListener();
        transaction1.registerListener(listener1);

        final Txn transaction2 = new Txn.ReusableTxn(realTransaction);

        transaction1.close();

        final CountingTxnListener listener2 = new CountingTxnListener();
        transaction2.registerListener(listener2);

        transaction2.close();

        assertEquals(0, listener1.getCommit());
        assertEquals(1, listener1.getAbort());

        assertEquals(0, listener2.getCommit());
        assertEquals(0, listener2.getAbort());

        assertEquals(0, realListener.getCommit());
        assertEquals(1, realListener.getAbort());

        helper.verifyMocks();
    }

    @Test
    public void abortOnMultipleReusableTransactionOnlyAbortsTransactionOnce() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn realTransaction = transact.beginTransaction();
        final CountingTxnListener realListener = new CountingTxnListener();
        realTransaction.registerListener(realListener);

        final Txn transaction1 = new Txn.ReusableTxn(realTransaction);
        final CountingTxnListener listener1 = new CountingTxnListener();
        transaction1.registerListener(listener1);

        final Txn transaction2 = new Txn.ReusableTxn(realTransaction);

        transaction1.abort();

        final CountingTxnListener listener2 = new CountingTxnListener();
        transaction2.registerListener(listener2);

        transaction2.abort();

        assertEquals(0, listener1.getCommit());
        assertEquals(1, listener1.getAbort());

        assertEquals(0, listener2.getCommit());
        assertEquals(0, listener2.getAbort());

        assertEquals(0, realListener.getCommit());
        assertEquals(1, realListener.getAbort());

        helper.verifyMocks();
    }
}
