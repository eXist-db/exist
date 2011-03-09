package org.exist.security;

import java.util.ArrayList;
import java.util.List;
import org.easymock.classextension.EasyMock;
import org.exist.config.ConfigurationException;
import org.exist.config.Reference;
import org.exist.config.ReferenceImpl;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 *
 * @author aretter
 */
public class AbstractGroupTest {
    
    @Test
    public void isManager_retuns_true_when_manager() throws ConfigurationException {
        
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        Account mockAccount = EasyMock.createMock(Account.class);
        
        TestableGroupImpl group = new TestableGroupImpl(mockRealm);
        
        final List<Reference<SecurityManager, Account>> managers = new ArrayList<Reference<SecurityManager, Account>>();
        managers.add(new ReferenceImpl<SecurityManager, Account>(mockSecurityManager, mockAccount));
        group.setManagers(managers);
        
        
        final boolean result = group.isManager(mockAccount);
        assertTrue(result);
    }
    
    @Test
    public void isManager_returns_false_when_not_manager() throws ConfigurationException {

        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        Account mockAccount = EasyMock.createMock(Account.class);

        TestableGroupImpl group = new TestableGroupImpl(mockRealm);

        final List<Reference<SecurityManager, Account>> managers = new ArrayList<Reference<SecurityManager, Account>>();
        managers.add(new ReferenceImpl<SecurityManager, Account>(mockSecurityManager, mockAccount));
        group.setManagers(managers);

        final Account otherAccount = EasyMock.createMock(Account.class);

        final boolean result = group.isManager(otherAccount);
        assertFalse(result);
    }
    
    public class TestableGroupImpl extends AbstractGroup {
        public TestableGroupImpl(AbstractRealm realm) throws ConfigurationException {
            super(realm, 1, "testGroup", null);
        }

        @Override
        public void setManagers(List<Reference<SecurityManager, Account>> managers) {
            super.setManagers(managers);
        }
    }
}
