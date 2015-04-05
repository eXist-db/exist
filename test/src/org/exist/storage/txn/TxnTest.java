package org.exist.storage.txn;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.NativeBroker;
import org.exist.storage.SystemTaskManager;
import org.exist.storage.journal.Journal;
import org.junit.Test;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;

/**
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class TxnTest {

    @Test
    public void commitTransaction() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = createTestableTransactionManager();

        final Txn transaction = transact.beginTransaction();

        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        transaction.commit();

        assertEquals(Txn.State.COMMITTED, transaction.getState());

        assertEquals(1, listener.getCommit());
        assertEquals(0, listener.getAbort());
    }

    @Test
    public void commitAndCloseTransaction() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = createTestableTransactionManager();

        final Txn transaction = transact.beginTransaction();

        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        transaction.commit();
        transaction.close();

        assertEquals(Txn.State.CLOSED, transaction.getState());

        assertEquals(1, listener.getCommit());
        assertEquals(0, listener.getAbort());
    }

    @Test
    public void abortTransaction() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = createTestableTransactionManager();

        final Txn transaction = transact.beginTransaction();

        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        transaction.abort();

        assertEquals(Txn.State.ABORTED, transaction.getState());

        assertEquals(0, listener.getCommit());
        assertEquals(1, listener.getAbort());
    }

    @Test
    public void abortAndCloseTransaction() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = createTestableTransactionManager();

        final Txn transaction = transact.beginTransaction();

        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        transaction.abort();
        transaction.close();

        assertEquals(Txn.State.CLOSED, transaction.getState());

        assertEquals(0, listener.getCommit());
        assertEquals(1, listener.getAbort());
    }

    @Test
    public void repeatedAbortOnlyAbortsTransactionOnce() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = createTestableTransactionManager();

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
    }

    @Test
    public void closeWithoutCommitAbortsTransaction() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = createTestableTransactionManager();

        final Txn transaction = transact.beginTransaction();

        final CountingTxnListener listener = new CountingTxnListener();
        transaction.registerListener(listener);

        transaction.close();

        assertEquals(Txn.State.CLOSED, transaction.getState());

        assertEquals(0, listener.getCommit());
        assertEquals(1, listener.getAbort());
    }

    @Test
    public void repeatedCloseWithoutCommitOnlyAbortsTransactionOnce() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final TransactionManager transact = createTestableTransactionManager();

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
    }

    private class CountingTxnListener implements TxnListener {

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

    private TransactionManager createTestableTransactionManager() throws NoSuchFieldException, IllegalAccessException, EXistException {
        final BrokerPool mockBrokerPool = createMock(BrokerPool.class);
        final NativeBroker mockBroker = createMock(NativeBroker.class);
        expect(mockBrokerPool.get(null)).andReturn(mockBroker).anyTimes();
        mockBrokerPool.release(mockBroker);
        expectLastCall().anyTimes();
        expect(mockBrokerPool.getBroker()).andReturn(mockBroker).anyTimes();

        final Journal mockJournal = createMock(Journal.class);
        final SystemTaskManager mockTaskManager = createMock(SystemTaskManager.class);

        replay(mockBrokerPool);

        return new TransactionManager(mockBrokerPool, true, mockJournal, false, false, mockTaskManager);
    }
}
