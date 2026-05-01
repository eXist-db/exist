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
package org.exist.xquery;

import io.lacuna.bifurcan.IEntry;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * XPath/XQuery 4.0 PR1501 coercion of map / array values to a typed
 * {@code map(K, V)} or {@code array(T)} binding.
 *
 * Given a value sequence and a declared {@link SequenceType}, attempt to
 * produce a new sequence whose maps/arrays have keys/values converted
 * to the declared component types. Returns {@code null} when coercion
 * fails for any entry; the caller can then fall back to a strict shape
 * check that raises XPTY0004 with a precise error message.
 */
public final class MapTypeCoercion {

    private MapTypeCoercion() {
        // utility
    }

    static Sequence tryCoerce(final XQueryContext context, final SequenceType targetType,
            final Sequence value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        final SequenceType[] params = targetType.getFunctionParamTypes();
        if (params == null) {
            return null;
        }
        final int primary = targetType.getPrimaryType();
        try {
            final ValueSequence result = new ValueSequence();
            for (final SequenceIterator i = value.iterate(); i.hasNext(); ) {
                final Item item = i.nextItem();
                if (primary == Type.MAP_ITEM && item instanceof AbstractMapType map) {
                    if (params.length != 2) {
                        return null;
                    }
                    final AbstractMapType coerced = coerceMap(context, map, params[0], params[1]);
                    if (coerced == null) {
                        return null;
                    }
                    result.add(coerced);
                } else if (primary == Type.ARRAY_ITEM && item instanceof ArrayType array) {
                    if (params.length != 1) {
                        return null;
                    }
                    final ArrayType coerced = coerceArray(context, array, params[0]);
                    if (coerced == null) {
                        return null;
                    }
                    result.add(coerced);
                } else {
                    return null;
                }
            }
            return result;
        } catch (final Throwable t) {
            // Coercion failures (FORG0001 from out-of-range numeric conversion,
            // unexpected runtime issues) leave the original value in place; the
            // caller falls back to a strict shape check that produces XPTY0004.
            return null;
        }
    }

    private static AbstractMapType coerceMap(final XQueryContext context, final AbstractMapType map,
            final SequenceType keyType, final SequenceType valueType) throws XPathException {
        final MapType result = new MapType(context);
        for (final IEntry<AtomicValue, Sequence> entry : map) {
            final AtomicValue key = entry.key();
            final AtomicValue coercedKey = coerceAtomic(key, keyType.getPrimaryType());
            if (coercedKey == null) {
                return null;
            }
            // PR1501: if coercion would collapse two distinct source keys
            // into one (e.g. xs:double 1.0e0 and 1.0e0 + epsilon both
            // becoming xs:float 1.0), the result no longer matches the
            // declared type — abort and let the caller raise XPTY0004.
            if (result.contains(coercedKey)) {
                return null;
            }
            final Sequence coercedValue = coerceSequence(entry.value(), valueType);
            if (coercedValue == null) {
                return null;
            }
            result.add(coercedKey, coercedValue);
        }
        return result;
    }

    private static ArrayType coerceArray(final XQueryContext context, final ArrayType array,
            final SequenceType memberType) throws XPathException {
        final List<Sequence> coerced = new ArrayList<>(array.getSize());
        for (final Sequence member : array.toArray()) {
            final Sequence c = coerceSequence(member, memberType);
            if (c == null) {
                return null;
            }
            coerced.add(c);
        }
        return new ArrayType(context, coerced);
    }

    private static Sequence coerceSequence(final Sequence seq, final SequenceType targetType) throws XPathException {
        try {
            targetType.checkCardinality(seq);
        } catch (final XPathException e) {
            return null;
        }
        if (seq.isEmpty()) {
            return seq;
        }
        if (targetType.checkType(seq)) {
            return seq;
        }
        final int targetPrimary = targetType.getPrimaryType();
        if (!Type.subTypeOf(targetPrimary, Type.ANY_ATOMIC_TYPE)) {
            return null;
        }
        final ValueSequence converted = new ValueSequence(seq.getItemCount());
        for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            if (!(item instanceof final AtomicValue av)) {
                return null;
            }
            final AtomicValue coerced = coerceAtomic(av, targetPrimary);
            if (coerced == null) {
                return null;
            }
            converted.add(coerced);
        }
        return converted;
    }

    private static AtomicValue coerceAtomic(final AtomicValue av, final int targetType) {
        if (Type.subTypeOf(av.getType(), targetType)) {
            return av;
        }
        // Per XPath casting rules, xs:decimal/double/float -> xs:integer
        // (or its subtypes) is only permitted when the source has no
        // fractional part. eXist's general convertTo silently truncates,
        // which is acceptable for explicit casts that opt-in to that
        // behaviour but would mask coercion failures here.
        if (Type.subTypeOf(targetType, Type.INTEGER)
                && Type.subTypeOf(av.getType(), Type.DECIMAL)
                && av instanceof org.exist.xquery.value.NumericValue nv
                && nv.hasFractionalPart()) {
            return null;
        }
        try {
            return av.convertTo(targetType);
        } catch (final XPathException e) {
            return null;
        }
    }
}
