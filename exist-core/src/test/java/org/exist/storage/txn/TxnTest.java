package org.exist.storage.txn;

import com.googlecode.junittoolbox.ParallelRunner;
import org.easymock.EasyMockSupport;
import org.exist.EXistException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
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

        verify(mockBrokerPool);
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

        verify(mockBrokerPool);
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

        verify(mockBrokerPool);
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

        verify(mockBrokerPool);
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

        verify(mockBrokerPool);
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

        verify(mockBrokerPool);
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

        verify(mockBrokerPool);
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

        final JournalManager mockJournalManager = createMock(JournalManager.class);
        final SystemTaskManager mockTaskManager = createMock(SystemTaskManager.class);

        final DBBroker mockBroker = createMock(DBBroker.class);
        final SecurityManager mockSecurityManager = createMock(SecurityManager.class);
        final Subject mockSystemSubject = createMock(Subject.class);
        expect(mockBrokerPool.get(Optional.of(mockSystemSubject))).andReturn(mockBroker);
        expect(mockBrokerPool.getSecurityManager()).andReturn(mockSecurityManager);
        expect(mockSecurityManager.getSystemSubject()).andReturn(mockSystemSubject);

        replay(mockBrokerPool, mockSecurityManager);

        return new TransactionManager(mockBrokerPool, Optional.of(mockJournalManager), mockTaskManager);
    }
}
