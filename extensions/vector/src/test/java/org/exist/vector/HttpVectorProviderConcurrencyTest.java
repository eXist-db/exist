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
package org.exist.vector;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the read/write lock guarding {@link HttpVectorProvider#close()} against
 * a concurrent, in-flight {@link HttpVectorProvider#embed}: without it, {@code close()} could
 * shut down the {@code HttpClient} out from under a request another thread is still sending
 * (the scenario {@code VectorEmbeddingService#evict} makes possible).
 * <p>
 * Uses the JDK's built-in {@link HttpServer} as a local, controllable stand-in for the real
 * embedding API, via {@link HttpVectorProvider#createForTesting}, which bypasses the
 * api.openai.com/api.cohere URL restriction on the public {@code create} factory.
 */
class HttpVectorProviderConcurrencyTest {

    private HttpServer server;
    private CountDownLatch serverReceivedRequest;
    private CountDownLatch serverMayRespond;

    @BeforeEach
    void startServer() throws Exception {
        serverReceivedRequest = new CountDownLatch(1);
        serverMayRespond = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/embeddings", exchange -> {
            serverReceivedRequest.countDown();
            try {
                serverMayRespond.await(10, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            final byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (final OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void closeBlocksUntilAnInFlightEmbedCompletesThenSubsequentEmbedReturnsNull() throws Exception {
        final String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/";
        final HttpVectorProvider provider = HttpVectorProvider.createForTesting(
                "test-model", baseUrl, "fake-key", 384, HttpVectorProvider.ApiType.OPENAI);

        final AtomicBoolean embedReturned = new AtomicBoolean(false);
        final Thread embedThread = new Thread(() -> {
            provider.embed("hello world");
            embedReturned.set(true);
        });
        embedThread.start();

        assertTrue(serverReceivedRequest.await(10, TimeUnit.SECONDS),
                "embed() never reached the server -- can't test the in-flight race");
        // embed() is now blocked inside httpClient.send(), holding the read lock.

        final AtomicBoolean closeReturned = new AtomicBoolean(false);
        final Thread closeThread = new Thread(() -> {
            provider.close();
            closeReturned.set(true);
        });
        closeThread.start();

        // Give close() a moment to reach the write-lock acquisition; it must not have
        // returned yet, since embed() (holding the read lock) is still in flight.
        Thread.sleep(300);
        assertFalse(closeReturned.get(), "close() returned before the in-flight embed() finished");
        assertFalse(embedReturned.get());

        serverMayRespond.countDown(); // let the server -- and so embed() -- finish
        embedThread.join(TimeUnit.SECONDS.toMillis(10));
        closeThread.join(TimeUnit.SECONDS.toMillis(10));

        assertTrue(embedReturned.get(), "embed() thread did not finish");
        assertTrue(closeReturned.get(), "close() thread did not finish");

        // A subsequent embed() after close() must return null without attempting a request
        // on the now-closed HttpClient.
        assertNull(provider.embed("after close"));
    }
}
