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

import java.util.ArrayList;
import java.util.List;

/**
 * array:build($seq, $fn?) — Build array from sequence with optional mapping function.
 */
public class ArrayBuild extends BasicFunction {

    private AnalyzeContextInfo cachedContextInfo;

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("build", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Builds an array from the items of a sequence.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "The resulting array")),
            new FunctionSignature(
                    new QName("build", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Builds an array by applying a function to each item of a sequence.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("action", Type.FUNCTION, Cardinality.ZERO_OR_ONE, "The function to apply")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "The resulting array"))
    };

    public ArrayBuild(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        super.analyze(contextInfo);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence input = args[0];
        final List<Sequence> members = new ArrayList<>();

        if (getArgumentCount() == 2 && !args[1].isEmpty()) {
            try (final FunctionReference fn = (FunctionReference) args[1].itemAt(0)) {
                fn.analyze(cachedContextInfo);
                for (final SequenceIterator i = input.iterate(); i.hasNext(); ) {
                    final Item item = i.nextItem();
                    members.add(fn.evalFunction(null, null, new Sequence[]{item.toSequence()}));
                }
            }
        } else {
            for (final SequenceIterator i = input.iterate(); i.hasNext(); ) {
                members.add(i.nextItem().toSequence());
            }
        }

        return new ArrayType(context, members);
    }
}
