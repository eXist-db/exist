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
import org.exist.xquery.functions.fn.FunCodepointEqual;
import org.exist.xquery.functions.fn.FunDeepEqual;
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
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
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

    /**
     * Implementation of <a href="https://www.w3.org/TR/xpath-functions-31/#func-same-key">op:same-key</a>
     *
     * @param collator a collator to use for the comparison, or null to use the default collator.
     * @param k1 the first key to be compared.
     * @param k2 the second key to be compared.
     *
     * @return true if the keys are equal according to the rules of op:same-key, false otherwise.
     */
    protected static boolean sameKey(@Nullable final Collator collator, final AtomicValue k1, final AtomicValue k2) {
        final int k1Type = k1.getType();
        final int k2Type = k2.getType();

        try {

            // $k1 is an instance of xs:string, xs:anyURI, or xs:untypedAtomic
            // $k2 is an instance of xs:string, xs:anyURI, or xs:untypedAtomic
            // fn:codepoint-equal($k1, $k2)
            if ((Type.subTypeOf(k1Type, Type.STRING) || k1Type == Type.ANY_URI || k1Type ==Type.UNTYPED_ATOMIC)
                    && (Type.subTypeOf(k2Type, Type.STRING) || k2Type == Type.ANY_URI || k2Type == Type.UNTYPED_ATOMIC)
                    && FunCodepointEqual.codepointEqual(k1, k2, collator)) {
                return true;
            }

            // $k1 is an instance of xs:decimal, xs:double, or xs:float
            // $k2 is an instance of xs:decimal, xs:double, or xs:float
            if ((Type.subTypeOf(k1Type, Type.DECIMAL) || k1Type == Type.DOUBLE || k1Type == Type.FLOAT)
                    && (Type.subTypeOf(k2Type, Type.DECIMAL) || k2Type == Type.DOUBLE || k2Type == Type.FLOAT)) {

                // Both $k1 and $k2 are NaN
                // Note: xs:double('NaN') is the same key as xs:float('NaN')
                if (((NumericValue) k1).isNaN() && ((NumericValue) k2).isNaN()) {
                    return true;
                }

                // Both $k1 and $k2 are positive infinity
                // Note: xs:double('INF') is the same key as xs:float('INF')
                if (((NumericValue) k1).isPositiveInfinity() && ((NumericValue) k2).isPositiveInfinity()) {
                    return true;
                }

                // Both $k1 and $k2 are negative infinity
                // Note: xs:double('-INF') is the same key as xs:float('-INF')
                if (((NumericValue) k1).isNegativeInfinity() && ((NumericValue) k2).isNegative()) {
                    return true;
                }

                // $k1 and $k2 when converted to decimal numbers with no rounding or loss of precision are mathematically equal.
                if (((NumericValue) k1).convertTo(Type.DECIMAL).equals(((NumericValue) k2).convertTo(Type.DECIMAL))) {
                    return true;
                }
            }

            // $k1 is an instance of xs:date, xs:time, xs:dateTime, xs:gYear, xs:gYearMonth, xs:gMonth, xs:gMonthDay, or xs:gDay
            // $k2 is an instance of xs:date, xs:time, xs:dateTime, xs:gYear, xs:gYearMonth, xs:gMonth, xs:gMonthDay, or xs:gDay
            // Both $k1 and $k2 have a timezone, OR Neither $k1 nor $k2 has a timezone
            // fn:deep-equal($k1, $k2)
            if ((k1Type == Type.DATE || k1Type == Type.TIME || Type.subTypeOf(k1Type, Type.DATE_TIME) || k1Type == Type.G_YEAR || k1Type == Type.G_YEAR_MONTH || k1Type == Type.G_MONTH || k1Type == Type.G_MONTH_DAY || k1Type == Type.G_DAY)
                    && (k2Type == Type.DATE || k2Type == Type.TIME || Type.subTypeOf(k2Type, Type.DATE_TIME) || k2Type == Type.G_YEAR || k2Type == Type.G_YEAR_MONTH || k2Type == Type.G_MONTH || k2Type == Type.G_MONTH_DAY || k2Type == Type.G_DAY)
                    && (((AbstractDateTimeValue) k1).hasTimezone() == ((AbstractDateTimeValue) k2).hasTimezone())
                    && FunDeepEqual.deepEquals(k1, k2, collator)) {
                return true;
            }

            // $k1 is an instance of xs:boolean, xs:hexBinary, xs:base64Binary, xs:duration, xs:QName, or xs:NOTATION
            // $k2 is an instance of xs:boolean, xs:hexBinary, xs:base64Binary, xs:duration, xs:QName, or xs:NOTATION
            // fn:deep-equal($k1, $k2)
            if ((k1Type == Type.BOOLEAN || k1Type == Type.HEX_BINARY || k1Type == Type.BASE64_BINARY || Type.subTypeOf(k1Type, Type.DURATION) || k1Type == Type.QNAME || k1Type == Type.NOTATION)
                    && (k2Type == Type.BOOLEAN || k2Type == Type.HEX_BINARY || k2Type == Type.BASE64_BINARY || Type.subTypeOf(k2Type, Type.DURATION) || k2Type == Type.QNAME || k2Type == Type.NOTATION)
                    && FunDeepEqual.deepEquals(k1, k2, collator)) {
                return true;
            }

        } catch (final XPathException e) {
            LOG.warn("Unable to compare k1={} with k2={}, using collation '{}'. Error: {}", k1, k2, collator, e.getMessage(), e);
        }

        return false;
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
