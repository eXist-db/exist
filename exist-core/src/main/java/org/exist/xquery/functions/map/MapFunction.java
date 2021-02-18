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
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

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

    private static final FunctionParameterSequenceType FS_PARAM_MAPS = optManyParam("maps", Type.MAP, "Existing maps to merge to create a new map.");

    private static final String FS_MERGE_NAME = "merge";
    public final static FunctionSignature[] FS_MERGE = functionSignatures(
        FS_MERGE_NAME,
        "Returns a map that combines the entries from a number of existing maps.",
        returns(Type.MAP, "A new map which is the result of merging the maps"),
        arities(
                arity(
                        FS_PARAM_MAPS
                ),
                arity(
                        FS_PARAM_MAPS,
                        param("options", Type.MAP, "Can be used to control the way in which duplicate keys are handled.")
                )
        )
    );

    public final static FunctionSignature FNS_SIZE = new FunctionSignature(
        QN_SIZE,
        "Returns the number of entries in the supplied map.",
        new SequenceType[] {
            new FunctionParameterSequenceType("input", Type.MAP, Cardinality.EXACTLY_ONE, "Any map to determine the size of.")
        },
        new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE)
    );

    public final static FunctionSignature FNS_KEYS = new FunctionSignature(
        QN_KEYS,
        "Returns a sequence containing all the key values present in a map.",
        new SequenceType[]{
            new FunctionParameterSequenceType(MapModule.PREFIX, Type.MAP, Cardinality.EXACTLY_ONE, "The map")
        },
        new SequenceType(Type.ATOMIC, Cardinality.ZERO_OR_MORE)
    );

    public final static FunctionSignature FNS_CONTAINS = new FunctionSignature(
        QN_CONTAINS,
        "Tests whether a supplied map contains an entry for a given key.",
        new SequenceType[] {
            new FunctionParameterSequenceType(MapModule.PREFIX, Type.MAP, Cardinality.EXACTLY_ONE, "The map"),
            new FunctionParameterSequenceType("key", Type.ATOMIC, Cardinality.EXACTLY_ONE, "The key to look up")
        },
        new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
    );

    public final static FunctionSignature FNS_GET = new FunctionSignature(
        QN_GET,
        "Returns the value associated with a supplied key in a given map.",
        new SequenceType[] {
            new FunctionParameterSequenceType(MapModule.PREFIX, Type.MAP, Cardinality.EXACTLY_ONE, "The map"),
            new FunctionParameterSequenceType("key", Type.ATOMIC, Cardinality.EXACTLY_ONE, "The key to look up")
        },
        new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
    );

    public final static FunctionSignature FNS_PUT = new FunctionSignature(
        QN_PUT,
        "Returns a map containing all the contents of the supplied map, but with an additional entry, which replaces any existing entry for the same key.",
        new SequenceType[] {
            new FunctionParameterSequenceType(MapModule.PREFIX, Type.MAP, Cardinality.EXACTLY_ONE, "The map"),
            new FunctionParameterSequenceType("key", Type.ATOMIC, Cardinality.EXACTLY_ONE, "The key for the entry to insert"),
            new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The value for the entry to insert")
        },
        new SequenceType(Type.MAP, Cardinality.EXACTLY_ONE)
    );

    public final static FunctionSignature FNS_ENTRY = new FunctionSignature(
        QN_ENTRY,
        "Creates a map that contains a single entry (a key-value pair).",
        new SequenceType[] {
            new FunctionParameterSequenceType("key", Type.ATOMIC, Cardinality.EXACTLY_ONE, "The key"),
            new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The associated value")
        },
        new SequenceType(Type.MAP, Cardinality.EXACTLY_ONE)
    );

    public final static FunctionSignature FNS_REMOVE = new FunctionSignature(
        QN_REMOVE,
        "Constructs a new map by removing an entry from an existing map.",
        new SequenceType[] {
            new FunctionParameterSequenceType(MapModule.PREFIX, Type.MAP, Cardinality.EXACTLY_ONE, "The map"),
            new FunctionParameterSequenceType("key", Type.ATOMIC, Cardinality.ZERO_OR_MORE, "The key to remove")
        },
        new SequenceType(Type.MAP, Cardinality.EXACTLY_ONE)
    );

    public final static FunctionSignature FNS_FOR_EACH = new FunctionSignature(
        QN_FOR_EACH,
        "takes any map as its $input argument and applies the supplied function to each entry in the map, in implementation-dependent order; the result is the sequence obtained by concatenating the results of these function calls. " +
        "The function supplied as $action takes two arguments. It is called supplying the key of the map entry as the first argument, and the associated value as the second argument.",
        new SequenceType[] {
            new FunctionParameterSequenceType("input", Type.MAP, Cardinality.EXACTLY_ONE, "The map"),
            new FunctionParameterSequenceType("action", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function to be called for each entry")
        },
        new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
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
        if (value != null) {
            return value;
        } else {
            return Sequence.EMPTY_SEQUENCE;
        }
    }

    private Sequence put(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        return map.put((AtomicValue) args[1].itemAt(0), args[2]);
    }

    private Sequence entry(final Sequence[] args) throws XPathException {
        final AtomicValue key = (AtomicValue) args[0].itemAt(0);
        return new SingleKeyMapType(this.context, null, key, args[1]);
    }

    private Sequence size(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        return new IntegerValue(map.size(), Type.INTEGER);
    }

    private Sequence merge(final Sequence[] args) throws XPathException {
        if (args[0].getItemCount() == 0) {
            // map:merge(())
            return new MapType(this.context);
        }

        final MergeDuplicates mergeDuplicates;
        if (args.length == 2) {
            final Sequence mapValue = ((MapType) args[1]).get(new StringValue("duplicates"));
            if (mapValue != null) {
                mergeDuplicates = MergeDuplicates.fromDuplicatesValue(mapValue.getStringValue());
                if (mergeDuplicates == null) {
                    throw new XPathException(this, ErrorCodes.FOJS0005, "value for duplicates key was not recognised: " + mapValue.getStringValue());
                }
            } else {
                // TODO(AR) the XQ3.1 spec says that the default for fn:merge#2 should be USE_FIRST... this needs to be revised in a major release of eXist-db
                mergeDuplicates = MergeDuplicates.USE_LAST;
            }
        } else {
            // TODO(AR) the XQ3.1 spec says that the default for fn:merge#1 should be USE_FIRST... this needs to be revised in a major release of eXist-db
            mergeDuplicates = MergeDuplicates.USE_LAST;
        }

        if (mergeDuplicates == MergeDuplicates.REJECT) {
            throw new XPathException(this, ErrorCodes.FOJS0005, "map { \"duplicates\": \"reject\" } is not yet implemented by eXist-db");
        } else if (mergeDuplicates == MergeDuplicates.COMBINE) {
            throw new XPathException(this, ErrorCodes.FOJS0005, "map { \"combine\": \"reject\" } is not yet implemented by eXist-db");
        }

        final Sequence maps = args[0];
        final int totalMaps = maps.getItemCount();
        final AbstractMapType head;
        final List<AbstractMapType> tail = new ArrayList<>(totalMaps - 1);

        if (mergeDuplicates == MergeDuplicates.USE_LAST) {
            // head is the first map
            head = (AbstractMapType) maps.itemAt(0);
            for (int i = 1; i < totalMaps; i++) {
                final AbstractMapType other = (AbstractMapType) maps.itemAt(i);
                tail.add(other);
            }

        } else {
            // head is the last map
            head = (AbstractMapType) maps.itemAt(totalMaps - 1);
            for (int i = totalMaps - 2; i >= 0; i--) {
                final AbstractMapType other = (AbstractMapType) maps.itemAt(i);
                tail.add(other);
            }
        }

        return head.merge(tail);
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
