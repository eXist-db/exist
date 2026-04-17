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
package org.exist.http.restxq.xquery;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

/**
 * XQuery module providing web:redirect(), web:forward(), and web:error()
 * functions for RESTXQ applications. Compatible with the BaseX web module.
 */
public class WebModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://basex.org/modules/web";
    public static final String PREFIX = "web";
    public static final String DESCRIPTION = "Web utility functions for RESTXQ (redirect, forward, error)";
    public static final String RELEASE_VERSION = "7.0";

    private static final FunctionDef[] functions = {
            new FunctionDef(WebFunctions.FNS_REDIRECT, WebFunctions.class),
            new FunctionDef(WebFunctions.FNS_FORWARD, WebFunctions.class),
            new FunctionDef(WebFunctions.FNS_ERROR_2, WebFunctions.class),
    };

    public WebModule(final Map<String, List<?>> parameters) {
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
        return DESCRIPTION;
    }

    @Override
    public String getReleaseVersion() {
        return RELEASE_VERSION;
    }
}
