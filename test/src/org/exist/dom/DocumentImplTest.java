package org.exist.dom;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.exist.security.PermissionFactory;
import org.exist.security.SecurityManager;
import org.easymock.classextension.EasyMock;
import static org.easymock.classextension.EasyMock.expect;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;
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

        SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        PermissionFactory.sm = mockSecurityManager;

        //constructor logic expectations
        expect(mockSecurityManager.getSystemSubject()).andReturn(null);
        expect(mockSecurityManager.getDBAGroup()).andReturn(null);
        expect(mockSecurityManager.getSystemSubject()).andReturn(null);
        expect(mockSecurityManager.getDBAGroup()).andReturn(null);

        //test values
        final DocumentMetadata otherMetadata = new DocumentMetadata();

        replay(mockSecurityManager);

        //test setup
        TestableDocumentImpl doc = new TestableDocumentImpl();
        DocumentImpl other = new DocumentImpl(null);
        other.setMetadata(otherMetadata);

        //actions
        doc.copyOf(other);

        verify(mockSecurityManager);

        //assertions
        assertEquals(1, doc.getMetadata_invCount());
    }

    @Test
    public void copyOf_calls_metadata_copyOf() {
        SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        PermissionFactory.sm = mockSecurityManager;

        //constructor logic expectations
        expect(mockSecurityManager.getSystemSubject()).andReturn(null);
        expect(mockSecurityManager.getDBAGroup()).andReturn(null);
        expect(mockSecurityManager.getSystemSubject()).andReturn(null);
        expect(mockSecurityManager.getDBAGroup()).andReturn(null);

        //test values
        final TestableDocumentMetadata docMetadata = new TestableDocumentMetadata();
        final DocumentMetadata otherMetadata = new DocumentMetadata();

        replay(mockSecurityManager);

        //test setup
        DocumentImpl doc = new DocumentImpl(null);
        doc.setMetadata(docMetadata);
        DocumentImpl other = new DocumentImpl(null);
        other.setMetadata(otherMetadata);

        //actions
        doc.copyOf(other);

        verify(mockSecurityManager);

        //assertions
        assertEquals(1, docMetadata.getCopyOf_invCount());
    }

    @Test
    public void copyOf_updates_metadata_created_and_lastModified() {
        SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        PermissionFactory.sm = mockSecurityManager;

        //constructor logic expectations
        expect(mockSecurityManager.getSystemSubject()).andReturn(null);
        expect(mockSecurityManager.getDBAGroup()).andReturn(null);
        expect(mockSecurityManager.getSystemSubject()).andReturn(null);
        expect(mockSecurityManager.getDBAGroup()).andReturn(null);

        //test values
        final DocumentMetadata docMetadata = new TestableDocumentMetadata();
        final DocumentMetadata otherMetadata = new DocumentMetadata();
        final long otherCreated = System.currentTimeMillis() - 2000;
        final long otherLastModified = System.currentTimeMillis() - 1000;

        replay(mockSecurityManager);

        //test setup
        DocumentImpl doc = new DocumentImpl(null);
        doc.setMetadata(docMetadata);
        DocumentImpl other = new DocumentImpl(null);
        other.setMetadata(otherMetadata);

        //actions
        doc.copyOf(other);

        verify(mockSecurityManager);

        //assertions
        assertThat(otherCreated, new LessThan(docMetadata.getCreated()));
        assertThat(otherLastModified, new LessThan(docMetadata.getLastModified()));
    }

    public class TestableDocumentImpl extends DocumentImpl {

        private int getMetadata_invCount = 0;
        private DocumentMetadata meta = new DocumentMetadata();

        public TestableDocumentImpl() {
            super(null);
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
