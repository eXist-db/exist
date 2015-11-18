package org.exist.security;

import java.util.Collections;

import org.exist.storage.DBBroker;
import org.exist.Database;
import org.junit.Test;
import org.easymock.EasyMock;
import org.exist.EXistException;
import org.exist.config.Configuration;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 *
 * @author aretter
 */
public class AbstractRealmTest {

    /*
    @Test
    public void updateAccount_calls_assertCanModifyAccount() throws PermissionDeniedException, EXistException {
        SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        Configuration mockConfiguration = EasyMock.createMock(Configuration.class);
        Database mockDatabase = EasyMock.createMock(Database.class);
        Subject mockSubject = EasyMock.createMock(Subject.class);

        Account mockAccount = EasyMock.createMockBuilder(AbstractAccount.class)
                .addMockedMethod("getName", new Class[0])
                .addMockedMethod("getGroups", new Class[0])
                .addMockedMethod("assertCanModifyAccount", new Class[]{Account.class})
                .addMockedMethod("getRealm", new Class[0])
                .createNiceMock();
        final String accountName = "someAccount";

        AbstractRealm mockRealm = EasyMock
                .createMockBuilder(AbstractRealm.class)
                .withConstructor(SecurityManager.class, Configuration.class)
                .withArgs(mockSecurityManager, mockConfiguration)
                .addMockedMethod("getDatabase", new Class[0])
                .addMockedMethod("getAccount", new Class[]{Subject.class, String.class})
                .createNiceMock();

        Account mockUpdatingAccount = EasyMock.createMock(Account.class);

        //expectations
        expect(mockRealm.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getCurrentSubject()).andReturn(mockSubject);
        mockAccount.assertCanModifyAccount(mockSubject);
        expect(mockAccount.getName()).andReturn(accountName);
        expect(mockRealm.getAccount(null, accountName)).andReturn(mockUpdatingAccount);
        expect(mockAccount.getGroups()).andReturn(new String[0]);
        expect(mockUpdatingAccount.getGroups()).andReturn(new String[0]);

        replay(mockRealm, mockDatabase, mockSubject, mockUpdatingAccount, mockAccount);

        mockRealm.updateAccount(null, mockAccount);

        verify(mockRealm, mockDatabase, mockSubject, mockUpdatingAccount, mockAccount);
    } */


    @Test
    public void updateGroup_calls_assertCanModifyGroup() throws PermissionDeniedException, EXistException {
        SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        Configuration mockConfiguration = EasyMock.createMock(Configuration.class);
        Database mockDatabase = EasyMock.createMock(Database.class);
        DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        Subject mockSubject = EasyMock.createMock(Subject.class);

        Group mockGroup = EasyMock.createMockBuilder(AbstractGroup.class)
                .addMockedMethod("getName", new Class[0])
                .addMockedMethod("getManagers", new Class[0])
                .addMockedMethod("assertCanModifyGroup", new Class[]{Account.class})
                .addMockedMethod("getMetadataKeys", new Class[0])
                .createNiceMock();
        final String groupName = "someGroup";

        AbstractRealm mockRealm = EasyMock
                .createMockBuilder(AbstractRealm.class)
                .withConstructor(SecurityManager.class, Configuration.class)
                .withArgs(mockSecurityManager, mockConfiguration)
                .addMockedMethod("getDatabase", new Class[0])
                .addMockedMethod("getGroup", new Class[]{String.class})
                .createNiceMock();

        Group mockUpdatingGroup = EasyMock.createNiceMock(Group.class);

        //expectations
        expect(mockRealm.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker);
        expect(mockBroker.getCurrentSubject()).andReturn(mockSubject);
        mockGroup.assertCanModifyGroup(mockSubject);
        expect(mockGroup.getName()).andReturn(groupName);
        expect(mockRealm.getGroup(groupName)).andReturn(mockUpdatingGroup);
        expect(mockGroup.getManagers()).andReturn(Collections.emptyList());
        expect(mockUpdatingGroup.getManagers()).andReturn(Collections.emptyList());
        expect(mockGroup.getMetadataKeys()).andReturn(Collections.emptySet());
        mockGroup.save();

        replay(mockRealm, mockDatabase, mockBroker, mockGroup, mockSubject, mockUpdatingGroup);

        mockRealm.updateGroup(mockGroup);

        verify(mockRealm, mockDatabase, mockBroker, mockGroup, mockSubject, mockUpdatingGroup);
    }
}
