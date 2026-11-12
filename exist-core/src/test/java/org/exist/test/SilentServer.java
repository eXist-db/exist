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
package org.exist.test;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A local HTTP "black hole" for tests: it accepts connections and never answers, like a remote
 * host that is down but still reachable. Register it on an instance field with
 * {@code @RegisterExtension}; each test gets a fresh server, so a late connection from one test
 * cannot show up in the next.
 */
public class SilentServer implements BeforeEachCallback, AfterEachCallback {

    private ServerSocket server;
    private final List<Socket> accepted = new CopyOnWriteArrayList<>();

    @Override
    public void beforeEach(final ExtensionContext context) throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        final ServerSocket current = server;
        final Thread acceptor = new Thread(() -> {
            while (!current.isClosed()) {
                try {
                    // hold on to the connection and never answer
                    accepted.add(current.accept());
                } catch (final IOException e) {
                    // server socket closed
                }
            }
        }, "silent-server");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @Override
    public void afterEach(final ExtensionContext context) throws IOException {
        server.close();
        for (final Socket socket : accepted) {
            socket.close();
        }
        accepted.clear();
    }

    /**
     * @param path the path, starting with a slash
     *
     * @return an http URL on this server
     */
    public String url(final String path) {
        return "http://127.0.0.1:" + server.getLocalPort() + path;
    }

    /**
     * @return the number of connections accepted so far in this test
     */
    public int connectionCount() {
        return accepted.size();
    }
}
