/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link BrokerPool#getBrokerForService(BrokerPoolService)}.
 *
 * The pool only refuses a broker when none is inactive, so the tests that care about the
 * refusal hold every broker on other threads while the service is stopping.
 */
class BrokerPoolServiceAdmissionTest {

    @Test
    void registeredServiceIsServedWhileItIsStopping() throws Exception {
        final List<String> events = new CopyOnWriteArrayList<>();
        final ExhaustingService service = new ExhaustingService(events);
        final Properties configProps = new Properties();
        configProps.put("exist.testBrokerPoolService", service);

        final ExistEmbeddedServer server = new ExistEmbeddedServer(configProps, true, true);
        server.startDb();
        service.holdAllBrokers(server.getBrokerPool());
        server.stopDb();
        service.joinHolders();

        assertEquals(List.of(
                "plain getBroker refused",
                "unregistered service refused",
                "registered service ok"), events);
    }

    @Test
    void registeredServiceGivesUpAfterTheShutdownWait() throws Exception {
        final List<String> events = new CopyOnWriteArrayList<>();
        final ExhaustingService service = new ExhaustingService(events, true);
        final Properties configProps = new Properties();
        configProps.put("exist.testBrokerPoolService", service);
        configProps.put(BrokerPool.PROPERTY_SHUTDOWN_DELAY, 2000L);

        final ExistEmbeddedServer server = new ExistEmbeddedServer(configProps, true, true);
        server.startDb();
        service.holdAllBrokers(server.getBrokerPool());
        server.stopDb();
        service.joinHolders();

        assertEquals(1, events.size());
        assertTrue(events.get(0).startsWith("registered service refused: BrokerPool is shutting down: no broker became available"),
                events.get(0));
    }

    @Test
    void argumentMakesNoDifferenceWhileOperational() throws Exception {
        final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);
        server.startDb();
        try {
            assertThrows(NullPointerException.class, () -> server.getBrokerPool().getBrokerForService(null));
            try (final DBBroker broker = server.getBrokerPool().getBrokerForService(new BrokerPoolService() {})) {
                assertNotNull(broker);
            }
        } finally {
            server.stopDb();
        }
    }

    /**
     * A service whose stop asks for brokers from threads that hold none, as background jobs do,
     * while every broker of the pool is held elsewhere.
     */
    private static class ExhaustingService implements BrokerPoolService {
        private final List<String> events;
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<Thread> holders = new ArrayList<>();
        private final boolean neverRelease;

        ExhaustingService(final List<String> events) {
            this(events, false);
        }

        /** @param neverRelease keep the brokers held until the service has given up waiting */
        ExhaustingService(final List<String> events, final boolean neverRelease) {
            this.events = events;
            this.neverRelease = neverRelease;
        }

        void holdAllBrokers(final BrokerPool pool) throws InterruptedException {
            final int max = pool.getMax();
            final CountDownLatch acquired = new CountDownLatch(max);
            for (int i = 0; i < max; i++) {
                final Thread t = new Thread(() -> {
                    try (final DBBroker b = pool.getBroker()) {
                        acquired.countDown();
                        release.await();
                    } catch (final Exception e) {
                        // the assertion on the events reports the failure
                    }
                });
                t.setDaemon(true);
                holders.add(t);
                t.start();
            }
            assertTrue(acquired.await(30, TimeUnit.SECONDS), "could not hold all brokers");
        }

        void joinHolders() throws InterruptedException {
            for (final Thread t : holders) {
                t.join(10_000);
            }
        }

        @Override
        public void stopMultiUser(final BrokerPool pool) throws BrokerPoolServiceException {
            try {
                if (!neverRelease) {
                    // refused at once: no broker is inactive and the caller is not a stopping service
                    events.add(attempt(pool, null, "plain getBroker") ? "plain getBroker ok" : "plain getBroker refused");
                    events.add(attempt(pool, new BrokerPoolService() {}, "unregistered service")
                            ? "unregistered service ok" : "unregistered service refused");
                }

                // admitted: it waits for a broker, which is handed back when the holders let go
                final AtomicReference<String> result = new AtomicReference<>("not finished");
                final Thread t = new Thread(() -> {
                    try (final DBBroker b = pool.getBrokerForService(this)) {
                        result.set("registered service ok");
                    } catch (final Exception e) {
                        result.set("registered service refused: " + e.getMessage());
                    }
                });
                t.setDaemon(true);
                t.start();
                if (!neverRelease) {
                    release.countDown();
                }
                t.join(neverRelease ? 30_000 : 10_000);
                release.countDown();
                if (t.isAlive()) {
                    throw new BrokerPoolServiceException("the registered service was not served");
                }
                events.add(result.get());
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BrokerPoolServiceException(e);
            }
        }

        /** @return true if a broker was handed out; service null means a plain getBroker() */
        private static boolean attempt(final BrokerPool pool, final BrokerPoolService service, final String what)
                throws InterruptedException {
            final boolean[] served = new boolean[1];
            final Thread t = new Thread(() -> {
                try (final DBBroker b = service == null ? pool.getBroker() : pool.getBrokerForService(service)) {
                    served[0] = true;
                } catch (final Exception e) {
                    served[0] = false;
                }
            });
            t.setDaemon(true);
            t.start();
            t.join(10_000);
            if (t.isAlive()) {
                throw new IllegalStateException(what + " neither served nor refused");
            }
            return served[0];
        }
    }
}
