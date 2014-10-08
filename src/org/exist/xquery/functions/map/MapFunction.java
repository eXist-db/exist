package org.exist.xquery.functions.map;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.Map;

/**
 * Implements all functions of the map module.
 */
public class MapFunction extends BasicFunction {
    
    public static final FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("new", MapModule.NAMESPACE_URI, MapModule.PREFIX),
            "Constructs and returns an empty map whose collation is the default collation in the static context.",
            null,
            new SequenceType(Type.MAP, Cardinality.EXACTLY_ONE)),
        new FunctionSignature(
            new QName("new", MapModule.NAMESPACE_URI, MapModule.PREFIX),
            "Constructs and returns an empty map whose collation is the default collation in the static context.",
            new SequenceType[] {
                new FunctionParameterSequenceType("maps", Type.MAP, Cardinality.ZERO_OR_MORE, "Existing maps to combine into the new map.")
            },
            new SequenceType(Type.MAP, Cardinality.EXACTLY_ONE)),
        new FunctionSignature(
            new QName("new", MapModule.NAMESPACE_URI, MapModule.PREFIX),
            "Constructs and returns an empty map whose collation is given in the second argument.",
            new SequenceType[] {
                new FunctionParameterSequenceType("maps", Type.MAP, Cardinality.ZERO_OR_MORE, "Existing maps to combine into the new map."),
                new FunctionParameterSequenceType("collation", Type.STRING, Cardinality.EXACTLY_ONE, "The collation to use for the new map.")
            },
            new SequenceType(Type.MAP, Cardinality.EXACTLY_ONE)),
        new FunctionSignature(
            new QName("entry", MapModule.NAMESPACE_URI, MapModule.PREFIX),
            "Creates a map that contains a single entry (a key-value pair).",
            new SequenceType[] {
                new FunctionParameterSequenceType("key", Type.ATOMIC, Cardinality.EXACTLY_ONE, "The key"),
                new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The associated value")
            },
            new SequenceType(Type.MAP, Cardinality.EXACTLY_ONE)),
        new FunctionSignature(
            new QName("get", MapModule.NAMESPACE_URI, MapModule.PREFIX),
            "Returns the value associated with a supplied key in a given map.",
            new SequenceType[] {
                new FunctionParameterSequenceType(MapModule.PREFIX, Type.MAP, Cardinality.EXACTLY_ONE, "The map"),
                new FunctionParameterSequenceType("key", Type.ATOMIC, Cardinality.EXACTLY_ONE, "The key to look up")
            },
            new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)),
        new FunctionSignature(
            new QName("contains", MapModule.NAMESPACE_URI, MapModule.PREFIX),
            "Tests whether a supplied map contains an entry for a given key.",
            new SequenceType[] {
                new FunctionParameterSequenceType(MapModule.PREFIX, Type.MAP, Cardinality.EXACTLY_ONE, "The map"),
                new FunctionParameterSequenceType("key", Type.ATOMIC, Cardinality.EXACTLY_ONE, "The key to look up")
            },
            new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)),
        new FunctionSignature(
            new QName("keys", MapModule.NAMESPACE_URI, MapModule.PREFIX),
            "Returns a sequence containing all the key values present in a map.",
            new SequenceType[]{new FunctionParameterSequenceType(MapModule.PREFIX, Type.MAP, Cardinality.EXACTLY_ONE, "The map")},
            new SequenceType(Type.ATOMIC, Cardinality.ZERO_OR_MORE)),
        new FunctionSignature(
            new QName("remove", MapModule.NAMESPACE_URI, MapModule.PREFIX),
            "Constructs a new map by removing an entry from an existing map.",
            new SequenceType[] {
                new FunctionParameterSequenceType(MapModule.PREFIX, Type.MAP, Cardinality.EXACTLY_ONE, "The map"),
                new FunctionParameterSequenceType("key", Type.STRING, Cardinality.EXACTLY_ONE, "The key to remove")
            },
            new SequenceType(Type.MAP, Cardinality.EXACTLY_ONE)),
        new FunctionSignature(
            new QName("for-each-entry", MapModule.NAMESPACE_URI, MapModule.PREFIX),
            "takes any map as its $input argument and applies the supplied function to each entry in the map, in implementation-dependent order; the result is the sequence obtained by concatenating the results of these function calls. " +
            "The function supplied as $action takes two arguments. It is called supplying the key of the map entry as the first argument, and the associated value as the second argument.",
            new SequenceType[] {
                new FunctionParameterSequenceType("input", Type.MAP, Cardinality.EXACTLY_ONE, "The map"),
                new FunctionParameterSequenceType("action", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function to be called for each entry")
            },
            new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
        )
    };

    private AnalyzeContextInfo cachedContextInfo;

    public MapFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        super.analyze(contextInfo);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (isCalledAs("new"))
            {return newMap(args);}
        if (isCalledAs("entry"))
            {return entry(args);}
        if (isCalledAs("get"))
            {return get(args);}
        if (isCalledAs("contains"))
            {return contains(args);}
        if (isCalledAs("keys"))
            {return keys(args);}
        if (isCalledAs("remove"))
            {return remove(args);}
        if (isCalledAs("for-each-entry")) {
            return forEachEntry(args);
        }
        return null;
    }

    private Sequence remove(Sequence[] args) {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        return map.remove((AtomicValue) args[1].itemAt(0));
    }

    private Sequence keys(Sequence[] args) {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        return map.keys();
    }

    private Sequence contains(Sequence[] args) {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        return BooleanValue.valueOf(map.contains((AtomicValue) args[1].itemAt(0)));
    }

    private Sequence get(Sequence[] args) {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        return map.get((AtomicValue) args[1].itemAt(0));
    }

    private Sequence entry(Sequence[] args) throws XPathException {
        final AtomicValue key = (AtomicValue) args[0].itemAt(0);
        return new SingleKeyMapType(this.context, null, key, args[1]);
    }

    private Sequence newMap(Sequence[] args) throws XPathException {
        if (args.length == 0) {
            return new MapType(this.context);
        }
        String collation = null;
        if (args.length == 2)
            {collation = args[1].getStringValue();}
        final MapType map = new MapType(this.context, collation);
        for (final SequenceIterator i = args[0].unorderedIterator(); i.hasNext(); ) {
            final AbstractMapType m = (AbstractMapType) i.nextItem();
            map.add(m);
        }
        return map;
    }

    private Sequence forEachEntry(Sequence[] args) throws XPathException {
        final AbstractMapType map = (AbstractMapType) args[0].itemAt(0);
        final FunctionReference ref = (FunctionReference) args[1].itemAt(0);
        ref.analyze(cachedContextInfo);
        final ValueSequence result = new ValueSequence();
        final Sequence fargs[] = new Sequence[2];
        for (Map.Entry<AtomicValue, Sequence> entry: map) {
            fargs[0] = entry.getKey();
            fargs[1] = entry.getValue();
            final Sequence s = ref.evalFunction(null, null, fargs);
            result.addAll(s);
        }
        return result;
    }
}
