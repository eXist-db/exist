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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.*;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implements fn:function-identity (XQuery 4.0).
 *
 * Returns a string that uniquely identifies a function item. Two calls with
 * the same function return codepoint-equal strings; calls with different
 * functions return different strings.
 *
 * For named functions, identity is based on QName + arity.
 * For anonymous functions, maps, and arrays, identity is based on object identity.
 */
public class FnFunctionIdentity extends BasicFunction {

    /** Counter for assigning unique IDs to anonymous function items, maps, and arrays. */
    private static final AtomicLong ID_COUNTER = new AtomicLong(1);

    /** Identity-based map to ensure the same object always gets the same ID.
     *  Uses reference equality (==), not equals(), so structurally equal but
     *  distinct maps/arrays get different IDs per the spec. */
    private static final Map<Object, Long> IDENTITY_MAP = new IdentityHashMap<>();

    private static synchronized long getOrAssignId(final Object obj) {
        return IDENTITY_MAP.computeIfAbsent(obj, k -> ID_COUNTER.getAndIncrement());
    }

    public static final FunctionSignature FN_FUNCTION_IDENTITY = new FunctionSignature(
            new QName("function-identity", Function.BUILTIN_FUNCTION_NS),
            "Returns a string that uniquely identifies a function item.",
            new SequenceType[]{
                    new FunctionParameterSequenceType("function", Type.ITEM,
                            Cardinality.EXACTLY_ONE, "The function item to identify")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE,
                    "A string uniquely identifying the function"));

    public FnFunctionIdentity(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Item funcItem = args[0].itemAt(0);
        return new StringValue(this, computeIdentity(funcItem));
    }

    private static String computeIdentity(final Item item) throws XPathException {
        if (item instanceof FunctionReference ref) {
            final FunctionSignature sig = ref.getSignature();
            final QName name = sig.getName();
            if (name != null && name != InlineFunction.INLINE_FUNCTION_QNAME) {
                // Named function: identity based on expanded QName + arity
                return "Q{" + (name.getNamespaceURI() != null ? name.getNamespaceURI() : "")
                        + "}" + name.getLocalPart() + "#" + sig.getArgumentCount();
            }
            // Anonymous function: use counter-based identity
            return "anon@" + getOrAssignId(ref);
        }
        if (item instanceof AbstractMapType) {
            // Each distinct map object gets a unique ID
            return "map@" + getOrAssignId(item);
        }
        if (item instanceof ArrayType) {
            return "array@" + getOrAssignId(item);
        }
        // Fallback for other function types
        return "func@" + getOrAssignId(item);
    }
}
