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
package org.exist.xquery.functions.websocket;

import org.exist.test.ExistWebServer;
import org.junit.ClassRule;
import org.junit.Test;

import jakarta.websocket.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Integration test for the WebSocket endpoint.
 * Starts an embedded eXist-db server and connects a WebSocket client.
 */
public class WebSocketEndpointTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    @Test
    public void connectAndHeartbeatKeepsSessionOpen() throws Exception {
        final int port = existWebServer.getPort();
        final URI wsUri = new URI("ws://localhost:" + port + "/ws");

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
            }
        }, ClientEndpointConfig.Builder.create().build(), wsUri);

        try {
            // The heartbeat sends WebSocket PING control frames every 500ms.
            // They are handled transparently by the WS layer and do not fire onMessage.
            // The observable effect is that the session stays open.
            Thread.sleep(1500);
            assertTrue("Session should remain open after heartbeat interval", session.isOpen());
        } finally {
            session.close();
        }
    }

    @Test
    public void subscribeToChannelAndReceiveMessage() throws Exception {
        final int port = existWebServer.getPort();
        final URI wsUri = new URI("ws://localhost:" + port + "/ws");

        final CountDownLatch subscribedLatch = new CountDownLatch(1);
        final CountDownLatch messageLatch = new CountDownLatch(1);
        final AtomicReference<String> receivedMessage = new AtomicReference<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                try {
                    session.getBasicRemote().sendText("{\"channel\": \"test-channel\"}");
                    subscribedLatch.countDown();
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        receivedMessage.set(message);
                        messageLatch.countDown();
                    }
                });
            }
        }, ClientEndpointConfig.Builder.create().build(), wsUri);

        try {
            // wait for subscription
            assertTrue("Should subscribe within 2s", subscribedLatch.await(2, TimeUnit.SECONDS));
            // allow server time to process subscription
            Thread.sleep(200);

            // send a message to the channel via the module
            WebSocketModule.send("test-channel", "{\"hello\": \"world\"}");

            // should receive the message
            assertTrue("Should receive channel message within 2s", messageLatch.await(2, TimeUnit.SECONDS));
            assertEquals("{\"hello\": \"world\"}", receivedMessage.get());
        } finally {
            session.close();
        }
    }

    @Test
    public void channelCountReflectsSubscribers() throws Exception {
        final int port = existWebServer.getPort();
        final URI wsUri = new URI("ws://localhost:" + port + "/ws");

        assertEquals("No subscribers initially", 0, WebSocketEndpoint.getChannelCount("count-test"));

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final CountDownLatch subscribedLatch = new CountDownLatch(1);

        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                try {
                    session.getBasicRemote().sendText("{\"channel\": \"count-test\"}");
                    subscribedLatch.countDown();
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }, ClientEndpointConfig.Builder.create().build(), wsUri);

        try {
            assertTrue("Should subscribe within 2s", subscribedLatch.await(2, TimeUnit.SECONDS));
            // poll for the server to process the subscription; @OnMessage runs
            // on a separate thread, so the client-side latch does not guarantee
            // the server-side state has been updated yet.
            assertTrue("One subscriber within 2s",
                    awaitChannelCount("count-test", 1, 2000));
        } finally {
            session.close();
            // poll for session cleanup so a leaked session cannot affect
            // subsequent tests sharing the static sessions map.
            awaitChannelCount("count-test", 0, 2000);
        }
    }

    private static boolean awaitChannelCount(final String channel, final int expected,
                                             final long timeoutMillis) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (WebSocketEndpoint.getChannelCount(channel) == expected) {
                return true;
            }
            Thread.sleep(25);
        }
        return WebSocketEndpoint.getChannelCount(channel) == expected;
    }
}
