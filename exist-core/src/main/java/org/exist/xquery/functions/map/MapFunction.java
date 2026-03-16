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
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.map.MapModule.functionSignatures;

/**
 * Implements all functions of the map module.
 */
public class MapFunction extends BasicFunction {

    private static final QName QN_SIZE = new QName("size", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_ENTRY = new QName("entry", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_GET = new QName("get", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_PUT = new QName("put", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_CONTAINS = new QName("contains", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_KEYS = new QName("keys", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_REMOVE = new QName("remove", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_FOR_EACH = new QName("for-each", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_FIND = new QName("find", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_EMPTY = new QName("empty", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_ITEMS = new QName("items", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_ENTRIES = new QName("entries", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_KEYS_WHERE = new QName("keys-where", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_FILTER = new QName("filter", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_BUILD = new QName("build", MapModule.NAMESPACE_URI, MapModule.PREFIX);

    private static final FunctionParameterSequenceType FS_PARAM_MAPS = optManyParam("maps", Type.MAP_ITEM, "Existing maps to merge to create a new map.");

    private static final String FS_MERGE_NAME = "merge";
    public static final FunctionSignature[] FS_MERGE = functionSignatures(
        FS_MERGE_NAME,
        "Returns a map that combines the entries from a number of existing maps.",
        returns(Type.MAP_ITEM, "A new map which is the result of merging the maps"),
        arities(
                arity(
                        FS_PARAM_MAPS
                ),
                arity(
                        FS_PARAM_MAPS,
                        optParam("options", Type.MAP_ITEM, "Can be used to control the way in which duplicate keys are handled.")
                )
        )
    );

    public static final FunctionSignature FS_FIND = functionSignature(
            QN_FIND,
            "Searches the supplied input sequence and any contained maps and arrays for a map entry with the supplied key, " +
                    "and returns the corresponding values.",
            returns(Type.ARRAY_ITEM, "An array containing the found values with the input key"),
            optManyParam("input", Type.ITEM, "The sequence of maps to search"),
            param("key", Type.ANY_ATOMIC_TYPE, "The key to match")
    );

    public final static FunctionSignature FNS_SIZE = new FunctionSignature(
        QN_SIZE,
        "Returns the number of entries in the supplied map.",
        new SequenceType[] {
            new FunctionParameterSequenceType("map", Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "Any map to determine the size of.")
        },
        new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE)
    );

    public final static FunctionSignature FNS_KEYS = new FunctionSignature(
        QN_KEYS,
        "Returns a sequence containing all the key values present in a map.",
        new SequenceType[]{
            new FunctionParameterSequenceType(MapModule.PREFIX, Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "The map")
        },
        new SequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE)
    );

    public final static FunctionSignature FNS_CONTAINS = new FunctionSignature(
        QN_CONTAINS,
        "Tests whether a supplied map contains an entry for a given key.",
        new SequenceType[] {
            new FunctionParameterSequenceType(MapModule.PREFIX, Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "The map"),
            new FunctionParameterSequenceType("key", Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE, "The key to look up")
        },
        new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
    );

    public final static FunctionSignature FNS_GET = new FunctionSignature(
        QN_GET,
        "Returns the value associated with a supplied key in a given map.",
        new SequenceType[] {
            new FunctionParameterSequenceType(MapModule.PREFIX, Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "The map"),
            new FunctionParameterSequenceType("key", Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE, "The key to look up")
        },
        new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
    );

    public final static FunctionSignature FNS_PUT = new FunctionSignature(
        QN_PUT,
        "Returns a map containing all the contents of the supplied map, but with an additional entry, which replaces any existing entry for the same key.",
        new SequenceType[] {
            new FunctionParameterSequenceType(MapModule.PREFIX, Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "The map"),
            new FunctionParameterSequenceType("key", Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE, "The key for the entry to insert"),
            new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The value for the entry to insert")
        },
        new SequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE)
    );

    public final static FunctionSignature FNS_ENTRY = new FunctionSignature(
        QN_ENTRY,
        "Creates a map that contains a single entry (a key-value pair).",
        new SequenceType[] {
            new FunctionParameterSequenceType("key", Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE, "The key"),
            new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The associated value")
        },
        new SequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE)
    );

    public final static FunctionSignature FNS_REMOVE = new FunctionSignature(
        QN_REMOVE,
        "Constructs a new map by removing an entry from an existing map.",
        new SequenceType[] {
            new FunctionParameterSequenceType(MapModule.PREFIX, Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "The map"),
            new FunctionParameterSequenceType("keys", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The key to remove")
        },
        new SequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE)
    );

    public final static FunctionSignature FNS_FOR_EACH = new FunctionSignature(
        QN_FOR_EACH,
        "takes any map as its $input argument and applies the supplied function to each entry in the map, in implementation-dependent order; the result is the sequence obtained by concatenating the results of these function calls. " +
        "The function supplied as $action takes two arguments. It is called supplying the key of the map entry as the first argument, and the associated value as the second argument.",
        new SequenceType[] {
            new FunctionParameterSequenceType("input", Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "The map"),
            new FunctionParameterFunctionSequenceType("action", Type.FUNCTION,
                    new SequenceType[] {
                            new SequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE),
                            new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
                    },
                    Cardinality.EXACTLY_ONE, "The function to be called for each entry")
        },
        new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
    );

    public final static FunctionSignature FNS_EMPTY = new FunctionSignature(
        QN_EMPTY,
        "Returns true if the supplied map contains no entries.",
        new SequenceType[] {
            new FunctionParameterSequenceType("map", Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "The map")
        },
        new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
    );

    public final static FunctionSignature FNS_ITEMS = new FunctionSignature(
        QN_ITEMS,
        "Returns a sequence containing all the values present in a map, in entry order.",
        new SequenceType[] {
            new FunctionParameterSequenceType("map", Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "The map")
        },
        new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
    );

    public final static FunctionSignature FNS_ENTRIES = new FunctionSignature(
        QN_ENTRIES,
        "Returns a sequence containing all the key-value pairs present in a map, each represented as a single-entry map, in entry order.",
        new SequenceType[] {
            new FunctionParameterSequenceType("map", Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "The map")
        },
        new SequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_MORE)
    );

    public final static FunctionSignature FNS_KEYS_WHERE = new FunctionSignature(
        QN_KEYS_WHERE,
        "Returns a sequence containing selected keys present in a map, where the predicate returns true for the key and value.",
        new SequenceType[] {
            new FunctionParameterSequenceType("map", Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "The map"),
            new FunctionParameterSequenceType("predicate", Type.FUNCTION, Cardinality.EXACTLY_ONE, "A function that takes a key and value and returns a boolean")
        },
        new SequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE)
    );

    public final static FunctionSignature FNS_FILTER = new FunctionSignature(
        QN_FILTER,
        "Selects entries from a map based on a predicate, returning a new map containing those entries for which the predicate returns true.",
        new SequenceType[] {
            new FunctionParameterSequenceType("map", Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "The map"),
            new FunctionParameterSequenceType("predicate", Type.FUNCTION, Cardinality.EXACTLY_ONE, "A function that takes a key, value, and position and returns a boolean")
        },
        new SequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE)
    );

    private static final String FS_BUILD_NAME = "build";
    private static final FunctionParameterSequenceType FS_PARAM_BUILD_INPUT = optManyParam("input", Type.ITEM, "The input sequence");
    public static final FunctionSignature[] FS_BUILD = functionSignatures(
        FS_BUILD_NAME,
        "Returns a map built from items in a supplied input sequence. Each item produces an entry whose key is computed by the $key function and whose value is computed by the $value function.",
        returns(Type.MAP_ITEM, "A new map built from the input sequence"),
        arities(
                arity(
                        FS_PARAM_BUILD_INPUT
                ),
                arity(
                        FS_PARAM_BUILD_INPUT,
                        optParam("key", Type.FUNCTION, "Function to compute the key for each item")
                ),
                arity(
                        FS_PARAM_BUILD_INPUT,
                        optParam("key", Type.FUNCTION, "Function to compute the key for each item"),
                        optParam("value", Type.FUNCTION, "Function to compute the value for each item")
                ),
                arity(
                        FS_PARAM_BUILD_INPUT,
                        optParam("key", Type.FUNCTION, "Function to compute the key for each item"),
                        optParam("value", Type.FUNCTION, "Function to compute the value for each item"),
                        optParam("options", Type.MAP_ITEM, "Options map controlling duplicate handling")
                )
        )
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
        if (isCalledAs(FS_MERGE_NAME)) {
            return merge(args);
        } else if (isCalledAs(QN_SIZE.getLocalPart())) {
            return size(args);
        } else if (isCalledAs(QN_KEYS.getLocalPart())) {
            return keys(args);
        } else if (isCalledAs(QN_CONTAINS.getLocalPart())) {
            return contains(args);
        } else if (isCalledAs(QN_GET.getLocalPart())) {
            return get(args);
        } else if (isCalledAs(QN_PUT.getLocalPart())) {
            return put(args);
        } else if (isCalledAs(QN_ENTRY.getLocalPart())) {
            return entry(args);
        } else if (isCalledAs(QN_REMOVE.getLocalPart())) {
            return remove(args);
        } else if (isCalledAs(QN_FOR_EACH.getLocalPart())) {
            return forEach(args);
        } else if (isCalledAs(QN_FIND.getLocalPart())) {
            return find(args);
        } else if (isCalledAs(QN_EMPTY.getLocalPart())) {
            return empty(args);
        } else if (isCalledAs(QN_ITEMS.getLocalPart())) {
            return items(args);
        } else if (isCalledAs(QN_ENTRIES.getLocalPart())) {
            return entries(args);
        } else if (isCalledAs(QN_KEYS_WHERE.getLocalPart())) {
            return keysWhere(args);
        } else if (isCalledAs(QN_FILTER.getLocalPart())) {
            return filter(args);
        } else if (isCalledAs(FS_BUILD_NAME)) {
            return build(args);
        }
        throw new XPathException(this, "No function: " + getName() + "#" + getSignature().getArgumentCount());
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
        if (args.length == 2 && !args[1].isEmpty()) {
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

        // XDM 4.0: maps are ordered, so always iterate in forward (left-to-right) order
        // to preserve insertion order. For USE_FIRST, we use a merge function that
        // keeps the existing (first) value on duplicate keys.
        head = (AbstractMapType) maps.itemAt(0);
        for (int i = 1; i < totalMaps; i++) {
            final AbstractMapType other = (AbstractMapType) maps.itemAt(i);
            tail.add(other);
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

        final AbstractMapType result;
        if (mergeDuplicates == MergeDuplicates.USE_FIRST) {
            // Keep existing (first) value on duplicate keys
            result = head.merge(tail, (existing, newVal) -> existing);
        } else {
            // USE_LAST: last value wins (default union behavior)
            result = head.merge(tail);
        }

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

    private Sequence empty(final Sequence[] args) {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        return BooleanValue.valueOf(map.size() == 0);
    }

    private Sequence items(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        final ArrayListValueSequence result = new ArrayListValueSequence(map.size());
        for (final IEntry<AtomicValue, Sequence> entry : map) {
            result.addAll(entry.value());
        }
        return result;
    }

    private Sequence entries(final Sequence[] args) {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        final ArrayListValueSequence result = new ArrayListValueSequence(map.size());
        for (final IEntry<AtomicValue, Sequence> entry : map) {
            result.add(new SingleKeyMapType(this, this.context, null, entry.key(), entry.value()));
        }
        return result;
    }

    private Sequence keysWhere(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        try (final FunctionReference ref = (FunctionReference) args[1].itemAt(0)) {
            ref.analyze(cachedContextInfo);
            final ArrayListValueSequence result = new ArrayListValueSequence();
            for (final IEntry<AtomicValue, Sequence> entry : map) {
                final Sequence predicateResult = ref.evalFunction(null, null,
                        new Sequence[]{ entry.key(), entry.value() });
                if (!predicateResult.isEmpty() && predicateResult.effectiveBooleanValue()) {
                    result.add(entry.key());
                }
            }
            return result;
        }
    }

    private Sequence filter(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        try (final FunctionReference ref = (FunctionReference) args[1].itemAt(0)) {
            ref.analyze(cachedContextInfo);
            final int arity = ref.getSignature().getArgumentCount();
            AbstractMapType result = new MapType(this, this.context);
            int position = 1;
            for (final IEntry<AtomicValue, Sequence> entry : map) {
                final Sequence predicateResult;
                if (arity >= 3) {
                    predicateResult = ref.evalFunction(null, null,
                            new Sequence[]{ entry.key(), entry.value(),
                                    new IntegerValue(this, position, Type.INTEGER) });
                } else {
                    predicateResult = ref.evalFunction(null, null,
                            new Sequence[]{ entry.key(), entry.value() });
                }
                if (!predicateResult.isEmpty() && predicateResult.effectiveBooleanValue()) {
                    result = result.put(entry.key(), entry.value());
                }
                position++;
            }
            return result;
        }
    }

    private Sequence build(final Sequence[] args) throws XPathException {
        final Sequence input = args[0];

        // $key function — defaults to identity
        final FunctionReference keyFn = (args.length >= 2 && !args[1].isEmpty())
                ? (FunctionReference) args[1].itemAt(0) : null;
        // $value function — defaults to identity
        final FunctionReference valueFn = (args.length >= 3 && !args[2].isEmpty())
                ? (FunctionReference) args[2].itemAt(0) : null;

        // Parse options
        MergeDuplicates mergeDuplicates = MergeDuplicates.COMBINE;
        FunctionReference duplicatesFn = null;
        if (args.length >= 4 && !args[3].isEmpty()) {
            final AbstractMapType options = (AbstractMapType) args[3].itemAt(0);
            final StringValue dupKey = new StringValue(this, "duplicates");
            if (options.contains(dupKey)) {
                final Sequence dupValue = options.get(dupKey);
                if (dupValue.getItemCount() == 1 && Type.subTypeOf(dupValue.itemAt(0).getType(), Type.FUNCTION)) {
                    duplicatesFn = (FunctionReference) dupValue.itemAt(0);
                    duplicatesFn.analyze(cachedContextInfo);
                    mergeDuplicates = null; // use custom function
                } else {
                    final String dupStr = dupValue.getStringValue();
                    mergeDuplicates = MergeDuplicates.fromDuplicatesValue(dupStr);
                    if (mergeDuplicates == null) {
                        throw new XPathException(this, ErrorCodes.FOJS0005,
                                "value for duplicates key was not recognised: " + dupStr);
                    }
                }
            }
        }

        if (keyFn != null) {
            keyFn.analyze(cachedContextInfo);
        }
        if (valueFn != null) {
            valueFn.analyze(cachedContextInfo);
        }

        try {
            AbstractMapType result = new MapType(this, this.context);
            int position = 1;
            for (int i = 0; i < input.getItemCount(); i++) {
                final Item item = input.itemAt(i);
                final Sequence itemSeq = item.toSequence();
                final IntegerValue posValue = new IntegerValue(this, position, Type.INTEGER);

                // Compute key(s)
                final Sequence keyResult;
                if (keyFn != null) {
                    final int keyArity = keyFn.getSignature().getArgumentCount();
                    keyResult = keyArity >= 2
                            ? keyFn.evalFunction(null, null, new Sequence[]{ itemSeq, posValue })
                            : keyFn.evalFunction(null, null, new Sequence[]{ itemSeq });
                } else {
                    keyResult = itemSeq;
                }

                // Compute value
                final Sequence valueResult;
                if (valueFn != null) {
                    final int valArity = valueFn.getSignature().getArgumentCount();
                    valueResult = valArity >= 2
                            ? valueFn.evalFunction(null, null, new Sequence[]{ itemSeq, posValue })
                            : valueFn.evalFunction(null, null, new Sequence[]{ itemSeq });
                } else {
                    valueResult = itemSeq;
                }

                // For each key, add/merge into the result map
                for (int k = 0; k < keyResult.getItemCount(); k++) {
                    final AtomicValue key = keyResult.itemAt(k).atomize();
                    if (result.contains(key)) {
                        // Handle duplicate
                        final Sequence existingValue = result.get(key);
                        final Sequence mergedValue;
                        if (duplicatesFn != null) {
                            mergedValue = duplicatesFn.evalFunction(null, null,
                                    new Sequence[]{ existingValue, valueResult });
                        } else if (mergeDuplicates == MergeDuplicates.COMBINE) {
                            final ValueSequence combined = new ValueSequence(existingValue);
                            combined.addAll(valueResult);
                            mergedValue = combined;
                        } else if (mergeDuplicates == MergeDuplicates.USE_FIRST) {
                            mergedValue = existingValue;
                        } else if (mergeDuplicates == MergeDuplicates.USE_LAST) {
                            mergedValue = valueResult;
                        } else if (mergeDuplicates == MergeDuplicates.USE_ANY) {
                            mergedValue = existingValue;
                        } else if (mergeDuplicates == MergeDuplicates.REJECT) {
                            throw new XPathException(this, ErrorCodes.FOJS0003,
                                    "Duplicate key in map:build: " + key.getStringValue());
                        } else {
                            mergedValue = valueResult;
                        }
                        result = result.put(key, mergedValue);
                    } else {
                        result = result.put(key, valueResult);
                    }
                }

                position++;
            }
            return result;
        } finally {
            if (keyFn != null) {
                keyFn.close();
            }
            if (valueFn != null) {
                valueFn.close();
            }
            if (duplicatesFn != null) {
                duplicatesFn.close();
            }
        }
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
