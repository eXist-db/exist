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
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Implements XQuery 4.0 fn:siblings.
 *
 * Returns the node together with its siblings in document order.
 * If the node has no parent (or is an attribute/namespace), returns just the node itself.
 */
public class FnSiblings extends BasicFunction {

    public static final FunctionSignature[] FN_SIBLINGS = {
            new FunctionSignature(
                    new QName("siblings", Function.BUILTIN_FUNCTION_NS),
                    "Returns the supplied node together with its siblings in document order.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("node", Type.NODE, Cardinality.ZERO_OR_ONE, "The node whose siblings to return")
                    },
                    new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the node and its siblings in document order")),
            new FunctionSignature(
                    new QName("siblings", Function.BUILTIN_FUNCTION_NS),
                    "Returns the context node together with its siblings in document order.",
                    new SequenceType[0],
                    new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the context node and its siblings in document order"))
    };

    public FnSiblings(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence input;
        if (args.length == 0) {
            // 0-arity: use context item
            if (contextSequence == null || contextSequence.isEmpty()) {
                throw new XPathException(this, ErrorCodes.XPDY0002,
                        "fn:siblings() called with no context item");
            }
            input = contextSequence;
        } else {
            input = args[0];
        }

        if (input.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Item nodeItem = input.itemAt(0);
        final int nodeType = nodeItem.getType();

        // Attribute and namespace nodes: return just the node itself
        if (nodeType == Type.ATTRIBUTE || nodeType == Type.NAMESPACE) {
            return nodeItem.toSequence();
        }

        final Node node = (Node) nodeItem;
        final Node parent = node.getParentNode();

        // No parent: return just the node
        if (parent == null) {
            return nodeItem.toSequence();
        }

        // Return all children of the parent (which includes all siblings + the node itself)
        final NodeList children = parent.getChildNodes();
        final ValueSequence result = new ValueSequence(children.getLength());
        for (int i = 0; i < children.getLength(); i++) {
            result.add((Item) children.item(i));
        }
        return result;
    }
}
