/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
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

    protected TransactionManager createTestableTransactionManager(final boolean expectTxnClose) throws NoSuchFieldException, IllegalAccessException, EXistException {
        mockBrokerPool = createMock(BrokerPool.class);
        mockBroker = createMock(NativeBroker.class);
        expect(mockBrokerPool.getBroker()).andReturn(mockBroker).atLeastOnce();
        mockBroker.addCurrentTransaction(anyObject());
        expectLastCall().atLeastOnce();
        if (expectTxnClose) {
            mockBroker.removeCurrentTransaction(anyObject());
            expectLastCall().atLeastOnce();
        }
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
