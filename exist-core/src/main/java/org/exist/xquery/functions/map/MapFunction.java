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

import io.lacuna.bifurcan.IEntry;
import org.exist.xquery.*;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import java.util.*;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.map.MapModule.functionSignature;
import static org.exist.xquery.functions.map.MapModule.functionSignatures;

/**
 * Implements all functions of the map module.
 */
public class MapFunction extends BasicFunction {

    private enum Fn {
        SIZE("size"),
        ENTRY("entry"),
        GET("get"),
        MERGE("merge"),
        PUT("put"),
        CONTAINS("contains"),
        KEYS("keys"),
        REMOVE("remove"),
        FOR_EACH("for-each"),
        FIND("find")
;
        final static Map<String, MapFunction.Fn> fnMap = new HashMap<>();
        static {
            for (MapFunction.Fn fn: MapFunction.Fn.values()) {
                fnMap.put(fn.fname, fn);
            }
        }

        static MapFunction.Fn get(String name) {
            return fnMap.get(name);
        }

        private final String fname;

        Fn(String name) {
            this.fname = name;
        }
    }

    private static final FunctionParameterSequenceType INPUT_MAP = param("map", Type.MAP_ITEM, "The input map");
    private static final FunctionParameterSequenceType FS_PARAM_MAPS = optManyParam("maps", Type.MAP_ITEM, "Existing maps to merge to create a new map.");
    private static final FunctionParameterSequenceType FS_PARAM_KEY = param("key", Type.ANY_ATOMIC_TYPE, "The key");
    private static final FunctionParameterSequenceType FS_PARAM_REMOVE_KEYS = param("keys", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The keys to remove");
    private static final FunctionParameterSequenceType FS_PARAM_VALUE = param("value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The value");

    public static final FunctionSignature[] FS_MERGE = functionSignatures(
            Fn.MERGE.fname,
            "Returns a map that combines the entries from a number of existing maps.",
            returns(Type.MAP_ITEM, "A new map which is the result of merging the maps"),
            arities(
                    arity(
                            FS_PARAM_MAPS
                    ),
                    arity(
                            FS_PARAM_MAPS,
                            param("options", Type.MAP_ITEM, "Can be used to control the way in which duplicate keys are handled.")
                    )
            )
    );

    public static final FunctionSignature FS_FIND = functionSignature(
            Fn.FIND.fname,
            "Searches the supplied input sequence and any contained maps and arrays for a map entry with the supplied key, " +
                    "and returns the corresponding values.",
            returns(Type.ARRAY_ITEM, "An array containing the found values with the input key"),
            optManyParam("input", Type.ITEM, "The sequence of maps to search"),
            FS_PARAM_KEY
    );

    public static final FunctionSignature FNS_SIZE = functionSignature(
            Fn.SIZE.fname,
            "Returns the number of entries in the supplied map.",
            returns(Type.INTEGER, "The number of key value pairs in the map"),
            INPUT_MAP
    );

    public final static FunctionSignature FNS_KEYS = functionSignature(
            Fn.KEYS.fname,
            "Returns a sequence containing all the key values present in a map.",
            returns(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE),
            INPUT_MAP
    );

    public final static FunctionSignature FNS_CONTAINS = functionSignature(
            Fn.CONTAINS.fname,
            "Tests whether a supplied map contains an entry for a given key.",
            returns(Type.BOOLEAN, Cardinality.EXACTLY_ONE),
            INPUT_MAP,
            FS_PARAM_KEY
    );

    public final static FunctionSignature FNS_GET = functionSignature(
            Fn.GET.fname,
            "Returns the value associated with a supplied key in a given map.",
            returns(Type.ITEM, Cardinality.ZERO_OR_MORE),
            INPUT_MAP,
            FS_PARAM_KEY
    );

    public final static FunctionSignature FNS_PUT = functionSignature(
            Fn.PUT.fname,
            "Returns a map containing all the contents of the supplied map, but with an additional entry, which replaces any existing entry for the same key.",
            returns(Type.MAP_ITEM, Cardinality.EXACTLY_ONE),
            INPUT_MAP,
            FS_PARAM_KEY,
            FS_PARAM_VALUE
    );

    public final static FunctionSignature FNS_ENTRY = functionSignature(
            Fn.ENTRY.fname,
            "Creates a map that contains a single entry (a key-value pair).",
            returns(Type.MAP_ITEM, Cardinality.EXACTLY_ONE),
            FS_PARAM_KEY,
            FS_PARAM_VALUE
    );

    public final static FunctionSignature FNS_REMOVE = functionSignature(
            Fn.REMOVE.fname,
            "Constructs a new map by removing an entry from an existing map.",
            returns(Type.MAP_ITEM, Cardinality.EXACTLY_ONE),
            INPUT_MAP,
            FS_PARAM_REMOVE_KEYS
    );

    public final static FunctionSignature FNS_FOR_EACH = functionSignature(
            Fn.FOR_EACH.fname,
            "takes any map as its $input argument and applies the supplied function to each entry in the map, in implementation-dependent order; the result is the sequence obtained by concatenating the results of these function calls. " +
                "The function supplied as $action takes two arguments. It is called supplying the key of the map entry as the first argument, and the associated value as the second argument.",
            returns(Type.ITEM, Cardinality.ZERO_OR_MORE),
            INPUT_MAP,
            funParam(
                "action", Type.FUNCTION,
                params(
                        param("asdf", Type.ANY_ATOMIC_TYPE,"asdf"),
                        optManyParam("asdf", Type.ITEM, "asdf")
                ),
                returns(Type.ITEM, Cardinality.ZERO_OR_MORE
            ),
            Cardinality.EXACTLY_ONE, "The function to be called for each entry")
    );

    private AnalyzeContextInfo cachedContextInfo;

    public MapFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        super.analyze(contextInfo);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final MapFunction.Fn called = MapFunction.Fn.get(getSignature().getName().getLocalPart());
        return switch (called) {
            case MERGE -> merge(args);
            case SIZE -> size(args);
            case KEYS -> keys(args);
            case CONTAINS -> contains(args);
            case GET -> get(args);
            case PUT -> put(args);
            case ENTRY -> entry(args);
            case REMOVE -> remove(args);
            case FOR_EACH -> forEach(args);
            case FIND -> find(args);
        };
    }

    private Sequence remove(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);

        final int length = args[1].getItemCount();
        final AtomicValue[] keys = new AtomicValue[length];

        for (int i = 0; i < length; i++) {
            keys[i] = (AtomicValue) args[1].itemAt(i);
        }
        return map.remove(keys);
    }

    private Sequence keys(final Sequence[] args) {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        return map.keys();
    }

    private Sequence contains(final Sequence[] args) {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        return BooleanValue.valueOf(map.contains((AtomicValue) args[1].itemAt(0)));
    }

    private Sequence get(final Sequence[] args) {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        final Sequence value = map.get((AtomicValue) args[1].itemAt(0));
        return Objects.requireNonNullElse(value, Sequence.EMPTY_SEQUENCE);
    }

    private Sequence put(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        return map.put((AtomicValue) args[1].itemAt(0), args[2]);
    }

    private Sequence entry(final Sequence[] args) throws XPathException {
        final AtomicValue key = (AtomicValue) args[0].itemAt(0);
        return new SingleKeyMapType(this, this.context, null, key, args[1]);
    }

    private Sequence size(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        return new IntegerValue(this, map.size(), Type.INTEGER);
    }

    private Sequence merge(final Sequence[] args) throws XPathException {
        if (args[0].getItemCount() == 0) {
            // map:merge(())
            return new MapType(this, this.context);
        }

        final MergeDuplicates mergeDuplicates;
        if (args.length == 2) {
            final MapType map = (MapType) args[1];
            final StringValue key = new StringValue(this, "duplicates");
            if (map.contains(key)) {
                final Sequence mapValue = map.get(key);
                mergeDuplicates = MergeDuplicates.fromDuplicatesValue(mapValue.getStringValue());
                if (mergeDuplicates == null) {
                    throw new XPathException(this, ErrorCodes.FOJS0005, "value for duplicates key was not recognised: " + mapValue.getStringValue());
                }
            } else {
                mergeDuplicates = MergeDuplicates.USE_FIRST;
            }
        } else {
            mergeDuplicates = MergeDuplicates.USE_FIRST;
        }

        final Sequence maps = args[0];
        final int totalMaps = maps.getItemCount();
        final AbstractMapType head;
        final List<AbstractMapType> tail = new ArrayList<>(totalMaps - 1);

        if (mergeDuplicates == MergeDuplicates.USE_LAST || mergeDuplicates == MergeDuplicates.COMBINE) {
            // head is the first map
            // USE_LAST will pick the item from the last map containing a duplicate item
            // COMBINE will combine duplicate items in head-first order
            head = (AbstractMapType) maps.itemAt(0);
            for (int i = 1; i < totalMaps; i++) {
                final AbstractMapType other = (AbstractMapType) maps.itemAt(i);
                tail.add(other);
            }

        } else {
            // head is the last map
            // USE_FIRST will pick the item from the first map containing a duplicate item
            head = (AbstractMapType) maps.itemAt(totalMaps - 1);
            for (int i = totalMaps - 2; i >= 0; i--) {
                final AbstractMapType other = (AbstractMapType) maps.itemAt(i);
                tail.add(other);
            }
        }

        if (mergeDuplicates == MergeDuplicates.COMBINE) {
            // Provide a callback function for merging items which share a key
            // Call merge variant
            final List<XPathException> mergeExceptions = new ArrayList<>();
            final AbstractMapType merged
                    = head.merge(tail, (first, second) -> {
                try {
                    final ValueSequence sequence = new ValueSequence(first);
                    sequence.addAll(second);
                    return sequence;
                } catch (final XPathException e) {
                    //We cannot throw out of the MapType - pass exceptions here.
                    mergeExceptions.add(e);
                }
                return Sequence.EMPTY_SEQUENCE;
            });
            if (!mergeExceptions.isEmpty()) {
                throw mergeExceptions.getFirst();
            }
            return merged;
        }

        final AbstractMapType result = head.merge(tail);

        if (mergeDuplicates == MergeDuplicates.REJECT) {

            int inputItemsSize = head.size();
            for (final AbstractMapType other : tail) {
                inputItemsSize += other.size();
            }
            if (inputItemsSize > result.size()) {
                // no duplicates, so we don't need to consider the duplicates
                throw new XPathException(this, ErrorCodes.FOJS0003, "map { \"duplicates\": \"reject\" } maps had duplicate entry");
            }
        }

        return result;
    }

    private Sequence forEach(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        try (final FunctionReference ref = (FunctionReference) args[1].itemAt(0)) {
            ref.analyze(cachedContextInfo);
            final ArrayListValueSequence result = new ArrayListValueSequence(map.size());
            for (final IEntry<AtomicValue, Sequence> entry : map) {
                final Sequence s = ref.evalFunction(null, null, new Sequence[]{ entry.key(), entry.value() });
                result.addAll(s);
            }
            return result;
        }
    }

    /**
     * Recursive helper for find
     *
     * Recursively find map members in a sequence
     * By searching each of the individual items in the sequence
     *
     * @param result add found values to this
     * @param key the key to match
     * @param sequence the sequence to search within
     */
    private static void findRec(final ArrayType result, final AtomicValue key, final Sequence sequence) {
        for (int i = 0; i < sequence.getItemCount(); i++) {
            findRec(result, key, sequence.itemAt(i));
        }
    }

    /**
     * Recursive helper for find
     *
     * Recursively find map members in items, which can only be maps or arrays
     * (They may be other types, but these are not containers)
     *
     * @param result add found values to this
     * @param key the key to match
     * @param item the item to search within
     */
    private static void findRec(final ArrayType result, final AtomicValue key, final Item item) {
        if (Type.subTypeOf(item.getType(), Type.ARRAY_ITEM)) {
            final ArrayType array = (ArrayType) item;
            for (final Sequence sequence : array.toArray()) {
                findRec(result, key, sequence);
            }
        } else if (Type.subTypeOf(item.getType(), Type.MAP_ITEM)) {
            final AbstractMapType map = (AbstractMapType) item;
            //append the values in the map with the supplied key
            result.add(map.get(key));
            //recursively examine all the values in the map (key notwithstanding), they may in turn be maps
            for (final IEntry<AtomicValue, Sequence> entry : map) {
                MapFunction.findRec(result, key, entry.value());
            }
        }
    }

    private ArrayType find(final Sequence[] args) {

        final AtomicValue key = (AtomicValue) args[1].itemAt(0);
        final ArrayType result = new ArrayType(this, context, Collections.emptyList());
        MapFunction.findRec(result, key, args[0]);
        return result;
    }

    private enum MergeDuplicates {
        REJECT,
        USE_FIRST,
        USE_LAST,
        USE_ANY,
        COMBINE;

        public static @Nullable MergeDuplicates fromDuplicatesValue(final String duplicatesValue) {
            for (final MergeDuplicates mergeDuplicates : values()) {
                if (mergeDuplicates.name().toLowerCase().replace('_', '-').equals(duplicatesValue)) {
                    return mergeDuplicates;
                }
            }

            return null;
        }
    }
}
