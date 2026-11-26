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

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that {@link BrokerPoolService#beforeStopMultiUser(BrokerPool)} is called while the
 * BrokerPool is still operational and before {@link BrokerPoolService#stopMultiUser(BrokerPool)}.
 */
class BrokerPoolServiceBeforeStopTest {

    @Test
    void quiesceIsCalledWhileOperationalAndBeforeStop() throws Exception {
        final List<String> events = new CopyOnWriteArrayList<>();
        runDatabaseWith(new RecordingService(events, false));

        assertEquals(List.of(
                "beforeStopMultiUser shuttingDown=false broker=ok",
                "stopMultiUser shuttingDown=true"), events);
    }

    @Test
    void failingQuiesceDoesNotPreventStop() throws Exception {
        final List<String> events = new CopyOnWriteArrayList<>();
        runDatabaseWith(new RecordingService(events, true));

        assertEquals(List.of(
                "beforeStopMultiUser shuttingDown=false broker=ok",
                "stopMultiUser shuttingDown=true"), events);
    }

    private static void runDatabaseWith(final BrokerPoolService service) throws Exception {
        final Properties configProps = new Properties();
        configProps.put("exist.testBrokerPoolService", service);
        final ExistEmbeddedServer server = new ExistEmbeddedServer(configProps, true, true);
        server.startDb();
        server.stopDb();
    }

    private static class RecordingService implements BrokerPoolService {
        private final List<String> events;
        private final boolean failQuiesce;

        RecordingService(final List<String> events, final boolean failQuiesce) {
            this.events = events;
            this.failQuiesce = failQuiesce;
        }

        @Override
        public void beforeStopMultiUser(final BrokerPool brokerPool) throws BrokerPoolServiceException {
            String broker;
            try (final DBBroker b = brokerPool.getBroker()) {
                broker = "ok";
            } catch (final Exception e) {
                broker = e.getMessage();
            }
            events.add("beforeStopMultiUser shuttingDown=" + brokerPool.isShuttingDownOrDown() + " broker=" + broker);
            if (failQuiesce) {
                throw new BrokerPoolServiceException("quiesce failed on purpose");
            }
        }

        @Override
        public void stopMultiUser(final BrokerPool brokerPool) {
            events.add("stopMultiUser shuttingDown=" + brokerPool.isShuttingDownOrDown());
        }
    }
}
