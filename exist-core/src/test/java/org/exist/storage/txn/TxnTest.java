package org.exist.storage.txn;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.EXistException;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@RunWith(ParallelRunner.class)
public class TxnTest {

    final TransactionManagerTestHelper helper = new TransactionManagerTestHelper();

    @Test
    public void commitTransaction() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn transaction = transact.beginTransaction();

        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        transaction.commit();

        assertEquals(Txn.State.COMMITTED, transaction.getState());

        assertEquals(1, listener.getCommit());
        assertEquals(0, listener.getAbort());

        helper.verifyMocks();
    }

    @Test
    public void commitAndCloseTransaction() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn transaction = transact.beginTransaction();

        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        transaction.commit();
        transaction.close();

        assertEquals(Txn.State.CLOSED, transaction.getState());

        assertEquals(1, listener.getCommit());
        assertEquals(0, listener.getAbort());

        helper.verifyMocks();
    }

    @Test
    public void abortTransaction() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn transaction = transact.beginTransaction();

        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        transaction.abort();

        assertEquals(Txn.State.ABORTED, transaction.getState());

        assertEquals(0, listener.getCommit());
        assertEquals(1, listener.getAbort());

        helper.verifyMocks();
    }

    @Test
    public void abortAndCloseTransaction() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn transaction = transact.beginTransaction();

        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        transaction.abort();
        transaction.close();

        assertEquals(Txn.State.CLOSED, transaction.getState());

        assertEquals(0, listener.getCommit());
        assertEquals(1, listener.getAbort());

        helper.verifyMocks();
    }

    @Test
    public void repeatedAbortOnlyAbortsTransactionOnce() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn transaction = transact.beginTransaction();

        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        //call 3 times, abort count should be one!
        transaction.abort();
        transaction.abort();
        transaction.abort();

        assertEquals(Txn.State.ABORTED, transaction.getState());

        assertEquals(0, listener.getCommit());
        assertEquals(1, listener.getAbort());

        helper.verifyMocks();
    }

    @Test
    public void closeWithoutCommitAbortsTransaction() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn transaction = transact.beginTransaction();

        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        transaction.close();

        assertEquals(Txn.State.CLOSED, transaction.getState());

        assertEquals(0, listener.getCommit());
        assertEquals(1, listener.getAbort());

        helper.verifyMocks();
    }

    @Test
    public void repeatedCloseWithoutCommitOnlyAbortsTransactionOnce() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = helper.createTestableTransactionManager();

        final Txn transaction = transact.beginTransaction();

        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        //call 3 times, abort count should be one!
        transaction.close();
        transaction.close();
        transaction.close();

        assertEquals(Txn.State.CLOSED, transaction.getState());

        assertEquals(0, listener.getCommit());
        assertEquals(1, listener.getAbort());

        helper.verifyMocks();
    }
}
