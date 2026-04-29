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
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.ValueSequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.exist.xquery.FunctionDSL.funParam;
import static org.exist.xquery.FunctionDSL.optFunParam;
import static org.exist.xquery.FunctionDSL.optManyParam;
import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.params;
import static org.exist.xquery.FunctionDSL.returns;
import static org.exist.xquery.FunctionDSL.returnsMany;
import static org.exist.xquery.FunctionDSL.returnsOptMany;
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
            optParam("options", Type.MAP_ITEM, "Can be used to control the way in which duplicate keys are handled.")
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
            "takes any map as its $input argument and applies the supplied function to each entry in the map; " +
                    "the result is the sequence obtained by concatenating the results of these function calls. " +
                    "In XQuery 4.0, the callback may accept 0 to 3 arguments: (key, value, position).",
            RETURN_OPT_MANY_ITEM,
            PARAM_INPUT_MAP,
            param("action", Type.FUNCTION, "The function to be called for each entry")
    );

    // --- XQuery 4.0 map functions ---
    public static final FunctionSignature[] FNS_EMPTY = {
            functionSignature(
                    Fn.EMPTY.fname,
                    "Returns an empty map.",
                    RETURN_MAP
            ),
            functionSignature(
                    Fn.EMPTY.fname,
                    "Returns true if the map is empty.",
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the map is empty"),
                    PARAM_INPUT_MAP
            )
    };

    public static final FunctionSignature FNS_GET_DEFAULT = functionSignature(
            Fn.GET_DEFAULT.fname,
            "Returns the value associated with a key, or a default value if the key is not present.",
            RETURN_OPT_MANY_ITEM,
            PARAM_INPUT_MAP,
            PARAM_KEY,
            optManyParam("default", Type.ITEM, "The default value")
    );

    public static final FunctionSignature BUILD_0 = functionSignature(
            Fn.BUILD.fname,
            "Builds a map from a sequence of items, using each item as both key and value.",
            RETURN_MAP,
            optManyParam("input", Type.ITEM, "The input sequence")
    );

    public static final FunctionSignature BUILD_1 = functionSignature(
            Fn.BUILD.fname,
            "Builds a map from a sequence of items, using a key function. " +
                    "If the key function is the empty sequence, each item is used as its own key.",
            RETURN_MAP,
            optManyParam("input", Type.ITEM, "The input sequence"),
            optParam("key", Type.FUNCTION, "The key function (arity 1 or 2: item, optional position)")
    );

    public static final FunctionSignature BUILD_2 = functionSignature(
            Fn.BUILD.fname,
            "Builds a map from a sequence of items, using key and value functions. " +
                    "An empty sequence for either function selects the default (identity).",
            RETURN_MAP,
            optManyParam("input", Type.ITEM, "The input sequence"),
            optParam("key", Type.FUNCTION, "The key function (arity 1 or 2: item, optional position)"),
            optParam("value", Type.FUNCTION, "The value function (arity 1 or 2: item, optional position)")
    );

    public static final FunctionSignature BUILD_3 = functionSignature(
            Fn.BUILD.fname,
            "Builds a map from a sequence of items, with key/value functions and options. " +
                    "The options map may contain a 'duplicates' entry controlling duplicate-key handling.",
            RETURN_MAP,
            optManyParam("input", Type.ITEM, "The input sequence"),
            optParam("key", Type.FUNCTION, "The key function (arity 1 or 2: item, optional position)"),
            optParam("value", Type.FUNCTION, "The value function (arity 1 or 2: item, optional position)"),
            optParam("options", Type.MAP_ITEM, "Options map controlling duplicate-key handling")
    );

    public static final FunctionSignature ITEMS = functionSignature(
            Fn.ITEMS.fname,
            "Returns a sequence of maps, each with 'key' and 'value' entries.",
            returnsOptMany(Type.MAP_ITEM, "A sequence of key-value pair maps"),
            PARAM_INPUT_MAP
    );

    public static final FunctionSignature ENTRIES = functionSignature(
            Fn.ENTRIES.fname,
            "Returns a sequence of maps, each with 'key' and 'value' entries.",
            returnsOptMany(Type.MAP_ITEM, "A sequence of key-value pair maps"),
            PARAM_INPUT_MAP
    );

    public static final FunctionSignature FILTER_SIG = functionSignature(
            Fn.FILTER.fname,
            "Returns a map containing only entries matching the predicate. " +
                    "In XQuery 4.0, the callback may accept 0 to 3 arguments: (key, value, position).",
            RETURN_MAP,
            PARAM_INPUT_MAP,
            param("predicate", Type.FUNCTION, "The filter predicate")
    );

    public static final FunctionSignature KEYS_WHERE = functionSignature(
            Fn.KEYS_WHERE.fname,
            "Returns keys whose entries match the predicate. " +
                    "In XQuery 4.0, the callback may accept 0 to 3 arguments: (key, value, position).",
            returnsOptMany(Type.ANY_ATOMIC_TYPE, "The matching keys"),
            PARAM_INPUT_MAP,
            param("predicate", Type.FUNCTION, "The filter predicate")
    );

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
            case EMPTY -> {
                if (args.length > 0 && !args[0].isEmpty()) {
                    // 1-arity: map:empty($map) — check if map is empty
                    final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
                    yield BooleanValue.valueOf(map.size() == 0);
                } else if (args.length > 0 && args[0].isEmpty()) {
                    // empty sequence passed — type error
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Expected map(*) but got empty sequence");
                } else {
                    // 0-arity: map:empty() — return empty map
                    yield new MapType(this, this.context);
                }
            }
            case GET_DEFAULT -> getDefault(args);
            case BUILD -> build(args);
            case ITEMS -> mapItems(args);
            case ENTRIES -> entries(args);
            case FILTER -> filter(args);
            case KEYS_WHERE -> keysWhere(args);
        };
    }

    private Sequence merge(final Sequence[] args) throws XPathException {
        if (args[0].getItemCount() == 0) {
            // map:merge(())
            return new MapType(this, this.context);
        }

        final BuildDuplicatesHandler handler = getMergeDuplicatesHandler(args);
        final Sequence maps = args[0];

        final int totalMaps = maps.getItemCount();
        // Iterate in source order so the resulting map's key order matches the
        // order entries were first added (XPath/XQuery 4.0 PR1703).
        final AbstractMapType head = (AbstractMapType) maps.itemAt(0);
        final List<AbstractMapType> tail = new ArrayList<>(totalMaps - 1);
        for (int i = 1; i < totalMaps; i++) {
            tail.add((AbstractMapType) maps.itemAt(i));
        }
        if (handler.combiner != null) {
            // XQ4 PR1041: a function-valued 'duplicates' option combines
            // existing and incoming values whenever a key collides.
            final FunctionReference combiner = handler.combiner;
            final int arity = referenceArity(combiner);
            if (arity != 1 && arity != 2) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "map:merge duplicates combiner must accept 1 or 2 arguments, got " + arity);
            }
            try {
                return head.merge(tail, (existing, incoming) -> {
                    try {
                        final Sequence[] callArgs = arity == 1
                                ? new Sequence[]{existing}
                                : new Sequence[]{existing, incoming};
                        return combiner.evalFunction(null, null, callArgs);
                    } catch (final XPathException e) {
                        throw new MergeCombinerException(e);
                    }
                });
            } catch (final MergeCombinerException e) {
                throw e.getCause();
            }
        }
        switch (handler.strategy) {
            case COMBINE -> { return combineDuplicates(head, tail); }
            case REJECT -> { return rejectDuplicates(head, tail); }
            case USE_FIRST, USE_ANY ->
                    // Forward iteration with a "keep the existing value" merge
                    // function gives use-first/use-any while preserving insertion order.
                    { return head.merge(tail, (existing, incoming) -> existing); }
            case USE_LAST ->
                    // The default bifurcan union semantics replace existing values,
                    // matching use-last while keeping forward insertion order.
                    { return head.merge(tail); }
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

    /**
     * Build an argument array for a map callback function, supporting XQ4 arity coercion.
     * The full argument list is (key, value, position). Functions with fewer parameters
     * receive only the leading arguments they accept.
     */
    private Sequence[] buildMapCallbackArgs(final int arity, final AtomicValue key,
            final Sequence value, final int position) throws XPathException {
        return switch (arity) {
            case 0 -> new Sequence[0];
            case 1 -> new Sequence[]{key.toSequence()};
            case 2 -> new Sequence[]{key.toSequence(), value};
            case 3 -> new Sequence[]{key.toSequence(), value,
                    new IntegerValue(this, position, Type.INTEGER)};
            default -> throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Map callback function must accept 0 to 3 arguments, got " + arity);
        };
    }

    private Sequence forEach(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        try (final FunctionReference ref = (FunctionReference) args[1].itemAt(0)) {
            ref.analyze(cachedContextInfo);
            final int arity = ref.getSignature().getArgumentCount();
            final ArrayListValueSequence result = new ArrayListValueSequence(map.size());
            int position = 1;
            for (final IEntry<AtomicValue, Sequence> entry : map) {
                final Sequence[] callArgs = buildMapCallbackArgs(arity, entry.key(), entry.value(), position++);
                final Sequence s = ref.evalFunction(null, null, callArgs);
                result.addAll(s);
            }
            return result;
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
        if (args.length == 1 || args[1].isEmpty()) {
            return DuplicateMergeStrategy.USE_FIRST;
        }
        final AbstractMapType map = (AbstractMapType) args[1].itemAt(0);
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

    /**
     * Resolve the 'duplicates' option for map:merge. Like map:build, the
     * value may be a string naming a built-in strategy or a function of
     * arity 1 or 2 that combines existing and incoming values (XQ4
     * PR1041). Defaults to USE_FIRST when the option is absent.
     */
    private BuildDuplicatesHandler getMergeDuplicatesHandler(final Sequence[] args) throws XPathException {
        if (args.length == 1 || args[1].isEmpty()) {
            return BuildDuplicatesHandler.ofStrategy(DuplicateMergeStrategy.USE_FIRST);
        }
        final AbstractMapType options = (AbstractMapType) args[1].itemAt(0);
        final Sequence value = options.get(new StringValue(this, "duplicates"));
        if (value == null || value.isEmpty()) {
            return BuildDuplicatesHandler.ofStrategy(DuplicateMergeStrategy.USE_FIRST);
        }
        if (value.itemAt(0) instanceof FunctionReference fn) {
            fn.analyze(cachedContextInfo);
            return BuildDuplicatesHandler.ofCombiner(fn);
        }
        final DuplicateMergeStrategy strategy = DuplicateMergeStrategy.get(value.getStringValue());
        if (strategy == null) {
            throw new XPathException(this, ErrorCodes.FOJS0005,
                    "value for duplicates key was not recognised: " + value.getStringValue());
        }
        return BuildDuplicatesHandler.ofStrategy(strategy);
    }

    private static final class MergeCombinerException extends RuntimeException {
        MergeCombinerException(final XPathException cause) { super(cause); }
        @Override public XPathException getCause() { return (XPathException) super.getCause(); }
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

    private Sequence getDefault(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        final AtomicValue key = (AtomicValue) args[1].itemAt(0);
        final Sequence value = map.get(key);
        if (value == null || value.isEmpty()) {
            return args[2];
        }
        return value;
    }

    private Sequence build(final Sequence[] args) throws XPathException {
        final Sequence input = args[0];
        final FunctionReference keyFn = (args.length > 1 && !args[1].isEmpty())
                ? (FunctionReference) args[1].itemAt(0) : null;
        final int keyArity;
        if (keyFn != null) {
            keyFn.analyze(cachedContextInfo);
            keyArity = referenceArity(keyFn);
        } else {
            keyArity = -1;
        }
        final FunctionReference valueFn = (args.length > 2 && !args[2].isEmpty())
                ? (FunctionReference) args[2].itemAt(0) : null;
        final int valueArity;
        if (valueFn != null) {
            valueFn.analyze(cachedContextInfo);
            valueArity = referenceArity(valueFn);
        } else {
            valueArity = -1;
        }

        final BuildDuplicatesHandler dupHandler = getBuildDuplicatesHandler(args);

        final MapType result = new MapType(this, context);
        int position = 1;
        for (final SequenceIterator i = input.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            final Sequence itemSeq = item.toSequence();
            final Sequence keyResult;
            if (keyFn != null) {
                keyResult = keyFn.evalFunction(null, null,
                        buildInputCallbackArgs(keyArity, itemSeq, position));
            } else {
                // Default key function is fn:data#1: atomize the input item.
                keyResult = item.atomize().toSequence();
            }
            // Per XQ4 spec: if key function returns empty sequence, skip this item
            if (keyResult.isEmpty()) {
                position++;
                continue;
            }
            final Sequence value;
            if (valueFn != null) {
                value = valueFn.evalFunction(null, null,
                        buildInputCallbackArgs(valueArity, itemSeq, position));
            } else {
                // Default value function is fn:identity#1: pass the item through.
                value = itemSeq;
            }
            // XQ4 PR1041: key function may return multiple keys; each maps to the same value
            for (final SequenceIterator ki = keyResult.iterate(); ki.hasNext(); ) {
                final Item rawKey = ki.nextItem();
                if (!(rawKey instanceof AtomicValue)) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "map:build key function must return atomic values");
                }
                final AtomicValue key = (AtomicValue) rawKey;
                applyBuildEntry(result, key, value, dupHandler);
            }
            position++;
        }
        return result;
    }

    /**
     * Return the arity bound to a function reference, treating bound
     * variadic references (e.g. {@code concat#2}) as their bound count
     * rather than the signature's -1 sentinel.
     */
    private static int referenceArity(final FunctionReference ref) {
        final FunctionSignature sig = ref.getSignature();
        if (sig.isVariadic()) {
            return sig.getArgumentTypes().length;
        }
        return sig.getArgumentCount();
    }

    /**
     * Build the argument list for a key/value function called from
     * map:build. The function may declare arity 1 (the item) or arity 2
     * (the item and its 1-based position). Arity 0 is allowed for
     * context-item lambdas that ignore arguments.
     */
    private Sequence[] buildInputCallbackArgs(final int arity, final Sequence itemSeq, final int position)
            throws XPathException {
        return switch (arity) {
            case 0 -> new Sequence[0];
            case 1 -> new Sequence[]{itemSeq};
            case 2 -> new Sequence[]{itemSeq, new IntegerValue(this, position, Type.INTEGER)};
            default -> throw new XPathException(this, ErrorCodes.XPTY0004,
                    "map:build callback function must accept 0, 1, or 2 arguments, got " + arity);
        };
    }

    /**
     * Apply one (key, value) pair to the in-progress build map, honoring
     * the caller-selected duplicate-key strategy (string key, function
     * combiner, or default COMBINE for arities &lt; 4).
     */
    private void applyBuildEntry(final MapType result, final AtomicValue key,
            final Sequence value, final BuildDuplicatesHandler dupHandler) throws XPathException {
        final Sequence existing = result.get(key);
        final boolean isDuplicate = existing != null && !existing.isEmpty();
        if (!isDuplicate) {
            result.add(key, value);
            return;
        }
        if (dupHandler.combiner != null) {
            final FunctionReference combiner = dupHandler.combiner;
            final int arity = referenceArity(combiner);
            final Sequence[] callArgs = switch (arity) {
                case 1 -> new Sequence[]{existing};
                case 2 -> new Sequence[]{existing, value};
                default -> throw new XPathException(this, ErrorCodes.XPTY0004,
                        "map:build duplicates combiner must accept 1 or 2 arguments, got " + arity);
            };
            result.add(key, combiner.evalFunction(null, null, callArgs));
            return;
        }
        switch (dupHandler.strategy) {
            case USE_FIRST, USE_ANY -> { /* keep existing */ }
            case USE_LAST -> result.add(key, value);
            case COMBINE -> {
                final ValueSequence combined = new ValueSequence(existing);
                combined.addAll(value);
                result.add(key, combined);
            }
            case REJECT -> throw new XPathException(this, ErrorCodes.FOJS0003,
                    "map:build: duplicate key \"" + key.getStringValue() + "\" with duplicates strategy 'reject'");
        }
    }

    private record BuildDuplicatesHandler(DuplicateMergeStrategy strategy, FunctionReference combiner) {
        static BuildDuplicatesHandler ofStrategy(final DuplicateMergeStrategy s) {
            return new BuildDuplicatesHandler(s, null);
        }
        static BuildDuplicatesHandler ofCombiner(final FunctionReference fn) {
            return new BuildDuplicatesHandler(null, fn);
        }
    }

    /**
     * Resolve the 'duplicates' option from the options map (4th positional
     * argument). Per XQ4 PR1041 the option may be a string naming a
     * built-in strategy ('reject', 'use-first', 'use-last', 'use-any',
     * 'combine') or a function reference of arity 1 or 2 that combines
     * the existing and new value. Absent options default to COMBINE so
     * multi-key results accumulate.
     */
    private BuildDuplicatesHandler getBuildDuplicatesHandler(final Sequence[] args) throws XPathException {
        if (args.length < 4 || args[3].isEmpty()) {
            return BuildDuplicatesHandler.ofStrategy(DuplicateMergeStrategy.COMBINE);
        }
        final AbstractMapType options = (AbstractMapType) args[3].itemAt(0);
        final Sequence value = options.get(new StringValue(this, "duplicates"));
        if (value == null || value.isEmpty()) {
            return BuildDuplicatesHandler.ofStrategy(DuplicateMergeStrategy.COMBINE);
        }
        if (!value.isEmpty() && value.itemAt(0) instanceof FunctionReference fn) {
            fn.analyze(cachedContextInfo);
            return BuildDuplicatesHandler.ofCombiner(fn);
        }
        final DuplicateMergeStrategy strategy = DuplicateMergeStrategy.get(value.getStringValue());
        if (strategy == null) {
            throw new XPathException(this, ErrorCodes.FOJS0005,
                    "map:build: unrecognised value for 'duplicates' option: " + value.getStringValue());
        }
        return BuildDuplicatesHandler.ofStrategy(strategy);
    }

    /**
     * map:items — returns a flat sequence of all values in the map.
     */
    private Sequence mapItems(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        final ValueSequence result = new ValueSequence();
        for (final IEntry<AtomicValue, Sequence> entry : map) {
            result.addAll(entry.value());
        }
        return result;
    }

    /**
     * map:entries — returns a sequence of single-entry maps, one per entry of
     * the source map, where each entry preserves the source's key and value.
     * Per the XPath/XQuery 4.0 spec (post-PR2148), the output of map:entries
     * is the sequence of singleton maps {k: v} - not 2-entry records with
     * literal "key" and "value" labels.
     */
    private Sequence entries(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        final ValueSequence result = new ValueSequence();
        for (final IEntry<AtomicValue, Sequence> entry : map) {
            result.add(new SingleKeyMapType(this, context, null, entry.key(), entry.value()));
        }
        return result;
    }

    private Sequence filter(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        final FunctionReference pred = (FunctionReference) args[1].itemAt(0);
        pred.analyze(cachedContextInfo);
        final int arity = pred.getSignature().getArgumentCount();
        final MapType result = new MapType(this, context);
        int position = 1;
        for (final IEntry<AtomicValue, Sequence> entry : map) {
            final Sequence[] callArgs = buildMapCallbackArgs(arity, entry.key(), entry.value(), position++);
            final Sequence testResult = pred.evalFunction(null, null, callArgs);
            if (testResult.effectiveBooleanValue()) {
                result.add(entry.key(), entry.value());
            }
        }
        return result;
    }

    private Sequence keysWhere(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        final FunctionReference pred = (FunctionReference) args[1].itemAt(0);
        pred.analyze(cachedContextInfo);
        final int arity = pred.getSignature().getArgumentCount();
        final ValueSequence result = new ValueSequence();
        int position = 1;
        for (final IEntry<AtomicValue, Sequence> entry : map) {
            final Sequence[] callArgs = buildMapCallbackArgs(arity, entry.key(), entry.value(), position++);
            final Sequence testResult = pred.evalFunction(null, null, callArgs);
            if (testResult.effectiveBooleanValue()) {
                result.add(entry.key());
            }
        }
        return result;
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
        // --- XQuery 4.0 ---
        EMPTY("empty"),
        GET_DEFAULT("get-default"),
        BUILD("build"),
        ITEMS("items"),
        ENTRIES("entries"),
        FILTER("filter"),
        KEYS_WHERE("keys-where");

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
