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

/**
 * fn:get($key as xs:anyAtomicType) as item()*
 *
 * XQuery 4.0 context-dependent lookup function. Looks up a value from
 * the context item:
 * - For arrays: returns the member at the given position
 * - For maps: returns the value for the given key
 * - For atomic values: returns the value itself (identity)
 */
public class FnGet extends BasicFunction {

    public static final FunctionSignature FN_GET = new FunctionSignature(
            new QName("get", Function.BUILTIN_FUNCTION_NS),
            "Looks up a value from the context item. For arrays, returns the member " +
            "at the given position. For maps, returns the value for the given key.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("key", Type.ANY_ATOMIC_TYPE,
                            Cardinality.EXACTLY_ONE, "The lookup key or index")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                    "The looked-up value"));

    public FnGet(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        // Get the context item
        Sequence ctxSeq = contextSequence;
        if (ctxSeq == null || ctxSeq.isEmpty()) {
            throw new XPathException(this, ErrorCodes.XPDY0002,
                    "fn:get requires a context item");
        }

        final Item contextItem = ctxSeq.itemAt(0);
        final AtomicValue key = (AtomicValue) args[0].itemAt(0);

        if (contextItem instanceof ArrayType array) {
            final int index = ((IntegerValue) key.convertTo(Type.INTEGER)).getInt();
            if (index < 1 || index > array.getSize()) {
                throw new XPathException(this, ErrorCodes.FOAY0001,
                        "Array index " + index + " out of bounds (1.." + array.getSize() + ")");
            }
            return array.get(index - 1);
        } else if (contextItem instanceof AbstractMapType map) {
            final Sequence value = map.get(key);
            return value != null ? value : Sequence.EMPTY_SEQUENCE;
        } else if (contextItem instanceof FunctionReference funcRef) {
            return funcRef.evalFunction(null, null, new Sequence[]{key.toSequence()});
        } else {
            return contextItem.toSequence();
        }
    }
}
