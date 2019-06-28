/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2013 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage;

import org.exist.EXistException;
import org.exist.security.Subject;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.LocalCollection;
import org.exist.xmldb.XmldbURI;
import org.junit.Rule;
import org.junit.Test;
import org.xmldb.api.base.XMLDBException;

import java.util.Optional;
import java.util.concurrent.*;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class BrokerPoolTest {

    @Rule
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void noPrivilegeEscalationThroughBrokerRelease() throws EXistException {
        //take a broker with the guest user
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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

    /**
     * Checks that when all broker leases are taken,
     * no further lease is taken, until a lease has
     * been released.
     */
    @Test
    public void canReleaseWhenSaturated() throws InterruptedException, ExecutionException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final int maxBrokers = pool.getMax();

        // test requires at least 2 leasedBrokers to prove the issue
        assertTrue(maxBrokers > 1);

        final CountDownLatch firstBrokerReleaseLatch = new CountDownLatch(1);
        final CountDownLatch releaseLatch = new CountDownLatch(1);
        try {

            // lease all brokers
            final Thread brokerUsers[] = new Thread[maxBrokers];
            final CountDownLatch acquiredLatch = new CountDownLatch(maxBrokers);

            final Thread firstBrokerUser = new Thread(new BrokerUser(pool, acquiredLatch, firstBrokerReleaseLatch), "first-brokerUser");
            brokerUsers[0] = firstBrokerUser;
            brokerUsers[0].start();
            for (int i = 1; i < maxBrokers; i++) {
                brokerUsers[i] = new Thread(new BrokerUser(pool, acquiredLatch, releaseLatch));
                brokerUsers[i].start();
            }

            // wait for all brokers to be acquired
            acquiredLatch.await();

            // check that we have all brokers
            assertEquals(maxBrokers, pool.total());
            assertEquals(0, pool.available());

            // create a new thread and attempt to get an additional broker
            final CountDownLatch additionalBrokerAcquiredLatch = new CountDownLatch(1);
            final Thread additionalBrokerUser = new Thread(new BrokerUser(pool, additionalBrokerAcquiredLatch, releaseLatch), "additional-brokerUser");
            assertEquals(1, additionalBrokerAcquiredLatch.getCount());
            additionalBrokerUser.start();

            // we should not be able to acquire an additional broker, as we have already leased max
            Thread.sleep(500);  // just to ensure the other thread has done something
            assertEquals(1, additionalBrokerAcquiredLatch.getCount());

            // we will now release a previously acquired broker (i.e. the first broker)... this should then allow the lease of an additional broker to advance
            assertEquals(1, firstBrokerReleaseLatch.getCount());
            firstBrokerReleaseLatch.countDown();
            assertEquals(0, firstBrokerReleaseLatch.getCount());
            firstBrokerUser.join(); // wait for the first broker lease thread to complete

            // check that the additional broker lease has now been acquired
            Thread.sleep(500);  // just to ensure the other thread has done something
            assertEquals(0, additionalBrokerAcquiredLatch.getCount());

        } finally {
            // release all brokers from brokerUsers
            if(firstBrokerReleaseLatch.getCount() == 1) {
                firstBrokerReleaseLatch.countDown();
            }
            releaseLatch.countDown();
        }
    }

    public static class BrokerUser implements Runnable {

        final BrokerPool brokerPool;
        private final CountDownLatch acquiredLatch;
        private final CountDownLatch releaseLatch;

        public BrokerUser(final BrokerPool brokerPool, final CountDownLatch acquiredLatch, final CountDownLatch releaseLatch) {
            this.brokerPool = brokerPool;
            this.acquiredLatch = acquiredLatch;
            this.releaseLatch = releaseLatch;
        }

        @Override
        public void run() {
            try(final DBBroker broker = brokerPool.getBroker()) {

                // signal that we have acquired the broker
                acquiredLatch.countDown();
                acquiredLatch.await();

                // wait for signal to release the broker
                releaseLatch.await();

            } catch(final EXistException | InterruptedException e) {
                fail(e.getMessage());
            }
        }
    }

}
