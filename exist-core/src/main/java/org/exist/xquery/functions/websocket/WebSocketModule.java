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

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

/**
 * XQuery module providing WebSocket messaging functions.
 *
 * Namespace: http://exist-db.org/xquery/websocket
 * Prefix: ws
 *
 * Provides functions to send messages to WebSocket clients subscribed
 * to named channels. Also serves as the backing module for the
 * backward-compatible console: namespace.
 */
public class WebSocketModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://exist-db.org/xquery/websocket";
    public static final String PREFIX = "ws";

    public static final FunctionDef[] functions = {
        new FunctionDef(WebSocketFunctions.WS_LOG[0], WebSocketFunctions.class),
        new FunctionDef(WebSocketFunctions.WS_LOG[1], WebSocketFunctions.class),
        new FunctionDef(WebSocketFunctions.WS_SEND, WebSocketFunctions.class),
        new FunctionDef(WebSocketFunctions.WS_BROADCAST, WebSocketFunctions.class),
        new FunctionDef(WebSocketFunctions.WS_CHANNEL_COUNT, WebSocketFunctions.class)
    };

    private static volatile ConsoleAdapter adapter = null;

    public WebSocketModule(final Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
    }

    public static void log(final String channel, final String message) {
        log(channel, false, message);
    }

    public static void log(final String channel, final boolean json, final String message) {
        if (adapter != null) {
            adapter.log(channel, json, message);
        }
    }

    public static void log(final String channel, final String source, final int line, final int column,
            final String message) {
        log(channel, source, line, column, false, message);
    }

    public static void log(final String channel, final String source, final int line, final int column,
            final boolean json, final String message) {
        if (adapter != null) {
            adapter.log(channel, source, line, column, json, message);
        }
    }

    public static void send(final String channel, final String json) {
        if (adapter != null) {
            adapter.send(channel, json);
        }
    }

    public static boolean initialized() {
        return adapter != null;
    }

    public static void setAdapter(final ConsoleAdapter consoleAdapter) {
        adapter = consoleAdapter;
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "XQuery module providing functions to send messages to WebSocket clients.";
    }

    @Override
    public String getReleaseVersion() {
        return "7.0.0";
    }
}
