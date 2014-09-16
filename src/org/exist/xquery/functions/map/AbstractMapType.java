package org.exist.xquery.functions.map;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.fn.FunDistinctValues;
import org.exist.xquery.value.*;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for map types. A map item is also a function item. This class thus extends
 * {@link FunctionReference} to allow the item to be called in a dynamic function
 * call.
 *
 * @author Wolfgang Meier
 */
public abstract class AbstractMapType extends FunctionReference
        implements Map.Entry<AtomicValue, Sequence>, Iterable<Map.Entry<AtomicValue, Sequence>> {

    private static final Comparator<AtomicValue> DEFAULT_COMPARATOR = new FunDistinctValues.ValueComparator(null);

    // the signature of the function which is evaluated if the map is called as a function item
    private static final FunctionSignature ACCESSOR =
        new FunctionSignature(
            new QName("get", MapModule.NAMESPACE_URI, MapModule.PREFIX),
            "Internal accessor function for maps.",
            new SequenceType[]{
                    new FunctionParameterSequenceType("key", Type.ATOMIC, Cardinality.EXACTLY_ONE, "The key to look up")
            },
            new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));

    private InternalFunctionCall accessorFunc = null;

    protected XQueryContext context;

    public AbstractMapType(XQueryContext context) {
        super(null);
        this.context = context;
    }

    public abstract Sequence get(AtomicValue key);

    public abstract boolean contains(AtomicValue key);

    public abstract Sequence keys();

    public abstract AbstractMapType remove(AtomicValue key);

    public abstract int getKeyType();

    public abstract int size();

    @Override
    public int getItemType() {
        return Type.MAP;
    };

    @Override
    public int getType() {
        return Type.MAP;
    }

    @Override
    public Sequence setValue(Sequence value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        getAccessorFunc().analyze(contextInfo);
    }

    @Override
    public Sequence eval(Sequence contextSequence) throws XPathException {
        return getAccessorFunc().eval(contextSequence);
    }

    @Override
    public void setArguments(List<Expression> arguments) throws XPathException {
        getAccessorFunc().setArguments(arguments);
    }

    @Override
    public void resetState(boolean postOptimization) {
        getAccessorFunc().resetState(postOptimization);
    }

    protected Comparator<AtomicValue> getComparator(String collation)
            throws XPathException {
        if (collation != null) {
            final Collator collator = this.context.getCollator(collation);
            return new FunDistinctValues.ValueComparator(collator);
        }
        return DEFAULT_COMPARATOR;
    }

    /**
     * Return the accessor function. Will be created on demand.
     */
    protected InternalFunctionCall getAccessorFunc() {
        initFunction();
        return accessorFunc;
    }

    /**
     * Lazy initialization of the accessor function. Creating
     * this for every map would be too expensive and we thus
     * only instantiate it on demand.
     */
    protected void initFunction() {
        if (this.accessorFunc != null)
            {return;}
        final Function fn = new AccessorFunc(this.context);
        this.accessorFunc = new InternalFunctionCall(fn);
    }

    /**
     * The accessor function which will be evaluated if the map is called
     * as a function item.
     */
    private class AccessorFunc extends BasicFunction {

        public AccessorFunc(XQueryContext context) {
            super(context, ACCESSOR);
        }

        public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
            return AbstractMapType.this.get((AtomicValue) args[0].itemAt(0));
        }
    }
}