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

import com.ibm.icu.text.Collator;
import io.lacuna.bifurcan.IEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BinaryOperator;

/**
 * Abstract base class for map types. A map item is also a function item. This class thus extends
 * {@link FunctionReference} to allow the item to be called in a dynamic function
 * call.
 *
 * @author Wolfgang Meier
 */
public abstract class AbstractMapType extends FunctionReference
        implements IEntry<AtomicValue, Sequence>, Iterable<IEntry<AtomicValue, Sequence>>,
        Lookup.LookupSupport {

    /**
     * Used when the type of the keys for the
     * map is unknown.
     */
    public static final int UNKNOWN_KEY_TYPE = Type.ANY_SIMPLE_TYPE;

    /**
     * Used when a map contains keys of various xs:anyAtomicType
     */
    public static final int MIXED_KEY_TYPES = Type.ANY_ATOMIC_TYPE;

    private final static Logger LOG = LogManager.getLogger(AbstractMapType.class);

    // the signature of the function which is evaluated if the map is called as a function item
    private static final FunctionSignature ACCESSOR =
        new FunctionSignature(
            new QName("get", MapModule.NAMESPACE_URI, MapModule.PREFIX),
            "Internal accessor function for maps.",
            new SequenceType[]{
                    new FunctionParameterSequenceType("key", Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE, "The key to look up")
            },
            new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));

    private InternalFunctionCall accessorFunc = null;

    protected XQueryContext context;

    protected AbstractMapType(final XQueryContext context) {
        this(null, context);
    }

    protected AbstractMapType(final Expression expression, final XQueryContext context) {
        super(expression, null);
        this.context = context;
    }

    public abstract Sequence get(AtomicValue key);

    public abstract AbstractMapType put(AtomicValue key, Sequence value) throws XPathException;

    public abstract AbstractMapType merge(Iterable<AbstractMapType> others);

    public abstract AbstractMapType merge(Iterable<AbstractMapType> others, BinaryOperator<Sequence> mergeFn);

    public abstract boolean contains(AtomicValue key);

    public abstract Sequence keys();

    public abstract AbstractMapType remove(AtomicValue[] keysAtomicValues) throws XPathException;

    @SuppressWarnings("unused")
    public abstract int getKeyType();

    public abstract int size();

    @Override
    public int getItemType() {
        return Type.MAP_ITEM;
    }

    @Override
    public int getType() {
        return Type.MAP_ITEM;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        getAccessorFunc().analyze(contextInfo);
    }

    @Override
    public Sequence eval(final Sequence contextSequence) throws XPathException {
        return getAccessorFunc().eval(contextSequence, null);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        return getAccessorFunc().eval(contextSequence, contextItem);
    }

    @Override
    public Sequence evalFunction(final Sequence contextSequence, final Item contextItem, final Sequence[] seq) throws XPathException {
        final AccessorFunc accessorFunc =  (AccessorFunc) getAccessorFunc().getFunction();
        return accessorFunc.eval(seq, contextSequence);
    }

    @Override
    public void setArguments(final List<Expression> arguments) throws XPathException {
        getAccessorFunc().setArguments(arguments);
    }

    @Override
    public void resetState(final boolean postOptimization) {
        getAccessorFunc().resetState(postOptimization);
    }

    protected static boolean keysEqual(@Nullable final Collator collator, final AtomicValue k1, final AtomicValue k2) {
        try {
            return ValueComparison.compareAtomic(collator, k1, k2, Constants.StringTruncationOperator.NONE, Constants.Comparison.EQ);
        } catch (final XPathException e) {
            LOG.warn("Unable to compare with collation '{}', will fallback to non-collation comparision. Error: {}", collator, e.getMessage(), e);
        }

        // fallback
        return k1.equals(k2);
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder("map {");

        boolean first = true;
        final Sequence keys = keys();
        if(keys != null && !keys.isEmpty()) {
            try {
                final SequenceIterator iterator = keys.iterate();
                while (iterator.hasNext()) {
                    final Item key = iterator.nextItem();

                    if (!first) {
                        buf.append(", ");
                    }

                    if(key instanceof StringValue) {
                        buf.append('\"');
                    }

                    buf.append(key);

                    if(key instanceof StringValue) {
                        buf.append('\"');
                    }

                    buf.append(": ");
                    final Sequence value = get((AtomicValue) key);

                    if(value != null && value.hasOne() && value instanceof StringValue) {
                        buf.append('\"');
                    }

                    buf.append(value);
                    if(value != null && value.hasOne() && value instanceof StringValue) {
                        buf.append('\"');
                    }

                    first = false;
                }
            } catch (final XPathException e) {
                LOG.error(e.getMessage(), e);
            }
        }

        buf.append('}');

        return buf.toString();
    }

    /**
     * Return the accessor function. Will be created on demand.
     *
     * @return function item to access the map
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
        if (this.accessorFunc != null) {
            return;
        }
        final Function fn = new AccessorFunc(context, this);
        this.accessorFunc = new InternalFunctionCall(fn);
    }

    /**
     * The accessor function which will be evaluated if the map is called
     * as a function item.
     */
    private static class AccessorFunc extends BasicFunction {
        private final AbstractMapType map;

        public AccessorFunc(final XQueryContext context, final AbstractMapType map) {
            super(context, ACCESSOR);
            this.map = map;
        }

        @Override
        public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
            return map.get((AtomicValue) args[0].itemAt(0));
        }
    }
}
