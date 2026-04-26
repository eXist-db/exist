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
 * fn:get($key as xs:anyAtomicType*) as item()*
 *
 * XQuery 4.0 context-dependent lookup function. Looks up values from
 * the context item by key or position. Accepts a sequence of keys and
 * returns the union of all lookups.
 * - For arrays: returns the members at the given positions
 * - For maps: returns the values for the given keys
 * - For atomic values: returns the value itself (identity)
 */
public class FnGet extends BasicFunction {

    public static final FunctionSignature FN_GET = new FunctionSignature(
            new QName("get", Function.BUILTIN_FUNCTION_NS),
            "Looks up values from the context item. For arrays, returns the members " +
            "at the given positions. For maps, returns the values for the given keys.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("key", Type.ANY_ATOMIC_TYPE,
                            Cardinality.ZERO_OR_MORE, "The lookup key(s) or index(es)")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                    "The looked-up value(s)"));

    public FnGet(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        // Get the context item
        final Sequence ctxSeq = contextSequence;
        if (ctxSeq == null || ctxSeq.isEmpty()) {
            throw new XPathException(this, ErrorCodes.XPDY0002,
                    "fn:get requires a context item");
        }

        final Item contextItem = ctxSeq.itemAt(0);
        final Sequence keys = args[0];

        // Empty key sequence returns empty
        if (keys.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // Single key: direct lookup (common case, avoids ValueSequence overhead)
        if (keys.getItemCount() == 1) {
            return lookupSingle(contextItem, (AtomicValue) keys.itemAt(0));
        }

        // Multiple keys: iterate and collect results
        final ValueSequence result = new ValueSequence();
        for (final SequenceIterator it = keys.iterate(); it.hasNext(); ) {
            final AtomicValue key = (AtomicValue) it.nextItem();
            final Sequence value = lookupSingle(contextItem, key);
            if (!value.isEmpty()) {
                result.addAll(value);
            }
        }
        return result;
    }

    private Sequence lookupSingle(final Item contextItem, final AtomicValue key) throws XPathException {
        if (contextItem instanceof ArrayType) {
            // Array lookup by position — return empty for out-of-bounds (XQ4 fn:get semantics)
            final ArrayType array = (ArrayType) contextItem;
            try {
                final int index = ((IntegerValue) key.convertTo(Type.INTEGER)).getInt();
                if (index < 1 || index > array.getSize()) {
                    return Sequence.EMPTY_SEQUENCE;
                }
                return array.get(index - 1);
            } catch (final XPathException e) {
                // Non-integer key on array: return empty
                return Sequence.EMPTY_SEQUENCE;
            }
        } else if (contextItem instanceof AbstractMapType) {
            // Map lookup by key
            final AbstractMapType map = (AbstractMapType) contextItem;
            final Sequence value = map.get(key);
            return value != null ? value : Sequence.EMPTY_SEQUENCE;
        } else if (contextItem instanceof FunctionReference) {
            // Function application
            final FunctionReference funcRef = (FunctionReference) contextItem;
            return funcRef.evalFunction(null, null, new Sequence[]{key.toSequence()});
        } else {
            // Atomic value: return the context item itself
            return contextItem.toSequence();
        }
    }
}
