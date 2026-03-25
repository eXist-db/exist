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
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Verifies that BrokerPool shutdown doesn't leave non-daemon threads
 * that would prevent JVM exit (cause of CI hangs).
 */
public class BrokerPoolShutdownTest {

    @Test
    public void shutdownLeavesNoNonDaemonExistThreads() throws Exception {
        // Start a BrokerPool
        final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);
        server.startDb();

        // Shut it down
        server.stopDb();

        // Give threads a moment to terminate
        Thread.sleep(2000);

        // Check for non-daemon eXist threads still alive
        final Thread[] threads = new Thread[Thread.activeCount() * 2];
        Thread.enumerate(threads);
        final List<Thread> leaks = Arrays.stream(threads)
            .filter(t -> t != null)
            .filter(t -> !t.isDaemon())
            .filter(t -> t.isAlive())
            .filter(t -> {
                final String name = t.getName().toLowerCase();
                return name.contains("exist") || name.contains("db.exist")
                    || name.contains("broker") || name.contains("blob");
            })
            .collect(Collectors.toList());

        if (!leaks.isEmpty()) {
            final String details = leaks.stream()
                .map(t -> t.getName() + " (state=" + t.getState() + ")")
                .collect(Collectors.joining(", "));
            fail("Non-daemon eXist threads survived shutdown: " + details);
        }
    }
}
