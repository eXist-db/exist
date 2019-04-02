package org.exist.xquery.functions.map;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.Map;

/**
 * Implements all functions of the map module.
 */
public class MapFunction extends BasicFunction {

    private static final QName QN_MERGE = new QName("merge", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_SIZE = new QName("size", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_ENTRY = new QName("entry", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_GET = new QName("get", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_PUT = new QName("put", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_CONTAINS = new QName("contains", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_KEYS = new QName("keys", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_REMOVE = new QName("remove", MapModule.NAMESPACE_URI, MapModule.PREFIX);
    private static final QName QN_FOR_EACH = new QName("for-each", MapModule.NAMESPACE_URI, MapModule.PREFIX);

    public final static FunctionSignature FNS_MERGE = new FunctionSignature(
        QN_MERGE,
        "Returns a map that combines the entries from a number of existing maps.",
        new SequenceType[] {
            new FunctionParameterSequenceType("maps", Type.MAP, Cardinality.ZERO_OR_MORE, "Existing maps to merge to create a new map.")
        },
        new SequenceType(Type.MAP, Cardinality.EXACTLY_ONE)
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

    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (isCalledAs(QN_MERGE.getLocalPart())) {
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
        return null;
    }

    private Sequence remove(final Sequence[] args) {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        return map.remove((AtomicValue) args[1].itemAt(0));
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
        if (args.length == 0) {
            return new MapType(this.context);
        }
        final MapType map = new MapType(this.context, null);
        for (final SequenceIterator i = args[0].unorderedIterator(); i.hasNext(); ) {
            final AbstractMapType m = (AbstractMapType) i.nextItem();
            map.add(m);
        }
        return map;
    }

    @Deprecated
    private Sequence newMap(final Sequence[] args) throws XPathException {
        if (args.length == 0) {
            return new MapType(this.context);
        }
        String collation = null;
        if (args.length == 2) {
            collation = args[1].getStringValue();
        }
        final MapType map = new MapType(this.context, collation);
        for (final SequenceIterator i = args[0].unorderedIterator(); i.hasNext(); ) {
            final AbstractMapType m = (AbstractMapType) i.nextItem();
            map.add(m);
        }
        return map;
    }

    private Sequence forEach(final Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        try (final FunctionReference ref = (FunctionReference) args[1].itemAt(0)) {
            ref.analyze(cachedContextInfo);
            final ValueSequence result = new ValueSequence();
            for (final Map.Entry<AtomicValue, Sequence> entry : map) {
                final Sequence s = ref.evalFunction(null, null, new Sequence[]{entry.getKey(), entry.getValue()});
                result.addAll(s);
            }
            return result;
        }
    }
}
