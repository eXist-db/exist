package org.exist.security;

import org.easymock.EasyMock;
import org.exist.security.internal.AccountImpl;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import org.exist.Database;
import org.exist.config.Configuration;
import org.exist.security.internal.SecurityManagerImpl;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 * @author aretter
 */
public class AccountTest {

    @Ignore
    @Test
    public void testGroupFallback() throws NoSuchMethodException, PermissionDeniedException {

//        final String mockRealmId = "mock";
        final String testAccountName = "testUser";
        final String testGroupName = "testGroup";

        Database mockDatabase = EasyMock.createMock(Database.class);

        SecurityManagerImpl mockSecurityManager = EasyMock.createMockBuilder(SecurityManagerImpl.class)
                .withConstructor(Database.class)
                .withArgs(mockDatabase)
                .createMock();

        Configuration mockConfiguration = EasyMock.createMock(Configuration.class);

        AbstractRealm mockRealm = EasyMock.createMockBuilder(AbstractRealm.class)
                .withConstructor(SecurityManager.class, Configuration.class)
                .withArgs(mockSecurityManager, mockConfiguration)
                .createMock();

        AccountImpl mockAccountImpl = EasyMock.createMockBuilder(AccountImpl.class)
                .withConstructor(AbstractRealm.class, String.class)
                .withArgs(mockRealm, testAccountName)
                .addMockedMethods(
                        AccountImpl.class.getMethod("getRealm"),
                        AccountImpl.class.getMethod("addGroup", Group.class)
                ).createMock();

        
        expect(mockAccountImpl.getRealm()).andReturn(mockRealm);
        expect(mockRealm.getGroup(testGroupName)).andReturn(null);
        //expect(mockAccountImpl.getRealm()).andReturn(mockRealm);
        //expect(mockRealm.getSecurityManager()).andReturn(mockSecurityManager);

        replay();

        mockAccountImpl.addGroup(testGroupName);

        verify();
    }
}
