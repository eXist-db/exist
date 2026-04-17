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
package org.exist.http.ws;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.storage.ProcessMonitor;
import org.exist.xquery.XQueryWatchDog;
import org.exist.xquery.functions.websocket.WebSocketEndpoint;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.StringWriter;

/**
 * Bridges the /ws/eval query lifecycle to the /ws pub/sub channel system,
 * broadcasting query events to admin monitoring clients subscribed to the
 * {@code _monitor} channel.
 *
 * <p>Also provides periodic snapshots of ALL running queries (including
 * REST/XQueryServlet queries that don't go through /ws/eval).</p>
 */
public final class QueryMonitorBroadcaster {

    private static final Logger LOG = LogManager.getLogger(QueryMonitorBroadcaster.class);
    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final String CHANNEL = "_monitor";
    private static final int MAX_SOURCE_LENGTH = 100;

    private QueryMonitorBroadcaster() {
        // utility class
    }

    /**
     * Broadcast a query lifecycle event to the _monitor channel.
     * No-op if nobody is subscribed.
     */
    public static void broadcastEvent(final String event, final String queryId,
                                       final String user, @Nullable final String query,
                                       @Nullable final String phase, final long items,
                                       final long elapsed) {
        if (WebSocketEndpoint.getChannelCount(CHANNEL) == 0) {
            return;
        }
        try {
            final String json = buildEventMessage(event, queryId, user, query, phase, items, elapsed);
            WebSocketEndpoint.sendAll(CHANNEL, json);
        } catch (final IOException e) {
            LOG.debug("Failed to broadcast monitor event: {}", e.getMessage());
        }
    }

    /**
     * Broadcast a snapshot of all running queries from the ProcessMonitor.
     * Called periodically (e.g., every 1 second) to catch queries from all
     * execution paths (REST, XQueryServlet, URL rewrite, etc.).
     */
    public static void broadcastSnapshot(final BrokerPool pool) {
        if (WebSocketEndpoint.getChannelCount(CHANNEL) == 0) {
            return;
        }
        try {
            final ProcessMonitor monitor = pool.getProcessMonitor();
            final XQueryWatchDog[] watchDogs = monitor.getRunningXQueries();

            final StringWriter sw = new StringWriter();
            try (final JsonGenerator gen = JSON_FACTORY.createGenerator(sw)) {
                gen.writeStartObject();
                gen.writeStringField("type", "monitor");
                gen.writeStringField("event", "snapshot");
                gen.writeArrayFieldStart("queries");

                for (final XQueryWatchDog wd : watchDogs) {
                    final var ctx = wd.getContext();
                    gen.writeStartObject();
                    gen.writeStringField("queryId", String.valueOf(System.identityHashCode(ctx)));
                    gen.writeStringField("user", ctx.getEffectiveUser().getName());

                    final String sourceKey = ctx.getSource() != null ? ctx.getSource().pathOrShortIdentifier() : "";
                    gen.writeStringField("source", sourceKey);

                    gen.writeStringField("phase", "evaluating");
                    gen.writeNumberField("elapsed",
                            System.currentTimeMillis() - wd.getStartTime());
                    gen.writeBooleanField("terminating", wd.isTerminating());
                    gen.writeEndObject();
                }

                gen.writeEndArray();
                gen.writeEndObject();
            }

            WebSocketEndpoint.sendAll(CHANNEL, sw.toString());
        } catch (final Exception e) {
            LOG.debug("Failed to broadcast monitor snapshot: {}", e.getMessage());
        }
    }

    private static String buildEventMessage(final String event, final String queryId,
                                             final String user, @Nullable final String query,
                                             @Nullable final String phase, final long items,
                                             final long elapsed) throws IOException {
        final StringWriter sw = new StringWriter();
        try (final JsonGenerator gen = JSON_FACTORY.createGenerator(sw)) {
            gen.writeStartObject();
            gen.writeStringField("type", "monitor");
            gen.writeStringField("event", event);
            gen.writeStringField("queryId", queryId);
            gen.writeStringField("user", user);
            if (query != null) {
                gen.writeStringField("source",
                        query.length() > MAX_SOURCE_LENGTH
                                ? query.substring(0, MAX_SOURCE_LENGTH) + "..."
                                : query);
            }
            if (phase != null) {
                gen.writeStringField("phase", phase);
            }
            gen.writeNumberField("items", items);
            gen.writeNumberField("elapsed", elapsed);
            gen.writeEndObject();
        }
        return sw.toString();
    }
}
