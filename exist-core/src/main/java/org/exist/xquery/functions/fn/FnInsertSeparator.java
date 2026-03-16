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
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements fn:insert-separator (XQuery 4.0).
 *
 * Inserts a separator between adjacent items in a sequence.
 */
public class FnInsertSeparator extends BasicFunction {

    public static final FunctionSignature FN_INSERT_SEPARATOR = new FunctionSignature(
            new QName("insert-separator", Function.BUILTIN_FUNCTION_NS),
            "Inserts a separator between adjacent items in a sequence.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                    new FunctionParameterSequenceType("separator", Type.ITEM, Cardinality.ZERO_OR_MORE, "The separator to insert")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the sequence with separators inserted"));

    public FnInsertSeparator(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence input = args[0];
        final Sequence separator = args[1];
        final int inputSize = input.getItemCount();
        if (inputSize <= 1 || separator.isEmpty()) {
            return input;
        }
        final ValueSequence result = new ValueSequence(inputSize + (inputSize - 1) * separator.getItemCount());
        result.add(input.itemAt(0));
        for (int i = 1; i < inputSize; i++) {
            result.addAll(separator);
            result.add(input.itemAt(i));
        }
        return result;
    }
}
