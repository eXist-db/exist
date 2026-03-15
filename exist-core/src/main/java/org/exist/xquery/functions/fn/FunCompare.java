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
package org.exist.xquery.functions.fn;

import com.ibm.icu.text.Collator;
import org.exist.dom.QName;
import org.exist.util.Collations;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AbstractDateTimeValue;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.DurationValue;
import org.exist.xquery.value.FloatValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.math.BigDecimal;
import java.math.BigInteger;

import javax.annotation.Nullable;

/**
 * @author perig
 * @author ljo
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class FunCompare extends CollatingFunction {

    public final static FunctionSignature[] signatures = {
        new FunctionSignature (
            new QName("compare", Function.BUILTIN_FUNCTION_NS),
            "Returns -1, 0, or 1, depending on whether $value-1 is less than, equal to, " +
            "or greater than $value-2. " +
            "If either comparand is the empty sequence, the empty sequence is returned.",
            new SequenceType[] {
                new FunctionParameterSequenceType("value-1", Type.ANY_ATOMIC_TYPE,
                    Cardinality.ZERO_OR_ONE, "The first value"),
                new FunctionParameterSequenceType("value-2", Type.ANY_ATOMIC_TYPE,
                    Cardinality.ZERO_OR_ONE, "The second value")
            },
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE,
                "-1, 0, or 1 depending on comparison result")),
        new FunctionSignature (
            new QName("compare", Function.BUILTIN_FUNCTION_NS),
            "Returns -1, 0, or 1, depending on whether $value-1 is less than, equal to, " +
            "or greater than $value-2, using the specified collation. " +
            "If either comparand is the empty sequence, the empty sequence is returned. " +
            THIRD_REL_COLLATION_ARG_EXAMPLE,
            new SequenceType[] {
                new FunctionParameterSequenceType("value-1", Type.ANY_ATOMIC_TYPE,
                    Cardinality.ZERO_OR_ONE, "The first value"),
                new FunctionParameterSequenceType("value-2", Type.ANY_ATOMIC_TYPE,
                    Cardinality.ZERO_OR_ONE, "The second value"),
                new FunctionParameterSequenceType("collation-uri", Type.STRING,
                    Cardinality.EXACTLY_ONE, "The relative collation URI")
            },
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE,
                "-1, 0, or 1 depending on comparison result"))
    };

    public FunCompare(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT ITEM", contextItem.toSequence());}
        }
        final Sequence seq1 = getArgument(0).eval(contextSequence, contextItem);
        final Sequence seq2 =	getArgument(1).eval(contextSequence, contextItem);
        Sequence result;
        if (seq1.isEmpty() || seq2.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {

            final Collator collator = getCollator(contextSequence, contextItem, 3);		
            result = new IntegerValue(this, compare(seq1.itemAt(0), seq2.itemAt(0), collator));
        }
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}
        return result;
	}

    public static int compare(final Item item1, final Item item2, @Nullable final Collator collator) throws XPathException {
        final AtomicValue v1 = item1.atomize();
        final AtomicValue v2 = item2.atomize();

        // For string-like types, use collation-aware comparison
        if (isStringLike(v1.getType()) && isStringLike(v2.getType())) {
            return normalizeComparison(Collations.compare(collator, v1.getStringValue(), v2.getStringValue()));
        }

        // XQ4 numeric total order: compare by exact mathematical magnitude
        if (v1 instanceof NumericValue && v2 instanceof NumericValue) {
            return numericTotalOrder((NumericValue) v1, (NumericValue) v2);
        }

        // XQ4 duration total order: months first, then seconds
        if (v1 instanceof DurationValue && v2 instanceof DurationValue) {
            return durationTotalOrder((DurationValue) v1, (DurationValue) v2);
        }

        // XQ4 date/time total order: normalize to millis for types where
        // XMLGregorianCalendar.compare() may return INDETERMINATE
        if (v1 instanceof AbstractDateTimeValue && v2 instanceof AbstractDateTimeValue
                && v1.getType() == v2.getType()) {
            return dateTimeTotalOrder((AbstractDateTimeValue) v1, (AbstractDateTimeValue) v2);
        }

        // For other atomic types, use natural ordering via compareTo
        return normalizeComparison(v1.compareTo(collator, v2));
    }

    /**
     * XQ4 numeric total order for fn:compare.
     * Float is promoted to double. NaN == NaN (and NaN < everything).
     * -0.0 == +0.0. Doubles and decimals compared by exact mathematical magnitude.
     */
    static int numericTotalOrder(final NumericValue v1, final NumericValue v2) throws XPathException {
        // Promote float to double
        final double d1 = v1.getDouble();
        final double d2 = v2.getDouble();

        final boolean nan1 = Double.isNaN(d1);
        final boolean nan2 = Double.isNaN(d2);

        // NaN equals NaN, NaN < everything else
        if (nan1 && nan2) {
            return Constants.EQUAL;
        }
        if (nan1) {
            return Constants.INFERIOR;
        }
        if (nan2) {
            return Constants.SUPERIOR;
        }

        // Handle infinities
        if (Double.isInfinite(d1) || Double.isInfinite(d2)) {
            if (d1 == d2) {
                return Constants.EQUAL;
            }
            return d1 < d2 ? Constants.INFERIOR : Constants.SUPERIOR;
        }

        // -0.0 == +0.0
        if (d1 == 0.0 && d2 == 0.0) {
            return Constants.EQUAL;
        }

        // Compare by exact mathematical magnitude using BigDecimal
        final BigDecimal bd1 = toBigDecimal(v1);
        final BigDecimal bd2 = toBigDecimal(v2);
        return normalizeComparison(bd1.compareTo(bd2));
    }

    private static BigDecimal toBigDecimal(final NumericValue v) throws XPathException {
        if (v instanceof org.exist.xquery.value.DecimalValue) {
            return ((org.exist.xquery.value.DecimalValue) v).getValue();
        }
        if (v instanceof IntegerValue) {
            return new BigDecimal(((IntegerValue) v).getValue());
        }
        // Double or Float — use exact decimal representation (no rounding)
        return new BigDecimal(v.getDouble());
    }

    /**
     * XQ4 duration total order for fn:compare.
     * Compares months component first, then seconds component.
     * This provides a total order even for xs:duration values where
     * months and seconds are both present (which XMLGregorianCalendar
     * considers INDETERMINATE).
     */
    static int durationTotalOrder(final DurationValue v1, final DurationValue v2) {
        final BigInteger months1 = v1.monthsValueSigned();
        final BigInteger months2 = v2.monthsValueSigned();
        final int monthsCmp = months1.compareTo(months2);
        if (monthsCmp != 0) {
            return normalizeComparison(monthsCmp);
        }
        final BigDecimal seconds1 = v1.secondsValueSigned();
        final BigDecimal seconds2 = v2.secondsValueSigned();
        return normalizeComparison(seconds1.compareTo(seconds2));
    }

    /**
     * XQ4 date/time total order for fn:compare.
     * Uses getTimeInMillis() to normalize both values to a common
     * representation, avoiding INDETERMINATE results from
     * XMLGregorianCalendar.compare() on partial date/time types.
     */
    static int dateTimeTotalOrder(final AbstractDateTimeValue v1, final AbstractDateTimeValue v2) {
        final long ms1 = v1.getTimeInMillis();
        final long ms2 = v2.getTimeInMillis();
        return normalizeComparison(Long.compare(ms1, ms2));
    }

    private static int normalizeComparison(final int cmp) {
        if (cmp == 0) {
            return Constants.EQUAL;
        }
        return cmp < 0 ? Constants.INFERIOR : Constants.SUPERIOR;
    }

    private static boolean isStringLike(final int type) {
        return Type.subTypeOf(type, Type.STRING)
                || type == Type.UNTYPED_ATOMIC
                || Type.subTypeOf(type, Type.ANY_URI);
    }
}
