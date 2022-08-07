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

import com.ibm.icu.text.Collator;
import io.lacuna.bifurcan.IEntry;
import io.lacuna.bifurcan.IMap;
import io.lacuna.bifurcan.Maps;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Sequence;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.function.BinaryOperator;

import static org.exist.xquery.functions.map.MapType.newLinearMap;

/**
 * Implementation of the XDM map() type for a map that only
 * contains a single key and value.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class SingleKeyMapType extends AbstractMapType {

    private final AtomicValue key;
    private final Sequence value;
    private final @Nullable Collator collator;

    public SingleKeyMapType(final XQueryContext context, final @Nullable Collator collator, final AtomicValue key, final Sequence value) {
        this(null, context, collator, key, value);
    }

    public SingleKeyMapType(final Expression expression, final XQueryContext context, final @Nullable Collator collator, final AtomicValue key, final Sequence value) {
        super(expression, context);
        this.key = key;
        this.value = value;
        this.collator = collator;
    }

    @Override
    public int getKeyType() {
        return key.getType();
    }

    @Override
    public Sequence get(final AtomicValue key) {
        if (sameKey(collator, this.key, key)) {
            return this.value;
        }
        return null;
    }

    @Override
    public AbstractMapType merge(final Iterable<AbstractMapType> others) {
        try (final MapType map = new MapType(getExpression(), context, collator, key, value)) {
            return map.merge(others);
        }
    }

    @Override
    public AbstractMapType merge(final Iterable<AbstractMapType> others, final BinaryOperator<Sequence> mergeFn) {
        try (final MapType map = new MapType(context, collator, key, value)) {
            return map.merge(others, mergeFn);
        }
    }

    @Override
    public AbstractMapType put(final AtomicValue key, final Sequence value) {
        final IMap<AtomicValue, Sequence> map = newLinearMap(collator);
        int keyType = UNKNOWN_KEY_TYPE;
        if (this.key != null) {
            map.put(this.key, this.value);
            keyType = this.key.getType();
        }
        map.put(key, value);
        if (keyType != key.getType()) {
            keyType = MIXED_KEY_TYPES;
        }

        return new MapType(getExpression(), context, map.forked(), keyType);
    }

    @Override
    public boolean contains(final AtomicValue key) {
        return sameKey(collator, this.key, key);
    }

    @Override
    public Sequence keys() {
        return key == null ? Sequence.EMPTY_SEQUENCE : key;
    }

    @Override
    public int size() {
        return key == null ? 0 : 1;
    }

    @Override
    public AbstractMapType remove(final AtomicValue[] keysAtomicValues) throws XPathException {
        for (final AtomicValue key: keysAtomicValues) {
            if (sameKey(collator, key, this.key)) {
                // single key map, and we matched on our key... return an empty map!
                return new MapType(getExpression(), context);
            }
        }

        // nothing to remove, return a copy
        return new SingleKeyMapType(getExpression(), context, collator, key, value);
    }

    @Override
    public AtomicValue key() {
        return key;
    }

    @Override
    public Sequence value() {
        return value;
    }

    @Override
    public Iterator<IEntry<AtomicValue, Sequence>> iterator() {
        return new SingleKeyMapIterator();
    }

    private class SingleKeyMapIterator implements Iterator<IEntry<AtomicValue, Sequence>> {

        boolean hasMore = true;

        @Override
        public boolean hasNext() {
            return hasMore;
        }

        @Override
        public IEntry<AtomicValue, Sequence> next() {
            if (!hasMore) {
                return null;
            }
            hasMore = false;
            return new Maps.Entry<>(key, value);
        }
    }
}