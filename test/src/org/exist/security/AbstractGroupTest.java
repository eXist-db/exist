package org.exist.security;

import org.exist.Database;
import java.util.ArrayList;
import java.util.List;
import org.easymock.classextension.EasyMock;
import static org.easymock.classextension.EasyMock.verify;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.expect;
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
        managers.add(new ReferenceImpl<SecurityManager, Account>(mockSecurityManager, mockAccount, "mockAccount"));
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
        managers.add(new ReferenceImpl<SecurityManager, Account>(mockSecurityManager, mockAccount, "mockAccount"));
        group.setManagers(managers);

        final Account otherAccount = EasyMock.createMock(Account.class);

        final boolean result = group.isManager(otherAccount);
        assertFalse(result);
    }

    @Test(expected=PermissionDeniedException.class)
    public void assertCanModifyGroup_fails_when_user_is_null() throws PermissionDeniedException, ConfigurationException {
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);

        TestableGroupImpl group = new TestableGroupImpl(mockRealm);

        group.assertCanModifyGroup(null);
    }

    @Test
    public void assertCanModifyGroup_succeeds_when_user_is_dba() throws PermissionDeniedException, ConfigurationException {
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Account mockAccount = EasyMock.createMock(Account.class);
        TestableGroupImpl group = new TestableGroupImpl(mockRealm);

        //expectations
        expect(mockAccount.hasDbaRole()).andReturn(Boolean.TRUE);

        
        replay(mockAccount);
        
        //test
        group.assertCanModifyGroup(mockAccount);

        verify(mockAccount);
    }

    @Test(expected=PermissionDeniedException.class)
    public void assertCanModifyGroup_fails_when_user_is_not_dba() throws PermissionDeniedException, ConfigurationException {
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Account mockAccount = EasyMock.createMock(Account.class);
        TestableGroupImpl group = new TestableGroupImpl(mockRealm);

        //expectations
        expect(mockAccount.hasDbaRole()).andReturn(Boolean.FALSE);
        expect(mockAccount.getName()).andReturn("test");

        replay(mockAccount);

        //test
        group.assertCanModifyGroup(mockAccount);

        verify(mockAccount);
    }

    @Test
    public void assertCanModifyGroup_succeeds_when_user_is_manager() throws PermissionDeniedException, ConfigurationException {
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Account mockAccount = EasyMock.createMock(Account.class);
        Group partialMockGroup = EasyMock.createMockBuilder(AbstractGroup.class)
                .withConstructor(AbstractRealm.class, int.class, String.class, List.class)
                .withArgs(mockRealm, 1, "testGroup", null)
                .addMockedMethod("isManager")
                .createNiceMock();

        //expectations
        expect(mockAccount.hasDbaRole()).andReturn(Boolean.FALSE);
        expect(partialMockGroup.isManager(mockAccount)).andReturn(Boolean.TRUE);

        replay(mockAccount, partialMockGroup);

        //test
        partialMockGroup.assertCanModifyGroup(mockAccount);

        verify(mockAccount, partialMockGroup);
    }

    @Test(expected=PermissionDeniedException.class)
    public void assertCanModifyGroup_fails_when_user_is_not_manager() throws PermissionDeniedException, ConfigurationException {
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Account mockAccount = EasyMock.createMock(Account.class);
        Group partialMockGroup = EasyMock.createMockBuilder(AbstractGroup.class)
                .withConstructor(AbstractRealm.class, int.class, String.class, List.class)
                .withArgs(mockRealm, 1, "testGroup", null)
                .addMockedMethod("isManager")
                .createMock();

        //expectations
        expect(mockAccount.hasDbaRole()).andReturn(Boolean.FALSE);
        expect(partialMockGroup.isManager(mockAccount)).andReturn(Boolean.FALSE);
        expect(mockAccount.getName()).andReturn("test");

        replay(mockAccount, partialMockGroup);

        //test
        partialMockGroup.assertCanModifyGroup(mockAccount);

        verify(mockAccount, partialMockGroup);
    }

    @Test
    public void addManager_calls_assertCanModifyGroup() throws PermissionDeniedException, NoSuchMethodException {
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Subject mockSubject = EasyMock.createMock(Subject.class);
        Database mockDatabase = EasyMock.createMock(Database.class);
        Group partialMockGroup = EasyMock.createMockBuilder(AbstractGroup.class)
                .withConstructor(AbstractRealm.class, int.class, String.class, List.class)
                .withArgs(mockRealm, 1, "testGroup", null)
                .addMockedMethod("assertCanModifyGroup", Account.class)
                .addMockedMethod(AbstractGroup.class.getDeclaredMethod("_addManager", Account.class))
                .createNiceMock();

        //expectations
        expect(mockRealm.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getSubject()).andReturn(mockSubject);
        partialMockGroup.assertCanModifyGroup(mockSubject);

        replay(mockRealm, mockDatabase, partialMockGroup);

        //test
        partialMockGroup.addManager((Account)null);

        verify(mockRealm, mockDatabase, partialMockGroup);
    }

    @Test
    public void addManagerWithString_calls_assertCanModifyGroup() throws PermissionDeniedException, NoSuchMethodException {
        AbstractRealm mockRealm = EasyMock.createNiceMock(AbstractRealm.class);
        Subject mockSubject = EasyMock.createMock(Subject.class);
        Database mockDatabase = EasyMock.createMock(Database.class);
        AbstractGroup partialMockGroup = EasyMock.createMockBuilder(AbstractGroup.class)
                .withConstructor(AbstractRealm.class, int.class, String.class, List.class)
                .withArgs(mockRealm, 1, "testGroup", null)
                .addMockedMethod("assertCanModifyGroup", Account.class)
                .addMockedMethod(AbstractGroup.class.getDeclaredMethod("_addManager", Account.class))
                .createNiceMock();

        //expectations
        expect(mockRealm.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getSubject()).andReturn(mockSubject);
        partialMockGroup.assertCanModifyGroup(mockSubject);

        replay(mockRealm, mockDatabase, partialMockGroup);

        //test
        partialMockGroup.addManager((String)null);

        verify(mockRealm, mockDatabase, partialMockGroup);
    }

    @Test
    public void removeManager_calls_assertCanModifyGroup() throws PermissionDeniedException, NoSuchMethodException {
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Subject mockSubject = EasyMock.createMock(Subject.class);
        Database mockDatabase = EasyMock.createMock(Database.class);
        Group partialMockGroup = EasyMock.createMockBuilder(AbstractGroup.class)
                .withConstructor(AbstractRealm.class, int.class, String.class, List.class)
                .withArgs(mockRealm, 1, "testGroup", null)
                .addMockedMethod("assertCanModifyGroup", Account.class)
                .addMockedMethod(AbstractGroup.class.getDeclaredMethod("_addManager", Account.class))
                .createNiceMock();

        //expectations
        expect(mockRealm.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getSubject()).andReturn(mockSubject);
        partialMockGroup.assertCanModifyGroup(mockSubject);

        replay(mockRealm, mockDatabase, partialMockGroup);

        //test
        partialMockGroup.removeManager((Account)null);

        verify(mockRealm, mockDatabase, partialMockGroup);
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
