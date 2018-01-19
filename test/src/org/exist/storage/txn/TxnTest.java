package org.exist.storage.txn;

import com.googlecode.junittoolbox.ParallelRunner;
import org.easymock.EasyMockSupport;
import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.NativeBroker;
import org.exist.storage.SystemTaskManager;
import org.exist.storage.journal.JournalManager;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;

/**
 * @author Adam Retter <adam.retter@googlemail.com>
 */
@RunWith(ParallelRunner.class)
public class TxnTest extends EasyMockSupport {

    private BrokerPool mockBrokerPool;
    private NativeBroker mockBroker;

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

        verify(mockBrokerPool, mockBroker);
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

        verify(mockBrokerPool, mockBroker);
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

        verify(mockBrokerPool, mockBroker);
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

        verify(mockBrokerPool, mockBroker);
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

        verify(mockBrokerPool, mockBroker);
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

        verify(mockBrokerPool, mockBroker);
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

        verify(mockBrokerPool, mockBroker);
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
        mockBrokerPool = createMock(BrokerPool.class);
        mockBroker = createMock(NativeBroker.class);
        expect(mockBrokerPool.getBroker()).andReturn(mockBroker).atLeastOnce();
        mockBroker.close();
        expectLastCall().atLeastOnce();

        final JournalManager mockJournalManager = createMock(JournalManager.class);
        final SystemTaskManager mockTaskManager = createMock(SystemTaskManager.class);

        replay(mockBrokerPool, mockBroker);

        return new TransactionManager(mockBrokerPool, Optional.of(mockJournalManager), mockTaskManager);
    }
}
