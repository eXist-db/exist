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

import java.util.*;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.map.MapModule.functionSignature;

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
        FIND("find");

        final static Map<String, MapFunction.Fn> fnMap = new HashMap<>();
        private final String fname;

        static {
            for (MapFunction.Fn fn: MapFunction.Fn.values()) {
                fnMap.put(fn.fname, fn);
            }
        }

        static MapFunction.Fn get(String name) {
            return fnMap.get(name);
        }

        Fn(String name) {
            this.fname = name;
        }
    }

    private static final FunctionParameterSequenceType PARAM_INPUT_MAP = param("map", Type.MAP_ITEM, "The input map");
    private static final FunctionParameterSequenceType PARAM_MERGE_MAPS = optManyParam("maps", Type.MAP_ITEM, "Existing maps to merge to create a new map.");
    private static final FunctionParameterSequenceType PARAM_KEY = param("key", Type.ANY_ATOMIC_TYPE, "The key");
    private static final FunctionParameterSequenceType PARAM_REMOVE_KEYS = param("keys", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The keys to remove");
    private static final FunctionParameterSequenceType PARAM_VALUE = param("value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The value");

    public static final FunctionSignature MERGE_1 = functionSignature(
            Fn.MERGE.fname,
            "Returns a map that combines the entries from a number of existing maps.",
            returns(Type.MAP_ITEM, "A new map which is the result of merging the maps"),
            PARAM_MERGE_MAPS
    );
    public static final FunctionSignature MERGE_2 = functionSignature(
            Fn.MERGE.fname,
            "Returns a map that combines the entries from a number of existing maps.",
            returns(Type.MAP_ITEM, "A new map which is the result of merging the maps"),
            PARAM_MERGE_MAPS,
            param("options", Type.MAP_ITEM, "Can be used to control the way in which duplicate keys are handled.")
    );

    public static final FunctionSignature FIND = functionSignature(
            Fn.FIND.fname,
            "Searches the supplied input sequence and any contained maps and arrays for a map entry with the supplied key, " +
                    "and returns the corresponding values.",
            returns(Type.ARRAY_ITEM, "An array containing the found values with the input key"),
            optManyParam("input", Type.ITEM, "The sequence of maps to search"),
            PARAM_KEY
    );

    public static final FunctionSignature SIZE = functionSignature(
            Fn.SIZE.fname,
            "Returns the number of entries in the supplied map.",
            returns(Type.INTEGER, "The number of key value pairs in the map"),
            PARAM_INPUT_MAP
    );

    public final static FunctionSignature KEYS = functionSignature(
            Fn.KEYS.fname,
            "Returns a sequence containing all the key values present in a map.",
            returns(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE),
            PARAM_INPUT_MAP
    );

    public final static FunctionSignature CONTAINS = functionSignature(
            Fn.CONTAINS.fname,
            "Tests whether a supplied map contains an entry for a given key.",
            returns(Type.BOOLEAN, Cardinality.EXACTLY_ONE),
            PARAM_INPUT_MAP,
            PARAM_KEY
    );

    public final static FunctionSignature GET = functionSignature(
            Fn.GET.fname,
            "Returns the value associated with a supplied key in a given map.",
            returns(Type.ITEM, Cardinality.ZERO_OR_MORE),
            PARAM_INPUT_MAP,
            PARAM_KEY
    );

    public final static FunctionSignature PUT = functionSignature(
            Fn.PUT.fname,
            "Returns a map containing all the contents of the supplied map, but with an additional entry, which replaces any existing entry for the same key.",
            returns(Type.MAP_ITEM, Cardinality.EXACTLY_ONE),
            PARAM_INPUT_MAP,
            PARAM_KEY,
            PARAM_VALUE
    );

    public final static FunctionSignature ENTRY = functionSignature(
            Fn.ENTRY.fname,
            "Creates a map that contains a single entry (a key-value pair).",
            returns(Type.MAP_ITEM, Cardinality.EXACTLY_ONE),
            PARAM_KEY,
            PARAM_VALUE
    );

    public final static FunctionSignature REMOVE = functionSignature(
            Fn.REMOVE.fname,
            "Constructs a new map by removing an entry from an existing map.",
            returns(Type.MAP_ITEM, Cardinality.EXACTLY_ONE),
            PARAM_INPUT_MAP,
            PARAM_REMOVE_KEYS
    );

    public final static FunctionSignature FOR_EACH = functionSignature(
            Fn.FOR_EACH.fname,
            "takes any map as its $input argument and applies the supplied function to each entry in the map, in implementation-dependent order; the result is the sequence obtained by concatenating the results of these function calls. " +
                "The function supplied as $action takes two arguments. It is called supplying the key of the map entry as the first argument, and the associated value as the second argument.",
            returns(Type.ITEM, Cardinality.ZERO_OR_MORE),
            PARAM_INPUT_MAP,
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

        final DuplicateMergeStrategy mergeDuplicates = getMergeStrategy(args);
        final Sequence maps = args[0];


        final int totalMaps = maps.getItemCount();
        final AbstractMapType head;
        final List<AbstractMapType> tail = new ArrayList<>(totalMaps - 1);

        // USE_LAST will pick the item from the last map containing a duplicate item
        // COMBINE will combine duplicate items in head-first order
        if (mergeDuplicates == DuplicateMergeStrategy.USE_LAST || mergeDuplicates == DuplicateMergeStrategy.COMBINE) {
            // head is the first map
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
        if (mergeDuplicates == DuplicateMergeStrategy.COMBINE) {
            return combineDuplicates(head, tail);
        }
        if (mergeDuplicates == DuplicateMergeStrategy.REJECT) {
            return rejectDuplicates(head, tail);
        }

        return head.merge(tail);
    }

    // COMBINE will combine duplicate items in head-first order
    private AbstractMapType rejectDuplicates(final AbstractMapType head, final List<AbstractMapType> tail) throws XPathException {
        // Provide a callback function for merging items which share a key
        // Call merge variant
        final List<Sequence> mergeExceptions = new ArrayList<>();
        final AbstractMapType merged = head.merge(tail, (first, second) -> {
            mergeExceptions.add(first);
            return Sequence.EMPTY_SEQUENCE;
        });

        if (!mergeExceptions.isEmpty()) {
            throw new XPathException(this, ErrorCodes.FOJS0003, "At least one duplicate key encountered with merge strategy being \"reject\".");
        }
        return merged;
    }

    // COMBINE will combine duplicate items in head-first order
    private AbstractMapType combineDuplicates(final AbstractMapType head, final List<AbstractMapType> tail) throws XPathException {
        // Provide a callback function for merging items which share a key
        // Call merge variant
        final List<XPathException> mergeExceptions = new ArrayList<>();
        final AbstractMapType merged = head.merge(tail, (first, second) -> {
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

    private DuplicateMergeStrategy getMergeStrategy(Sequence[] args) throws XPathException {
        if (args.length == 1) {
            return DuplicateMergeStrategy.USE_FIRST;
        }
        final MapType map = (MapType) args[1];
        final StringValue key = new StringValue(this, "duplicates");
        final Sequence mapValue = map.get(key);
        if (mapValue.isEmpty()) {
            return DuplicateMergeStrategy.USE_FIRST;
        }

        final DuplicateMergeStrategy mergeDuplicates = DuplicateMergeStrategy.get(mapValue.getStringValue());
        if (mergeDuplicates == null) {
            throw new XPathException(this, ErrorCodes.FOJS0005, "value for duplicates key was not recognised: " + mapValue.getStringValue());
        }
        return mergeDuplicates;
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
     * <p>
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
     * <p>
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

    private enum DuplicateMergeStrategy {
        REJECT("reject"),
        USE_FIRST("use-first"),
        USE_LAST("use-last"),
        USE_ANY("use-any"),
        COMBINE("combine");
        final static Map<String, MapFunction.DuplicateMergeStrategy> dmsMap = new HashMap<>();
        private final String key;

        static {
            for (MapFunction.DuplicateMergeStrategy dms: MapFunction.DuplicateMergeStrategy.values()) {
                dmsMap.put(dms.key, dms);
            }
        }

        static MapFunction.DuplicateMergeStrategy get(String key) {
            return dmsMap.get(key);
        }

        DuplicateMergeStrategy(String key) {
            this.key = key;
        }
    }
}
