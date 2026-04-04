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

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements fn:items-at (XQuery 4.0).
 *
 * Returns items from the input at the positions specified by the second argument.
 */
public class FnItemsAt extends BasicFunction {

    public static final FunctionSignature FN_ITEMS_AT = new FunctionSignature(
            new QName("items-at", Function.BUILTIN_FUNCTION_NS),
            "Returns the items at the specified positions in the input sequence.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                    new FunctionParameterSequenceType("at", Type.INTEGER, Cardinality.ZERO_OR_MORE, "The positions to select")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "items at the specified positions"));

    public FnItemsAt(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence input = args[0];
        final Sequence at = args[1];
        if (input.isEmpty() || at.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final int inputSize = input.getItemCount();
        final ValueSequence result = new ValueSequence();
        for (final SequenceIterator i = at.iterate(); i.hasNext(); ) {
            final Item posItem = i.nextItem();
            final int pos = (int) ((IntegerValue) posItem).getLong();
            if (pos >= 1 && pos <= inputSize) {
                result.add(input.itemAt(pos - 1));
            }
        }
        return result;
    }
}
