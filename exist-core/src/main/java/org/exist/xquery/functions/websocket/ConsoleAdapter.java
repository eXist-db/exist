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

/**
 * Adapter interface for sending console/log messages over WebSocket.
 * Implementations bridge the XQuery module to the WebSocket endpoint.
 */
public interface ConsoleAdapter {

    void log(String channel, String message);

    void log(String channel, boolean json, String message);

    void log(String channel, String source, int line, int column, String message);

    void log(String channel, String source, int line, int column, boolean json, String message);

    void send(String channel, String message);
}
