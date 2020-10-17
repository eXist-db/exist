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
package org.exist.xquery.functions.map;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

/**
 * Implements the XQuery extension for maps as proposed by Michael Kay:
 *
 * http://dev.saxonica.com/blog/mike/2012/01/#000188
 */
public class MapModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://www.w3.org/2005/xpath-functions/map";
    public static final String PREFIX = "map";

    private static final FunctionDef[] functions = {
            new FunctionDef(MapFunction.FNS_MERGE, MapFunction.class),
            new FunctionDef(MapFunction.FNS_SIZE, MapFunction.class),
            new FunctionDef(MapFunction.FNS_KEYS, MapFunction.class),
            new FunctionDef(MapFunction.FNS_CONTAINS, MapFunction.class),
            new FunctionDef(MapFunction.FNS_GET, MapFunction.class),
            new FunctionDef(MapFunction.FNS_PUT, MapFunction.class),
            new FunctionDef(MapFunction.FNS_ENTRY, MapFunction.class),
            new FunctionDef(MapFunction.FNS_REMOVE, MapFunction.class),
            new FunctionDef(MapFunction.FNS_FOR_EACH, MapFunction.class)
    };

    public MapModule(Map<String, List<?>> parameters) {
        super(functions, parameters, false);
    }

    public String getNamespaceURI() {
        return "http://www.w3.org/2005/xpath-functions/map";
    }

    public String getDefaultPrefix() {
        return "map";
    }

    public String getDescription() {
        return "Functions that operate on maps";
    }

    public String getReleaseVersion() {
        return "eXist-2.0.x";
    }
}
