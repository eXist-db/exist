package org.exist.security;

import java.util.ArrayList;
import java.util.Arrays;
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

    }
}
