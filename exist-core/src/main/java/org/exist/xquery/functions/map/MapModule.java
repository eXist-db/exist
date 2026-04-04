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

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDSL;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;

import java.util.List;
import java.util.Map;

import static org.exist.xquery.FunctionDSL.functionDefs;

/**
 * Register the
 * <a href="https://www.w3.org/TR/xpath-functions-31/#map-functions">functions that operate on maps</a>
 */
public class MapModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://www.w3.org/2005/xpath-functions/map";
    public static final String PREFIX = "map";

    private static final FunctionDef[] functions = functionDefs(
            MapFunction.class,
            MapFunction.MERGE_1,
            MapFunction.MERGE_2,
            MapFunction.SIZE,
            MapFunction.KEYS,
            MapFunction.CONTAINS,
            MapFunction.GET,
            MapFunction.FIND,
            MapFunction.PUT,
            MapFunction.ENTRY,
            MapFunction.REMOVE,
            MapFunction.FOR_EACH,
            // --- XQuery 4.0 map functions ---
            MapFunction.FNS_GET_DEFAULT,
            MapFunction.FNS_EMPTY[0],
            MapFunction.FNS_EMPTY[1],
            MapFunction.BUILD_1,
            MapFunction.BUILD_2,
            MapFunction.ITEMS,
            MapFunction.ENTRIES,
            MapFunction.FILTER_SIG,
            MapFunction.KEYS_WHERE
    );

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

    static FunctionSignature functionSignature(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType... paramTypes) {
        return FunctionDSL.functionSignature(new QName(name, NAMESPACE_URI, PREFIX), description, returnType, paramTypes);
    }
}
