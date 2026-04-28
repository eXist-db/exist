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
package org.exist.xquery.functions.array;

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
 * Module registering functions that operate on arrays.
 */
public class ArrayModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://www.w3.org/2005/xpath-functions/array";
    public static final String PREFIX = "array";

    private static final FunctionDef[] functions = functionDefs(
            functionDefs(ArrayFunction.class,
                    ArrayFunction.SIZE,
                    ArrayFunction.GET,
                    ArrayFunction.GET_DEFAULT,
                    ArrayFunction.PUT,
                    ArrayFunction.APPEND,
                    ArrayFunction.SUBARRAY_1,
                    ArrayFunction.SUBARRAY_2,
                    ArrayFunction.REMOVE,
                    ArrayFunction.INSERT_BEFORE,
                    ArrayFunction.HEAD,
                    ArrayFunction.TAIL,
                    ArrayFunction.REVERSE,
                    ArrayFunction.JOIN,
                    ArrayFunction.FOR_EACH,
                    ArrayFunction.FILTER,
                    ArrayFunction.FOLD_LEFT,
                    ArrayFunction.FOLD_RIGHT,
                    ArrayFunction.FOR_EACH_PAIR,
                    ArrayFunction.SORT_1,
                    ArrayFunction.SORT_2,
                    ArrayFunction.SORT_3,
                    ArrayFunction.FLATTEN,
                    // --- XQuery 4.0 ---
                    ArrayFunction.ARRAY_EMPTY,
                    ArrayFunction.ARRAY_FOOT,
                    ArrayFunction.ARRAY_TRUNK,
                    ArrayFunction.ARRAY_ITEMS,
                    ArrayFunction.ARRAY_MEMBERS
            ),
            functionDefs(ArraySlice.class, ArraySlice.signatures),
            functionDefs(ArrayIndexWhere.class, ArrayIndexWhere.signatures),
            functionDefs(ArraySortWith.class, ArraySortWith.signatures),
            functionDefs(ArraySortBy.class, ArraySortBy.signatures),
            functionDefs(ArrayBuild.class, ArrayBuild.signatures),
            functionDefs(ArrayIndexOf.class, ArrayIndexOf.signatures),
            functionDefs(ArrayOfMembers.class, ArrayOfMembers.signatures),
            functionDefs(ArraySplit.class, ArraySplit.signatures)
    );

    public ArrayModule(Map<String, List<?>> parameters) {
        super(functions, parameters, false);
    }

    static FunctionSignature functionSignature(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType... paramTypes) {
        return FunctionDSL.functionSignature(new QName(name, NAMESPACE_URI, PREFIX), description, returnType, paramTypes);
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
        return "Functions that operate on arrays";
    }

    @Override
    public String getReleaseVersion() {
        return "2.2.1";
    }
}
