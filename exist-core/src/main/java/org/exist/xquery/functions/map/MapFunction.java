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
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.value.ArrayListValueSequence;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.exist.xquery.FunctionDSL.funParam;
import static org.exist.xquery.FunctionDSL.optManyParam;
import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.params;
import static org.exist.xquery.FunctionDSL.returns;
import static org.exist.xquery.functions.map.MapModule.functionSignature;

/**
 * Implement the functions that operate on maps as described in
 * <a href="https://www.w3.org/TR/xpath-functions-31/#map-functions">XQuery Functions and Operators 3.1 §17.1</a>
 * <p>
 * 17.1.2 map:merge
 * 17.1.3 map:size
 * 17.1.4 map:keys
 * 17.1.5 map:contains
 * 17.1.6 map:get
 * 17.1.7 map:find
 * 17.1.8 map:put
 * 17.1.9 map:entry
 * 17.1.10 map:remove
 * 17.1.11 map:for-each
 */
public class MapFunction extends BasicFunction {
    private static final FunctionParameterSequenceType PARAM_INPUT_MAP = param("map", Type.MAP_ITEM, "The input map");
    private static final FunctionReturnSequenceType RETURN_MAP = returns(Type.MAP_ITEM, Cardinality.EXACTLY_ONE);
    private static final FunctionReturnSequenceType RETURN_OPT_MANY_ITEM = returns(Type.ITEM, Cardinality.ZERO_OR_MORE);
    private static final FunctionParameterSequenceType PARAM_VALUE = param("value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The value");
    private static final FunctionParameterSequenceType PARAM_MERGE_MAPS = optManyParam("maps", Type.MAP_ITEM, "Existing maps to merge to create a new map.");
    private static final FunctionReturnSequenceType PARAM_MERGE_RETURN = returns(Type.MAP_ITEM, "A new map which is the result of merging the maps");
    private static final FunctionParameterSequenceType PARAM_KEY = param("key", Type.ANY_ATOMIC_TYPE, "The key");

    public static final FunctionSignature MERGE_1 = functionSignature(
            Fn.MERGE.fname,
            "Returns a map that combines the entries from a number of existing maps.",
            PARAM_MERGE_RETURN,
            PARAM_MERGE_MAPS
    );
    public static final FunctionSignature MERGE_2 = functionSignature(
            Fn.MERGE.fname,
            "Returns a map that combines the entries from a number of existing maps.",
            PARAM_MERGE_RETURN,
            PARAM_MERGE_MAPS,
            param("options", Type.MAP_ITEM, "Can be used to control the way in which duplicate keys are handled.")
    );
    public static final FunctionSignature SIZE = functionSignature(
            Fn.SIZE.fname,
            "Returns the number of entries in the supplied map.",
            returns(Type.INTEGER, "The number of key value pairs in the map"),
            PARAM_INPUT_MAP
    );
    public static final FunctionSignature KEYS = functionSignature(
            Fn.KEYS.fname,
            "Returns a sequence containing all the key values present in a map.",
            returns(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE),
            PARAM_INPUT_MAP
    );
    public static final FunctionSignature CONTAINS = functionSignature(
            Fn.CONTAINS.fname,
            "Tests whether a supplied map contains an entry for a given key.",
            returns(Type.BOOLEAN, Cardinality.EXACTLY_ONE),
            PARAM_INPUT_MAP,
            PARAM_KEY
    );
    public static final FunctionSignature GET = functionSignature(
            Fn.GET.fname,
            "Returns the value associated with a supplied key in a given map.",
            RETURN_OPT_MANY_ITEM,
            PARAM_INPUT_MAP,
            PARAM_KEY
    );
    public static final FunctionSignature FIND = functionSignature(
            Fn.FIND.fname,
            "Searches the supplied input sequence and any contained maps and arrays for a map entry with the supplied key, " +
                    "and returns the corresponding values.",
            returns(Type.ARRAY_ITEM, "An array containing the found values with the input key"),
            optManyParam("input", Type.ITEM, "The sequence of maps to search"),
            PARAM_KEY
    );
    public static final FunctionSignature PUT = functionSignature(
            Fn.PUT.fname,
            "Returns a map containing all the contents of the supplied map, but with an additional entry, which replaces any existing entry for the same key.",
            RETURN_MAP,
            PARAM_INPUT_MAP,
            PARAM_KEY,
            PARAM_VALUE
    );
    public static final FunctionSignature ENTRY = functionSignature(
            Fn.ENTRY.fname,
            "Creates a map that contains a single entry (a key-value pair).",
            RETURN_MAP,
            PARAM_KEY,
            PARAM_VALUE
    );
    public static final FunctionSignature REMOVE = functionSignature(
            Fn.REMOVE.fname,
            "Constructs a new map by removing an entry from an existing map.",
            returns(Type.MAP_ITEM, Cardinality.EXACTLY_ONE),
            PARAM_INPUT_MAP,
            param("keys", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The keys to remove")
    );
    public static final FunctionSignature FOR_EACH = functionSignature(
            Fn.FOR_EACH.fname,
            "takes any map as its $input argument and applies the supplied function to each entry in the map, in implementation-dependent order; " +
                    "the result is the sequence obtained by concatenating the results of these function calls. " +
                    "The function supplied as $action takes two arguments. It is called supplying the key of the map entry as the first argument, " +
                    "and the associated value as the second argument.",
            RETURN_OPT_MANY_ITEM,
            PARAM_INPUT_MAP,
            funParam(
                    "action",
                    params(
                            param("key", Type.ANY_ATOMIC_TYPE, "the next key"),
                            optManyParam("value", Type.ITEM, "the next value")
                    ),
                    returns(Type.ITEM, Cardinality.ZERO_OR_MORE),
                    "The function to be called for each entry"
            )
    );

    // --- XQuery 4.0 map functions ---
    public static final FunctionSignature FNS_EMPTY = functionSignature(
            Fn.EMPTY.fname,
            "Returns true if the supplied map contains no entries.",
            returns(Type.BOOLEAN, Cardinality.EXACTLY_ONE),
            PARAM_INPUT_MAP
    );
    public static final FunctionSignature FNS_ITEMS = functionSignature(
            Fn.ITEMS.fname,
            "Returns a sequence containing all the values present in a map, in entry order.",
            RETURN_OPT_MANY_ITEM,
            PARAM_INPUT_MAP
    );
    public static final FunctionSignature FNS_ENTRIES = functionSignature(
            Fn.ENTRIES.fname,
            "Returns the entries of a map as a sequence of singleton maps.",
            returns(Type.MAP_ITEM, Cardinality.ZERO_OR_MORE, "A sequence of singleton maps"),
            PARAM_INPUT_MAP
    );
    public static final FunctionSignature FNS_KEYS_WHERE = functionSignature(
            Fn.KEYS_WHERE.fname,
            "Returns the keys in a map for which the supplied predicate function returns true.",
            returns(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE),
            PARAM_INPUT_MAP,
            funParam(
                    "predicate",
                    params(
                            param("key", Type.ANY_ATOMIC_TYPE, "the key"),
                            optManyParam("value", Type.ITEM, "the value")
                    ),
                    returns(Type.BOOLEAN),
                    "The predicate function"
            )
    );
    public static final FunctionSignature FNS_FILTER = functionSignature(
            Fn.FILTER.fname,
            "Returns a map containing those entries from the input map for which the supplied predicate returns true.",
            RETURN_MAP,
            PARAM_INPUT_MAP,
            funParam(
                    "predicate",
                    params(
                            param("key", Type.ANY_ATOMIC_TYPE, "the key"),
                            optManyParam("value", Type.ITEM, "the value")
                    ),
                    returns(Type.BOOLEAN),
                    "The predicate function"
            )
    );
    public static final FunctionSignature[] FS_BUILD = {
            functionSignature(
                    Fn.BUILD.fname,
                    "Constructs a map from a sequence by applying key and value functions.",
                    RETURN_MAP,
                    optManyParam("input", Type.ITEM, "The input sequence")
            ),
            functionSignature(
                    Fn.BUILD.fname,
                    "Constructs a map from a sequence by applying key and value functions.",
                    RETURN_MAP,
                    optManyParam("input", Type.ITEM, "The input sequence"),
                    funParam("key",
                            params(optManyParam("item", Type.ITEM, "the current item")),
                            returns(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE),
                            "The key function"
                    )
            ),
            functionSignature(
                    Fn.BUILD.fname,
                    "Constructs a map from a sequence by applying key and value functions.",
                    RETURN_MAP,
                    optManyParam("input", Type.ITEM, "The input sequence"),
                    funParam("key",
                            params(optManyParam("item", Type.ITEM, "the current item")),
                            returns(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE),
                            "The key function"
                    ),
                    funParam("value",
                            params(optManyParam("item", Type.ITEM, "the current item")),
                            RETURN_OPT_MANY_ITEM,
                            "The value function"
                    )
            ),
            functionSignature(
                    Fn.BUILD.fname,
                    "Constructs a map from a sequence by applying key and value functions.",
                    RETURN_MAP,
                    optManyParam("input", Type.ITEM, "The input sequence"),
                    funParam("key",
                            params(optManyParam("item", Type.ITEM, "the current item")),
                            returns(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE),
                            "The key function"
                    ),
                    funParam("value",
                            params(optManyParam("item", Type.ITEM, "the current item")),
                            RETURN_OPT_MANY_ITEM,
                            "The value function"
                    ),
                    param("options", Type.MAP_ITEM, Cardinality.ZERO_OR_ONE, "Options map with duplicates handling")
            )
    };

    private AnalyzeContextInfo cachedContextInfo;

    public MapFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    /**
     * Recursive helper for find
     * <p>
     * Recursively find map members in a sequence
     * By searching each of the individual items in the sequence
     *
     * @param result   add found values to this
     * @param key      the key to match
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
     * @param key    the key to match
     * @param item   the item to search within
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
            case FIND -> find(args);
            case PUT -> put(args);
            case ENTRY -> entry(args);
            case REMOVE -> remove(args);
            case FOR_EACH -> forEach(args);
            case EMPTY -> empty(args);
            case ITEMS -> items(args);
            case ENTRIES -> entries(args);
            case KEYS_WHERE -> keysWhere(args);
            case FILTER -> filter(args);
            case BUILD -> build(args);
        };
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

    private Sequence size(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        return new IntegerValue(this, map.size(), Type.INTEGER);
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

    private ArrayType find(final Sequence[] args) {
        final AtomicValue key = (AtomicValue) args[1].itemAt(0);
        final ArrayType result = new ArrayType(this, context, Collections.emptyList());
        MapFunction.findRec(result, key, args[0]);
        return result;
    }

    private Sequence put(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        return map.put((AtomicValue) args[1].itemAt(0), args[2]);
    }

    private Sequence entry(final Sequence[] args) {
        final AtomicValue key = (AtomicValue) args[0].itemAt(0);
        return new SingleKeyMapType(this, this.context, null, key, args[1]);
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

    private Sequence forEach(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        try (final FunctionReference ref = (FunctionReference) args[1].itemAt(0)) {
            ref.analyze(cachedContextInfo);
            final ArrayListValueSequence result = new ArrayListValueSequence(map.size());
            for (final IEntry<AtomicValue, Sequence> entry : map) {
                final Sequence s = ref.evalFunction(null, null, new Sequence[]{entry.key(), entry.value()});
                result.addAll(s);
            }
            return result;
        }
    }

    // --- XQuery 4.0 map function implementations ---

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

        // $key function -- defaults to identity
        final FunctionReference keyFn = (args.length >= 2 && !args[1].isEmpty())
                ? (FunctionReference) args[1].itemAt(0) : null;
        // $value function -- defaults to identity
        final FunctionReference valueFn = (args.length >= 3 && !args[2].isEmpty())
                ? (FunctionReference) args[2].itemAt(0) : null;

        // Parse options
        DuplicateMergeStrategy mergeDuplicates = DuplicateMergeStrategy.COMBINE;
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
                    mergeDuplicates = DuplicateMergeStrategy.get(dupStr);
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
                        } else if (mergeDuplicates == DuplicateMergeStrategy.COMBINE) {
                            final ValueSequence combined = new ValueSequence(existingValue);
                            combined.addAll(valueResult);
                            mergedValue = combined;
                        } else if (mergeDuplicates == DuplicateMergeStrategy.USE_FIRST) {
                            mergedValue = existingValue;
                        } else if (mergeDuplicates == DuplicateMergeStrategy.USE_LAST) {
                            mergedValue = valueResult;
                        } else if (mergeDuplicates == DuplicateMergeStrategy.USE_ANY) {
                            mergedValue = existingValue;
                        } else if (mergeDuplicates == DuplicateMergeStrategy.REJECT) {
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

    /*
     * Reject duplicate items
     */
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

    /*
     * Combine duplicate items in head-first order
     */
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

    private enum DuplicateMergeStrategy {
        REJECT("reject"),
        USE_FIRST("use-first"),
        USE_LAST("use-last"),
        USE_ANY("use-any"),
        COMBINE("combine");
        final static Map<String, MapFunction.DuplicateMergeStrategy> dmsMap = new HashMap<>();

        static {
            for (MapFunction.DuplicateMergeStrategy dms : MapFunction.DuplicateMergeStrategy.values()) {
                dmsMap.put(dms.key, dms);
            }
        }

        private final String key;

        DuplicateMergeStrategy(String key) {
            this.key = key;
        }

        static MapFunction.DuplicateMergeStrategy get(String key) {
            return dmsMap.get(key);
        }
    }

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
        FIND("find"),
        EMPTY("empty"),
        ITEMS("items"),
        ENTRIES("entries"),
        KEYS_WHERE("keys-where"),
        FILTER("filter"),
        BUILD("build");

        final static Map<String, MapFunction.Fn> fnMap = new HashMap<>();

        static {
            for (MapFunction.Fn fn : MapFunction.Fn.values()) {
                fnMap.put(fn.fname, fn);
            }
        }

        private final String fname;

        Fn(String name) {
            this.fname = name;
        }

        static MapFunction.Fn get(String name) {
            return fnMap.get(name);
        }
    }
}
