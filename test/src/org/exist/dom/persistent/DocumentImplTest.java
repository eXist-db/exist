/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.dom.persistent;

import org.exist.Database;
import org.exist.security.Group;
import org.exist.security.internal.RealmImpl;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.exist.security.SecurityManager;
import org.easymock.EasyMock;
import org.exist.security.Permission;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.expect;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 *
 * @author aretter
 */
public class DocumentImplTest {

    @Test
    public void copyOf_calls_getMetadata() {

        BrokerPool mockBrokerPool = EasyMock.createMock(BrokerPool.class);
        Database mockDatabase = EasyMock.createMock(Database.class);
        DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        Subject mockCurrentSubject = EasyMock.createMock(Subject.class);
        Group mockCurrentSubjectGroup= EasyMock.createMock(Group.class);
        SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        //test values
        final DocumentMetadata otherMetadata = new DocumentMetadata();

        //expectations
        expect(mockBrokerPool.getSecurityManager()).andReturn(mockSecurityManager).times(2);
        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(2);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker).times(2);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject).times(2);
        expect(mockCurrentSubject.getUserMask()).andReturn(Permission.DEFAULT_UMASK).times(2);
        expect(mockCurrentSubject.getId()).andReturn(RealmImpl.SYSTEM_ACCOUNT_ID).times(2);
        expect(mockCurrentSubject.getDefaultGroup()).andReturn(mockCurrentSubjectGroup).times(2);
        expect(mockCurrentSubjectGroup.getId()).andReturn(RealmImpl.DBA_GROUP_ID).times(2);

        replay(mockBrokerPool, mockDatabase, mockBroker, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //test setup
        TestableDocumentImpl doc = new TestableDocumentImpl(mockBrokerPool);
        DocumentImpl other = new DocumentImpl(mockBrokerPool, -1, null, null);
        other.setMetadata(otherMetadata);

        //actions
        doc.copyOf(other, false);

        verify(mockBrokerPool, mockDatabase, mockBroker, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //assertions
        assertEquals(1, doc.getMetadata_invCount());
    }

    @Test
    public void copyOf_calls_metadata_copyOf() {
        BrokerPool mockBrokerPool = EasyMock.createMock(BrokerPool.class);
        Database mockDatabase = EasyMock.createMock(Database.class);
        DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        Subject mockCurrentSubject = EasyMock.createMock(Subject.class);
        Group mockCurrentSubjectGroup= EasyMock.createMock(Group.class);
        SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        //test values
        final TestableDocumentMetadata docMetadata = new TestableDocumentMetadata();
        final DocumentMetadata otherMetadata = new DocumentMetadata();

        //expectations
        expect(mockBrokerPool.getSecurityManager()).andReturn(mockSecurityManager).times(2);
        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(2);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker).times(2);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject).times(2);
        expect(mockCurrentSubject.getUserMask()).andReturn(Permission.DEFAULT_UMASK).times(2);
        expect(mockCurrentSubject.getId()).andReturn(RealmImpl.SYSTEM_ACCOUNT_ID).times(2);
        expect(mockCurrentSubject.getDefaultGroup()).andReturn(mockCurrentSubjectGroup).times(2);
        expect(mockCurrentSubjectGroup.getId()).andReturn(RealmImpl.DBA_GROUP_ID).times(2);

        replay(mockBrokerPool, mockDatabase, mockBroker, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //test setup
        DocumentImpl doc = new DocumentImpl(mockBrokerPool, -1, null, null);
        doc.setMetadata(docMetadata);
        DocumentImpl other = new DocumentImpl(mockBrokerPool, -1, null, null);
        other.setMetadata(otherMetadata);

        //actions
        doc.copyOf(other, false);

        verify(mockBrokerPool, mockDatabase, mockBroker, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //assertions
        assertEquals(1, docMetadata.getCopyOf_invCount());
    }

    @Test
    public void copyOf_updates_metadata_created_and_lastModified() {
        BrokerPool mockBrokerPool = EasyMock.createMock(BrokerPool.class);
        Database mockDatabase = EasyMock.createMock(Database.class);
        DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        Subject mockCurrentSubject = EasyMock.createMock(Subject.class);
        Group mockCurrentSubjectGroup= EasyMock.createMock(Group.class);
        SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        //test values
        final DocumentMetadata docMetadata = new TestableDocumentMetadata();
        final DocumentMetadata otherMetadata = new DocumentMetadata();
        final long otherCreated = System.currentTimeMillis() - 2000;
        final long otherLastModified = System.currentTimeMillis() - 1000;

        //expectations
        expect(mockBrokerPool.getSecurityManager()).andReturn(mockSecurityManager).times(2);
        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(2);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker).times(2);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject).times(2);
        expect(mockCurrentSubject.getUserMask()).andReturn(Permission.DEFAULT_UMASK).times(2);
        expect(mockCurrentSubject.getId()).andReturn(RealmImpl.SYSTEM_ACCOUNT_ID).times(2);
        expect(mockCurrentSubject.getDefaultGroup()).andReturn(mockCurrentSubjectGroup).times(2);
        expect(mockCurrentSubjectGroup.getId()).andReturn(RealmImpl.DBA_GROUP_ID).times(2);

        replay(mockBrokerPool, mockDatabase, mockBroker, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //test setup
        DocumentImpl doc = new DocumentImpl(mockBrokerPool, -1, null, null);
        doc.setMetadata(docMetadata);
        DocumentImpl other = new DocumentImpl(mockBrokerPool, -1, null, null);
        other.setMetadata(otherMetadata);

        //actions
        doc.copyOf(other, false);

        verify(mockBrokerPool, mockDatabase, mockBroker, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //assertions
        assertThat(otherCreated, new LessThan(docMetadata.getCreated()));
        assertThat(otherLastModified, new LessThan(docMetadata.getLastModified()));
    }

    public class TestableDocumentImpl extends DocumentImpl {

        private int getMetadata_invCount = 0;
        private DocumentMetadata meta = new DocumentMetadata();

        public TestableDocumentImpl(BrokerPool pool) {
            super(pool, -1, null, null);
        }

        public int getMetadata_invCount() {
            return getMetadata_invCount;
        }

        @Override
        public DocumentMetadata getMetadata() {
            getMetadata_invCount++;
            return meta;
        }
    }

    public class TestableDocumentMetadata extends DocumentMetadata {

        private int copyOf_invCount = 0;

        public int getCopyOf_invCount() {
            return copyOf_invCount;
        }

        @Override
        public void copyOf(DocumentMetadata other) {
            copyOf_invCount++;
        }
    }

    public class LessThan extends BaseMatcher<Long> {
        private final Long actual;

        public LessThan(Long actual) {
            this.actual = actual;
        }

        @Override
        public boolean matches(Object expected) {
            if(!(expected instanceof Long)) {
                return false;
            }

            return ((Long)expected).compareTo(actual) < 0;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("Less than");
        }
    };
}