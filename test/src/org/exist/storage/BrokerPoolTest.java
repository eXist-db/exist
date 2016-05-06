package org.exist.storage;

import org.exist.EXistException;
import org.exist.security.Subject;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.XMLDBException;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class BrokerPoolTest {

    private BrokerPool pool;

    @Test
    public void noPrivilegeEscalationThroughBrokerRelease() throws EXistException {
        //take a broker with the guest user
        final Subject guestUser = pool.getSecurityManager().getGuestSubject();
        try(final DBBroker broker1 = pool.get(Optional.of(guestUser))) {

            assertEquals("Expected `guest` user, but was: " + broker1.getCurrentSubject().getName(), guestUser.getId(), broker1.getCurrentSubject().getId());

            //take a broker with the system user
            final Subject sysUser = pool.getSecurityManager().getSystemSubject();
            try (final DBBroker broker2 = pool.get(Optional.of(sysUser))) {
                assertEquals("Expected `SYSTEM` user, but was: " + broker2.getCurrentSubject().getName(), sysUser.getId(), broker2.getCurrentSubject().getId());
            }

            //ensure that after releasing the broker, the user has been returned to the guest user
            assertEquals("Expected `guest` user, but was: " + broker1.getCurrentSubject().getName(), guestUser.getId(), broker1.getCurrentSubject().getId());
        }
    }

    @Test
    public void privilegeStableWhenSubjectNull() throws EXistException {
        //take a broker with the SYSTEM user
        final Subject sysUser = pool.getSecurityManager().getSystemSubject();
        try(final DBBroker broker1 = pool.get(Optional.of(sysUser))) {

            assertEquals("Expected `SYSTEM` user, but was: " + broker1.getCurrentSubject().getName(), sysUser.getId(), broker1.getCurrentSubject().getId());

            //take a broker without changing the user
            try (final DBBroker broker2 = pool.getBroker()) {
                assertEquals("Expected `SYSTEM` user, but was: " + broker2.getCurrentSubject().getName(), sysUser.getId(), broker2.getCurrentSubject().getId());
            }

            //ensure that after releasing the broker, the user is still the SYSTEM user
            assertEquals("Expected `guest` user, but was: " + broker1.getCurrentSubject().getName(), sysUser.getId(), broker1.getCurrentSubject().getId());
        }
    }

    @Test
    public void guestDefaultPriviledge() throws EXistException {
        //take a broker with default perms
        try(final DBBroker broker1 = pool.getBroker()) {

            final Subject guestUser = pool.getSecurityManager().getGuestSubject();

            assertEquals("Expected `guest` user, but was: " + broker1.getCurrentSubject().getName(), guestUser.getId(), broker1.getCurrentSubject().getId());

            //take a broker without changing the user
            try (final DBBroker broker2 = pool.getBroker()) {
                assertEquals("Expected `guest` user, but was: " + broker2.getCurrentSubject().getName(), guestUser.getId(), broker2.getCurrentSubject().getId());
            }

            //ensure that after releasing the broker, the user is still the SYSTEM user
            assertEquals("Expected `guest` user, but was: " + broker1.getCurrentSubject().getName(), guestUser.getId(), broker1.getCurrentSubject().getId());
        }
    }

    @Test
    public void noPrivilegeEscalationThroughBrokerRelease_xmldb() throws EXistException, XMLDBException {
        //take a broker with the guest user
        final Subject guestUser = pool.getSecurityManager().getGuestSubject();
        try(final DBBroker broker1 = pool.get(Optional.of(guestUser))) {

            assertEquals("Expected `guest` user, but was: " + broker1.getCurrentSubject().getName(), guestUser.getId(), broker1.getCurrentSubject().getId());

            //perform an XML:DB operation as the SYSTEM user
            final Subject sysUser = pool.getSecurityManager().getSystemSubject();
            new LocalCollection(sysUser, pool, XmldbURI.ROOT_COLLECTION_URI);

            //ensure that after releasing the broker, the user has been returned to the guest user
            assertEquals("Expected `guest` user, but was: " + broker1.getCurrentSubject().getName(), guestUser.getId(), broker1.getCurrentSubject().getId());
        }
    }



    @Before
    public void setUp() throws DatabaseConfigurationException, EXistException {
        Configuration config = new Configuration();
        BrokerPool.configure(1, 5, config);
        pool = BrokerPool.getInstance();
    }

    @After
    public void tearDown() {
        BrokerPool.stopAll(false);
    }

    private class TestableDatabaseManager extends DatabaseManager {

    }
}
