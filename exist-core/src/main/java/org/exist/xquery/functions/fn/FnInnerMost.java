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
import org.exist.xquery.*;
import org.exist.xquery.value.*;


public class FnInnerMost extends BasicFunction {

    public final static FunctionSignature FNS_INNERMOST = new FunctionSignature(
            new QName("innermost", Function.BUILTIN_FUNCTION_NS),
            "Returns every node within the input sequence that is not an ancestor of another member of the input sequence; the nodes are returned in document order with duplicates eliminated.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE, "The nodes to test")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "The nodes that are not an ancestor of another node in the input sequence")
    );

    public FnInnerMost(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        return FnOuterMost.filterByDocumentAncestry(
                args[0],
                (currentNodeId, docNodeIds) -> docNodeIds.parallelStream().anyMatch(nodeId -> nodeId.isDescendantOf(currentNodeId))
        );
    }
}
