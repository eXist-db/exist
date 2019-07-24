package org.exist.storage.txn;

import org.exist.EXistException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.NativeBroker;
import org.exist.storage.SystemTaskManager;
import org.exist.storage.journal.JournalManager;

import java.util.Optional;

import static org.easymock.EasyMock.*;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class TransactionManagerTestHelper {

    BrokerPool mockBrokerPool = null;
    NativeBroker mockBroker = null;

    protected TransactionManager createTestableTransactionManager() throws NoSuchFieldException, IllegalAccessException, EXistException {
        mockBrokerPool = createMock(BrokerPool.class);
        mockBroker = createMock(NativeBroker.class);
        expect(mockBrokerPool.getBroker()).andReturn(mockBroker).atLeastOnce();
        mockBroker.setCurrentTransaction(anyObject());
        expectLastCall().atLeastOnce();
        mockBroker.close();
        expectLastCall().atLeastOnce();
        final SecurityManager mockSecurityManager = createMock(SecurityManager.class);
        final Subject mockSystemSubject = createMock(Subject.class);
        expect(mockBrokerPool.get(Optional.of(mockSystemSubject))).andReturn(mockBroker).anyTimes();
        expect(mockBrokerPool.getSecurityManager()).andReturn(mockSecurityManager).anyTimes();
        expect(mockSecurityManager.getSystemSubject()).andReturn(mockSystemSubject).anyTimes();

        final JournalManager mockJournalManager = createMock(JournalManager.class);
        final SystemTaskManager mockTaskManager = createMock(SystemTaskManager.class);

        replay(mockBrokerPool, mockBroker, mockSecurityManager);

        return new TransactionManager(mockBrokerPool, Optional.of(mockJournalManager), mockTaskManager);
    }

    protected void verifyMocks() {
	verify(mockBrokerPool, mockBroker);
    }
}
