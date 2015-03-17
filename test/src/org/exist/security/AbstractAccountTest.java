package org.exist.security;

import java.util.ArrayList;
import java.util.List;
import org.easymock.classextension.EasyMock;
import static org.easymock.classextension.EasyMock.expect;
import static org.easymock.classextension.EasyMock.verify;
import static org.easymock.classextension.EasyMock.replay;
import org.exist.Database;
import org.exist.config.ConfigurationException;
import org.junit.Test;

/**
 *
 * @author aretter
 */
public class AbstractAccountTest {

    @Test
    public void addGroup_calls_assertCanModifyGroup() throws PermissionDeniedException, NoSuchMethodException {
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Database mockDatabase = EasyMock.createMock(Database.class);
        Subject mockSubject = EasyMock.createMock(Subject.class);
        Group mockGroup = EasyMock.createMock(Group.class);
        Account partialMockAccount = EasyMock.createMockBuilder(AbstractAccount.class)
                .withConstructor(AbstractRealm.class, int.class, String.class)
                .withArgs(mockRealm, 1, "testAccount")
                .addMockedMethod(AbstractGroup.class.getDeclaredMethod("_addManager", Account.class))
                .createMock();

        //expectations
        expect(mockRealm.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getSubject()).andReturn(mockSubject);
        mockGroup.assertCanModifyGroup(mockSubject);

        replay(mockRealm, mockDatabase, mockGroup, partialMockAccount);

        //test
        partialMockAccount.addGroup(mockGroup);

        verify(mockRealm, mockDatabase, mockGroup, partialMockAccount);

        //TODO calls on assert from AbstractAccountXQuerty
    }

    @Test
    public void remGroup_calls_assertCanModifyGroupForEachGroup() throws PermissionDeniedException, NoSuchMethodException, ConfigurationException {
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Database mockDatabase = EasyMock.createMock(Database.class);
        Subject mockSubject = EasyMock.createMock(Subject.class);
        Group mockGroup = EasyMock.createMock(Group.class);
        final String groupName = "testGroup";

        TestableAbstractAccount partialMockAccount = new TestableAbstractAccount(mockRealm, 1, "testGroup");
        List<Group> groups = new ArrayList<Group>();
        groups.add(mockGroup);
        partialMockAccount.setInternalGroups(groups);

        //expectations
        expect(mockRealm.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getSubject()).andReturn(mockSubject);
        expect(mockGroup.getName()).andReturn(groupName);
        mockGroup.assertCanModifyGroup(mockSubject);

        replay(mockRealm, mockDatabase, mockGroup);

        //test
        partialMockAccount.remGroup(groupName);

        verify(mockRealm, mockDatabase, mockGroup);

        //TODO calls on assert from AbstractAccountXQuerty
    }

    @Test(expected=PermissionDeniedException.class)
    public void assertCanModifyAccount_fails_when_user_is_null() throws PermissionDeniedException, ConfigurationException {
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);

        TestableAbstractAccount account = new TestableAbstractAccount(mockRealm, 1, "testAccount");

        account.assertCanModifyAccount(null);
    }

    @Test
    public void assertCanModifyAccount_succeeds_when_user_is_dba() throws PermissionDeniedException, ConfigurationException {
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Account mockAccount = EasyMock.createMock(Account.class);
        TestableAbstractAccount account = new TestableAbstractAccount(mockRealm, 1, "testAccount");

        //expectations
        expect(mockAccount.hasDbaRole()).andReturn(Boolean.TRUE);


        replay(mockAccount);

        //test
        account.assertCanModifyAccount(mockAccount);

        verify(mockAccount);
    }

    @Test(expected=PermissionDeniedException.class)
    public void assertCanModifyAccount_fails_when_user_is_not_dba() throws PermissionDeniedException, ConfigurationException {
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Account mockAccount = EasyMock.createMock(Account.class);
        TestableAbstractAccount account = new TestableAbstractAccount(mockRealm, 1, "testAccount");

        //expectations
        expect(mockAccount.hasDbaRole()).andReturn(Boolean.FALSE);
        expect(mockAccount.getName()).andReturn("test").times(2);

        replay(mockAccount);

        //test
        account.assertCanModifyAccount(mockAccount);

        verify(mockAccount);
    }

    @Test
    public void assertCanModifyAccount_succeeds_when_user_is_same() throws PermissionDeniedException, ConfigurationException {
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Account mockAccount = EasyMock.createMock(Account.class);
        final String accountName = "testAccount";
        TestableAbstractAccount account = new TestableAbstractAccount(mockRealm, 1, accountName);

        //expectations
        expect(mockAccount.hasDbaRole()).andReturn(Boolean.FALSE);
        expect(mockAccount.getName()).andReturn(accountName);

        replay(mockAccount);

        //test
        account.assertCanModifyAccount(mockAccount);

        verify(mockAccount);
    }

    @Test(expected=PermissionDeniedException.class)
    public void assertCanModifyAccount_fails_when_user_is_not_same() throws PermissionDeniedException, ConfigurationException {
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Account mockAccount = EasyMock.createMock(Account.class);
        TestableAbstractAccount account = new TestableAbstractAccount(mockRealm, 1, "testAccount");

        //expectations
        expect(mockAccount.hasDbaRole()).andReturn(Boolean.FALSE);
        expect(mockAccount.getName()).andReturn("otherAccount").times(2);

        replay(mockAccount);

        //test
        account.assertCanModifyAccount(mockAccount);

        verify(mockAccount);
    }

    public class TestableAbstractAccount extends AbstractAccount {

        public TestableAbstractAccount(AbstractRealm realm, int id, String name) throws ConfigurationException {
            super(realm, id, name);
        }

        public void setInternalGroups(List<Group> groups) {
            this.groups = groups;
        }

        @Override
        public String getDigestPassword() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String getPassword() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setPassword(String passwd) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setCredential(Credential credential) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
