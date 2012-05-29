package org.exist.security;

import java.lang.reflect.Method;
import org.easymock.classextension.ConstructorArgs;
import org.easymock.classextension.EasyMock;
import org.exist.security.internal.AccountImpl;
import static org.easymock.classextension.EasyMock.expect;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;
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

        SecurityManagerImpl mockSecurityManager = EasyMock.createMock(SecurityManagerImpl.class,
                new ConstructorArgs(
                    SecurityManagerImpl.class.getConstructor(Database.class),
                    new Object[] {
                        mockDatabase
                    }
                )
        );

        Configuration mockConfiguration = EasyMock.createMock(Configuration.class);

        AbstractRealm mockRealm = EasyMock.createMock(AbstractRealm.class,
                new ConstructorArgs(
                    AbstractRealm.class.getDeclaredConstructor(SecurityManager.class, Configuration.class),
                    new Object[] {
                        mockSecurityManager,
                        mockConfiguration
                    }
                )
        );

        AccountImpl mockAccountImpl = EasyMock.createMock(AccountImpl.class,
            new ConstructorArgs(
                AccountImpl.class.getDeclaredConstructor(AbstractRealm.class, String.class),
                new Object[] {
                    mockRealm,
                    testAccountName
                }
            ),
            new Method[]{
                AccountImpl.class.getMethod("getRealm"),
                AccountImpl.class.getMethod("addGroup", Group.class)
            }
        );

        
        expect(mockAccountImpl.getRealm()).andReturn(mockRealm);
        expect(mockRealm.getGroup(testGroupName)).andReturn(null);
        //expect(mockAccountImpl.getRealm()).andReturn(mockRealm);
        //expect(mockRealm.getSecurityManager()).andReturn(mockSecurityManager);

        replay();

        mockAccountImpl.addGroup(testGroupName);

        verify();
    }
}
