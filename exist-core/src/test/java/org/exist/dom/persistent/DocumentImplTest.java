package org.exist.dom.persistent;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.Database;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.internal.RealmImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.easymock.EasyMock;

import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author aretter
 */
@RunWith(ParallelRunner.class)
public class DocumentImplTest {

    @Test
    public void copyOf_calls_getMetadata() throws PermissionDeniedException {

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
        TestableDocumentImpl doc = new TestableDocumentImpl(mockBrokerPool, 888);
        DocumentImpl other = new DocumentImpl(mockBrokerPool, 999);
        other.setMetadata(otherMetadata);

        //actions
        doc.copyOf(mockBroker, other, (DocumentImpl)null);

        verify(mockBrokerPool, mockDatabase, mockBroker, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //assertions
        assertEquals(1, doc.getMetadata_invCount());
    }

    @Test
    public void copyOf_calls_metadata_copyOf() throws PermissionDeniedException {
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
        DocumentImpl doc = new DocumentImpl(mockBrokerPool, 888);
        doc.setMetadata(docMetadata);
        DocumentImpl other = new DocumentImpl(mockBrokerPool, 999);
        other.setMetadata(otherMetadata);

        //actions
        doc.copyOf(mockBroker, other, (DocumentImpl)null);

        verify(mockBrokerPool, mockDatabase, mockBroker, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //assertions
        assertEquals(1, docMetadata.getCopyOf_invCount());
    }

    @Test
    public void copyOf_updates_metadata_created_and_lastModified() throws PermissionDeniedException {
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
        DocumentImpl doc = new DocumentImpl(mockBrokerPool, 888);
        doc.setMetadata(docMetadata);
        DocumentImpl other = new DocumentImpl(mockBrokerPool, 999);
        other.setMetadata(otherMetadata);

        //actions
        doc.copyOf(mockBroker, other, (DocumentImpl)null);

        verify(mockBrokerPool, mockDatabase, mockBroker, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //assertions
        assertThat(otherCreated, new LessThan(docMetadata.getCreated()));
        assertThat(otherLastModified, new LessThan(docMetadata.getLastModified()));
    }

    @Test
    public void isSameNode_sameDoc() {
        final BrokerPool mockBrokerPool = EasyMock.createMock(BrokerPool.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);
        final Group mockCurrentSubjectGroup= EasyMock.createMock(Group.class);
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        //expectations
        expect(mockBrokerPool.getSecurityManager()).andReturn(mockSecurityManager);
        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject);
        expect(mockCurrentSubject.getUserMask()).andReturn(Permission.DEFAULT_UMASK);
        expect(mockCurrentSubject.getId()).andReturn(RealmImpl.SYSTEM_ACCOUNT_ID);
        expect(mockCurrentSubject.getDefaultGroup()).andReturn(mockCurrentSubjectGroup);
        expect(mockCurrentSubjectGroup.getId()).andReturn(RealmImpl.DBA_GROUP_ID);

        replay(mockBrokerPool, mockDatabase, mockBroker, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //test setup
        final DocumentImpl doc = new DocumentImpl(mockBrokerPool, 99);
        assertTrue(doc.isSameNode(doc));

        verify(mockBrokerPool, mockDatabase, mockBroker, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);
    }

    @Test
    public void isSameNode_differentDoc() {
        final BrokerPool mockBrokerPool = EasyMock.createMock(BrokerPool.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);
        final Group mockCurrentSubjectGroup= EasyMock.createMock(Group.class);
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

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
        final DocumentImpl doc = new DocumentImpl(mockBrokerPool, 99);

        final DocumentImpl doc2 = new DocumentImpl(mockBrokerPool, 765);

        assertFalse(doc.isSameNode(doc2));

        verify(mockBrokerPool, mockDatabase, mockBroker, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);
    }

    @Test
    public void isSameNode_nonDoc() {
        final BrokerPool mockBrokerPool = EasyMock.createMock(BrokerPool.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);
        final Group mockCurrentSubjectGroup= EasyMock.createMock(Group.class);
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        //expectations
        expect(mockBrokerPool.getSecurityManager()).andReturn(mockSecurityManager);
        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject);
        expect(mockCurrentSubject.getUserMask()).andReturn(Permission.DEFAULT_UMASK);
        expect(mockCurrentSubject.getId()).andReturn(RealmImpl.SYSTEM_ACCOUNT_ID);
        expect(mockCurrentSubject.getDefaultGroup()).andReturn(mockCurrentSubjectGroup);
        expect(mockCurrentSubjectGroup.getId()).andReturn(RealmImpl.DBA_GROUP_ID);

        replay(mockBrokerPool, mockDatabase, mockBroker, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //test setup
        final DocumentImpl doc = new DocumentImpl(mockBrokerPool, 99);

        final TextImpl text = new TextImpl("hello");

        assertFalse(doc.isSameNode(text));

        verify(mockBrokerPool, mockDatabase, mockBroker, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);
    }

    public class TestableDocumentImpl extends DocumentImpl {

        private int getMetadata_invCount = 0;
        private DocumentMetadata meta = new DocumentMetadata();

        public TestableDocumentImpl(BrokerPool pool, int docId) {
            super(pool, docId);
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
    }
}