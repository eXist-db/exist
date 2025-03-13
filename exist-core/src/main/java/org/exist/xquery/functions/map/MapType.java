/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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

import com.evolvedbinary.j8fu.tuple.Tuple2;
import com.ibm.icu.text.Collator;
import io.lacuna.bifurcan.IEntry;
import io.lacuna.bifurcan.IMap;
import io.lacuna.bifurcan.LinearMap;
import io.lacuna.bifurcan.Map;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.ToLongFunction;

/**
 * Full implementation of the XDM map() type based on an
 * immutable hash-map.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class MapType extends AbstractMapType {

    private static final ToLongFunction<AtomicValue> KEY_HASH_FN = AtomicValue::hashCode;

    // TODO(AR) future potential optimisation... could the class member `map` remain `linear` ?
    private IMap<AtomicValue, Sequence> map;

    /**
     * The type of the keys in the map,
     * if not all keys have the same type
     * then this is set to {@link #MIXED_KEY_TYPES}.
     *
     * Uses integer values from {@link org.exist.xquery.value.Type}.
     */
    private int keyType = UNKNOWN_KEY_TYPE;

    private static IMap<AtomicValue, Sequence> newMap(@Nullable final Collator collator) {
        return new Map<>(KEY_HASH_FN, (k1, k2) -> sameKey(collator, k1, k2));
    }

    /**
     * Construct a new Bifurcan mutable-map for use with AtomicValue keys.
     * 
     * This function is predominantly for pre-building a Map of key/values
     * for passing to {@link #MapType(XQueryContext, IMap, Integer)}.
     *
     * @param <V> the value type of the linear map
     * @param collator The collator if a collation is in effect for comparing keys.
     * 
     * @return A mutable-map on which {@link IMap#forked()} can be called to produce an immutable map. 
     */
    public static <V> IMap<AtomicValue, V> newLinearMap(@Nullable final Collator collator) {
        return new LinearMap<>(KEY_HASH_FN, (k1, k2) -> sameKey(collator, k1, k2));
    }

    public MapType(final XQueryContext context) {
        this(null, context);
    }

    public MapType(final Expression expression, final XQueryContext context) {
        this(expression, context,null);
    }

    public MapType(final XQueryContext context, @Nullable final Collator collator) {
        this(null, context, collator);
    }

    public MapType(final Expression expression, final XQueryContext context, @Nullable final Collator collator) {
        super(expression, context);
        // if there's no collation, we'll use a hash map for better performance
        this.map = newMap(collator);
    }

    public MapType(final XQueryContext context, @Nullable final Collator collator, final AtomicValue key, final Sequence value) {
        this(null, context, collator, key, value);
    }

    public MapType(final Expression expression, final XQueryContext context, @Nullable final Collator collator, final AtomicValue key, final Sequence value) {
        super(expression, context);
        this.map = newMap(collator).put(key, value);
        this.keyType = key.getType();
    }

    public MapType(final XQueryContext context, @Nullable final Collator collator, final Iterable<Tuple2<AtomicValue, Sequence>> keyValues) {
        this(null, context, collator, keyValues);
    }

    public MapType(final Expression expression, final XQueryContext context, @Nullable final Collator collator, final Iterable<Tuple2<AtomicValue, Sequence>> keyValues) {
        this(expression, context, collator, keyValues.iterator());
    }

    public MapType(final XQueryContext context, @Nullable final Collator collator, final Iterator<Tuple2<AtomicValue, Sequence>> keyValues) {
        this(null, context, collator, keyValues);
    }

    public MapType(final Expression expression, final XQueryContext context, @Nullable final Collator collator, final Iterator<Tuple2<AtomicValue, Sequence>> keyValues) {
        super(expression, context);

        // bulk put
        final IMap<AtomicValue, Sequence> map = newMap(collator).linear();
        keyValues.forEachRemaining(kv -> map.put(kv._1, kv._2));
        this.map = map.forked();

        setKeyType(map);
    }

    public MapType(final XQueryContext context, final IMap<AtomicValue, Sequence> other, @Nullable final Integer keyType) {
        this(null, context, other, keyType);
    }

    public MapType(final Expression expression, final XQueryContext context, final IMap<AtomicValue, Sequence> other, @Nullable final Integer keyType) {
        super(expression, context);

        if (other.isLinear()) {
            throw new IllegalArgumentException("Map must be immutable, but linear Map was provided");
        }

        this.map = other;

        if (keyType != null) {
            this.keyType = keyType;
        } else {
            setKeyType(map);
        }
    }

    public void add(final AbstractMapType other) {
        setKeyType(other.key() != null ? other.key().getType() : UNKNOWN_KEY_TYPE);

        if(other instanceof MapType) {
            map = map.union(((MapType)other).map);
        } else {

            // create a transient map
            final IMap<AtomicValue, Sequence> newMap = map.linear();

            for (final IEntry<AtomicValue, Sequence> entry : other) {
                newMap.put(entry.key(), entry.value());
            }

            // return to immutable map
            map = newMap.forked();
        }
    }

    @Override
    public AbstractMapType merge(final Iterable<AbstractMapType> others) {

        // create a transient map
        IMap<AtomicValue, Sequence> newMap = map.linear();

        int prevType = keyType;
        for (final AbstractMapType other: others) {
            if (other instanceof MapType otherMap) {
                // MapType - optimise merge
                newMap = newMap.union(otherMap.map);

                if (prevType != otherMap.keyType) {
                    prevType = MIXED_KEY_TYPES;
                }
            } else {
                // non MapType
                for (final IEntry<AtomicValue, Sequence> entry : other) {
                    final AtomicValue key = entry.key();
                    newMap = newMap.put(key, entry.value());
                    if (prevType != key.getType()) {
                        prevType = MIXED_KEY_TYPES;
                    }
                }
            }
        }

        // return an immutable map
        return new MapType(getExpression(), context, newMap.forked(), prevType);
    }

    @Override
    public AbstractMapType merge(final Iterable<AbstractMapType> others, final BinaryOperator<Sequence> mergeFn) {

        // create a transient map
        IMap<AtomicValue, Sequence> newMap = map.linear();

        int prevType = keyType;
        for (final AbstractMapType other: others) {
            if (other instanceof MapType otherMap) {
                // MapType - optimise merge
                newMap = newMap.merge(otherMap.map, mergeFn);

                if (prevType != otherMap.keyType) {
                    prevType = MIXED_KEY_TYPES;
                }
            } else {
                // non MapType
                for (final IEntry<AtomicValue, Sequence> entry : other) {
                    final AtomicValue key = entry.key();
                    final Optional<Sequence> headEntry = newMap.get(key);
                    if (headEntry.isPresent()) {
                        newMap = newMap.put(key, mergeFn.apply(headEntry.get(), entry.value()));
                    } else {
                        newMap = newMap.put(key, entry.value());
                    }
                    if (prevType != key.getType()) {
                        prevType = MIXED_KEY_TYPES;
                    }
                }
            }
        }

        // return an immutable map
        return new MapType(context, newMap.forked(), prevType);
    }

    public void add(final AtomicValue key, final Sequence value) {
        setKeyType(key.getType());
        map = map.put(key, value);
    }

    @Override
    public Sequence get(AtomicValue key) {
        key = convert(key);
        if (key == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Sequence result = map.get(key, null);
        return result == null ? Sequence.EMPTY_SEQUENCE : result;
    }

    @Override
    public AbstractMapType put(final AtomicValue key, final Sequence value) {
        final IMap<AtomicValue, Sequence> newMap = map.put(key, value);
        return new MapType(getExpression(), this.context, newMap, keyType == key.getType() ? keyType : MIXED_KEY_TYPES);
    }

    @Override
    public boolean contains(AtomicValue key) {
        key = convert(key);
        if (key == null) {
            return false;
        }

        return map.contains(key);
    }

    @Override
    public boolean containsReference(final Item item) {
        for (final Sequence value : map.values()) {
            if (value == item || value.containsReference(item)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(final Item item) {
        for (final Sequence value : map.values()) {
            if (value.equals(item) || value.contains(item)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Sequence keys() {
        final ArrayListValueSequence seq = new ArrayListValueSequence((int)map.size());
        for (final AtomicValue key: map.keys()) {
            seq.add(key);
        }
        return seq;
    }

    public AbstractMapType remove(final AtomicValue[] keysAtomicValues) {

        // create a transient map
        IMap<AtomicValue, Sequence> newMap = map.linear();

        for (final AtomicValue key: keysAtomicValues) {
            newMap = newMap.remove(key);
        }

        // return an immutable map
        return new MapType(getExpression(), context, newMap.forked(), keyType);
    }

    @Override
    public int size() {
        return (int)map.size();
    }

    @Override
    public Iterator<IEntry<AtomicValue, Sequence>> iterator() {
        return map.iterator();
    }

    @Override
    public AtomicValue key() {
        if (map.size() > 0) {
            final IEntry<AtomicValue, Sequence> entry = map.nth(0);
            if (entry != null) {
                return entry.key();
            }
        }

        return null;
    }

    @Override
    public Sequence value() {
        if (map.size() > 0) {
            final IEntry<AtomicValue, Sequence> entry = map.nth(0);
            if (entry != null) {
                return entry.value();
            }
        }

        return null;
    }

    private void setKeyType(final int newType) {
        if (keyType == UNKNOWN_KEY_TYPE) {
            keyType = newType;

        } else if (keyType != newType) {
            keyType = MIXED_KEY_TYPES;
        }
    }

    private void setKeyType(final IMap<AtomicValue, Sequence> newMap) {
        for (final AtomicValue newKey : newMap.keys()) {
            final int newType = newKey.getType();

            if (keyType == UNKNOWN_KEY_TYPE) {
                keyType = newType;

            } else if (keyType != newType) {
                keyType = MIXED_KEY_TYPES;
                break; // done, we only have to detect this once!
            }
        }
    }

    private AtomicValue convert(final AtomicValue key) {
        if (keyType != UNKNOWN_KEY_TYPE && keyType != MIXED_KEY_TYPES) {
            try {
                return key.convertTo(keyType);
            } catch (final XPathException e) {
                return null;
            }
        }
        return key;
    }

    @Override
    public int getKeyType() {
        return keyType;
    }
}
