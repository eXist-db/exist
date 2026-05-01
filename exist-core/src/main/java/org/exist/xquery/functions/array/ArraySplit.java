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
package org.exist.xquery.functions.array;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.Collections;

/**
 * array:split($array) — Split array into sequence of single-member arrays.
 */
public class ArraySplit extends BasicFunction {

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("split", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Splits an array into a sequence of single-member arrays.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "The array to split")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.ZERO_OR_MORE, "A sequence of single-member arrays"))
    };

    public ArraySplit(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        final ValueSequence result = new ValueSequence(array.getSize());
        for (int i = 0; i < array.getSize(); i++) {
            result.add(new ArrayType(context, Collections.singletonList(array.get(i))));
        }
        return result;
    }
}
