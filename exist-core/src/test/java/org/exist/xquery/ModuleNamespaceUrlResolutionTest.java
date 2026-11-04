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
package org.exist.xquery;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.source.URLSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loading a module must not make the query wait on a remote host forever,
 * see <a href="https://github.com/eXist-db/exist/issues/6898">#6898</a>.
 * <p>
 * The remote host is a local socket that accepts connections and never answers. Before the fix
 * every query below hung forever, the class level timeout turns that into a failure.
 */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class ModuleNamespaceUrlResolutionTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static ServerSocket silentServer;
    private static final List<Socket> accepted = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void setup() throws IOException {
        silentServer = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        final Thread acceptor = new Thread(() -> {
            while (!silentServer.isClosed()) {
                try {
                    // hold on to the connection and never answer
                    accepted.add(silentServer.accept());
                } catch (final IOException e) {
                    // server socket closed
                }
            }
        }, "silent-server");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @AfterAll
    static void teardown() throws IOException {
        for (final Socket socket : accepted) {
            socket.close();
        }
        silentServer.close();
    }

    @AfterEach
    void resetConfiguration() {
        setReadTimeout(URLSource.DEFAULT_READ_TIMEOUT);
        accepted.clear();
    }

    @Test
    void locationHintIsBoundedByReadTimeout() {
        setReadTimeout(500);
        final XPathException e = assertThrows(XPathException.class, () -> execute("""
                import module namespace silent = "http://example.org/silent" at "%s";
                1
                """.formatted(silentNamespace())));
        assertSame(ErrorCodes.XQST0059, e.getErrorCode());
        assertTrue(accepted.size() > 0, "a connection to the location hint expected");
    }

    private static String silentNamespace() {
        return "http://127.0.0.1:" + silentServer.getLocalPort() + "/ns";
    }

    private static void setReadTimeout(final int readTimeout) {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        pool.getConfiguration().setProperty(XQueryContext.PROPERTY_URL_READ_TIMEOUT, readTimeout);
    }

    private static Sequence execute(final String query) throws XPathException, PermissionDeniedException, EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            return pool.getXQueryService().execute(broker, query, null);
        }
    }
}
