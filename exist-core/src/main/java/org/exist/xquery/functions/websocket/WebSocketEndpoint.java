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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.http.ws.QueryMonitorBroadcaster;
import org.exist.storage.BrokerPool;
import org.exist.util.ThreadUtils;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket endpoint for eXist-db console and messaging.
 *
 * Clients connect and optionally subscribe to a channel by sending
 * {@code {"channel": "channel-name"}}. Messages sent via the XQuery
 * ws:send() or console:send() functions are broadcast to all clients
 * subscribed to the matching channel.
 *
 * A 500ms heartbeat ping keeps connections alive through proxies.
 *
 * Ported from monex's RemoteConsoleEndpoint to exist-core for
 * general WebSocket support.
 */
@ServerEndpoint("/ws")
public class WebSocketEndpoint {

    public static final String DEFAULT_CHANNEL = "_default_";

    private static final Logger LOG = LogManager.getLogger(WebSocketEndpoint.class);
    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final Map<Session, String> sessions = new ConcurrentHashMap<>();
    private static final ByteBuffer PING_PAYLOAD = ByteBuffer.allocate(0);

    private static volatile boolean initialized = false;
    private static ScheduledExecutorService heartbeatService = null;
    private static ScheduledExecutorService monitorService = null;

    /**
     * Initialize the WebSocket subsystem. Called once during Jetty startup.
     */
    public static synchronized void initialize() {
        if (!initialized) {
            WebSocketModule.setAdapter(new WebSocketAdapter(
                    WebSocketEndpoint::sendAll, WebSocketEndpoint::sendAll));

            heartbeatService = Executors.newSingleThreadScheduledExecutor(
                    runnable -> ThreadUtils.newGlobalThread("ws-heartbeat", runnable));
            heartbeatService.scheduleAtFixedRate(WebSocketEndpoint::pingAll, 500, 500, TimeUnit.MILLISECONDS);

            // Schedule periodic snapshot of all running queries for admin monitors
            monitorService = Executors.newSingleThreadScheduledExecutor(
                    runnable -> ThreadUtils.newGlobalThread("ws-monitor-snapshot", runnable));
            monitorService.scheduleAtFixedRate(() -> {
                try {
                    final BrokerPool pool = BrokerPool.getInstance();
                    QueryMonitorBroadcaster.broadcastSnapshot(pool);
                } catch (final Exception e) {
                    LOG.debug("Monitor snapshot failed: {}", e.getMessage());
                }
            }, 1, 1, TimeUnit.SECONDS);

            initialized = true;
        }
    }

    /**
     * Shutdown the WebSocket subsystem. Called once during Jetty shutdown.
     */
    public static synchronized void shutdown() {
        if (initialized) {
            if (heartbeatService != null) {
                heartbeatService.shutdown();
                heartbeatService = null;
            }
            if (monitorService != null) {
                monitorService.shutdown();
                monitorService = null;
            }
            sessions.clear();
            initialized = false;
        }
    }

    @OnOpen
    public void openSession(final Session session) {
        sessions.put(session, DEFAULT_CHANNEL);
    }

    @OnClose
    public void closeSession(final Session session, final CloseReason closeReason) {
        sessions.remove(session);
    }

    @OnError
    public void onError(final Session session, final Throwable throwable) {
        sessions.remove(session);
        if (throwable instanceof ClosedChannelException) {
            LOG.debug("WebSocket client disconnected abruptly: session {}", session.getId());
        } else {
            LOG.warn("WebSocket error on session {}: {}", session.getId(), throwable.getMessage(), throwable);
        }
    }

    @OnMessage
    public void recv(final String message, final Session session) {
        try (final JsonParser parser = JSON_FACTORY.createParser(message)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME && "channel".equals(parser.currentName())) {
                    parser.nextToken();
                    final String channel = parser.getValueAsString();
                    if (channel != null) {
                        sessions.put(session, channel);
                    }
                }
            }
        } catch (final IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    static void pingAll() {
        for (final Session session : sessions.keySet()) {
            try {
                session.getBasicRemote().sendPing(PING_PAYLOAD.duplicate());
            } catch (final ClosedChannelException e) {
                sessions.remove(session);
            } catch (final IOException e) {
                LOG.debug("Ping failed, removing session {}: {}", session.getId(), e.getMessage());
                sessions.remove(session);
            }
        }
    }

    /**
     * Send a map as JSON to all clients subscribed to the channel.
     *
     * @param toChannel the channel, or null to send to all clients
     * @param data map to serialize as JSON
     */
    public static void sendAll(final String toChannel, final Map<String, Object> data) {
        try {
            final StringWriter writer = new StringWriter();
            try (final JsonGenerator gen = JSON_FACTORY.createGenerator(writer)) {
                gen.writeStartObject();
                for (final Map.Entry<String, Object> entry : data.entrySet()) {
                    final String key = entry.getKey();
                    switch (entry.getValue()) {
                        case String s -> gen.writeStringField(key, s);
                        case Integer i -> gen.writeNumberField(key, i);
                        case Long l -> gen.writeNumberField(key, l);
                        case Double d -> gen.writeNumberField(key, d);
                        case Boolean b -> gen.writeBooleanField(key, b);
                        case null -> gen.writeNullField(key);
                        default -> gen.writeStringField(key, entry.getValue().toString());
                    }
                }
                gen.writeEndObject();
            }
            sendAll(toChannel, writer.toString());
        } catch (final IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    /**
     * Send a text message to all clients subscribed to the channel.
     *
     * @param toChannel the channel, or null to send to all clients
     * @param message the message text
     */
    public static void sendAll(final String toChannel, final String message) {
        for (final Map.Entry<Session, String> entry : sessions.entrySet()) {
            final Session session = entry.getKey();
            final String channel = entry.getValue();
            if (toChannel == null || (!channel.equals(DEFAULT_CHANNEL) && toChannel.equals(channel))) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (final IOException e) {
                    LOG.debug("Removing disconnected WebSocket session: {}", e.getMessage());
                    sessions.remove(session);
                }
            }
        }
    }

    /**
     * Get the number of sessions subscribed to a channel.
     */
    public static int getChannelCount(final String channel) {
        return (int) sessions.values().stream().filter(channel::equals).count();
    }

    /**
     * Check if any WebSocket sessions are connected.
     */
    public static boolean hasConnections() {
        return !sessions.isEmpty();
    }
}
