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
 * Implements XQuery 4.0 fn:distinct-ordered-nodes.
 *
 * Returns nodes in document order with duplicates removed, equivalent to
 * the "/" operator's node deduplication behavior.
 */
public class FnDistinctOrderedNodes extends BasicFunction {

    public static final FunctionSignature FN_DISTINCT_ORDERED_NODES = new FunctionSignature(
            new QName("distinct-ordered-nodes", Function.BUILTIN_FUNCTION_NS),
            "Returns nodes in document order with duplicates removed.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE, "The nodes to deduplicate and order")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the deduplicated nodes in document order"));

    public FnDistinctOrderedNodes(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence nodes = args[0];
        if (nodes.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // ValueSequence with noDups=true handles both document ordering and deduplication
        final ValueSequence result = new ValueSequence(true);
        result.addAll(nodes);
        result.removeDuplicates();
        return result;
    }
}
