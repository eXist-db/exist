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

import org.exist.Namespaces;
import org.exist.dom.INode;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.*;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;

import static org.exist.xquery.FunctionDSL.functionSignature;

public class FunPath extends BasicFunction {

    private static final QName FN_PATH_NAME = new QName("path", Function.BUILTIN_FUNCTION_NS);
    private static final String FN_PATH_DESCRIPTION =
            "Returns a path expression that can be used to select the supplied node " +
                    "relative to the root of its containing document.";
    private static final FunctionReturnSequenceType FN_PATH_RETURN = new FunctionReturnSequenceType(
            Type.STRING, Cardinality.ZERO_OR_ONE, "The path expression, or any empty sequence");

    public static final FunctionSignature[] FS_PATH_SIGNATURES = {
            functionSignature(FunPath.FN_PATH_NAME, FunPath.FN_PATH_DESCRIPTION, FunPath.FN_PATH_RETURN),
            functionSignature(FunPath.FN_PATH_NAME, FunPath.FN_PATH_DESCRIPTION, FunPath.FN_PATH_RETURN,
                    new FunctionParameterSequenceType("node", Type.NODE, Cardinality.ZERO_OR_ONE, "The node for which to calculate a path expression"))
    };

    public FunPath(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence result;
        final Sequence sequence;

        // The behavior of the function if the argument is omitted is exactly
        // the same as if the context item (.) had been passed as the argument.
        if (getArgumentCount() == 0) {
            if (contextSequence != null) {
                sequence = contextSequence;
            } else {
                sequence = Sequence.EMPTY_SEQUENCE;
            }
        } else {
            sequence = args[0];
        }

        if (sequence.isEmpty()) {
            // If $arg is the empty sequence, the function returns the empty sequence.
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            final Item item = sequence.itemAt(0);
            if (item.getType() == Type.DOCUMENT) {
                // If $arg is a document node, the function returns the string "/".
                result = new StringValue(this, "/");
            } else if (Type.subTypeOf(item.getType(), Type.NODE)) {
                // For an element node, Q{uri}local[position], where uri is the
                // namespace URI of the node name or the empty string if the
                // node is in no namespace, local is the local part of the node
                // name, and position is an integer representing the position
                // of the selected node among its like-named siblings.
                final NodeValue nodeValue = (NodeValue) item;
                final Node node = nodeValue.getNode();
                final LinkedList<String> pathValues = new LinkedList<>();
                getPathValues(node, pathValues);
                if ((node.getOwnerDocument() == null ||
                     node.getOwnerDocument().getDocumentElement() == null ||
                     (node.getOwnerDocument() instanceof org.exist.dom.memtree.DocumentImpl &&
                      !((org.exist.dom.memtree.DocumentImpl) node.getOwnerDocument()).isExplicitlyCreated()))) {
                    // This string is prefixed by "Q{http://www.w3.org/2005/xpath-functions}root()"
                    // if the root node is not a document node.
                    pathValues.removeFirst();
                    result = new StringValue(this, String.format("Q{%s}root()", Namespaces.XPATH_FUNCTIONS_NS) + String.join("", pathValues));
                } else {
                    result = new StringValue(this, String.join("", pathValues));
                }
            } else {
                // If the context item is not a node, type error [err:XPTY0004].
                throw new XPathException(this, ErrorCodes.XPTY0004,  "Item is not a document or node; got '" + Type.getTypeName(item.getType()) + "'", sequence);
            }
        }
        return result;
    }

    /**
     * Gets the position of a specified node among its like-named siblings.
     *
     * @param   node  the node whose position to get
     * @return  the position of the specified node, or zero if this method
     *          failed to determine the position of the specified node
     */
    private static int getNodePosition(final Node node) {
        int position = 1;
        Node siblingNode = node.getPreviousSibling();
        while (siblingNode != null) {
            if (siblingNode.getNodeName().equals(node.getNodeName())) {
                ++position;
            }
            siblingNode = siblingNode.getPreviousSibling();
        }
        return position;
    }

    /**
     * Gets the path values of a specified node.
     *
     * @param   node    the node whose path values to get
     * @param   values  the path values
     */
    private static void getPathValues(final Node node, final List<String> values) {
        @Nullable Node parent = node.getParentNode();

        final StringBuilder value = new StringBuilder();

        switch (node.getNodeType()) {
            case Node.ATTRIBUTE_NODE:
                // For an attribute node, if the node is in no namespace,
                // @local, where local is the local part of the node name.
                // Otherwise, @Q{uri}local, where uri is the namespace URI of
                // the node name, and local is the local part of the node name.
                value.append('/');
                if (node.getNamespaceURI() != null) {
                    value.append(String.format("@Q{%s}", node.getNamespaceURI()));
                } else {
                    value.append('@');
                }
                value.append(node.getLocalName());

                // attributes have an owner element - not a parent node!
                parent = ((Attr) node).getOwnerElement();
                break;

            case Node.TEXT_NODE:
                // For a text node: text()[position] where position is an integer
                // representing the position of the selected node among its text
                // node siblings
                final int textNodePosition = getNodePosition(node);
                if (textNodePosition > 0) {
                    value.append(String.format("/text()[%d]", textNodePosition));
                }
                break;

            case Node.COMMENT_NODE:
                // For a comment node: comment()[position] where position is an
                // integer representing the position of the selected node among
                // its comment node siblings.
                final int commentNodePosition = getNodePosition(node);
                if (commentNodePosition > 0) {
                    value.append(String.format("/comment()[%d]", commentNodePosition));
                }
                break;

            case Node.PROCESSING_INSTRUCTION_NODE:
                // For a processing-instruction node: processing-instruction(local)[position]
                // where local is the name of the processing instruction node and position is
                // an integer representing the position of the selected node among its
                // like-named processing-instruction node siblings.
                int processingInstructionNodePosition = getNodePosition(node);
                if (processingInstructionNodePosition > 0) {
                    value.append(String.format("/processing-instruction(%s)[%d]", node.getNodeName(), processingInstructionNodePosition));
                }
                break;

            case INode.NAMESPACE_NODE:
                // For a namespace node: If the namespace node has a name: namespace::prefix,
                // where prefix is the local part of the name of the namespace node
                // (which represents the namespace prefix).  If the namespace node
                // has no name (that is, it represents the default namespace):
                // namespace::*[Q{http://www.w3.org/2005/xpath-functions}local-name()=""]
                if (node.getNamespaceURI() != null) {
                    value.append(String.format("namespace::{%s}", node.getLocalName()));
                } else {
                    value.append("namespace::*[Q{http://www.w3.org/2005/xpath-functions}local-name()=\"\"]");
                }
                break;

            default:
                if (node.getLocalName() != null) {
                    // For an element node, Q{uri}local[position], where uri is the
                    // namespace URI of the node name or the empty string if the
                    // node is in no namespace, local is the local part of the node
                    // name, and position is an integer representing the position
                    // of the selected node among its like-named siblings.
                    final int nodePosition = getNodePosition(node);
                    value.append((node.getOwnerDocument() != null && node.getOwnerDocument().getDocumentElement() != null) ? "/Q" : "Q");
                    value.append(((INode) node).getQName().toURIQualifiedName());
                    if (nodePosition > 0) {
                        value.append(String.format("[%d]", nodePosition));
                    }
                }
                break;
        }

        if (parent != null) {
            getPathValues(parent, values);
        }

        if (!value.toString().isEmpty()) {
            values.add(value.toString());
        }
    }
}
