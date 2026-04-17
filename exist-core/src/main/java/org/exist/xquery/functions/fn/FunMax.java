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
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.DurationValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implementation of fn:max with XQuery 4.0 semantics.
 * Uses fn:compare-based mutual comparability (XQ4 numeric total order,
 * duration total order, date/time total order).
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FunMax extends CollatingFunction {

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("max", Function.BUILTIN_FUNCTION_NS),
            "Returns the maximum value from the input sequence, using XQ4 comparison semantics.",
            new SequenceType[] {
                new FunctionParameterSequenceType("values", Type.ANY_ATOMIC_TYPE,
                    Cardinality.ZERO_OR_MORE, "The input sequence")
            },
            new FunctionReturnSequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_ONE,
                "the maximum value")
        ),
        new FunctionSignature(
            new QName("max", Function.BUILTIN_FUNCTION_NS),
            "Returns the maximum value from the input sequence, using the specified collation.",
            new SequenceType[] {
                new FunctionParameterSequenceType("values", Type.ANY_ATOMIC_TYPE,
                    Cardinality.ZERO_OR_MORE, "The input sequence"),
                new FunctionParameterSequenceType("collation", Type.STRING,
                    Cardinality.ZERO_OR_ONE, "The collation URI")
            },
            new FunctionReturnSequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_ONE,
                "the maximum value")
        )
    };

    public FunMax(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES",
                Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT ITEM", contextItem.toSequence());}
        }

        Sequence result;
        final Sequence arg = getArgument(0).eval(contextSequence, contextItem);
        if (arg.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            final Collator collator = getOptionalCollator(contextSequence, contextItem);
            result = findMax(arg, collator);
        }

        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);}

        return result;
    }

    /**
     * Get collator, handling empty sequence for XQ4 optional collation parameter.
     */
    private Collator getOptionalCollator(Sequence contextSequence, Item contextItem)
            throws XPathException {
        if (getArgumentCount() == 2) {
            final Sequence collationSeq = getArgument(1).eval(contextSequence, contextItem);
            if (!collationSeq.isEmpty()) {
                final String collationURI = collationSeq.getStringValue();
                return context.getCollator(collationURI, ErrorCodes.FOCH0002);
            }
        }
        return context.getDefaultCollator();
    }

    private Sequence findMax(Sequence arg, Collator collator) throws XPathException {
        final SequenceIterator iter = arg.unorderedIterator();
        AtomicValue max = null;
        boolean hasNaN = false;
        AtomicValue nanValue = null;

        while (iter.hasNext()) {
            final Item item = iter.nextItem();
            AtomicValue value = item.atomize();

            // Cast untypedAtomic to double
            if (value.getType() == Type.UNTYPED_ATOMIC) {
                value = value.convertTo(Type.DOUBLE);
            }

            // Wrap duration subtypes
            if (Type.subTypeOf(value.getType(), Type.DURATION)) {
                value = ((DurationValue) value).wrap();
            }

            // Track NaN: if any value is NaN, result is NaN
            if (value instanceof NumericValue && ((NumericValue) value).isNaN()) {
                if (!hasNaN) {
                    hasNaN = true;
                    nanValue = value;
                }
                continue;
            }

            if (max == null) {
                max = value;
            } else {
                try {
                    final int cmp = FunCompare.compare(value, max, collator);
                    if (cmp > 0) {
                        max = value;
                    }
                } catch (final XPathException e) {
                    throw new XPathException(this, ErrorCodes.FORG0006,
                        "Cannot compare " + Type.getTypeName(max.getType()) +
                        " and " + Type.getTypeName(value.getType()), value);
                }
            }
        }

        if (hasNaN) {
            return nanValue;
        }
        return max;
    }
}
