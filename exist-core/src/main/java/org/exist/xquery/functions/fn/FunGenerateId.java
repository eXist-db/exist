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

import org.exist.xquery.*;
import org.exist.xquery.value.*;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.fn.FnModule.functionSignatures;

public class FunGenerateId extends BasicFunction {

    private static final String FN_GENERATE_ID = "generate-id";
    public static final FunctionSignature[] signatures = functionSignatures(
            FN_GENERATE_ID,
            "This function returns a string that uniquely identifies a given node. Without an argument, the node to identify is taken from the current context item.",
            returns(Type.STRING, "Unique identifier for the node"),
            arities(
                    arity(),
                    arity(
                            optParam("node", Type.NODE, "The node for which an identifier will be generated. If it is the empty sequence, the result will be the empty string")
                    )
            )
    );

    public FunGenerateId(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final NodeValue node;
        if (getArgumentCount() == 0) {
            if (contextSequence.isEmpty()) {
                throw new XPathException(this, ErrorCodes.XPDY0002, "No context item available in call to generate-id");
            }
            final Item contextItem = contextSequence.itemAt(0);
            if (!Type.subTypeOf(contextItem.getType(), Type.NODE)) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "Context item is not a node in call to generate-id");
            }
            node = (NodeValue) contextItem;

        } else {
            if (args[0].isEmpty()) {
                return StringValue.EMPTY_STRING;
            }
            node = (NodeValue) args[0].itemAt(0);
        }

        final String id = node.getNodeId().toString();
        return new StringValue("N" + id);
    }
}
