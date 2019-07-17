package org.exist.xquery.functions.securitymanager;

import com.evolvedbinary.j8fu.function.Runnable3E;
import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class GroupMembershipFunctionRemoveGroupMemberTest {

    private static final String USER1_NAME = "user1";
    private static final String USER1_PWD = USER1_NAME;

    private static final String OTHER_GROUP1_NAME = "otherGroup";
    private static final String OTHER_GROUP2_NAME = "otherGroup2";

    @Rule
    public final ExistEmbeddedServer existWebServer = new ExistEmbeddedServer(true, true);

    @Test(expected = PermissionDeniedException.class)
    public void cannotRemoveAllGroupsFromUserAsOwner() throws XPathException, PermissionDeniedException, EXistException, AuthenticationException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        final Subject owner = pool.getSecurityManager().authenticate(USER1_NAME, USER1_NAME);
        extractPermissionDenied(() -> {
            xqueryRemoveUserFromGroup(USER1_NAME, OTHER_GROUP2_NAME, Optional.of(owner));
            xqueryRemoveUserFromGroup(USER1_NAME, OTHER_GROUP1_NAME, Optional.of(owner));
            xqueryRemoveUserFromGroup(USER1_NAME, USER1_NAME, Optional.of(owner));
        });
    }

    @Test(expected = PermissionDeniedException.class)
    public void cannotRemoveAllGroupsFromUserAsDBA() throws XPathException, PermissionDeniedException, EXistException, AuthenticationException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        final Subject admin = pool.getSecurityManager().authenticate(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        extractPermissionDenied(() -> {
            xqueryRemoveUserFromGroup(USER1_NAME, OTHER_GROUP2_NAME, Optional.of(admin));
            xqueryRemoveUserFromGroup(USER1_NAME, OTHER_GROUP1_NAME, Optional.of(admin));
            xqueryRemoveUserFromGroup(USER1_NAME, USER1_NAME, Optional.of(admin));
        });
    }

    @Before
    public void setup() throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        final SecurityManager sm = pool.getSecurityManager();

        // create user with personal group as primary group
        try (final DBBroker broker = pool.get(Optional.of(sm.getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final Account user1 = createUser(broker, sm, USER1_NAME, USER1_PWD);

            final Group otherGroup1 = createGroup(broker, sm, OTHER_GROUP1_NAME);
            addUserToGroup(sm, user1, otherGroup1);
            addUserAsGroupManager(USER1_NAME, OTHER_GROUP1_NAME);

            final Group otherGroup2 = createGroup(broker, sm, OTHER_GROUP2_NAME);
            addUserToGroup(sm, user1, otherGroup2);
            addUserAsGroupManager(USER1_NAME, OTHER_GROUP2_NAME);

            transaction.commit();
        }

        // check that the user is as we expect
        try (final DBBroker broker = pool.get(Optional.of(sm.getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final Account user1 = sm.getAccount(USER1_NAME);
            assertEquals(USER1_NAME, user1.getPrimaryGroup());
            final String[] user1Groups = user1.getGroups();
            assertArrayEquals(new String[] { USER1_NAME, OTHER_GROUP1_NAME, OTHER_GROUP2_NAME }, user1Groups);
            for (final String user1Group : user1Groups) {
                assertNotNull(sm.getGroup(user1Group));
            }
            transaction.commit();
        }
    }

    private Sequence xqueryRemoveUserFromGroup(final String username, final String groupname) throws XPathException, PermissionDeniedException, EXistException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        final Optional<Subject> asUser = Optional.of(pool.getSecurityManager().getSystemSubject());
        return xqueryRemoveUserFromGroup(username, groupname, asUser);
    }

    private Sequence xqueryRemoveUserFromGroup(final String username, final String groupname, final Optional<Subject> asUser) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existWebServer.getBrokerPool();

        final String query =
                "import module namespace sm = 'http://exist-db.org/xquery/securitymanager';\n" +
                        "sm:remove-group-member('" + groupname + "', '" + username + "')";

        try (final DBBroker broker = pool.get(asUser)) {
            final XQuery xquery = existWebServer.getBrokerPool().getXQueryService();
            final Sequence result = xquery.execute(broker, query, null);
            return result;
        }
    }

    private Sequence addUserAsGroupManager(final String username, final String groupname) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existWebServer.getBrokerPool();

        final String query =
                "import module namespace sm = 'http://exist-db.org/xquery/securitymanager';\n" +
                        "sm:add-group-manager('" + groupname + "', '" + username + "')";

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = existWebServer.getBrokerPool().getXQueryService();
            final Sequence result = xquery.execute(broker, query, null);
            return result;
        }
    }

    private static Account createUser(final DBBroker broker, final SecurityManager sm, final String username, final String password) throws PermissionDeniedException, EXistException {
        Group userGroup = new GroupAider(username);
        sm.addGroup(broker, userGroup);
        final Account user = new UserAider(username);
        user.setPassword(password);
        user.setPrimaryGroup(userGroup);
        sm.addAccount(user);

        userGroup = sm.getGroup(username);
        userGroup.addManager(sm.getAccount(username));
        sm.updateGroup(userGroup);

        return user;
    }

    private static Group createGroup(final DBBroker broker, final SecurityManager sm, final String groupName) throws PermissionDeniedException, EXistException {
        final Group otherGroup = new GroupAider(groupName);
        return sm.addGroup(broker, otherGroup);
    }

    private static void addUserToGroup(final SecurityManager sm, final Account user, final Group group) throws PermissionDeniedException, EXistException {
        user.addGroup(group.getName());
        sm.updateAccount(user);
    }

    private static void extractPermissionDenied(final Runnable3E<XPathException, PermissionDeniedException, EXistException> runnable) throws XPathException, PermissionDeniedException, EXistException {
        try {
            runnable.run();
        } catch (final XPathException e) {
            if (e.getCause() != null && e.getCause() instanceof PermissionDeniedException) {
                throw (PermissionDeniedException)e.getCause();
            } else {
                throw e;
            }
        }
    }
}
