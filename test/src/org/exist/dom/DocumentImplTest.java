package org.exist.dom;

import org.exist.Database;
import org.exist.security.Group;
import org.exist.security.internal.RealmImpl;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.exist.security.PermissionFactory;
import org.exist.security.SecurityManager;
import org.easymock.classextension.EasyMock;
import org.exist.security.Permission;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;
import static org.easymock.classextension.EasyMock.expect;
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
        Subject mockCurrentSubject = EasyMock.createMock(Subject.class);
        Group mockCurrentSubjectGroup= EasyMock.createMock(Group.class);
        SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        PermissionFactory.sm = mockSecurityManager;

        //test values
        final DocumentMetadata otherMetadata = new DocumentMetadata();

        //expectations
        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(2);
        expect(mockDatabase.getSubject()).andReturn(mockCurrentSubject).times(2);
        expect(mockCurrentSubject.getUserMask()).andReturn(Permission.DEFAULT_UMASK).times(2);
        expect(mockCurrentSubject.getId()).andReturn(RealmImpl.SYSTEM_ACCOUNT_ID).times(2);
        expect(mockCurrentSubject.getDefaultGroup()).andReturn(mockCurrentSubjectGroup).times(2);
        expect(mockCurrentSubjectGroup.getId()).andReturn(RealmImpl.DBA_GROUP_ID).times(2);

        replay(mockBrokerPool, mockDatabase, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //test setup
        TestableDocumentImpl doc = new TestableDocumentImpl(mockBrokerPool);
        DocumentImpl other = new DocumentImpl(mockBrokerPool);
        other.setMetadata(otherMetadata);

        //actions
        doc.copyOf(other, false);

        verify(mockBrokerPool, mockDatabase, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //assertions
        assertEquals(1, doc.getMetadata_invCount());
    }

    @Test
    public void copyOf_calls_metadata_copyOf() {
        BrokerPool mockBrokerPool = EasyMock.createMock(BrokerPool.class);
        Database mockDatabase = EasyMock.createMock(Database.class);
        Subject mockCurrentSubject = EasyMock.createMock(Subject.class);
        Group mockCurrentSubjectGroup= EasyMock.createMock(Group.class);
        SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        PermissionFactory.sm = mockSecurityManager;

        //test values
        final TestableDocumentMetadata docMetadata = new TestableDocumentMetadata();
        final DocumentMetadata otherMetadata = new DocumentMetadata();

        //expectations
        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(2);
        expect(mockDatabase.getSubject()).andReturn(mockCurrentSubject).times(2);
        expect(mockCurrentSubject.getUserMask()).andReturn(Permission.DEFAULT_UMASK).times(2);
        expect(mockCurrentSubject.getId()).andReturn(RealmImpl.SYSTEM_ACCOUNT_ID).times(2);
        expect(mockCurrentSubject.getDefaultGroup()).andReturn(mockCurrentSubjectGroup).times(2);
        expect(mockCurrentSubjectGroup.getId()).andReturn(RealmImpl.DBA_GROUP_ID).times(2);

        replay(mockBrokerPool, mockDatabase, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //test setup
        DocumentImpl doc = new DocumentImpl(mockBrokerPool);
        doc.setMetadata(docMetadata);
        DocumentImpl other = new DocumentImpl(mockBrokerPool);
        other.setMetadata(otherMetadata);

        //actions
        doc.copyOf(other, false);

        verify(mockBrokerPool, mockDatabase, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //assertions
        assertEquals(1, docMetadata.getCopyOf_invCount());
    }

    @Test
    public void copyOf_updates_metadata_created_and_lastModified() {
        BrokerPool mockBrokerPool = EasyMock.createMock(BrokerPool.class);
        Database mockDatabase = EasyMock.createMock(Database.class);
        Subject mockCurrentSubject = EasyMock.createMock(Subject.class);
        Group mockCurrentSubjectGroup= EasyMock.createMock(Group.class);
        SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        PermissionFactory.sm = mockSecurityManager;

        //test values
        final DocumentMetadata docMetadata = new TestableDocumentMetadata();
        final DocumentMetadata otherMetadata = new DocumentMetadata();
        final long otherCreated = System.currentTimeMillis() - 2000;
        final long otherLastModified = System.currentTimeMillis() - 1000;

        //expectations
        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(2);
        expect(mockDatabase.getSubject()).andReturn(mockCurrentSubject).times(2);
        expect(mockCurrentSubject.getUserMask()).andReturn(Permission.DEFAULT_UMASK).times(2);
        expect(mockCurrentSubject.getId()).andReturn(RealmImpl.SYSTEM_ACCOUNT_ID).times(2);
        expect(mockCurrentSubject.getDefaultGroup()).andReturn(mockCurrentSubjectGroup).times(2);
        expect(mockCurrentSubjectGroup.getId()).andReturn(RealmImpl.DBA_GROUP_ID).times(2);

        replay(mockBrokerPool, mockDatabase, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //test setup
        DocumentImpl doc = new DocumentImpl(mockBrokerPool);
        doc.setMetadata(docMetadata);
        DocumentImpl other = new DocumentImpl(mockBrokerPool);
        other.setMetadata(otherMetadata);

        //actions
        doc.copyOf(other, false);

        verify(mockBrokerPool, mockDatabase, mockCurrentSubject, mockCurrentSubjectGroup, mockSecurityManager);

        //assertions
        assertThat(otherCreated, new LessThan(docMetadata.getCreated()));
        assertThat(otherLastModified, new LessThan(docMetadata.getLastModified()));
    }

    public class TestableDocumentImpl extends DocumentImpl {

        private int getMetadata_invCount = 0;
        private DocumentMetadata meta = new DocumentMetadata();

        public TestableDocumentImpl(BrokerPool pool) {
            super(pool);
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