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
 * Backward-compatible XQuery module using the console: namespace.
 *
 * Namespace: http://exist-db.org/xquery/console
 * Prefix: console
 *
 * Delegates to {@link WebSocketModule} so that existing monex XQuery code
 * (console:log(), console:send()) works unchanged when the WebSocket
 * endpoint is in exist-core.
 */
public class ConsoleCompatModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://exist-db.org/xquery/console";
    public static final String PREFIX = "console";

    public static final FunctionDef[] functions = {
        new FunctionDef(ConsoleCompatFunctions.CONSOLE_LOG[0], ConsoleCompatFunctions.class),
        new FunctionDef(ConsoleCompatFunctions.CONSOLE_LOG[1], ConsoleCompatFunctions.class),
        new FunctionDef(ConsoleCompatFunctions.CONSOLE_SEND, ConsoleCompatFunctions.class)
    };

    public ConsoleCompatModule(final Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
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
        return "Backward-compatible console module. Delegates to the WebSocket module.";
    }

    @Override
    public String getReleaseVersion() {
        return "7.0.0";
    }
}
