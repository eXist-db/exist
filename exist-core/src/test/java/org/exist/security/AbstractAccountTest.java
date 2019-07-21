package org.exist.security;

import java.util.ArrayList;
import java.util.List;
import org.easymock.EasyMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

import org.exist.Database;
import org.exist.config.ConfigurationException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.junit.Test;

/**
 *
 * @author aretter
 */
public class AbstractAccountTest {

    @Test
    public void addGroup_calls_assertCanModifyGroup() throws PermissionDeniedException, NoSuchMethodException {
        DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Database mockDatabase = EasyMock.createMock(Database.class);
        Subject mockSubject = EasyMock.createMock(Subject.class);
        Group mockGroup = EasyMock.createMock(Group.class);
        Account partialMockAccount = EasyMock.createMockBuilder(AbstractAccount.class)
                .withConstructor(DBBroker.class, AbstractRealm.class, int.class, String.class)
                .withArgs(mockBroker, mockRealm, 1, "testAccount")
                .addMockedMethod(AbstractGroup.class.getDeclaredMethod("_addManager", Account.class))
                .createMock();

        //expectations
        expect(mockRealm.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker);
        expect(mockBroker.getCurrentSubject()).andReturn(mockSubject);
        mockGroup.assertCanModifyGroup(mockSubject);
        expect(mockGroup.getName()).andReturn("testGroup");

        replay(mockRealm, mockDatabase, mockBroker, mockGroup, partialMockAccount);

        //test
        partialMockAccount.addGroup(mockGroup);

        verify(mockRealm, mockDatabase, mockBroker, mockGroup, partialMockAccount);

        //TODO calls on assert from AbstractAccountXQuery
    }

    @Test
    public void remGroup_calls_assertCanModifyGroupForEachGroup() throws PermissionDeniedException, ConfigurationException {
        DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Database mockDatabase = EasyMock.createMock(Database.class);
        Subject mockSubject = EasyMock.createMock(Subject.class);
        Group mockGroup = EasyMock.createMock(Group.class);
        final String groupName = "testGroup";

        TestableAbstractAccount partialMockAccount = new TestableAbstractAccount(mockBroker, mockRealm, 1, "testGroup");
        List<Group> groups = new ArrayList<Group>();
        groups.add(mockGroup);
        partialMockAccount.setInternalGroups(groups);

        //expectations
        expect(mockRealm.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker);
        expect(mockBroker.getCurrentSubject()).andReturn(mockSubject);
        expect(mockGroup.getName()).andReturn(groupName);
        mockGroup.assertCanModifyGroup(mockSubject);

        replay(mockRealm, mockDatabase, mockBroker, mockGroup);

        //test
        try {
            partialMockAccount.remGroup(groupName);
        } catch (final PermissionDeniedException e) {
            assertEquals("You cannot remove the primary group of an account.", e.getMessage());
        }

        verify(mockRealm, mockDatabase, mockBroker, mockGroup);

        //TODO calls on assert from AbstractAccountXQuery
    }

    @Test(expected=PermissionDeniedException.class)
    public void assertCanModifyAccount_fails_when_user_is_null() throws PermissionDeniedException, ConfigurationException {
        DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);

        TestableAbstractAccount account = new TestableAbstractAccount(mockBroker, mockRealm, 1, "testAccount");

        account.assertCanModifyAccount(null);
    }

    @Test
    public void assertCanModifyAccount_succeeds_when_user_is_dba() throws PermissionDeniedException, ConfigurationException {
        DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Account mockAccount = EasyMock.createMock(Account.class);
        TestableAbstractAccount account = new TestableAbstractAccount(mockBroker, mockRealm, 1, "testAccount");

        //expectations
        expect(mockAccount.hasDbaRole()).andReturn(Boolean.TRUE);


        replay(mockAccount);

        //test
        account.assertCanModifyAccount(mockAccount);

        verify(mockAccount);
    }

    @Test(expected=PermissionDeniedException.class)
    public void assertCanModifyAccount_fails_when_user_is_not_dba() throws PermissionDeniedException, ConfigurationException {
        DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Account mockAccount = EasyMock.createMock(Account.class);
        TestableAbstractAccount account = new TestableAbstractAccount(mockBroker, mockRealm, 1, "testAccount");

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
        DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Account mockAccount = EasyMock.createMock(Account.class);
        final String accountName = "testAccount";
        TestableAbstractAccount account = new TestableAbstractAccount(mockBroker, mockRealm, 1, accountName);

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
        DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class);
        Account mockAccount = EasyMock.createMock(Account.class);
        TestableAbstractAccount account = new TestableAbstractAccount(mockBroker, mockRealm, 1, "testAccount");

        //expectations
        expect(mockAccount.hasDbaRole()).andReturn(Boolean.FALSE);
        expect(mockAccount.getName()).andReturn("otherAccount").times(2);

        replay(mockAccount);

        //test
        account.assertCanModifyAccount(mockAccount);

        verify(mockAccount);
    }

    public class TestableAbstractAccount extends AbstractAccount {

        public TestableAbstractAccount(DBBroker broker, AbstractRealm realm, int id, String name) throws ConfigurationException {
            super(broker, realm, id, name);
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
