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
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements fn:duplicate-values (XQuery 4.0).
 *
 * Returns the values that appear more than once in the input sequence.
 */
public class FnDuplicateValues extends BasicFunction {

    public static final FunctionSignature[] FN_DUPLICATE_VALUES = {
            new FunctionSignature(
                    new QName("duplicate-values", Function.BUILTIN_FUNCTION_NS),
                    "Returns those values that appear more than once in the input sequence.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("values", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The input values")
                    },
                    new FunctionReturnSequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "the duplicate values")),
            new FunctionSignature(
                    new QName("duplicate-values", Function.BUILTIN_FUNCTION_NS),
                    "Returns those values that appear more than once in the input sequence, using the specified collation.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("values", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The input values"),
                            new FunctionParameterSequenceType("collation", Type.STRING, Cardinality.ZERO_OR_ONE, "The collation URI")
                    },
                    new FunctionReturnSequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "the duplicate values"))
    };

    public FnDuplicateValues(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence values = args[0];
        if (values.getItemCount() <= 1) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Collator collator = getCollator(args);

        // Use contextual equality (fn:compare = 0) per XQ4 spec
        final java.util.List<AtomicValue> seen = new java.util.ArrayList<>();
        final java.util.List<AtomicValue> reported = new java.util.ArrayList<>();
        final ValueSequence result = new ValueSequence();

        for (final SequenceIterator i = values.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            final AtomicValue value = item.atomize();

            boolean isDuplicate = false;
            for (final AtomicValue prev : seen) {
                if (FnAllEqualDifferent.contextuallyEqual(prev, value, collator)) {
                    isDuplicate = true;
                    break;
                }
            }

            if (isDuplicate) {
                // Check if we already reported this value
                boolean alreadyReported = false;
                for (final AtomicValue rep : reported) {
                    if (FnAllEqualDifferent.contextuallyEqual(rep, value, collator)) {
                        alreadyReported = true;
                        break;
                    }
                }
                if (!alreadyReported) {
                    result.add(value);
                    reported.add(value);
                }
            } else {
                seen.add(value);
            }
        }
        return result;
    }

    private Collator getCollator(final Sequence[] args) throws XPathException {
        if (args.length > 1 && !args[1].isEmpty()) {
            final String collationURI = args[1].getStringValue();
            return context.getCollator(collationURI, ErrorCodes.FOCH0002);
        }
        return context.getDefaultCollator();
    }

}
