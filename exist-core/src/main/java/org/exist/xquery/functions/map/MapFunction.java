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
    public static final FunctionSignature GET_3 = functionSignature(
            Fn.GET.fname,
            "Returns the value associated with a supplied key in a given map, " +
                    "or the supplied default value if the key is not present.",
            RETURN_OPT_MANY_ITEM,
            PARAM_INPUT_MAP,
            PARAM_KEY,
            optManyParam("default", Type.ITEM, "The default value to return when the key is absent")
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
                    "In XQuery 4.0, the callback may accept 0 to 2 arguments: (item, position). " +
                    "If the key function is the empty sequence, fn:data#1 is used (atomize the item).",
            RETURN_MAP,
            optManyParam("input", Type.ITEM, "The input sequence"),
            optParam("key", Type.FUNCTION, "The key function")
    );

    public static final FunctionSignature BUILD_2 = functionSignature(
            Fn.BUILD.fname,
            "Builds a map from a sequence of items, using key and value functions. " +
                    "In XQuery 4.0, the callbacks may accept 0 to 2 arguments: (item, position). " +
                    "An empty sequence for either function selects the default (fn:data#1 / fn:identity#1).",
            RETURN_MAP,
            optManyParam("input", Type.ITEM, "The input sequence"),
            optParam("key", Type.FUNCTION, "The key function"),
            optParam("value", Type.FUNCTION, "The value function")
    );

    public static final FunctionSignature BUILD_3 = functionSignature(
            Fn.BUILD.fname,
            "Builds a map from a sequence of items, with key/value functions and options. " +
                    "The options map may contain a 'duplicates' entry controlling duplicate-key handling.",
            RETURN_MAP,
            optManyParam("input", Type.ITEM, "The input sequence"),
            optFunParam("keys", new FunctionParameterSequenceType[]{
                            optManyParam("item", Type.ITEM, "the item")},
                    returnsOptMany(Type.ANY_ATOMIC_TYPE), "The key function"),
            optFunParam("value", new FunctionParameterSequenceType[]{
                            optManyParam("item", Type.ITEM, "the item")},
                    returnsOptMany(Type.ITEM), "The value function"),
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
        // XQuery 4.0: 3-arg form returns $default if the key is absent.
        if (args.length > 2) {
            if (value == null || value.isEmpty()) {
                return args[2];
            }
            return value;
        }
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
            // Default key function is fn:data#1 (atomize). Required by map-build-117
            // where the input is a sequence of element nodes.
            final Sequence keyResult = keyFn != null
                    ? keyFn.evalFunction(null, null, buildBuildCallbackArgs(keyArity, itemSeq, position))
                    : item.atomize().toSequence();
            // Per XQ4 spec: if key function returns empty sequence, skip this item
            if (keyResult.isEmpty()) {
                position++;
                continue;
            }
            // Default value function is fn:identity#1.
            final Sequence value = valueFn != null
                    ? valueFn.evalFunction(null, null, buildBuildCallbackArgs(valueArity, itemSeq, position))
                    : itemSeq;
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
     * Arity of a function reference, accounting for partial application
     * (a partially applied reference reports its remaining arity).
     */
    private static int referenceArity(final FunctionReference ref) {
        return ref.getSignature().getArgumentCount();
    }

    /**
     * Build callback args for map:build's $key and $value functions, supporting
     * XQ4 arity 0, 1 (item), or 2 (item, position).
     */
    private Sequence[] buildBuildCallbackArgs(final int arity, final Sequence item, final int position) throws XPathException {
        return switch (arity) {
            case 0 -> new Sequence[0];
            case 1 -> new Sequence[]{item};
            case 2 -> new Sequence[]{item, new IntegerValue(this, position, Type.INTEGER)};
            default -> throw new XPathException(this, ErrorCodes.XPTY0004,
                    "map:build callback must accept 0 to 2 arguments, got " + arity);
        };
    }

    /**
     * Apply one (key, value) pair to the in-progress build map, honoring the
     * caller-selected duplicate-key strategy or function combiner.
     */
    private void applyBuildEntry(final MapType result, final AtomicValue key,
            final Sequence value, final BuildDuplicatesHandler handler) throws XPathException {
        final Sequence existing = result.get(key);
        final boolean isDuplicate = existing != null && !existing.isEmpty();
        if (!isDuplicate) {
            result.add(key, value);
            return;
        }
        if (handler.combiner != null) {
            // XQ4 PR1041: function-valued 'duplicates' combines existing/incoming values.
            final FunctionReference combiner = handler.combiner;
            final int arity = referenceArity(combiner);
            final Sequence[] callArgs = arity == 1
                    ? new Sequence[]{existing}
                    : new Sequence[]{existing, value};
            if (arity != 1 && arity != 2) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "map:build duplicates combiner must accept 1 or 2 arguments, got " + arity);
            }
            result.add(key, combiner.evalFunction(null, null, callArgs));
            return;
        }
        switch (handler.strategy) {
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

    /**
     * Resolve the duplicate-key handler from the options argument (4th positional).
     * The 'duplicates' option may be a string strategy name or a function reference
     * (arity 1: sees existing only; arity 2: sees existing and incoming).
     * Defaults to COMBINE so multi-key results accumulate values (XQ4 PR1041).
     */
    private BuildDuplicatesHandler getBuildDuplicatesHandler(final Sequence[] args) throws XPathException {
        if (args.length < 4 || args[3].isEmpty()) {
            return BuildDuplicatesHandler.of(DuplicateMergeStrategy.COMBINE);
        }
        final AbstractMapType options = (AbstractMapType) args[3].itemAt(0);
        final Sequence value = options.get(new StringValue(this, "duplicates"));
        if (value == null || value.isEmpty()) {
            return BuildDuplicatesHandler.of(DuplicateMergeStrategy.COMBINE);
        }
        final Item first = value.itemAt(0);
        if (first instanceof FunctionReference fnRef) {
            fnRef.analyze(cachedContextInfo);
            return BuildDuplicatesHandler.ofCombiner(fnRef);
        }
        final DuplicateMergeStrategy strategy = DuplicateMergeStrategy.get(value.getStringValue());
        if (strategy == null) {
            throw new XPathException(this, ErrorCodes.FOJS0005,
                    "map:build: unrecognised value for 'duplicates' option: " + value.getStringValue());
        }
        return BuildDuplicatesHandler.of(strategy);
    }

    /**
     * Carrier for either a named duplicate-key strategy or a function combiner.
     */
    private static final class BuildDuplicatesHandler {
        final DuplicateMergeStrategy strategy;
        final FunctionReference combiner;

        private BuildDuplicatesHandler(final DuplicateMergeStrategy strategy, final FunctionReference combiner) {
            this.strategy = strategy;
            this.combiner = combiner;
        }

        static BuildDuplicatesHandler of(final DuplicateMergeStrategy strategy) {
            return new BuildDuplicatesHandler(strategy, null);
        }

        static BuildDuplicatesHandler ofCombiner(final FunctionReference combiner) {
            return new BuildDuplicatesHandler(null, combiner);
        }
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
     * map:entries — returns a sequence of maps, each with 'key' and 'value' entries.
     * (XQ4 spec says records, but without record support we return maps.)
     */
    private Sequence entries(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        final ValueSequence result = new ValueSequence();
        for (final IEntry<AtomicValue, Sequence> entry : map) {
            final MapType entryMap = new MapType(this, context);
            entryMap.add(new StringValue("key"), entry.key().toSequence());
            entryMap.add(new StringValue("value"), entry.value());
            result.add(entryMap);
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
