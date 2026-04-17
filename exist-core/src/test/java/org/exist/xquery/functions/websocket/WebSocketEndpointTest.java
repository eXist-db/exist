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
    public void connectAndReceiveHeartbeat() throws Exception {
        final int port = existWebServer.getPort();
        final URI wsUri = new URI("ws://localhost:" + port + "/ws");

        final CountDownLatch messageLatch = new CountDownLatch(1);
        final AtomicReference<String> receivedMessage = new AtomicReference<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
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
            // should receive a heartbeat ping within 1 second
            assertTrue("Should receive a message within 2s", messageLatch.await(2, TimeUnit.SECONDS));
            assertEquals("ping", receivedMessage.get());
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
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    private boolean subscribed = false;

                    @Override
                    public void onMessage(final String message) {
                        if (!subscribed && "ping".equals(message)) {
                            // after first ping, subscribe to a channel
                            try {
                                session.getBasicRemote().sendText("{\"channel\": \"test-channel\"}");
                                subscribed = true;
                                subscribedLatch.countDown();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        } else if (subscribed && !"ping".equals(message)) {
                            receivedMessage.set(message);
                            messageLatch.countDown();
                        }
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
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        if ("ping".equals(message)) {
                            try {
                                session.getBasicRemote().sendText("{\"channel\": \"count-test\"}");
                                subscribedLatch.countDown();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                });
            }
        }, ClientEndpointConfig.Builder.create().build(), wsUri);

        try {
            assertTrue("Should subscribe within 2s", subscribedLatch.await(2, TimeUnit.SECONDS));
            // small delay for server to process subscription
            Thread.sleep(100);
            assertEquals("One subscriber", 1, WebSocketEndpoint.getChannelCount("count-test"));
        } finally {
            session.close();
            // small delay for session cleanup
            Thread.sleep(100);
        }
    }
}
