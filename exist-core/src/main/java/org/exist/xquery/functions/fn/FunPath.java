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

import org.apache.xerces.dom.DocumentImpl;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

import java.util.Objects;

import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.returnsOpt;
import static org.exist.xquery.functions.fn.FnModule.functionSignature;

/**
 * @author Dannes
 */
public class FunPath extends Function {

    private static final FunctionParameterSequenceType FS_PARAM_NODE = optParam("arg", Type.NODE, "The node.");

    private static final String FS_PATH = "path";
    private static final String FS_DESCRIPTION = "Returns a path expression that can be used to select the supplied node relative to the root of its containing document.";
    private static final String FS_RETURN_DESCRIPTION = "The path of the node";

    static final FunctionSignature FS_PATH_0 = functionSignature(
            FS_PATH,
            FS_DESCRIPTION,
            returnsOpt(Type.STRING, FS_RETURN_DESCRIPTION)
    );

    static final FunctionSignature FS_PATH_1 = functionSignature(
            FS_PATH,
            FS_DESCRIPTION,
            returnsOpt(Type.STRING, FS_RETURN_DESCRIPTION),
            FS_PARAM_NODE
    );

    public FunPath(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                    "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
            }
        }

        final boolean contextItemIsAbsent = (contextItem == null);
        final boolean argumentIsOmitted = (getSignature().getArgumentCount() == 0);

        // Error condition 1
        if (argumentIsOmitted && contextItemIsAbsent) {
            throw new XPathException(this, ErrorCodes.XPDY0002, "Context item is absent.");
        }

        // Get sequence from contextItem or from context Sequence
        final Sequence seq = (contextItem != null)
                ? contextItem.toSequence()
                : getArgument(0).eval(contextSequence, contextItem);

        // Error condition 2
        if (!contextItemIsAbsent && !Type.subTypeOf(seq.getItemType(), Type.NODE)) {
            throw new XPathException(this, ErrorCodes.XPTY0004, "Context item is not a node.");
        }

        // If $arg is the empty sequence, the function returns the empty sequence.
        if (seq.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Item item = seq.itemAt(0);
        final NodeValue nodeValue = (NodeValue) item;
        Node node = nodeValue.getNode();

        // Document node
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            return new StringValue("/");
        }

        final StringBuilder buf = new StringBuilder();

        try {
            // First hit
            if (node.getParentNode() instanceof org.w3c.dom.Document) {
                buf.append("Q{http://www.w3.org/2005/xpath-functions}root()");
            } else {
                buf.append(nodeToXPath(node));
            }

            // Iterate over tree
            while ((node = getParent(node)) != null) {
                if (node.getNodeType() == Node.ELEMENT_NODE) {

//                    if (node.getParentNode() instanceof org.w3c.dom.Document) {
//                        buf.insert(0, "Q{http://www.w3.org/2005/xpath-functions}root()");
//                    } else {
                        buf.insert(0, nodeToXPath(node));
//                    }
                }
            }
        } catch (XPathException ex) {
            throw new XPathException(this, ErrorCodes.ERROR, ex.getMessage());
        }

        Sequence result = new StringValue(buf.toString());

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }
        return result;
    }

    private StringBuilder nodeToXPath(Node node) throws XPathException {
        final StringBuilder xpath = new StringBuilder("/");
        getFullNodeName(node, xpath);
        return xpath;
    }

    private void appendNodeIndex(Node currentNode, StringBuilder xpath) {
        int count = 1;
        Node previousSibbling = currentNode;

        if (currentNode.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
            final String target = ((ProcessingInstruction) currentNode).getTarget();
            while ((previousSibbling = previousSibbling.getPreviousSibling()) != null) {
                if (target.equals(((ProcessingInstruction) previousSibbling).getTarget())) {
                    count++;
                }
            }

        } else {
            final String localNameN = currentNode.getLocalName();
            final String prefixN = currentNode.getPrefix();
            final String namespaceN = currentNode.getNamespaceURI();

            while ((previousSibbling = previousSibbling.getPreviousSibling()) != null) {
                final String localNameP = previousSibbling.getLocalName();
                final String prefixP = previousSibbling.getPrefix();

                if (Objects.equals(localNameN, localNameP)
                        && Objects.equals(prefixN, prefixP)
                        && Objects.equals(namespaceN, currentNode.getNamespaceURI())) {
                    count++;
                }
            }
        }

        xpath.append('[').append(count).append(']');

    }

    private void getFullNodeName(final Node node, final StringBuilder xpath) throws XPathException {

        switch (node.getNodeType()) {
            case Node.ATTRIBUTE_NODE:
                xpath.append(getFullAttributeName(node));
                break;
            case Node.TEXT_NODE:
                xpath.append("text()");
                appendNodeIndex(node, xpath);
                break;
            case Node.COMMENT_NODE:
                xpath.append("comment()");
                appendNodeIndex(node, xpath);
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                xpath.append("processing-instruction(").append(((ProcessingInstruction) node).getTarget()).append(")");
                appendNodeIndex(node, xpath);
                break;
            case Node.ELEMENT_NODE:
                xpath.append(getFullElementName(node));
                appendNodeIndex(node, xpath);
                break;
            default:
                throw new XPathException(ErrorCodes.ERROR, "Unable to process node type " + node.getNodeType());
        }
    }

    private String getFullElementName(final Node node) {

//        if(node.getParentNode() instanceof org.w3c.dom.Document){
//            return  "Q{http://www.w3.org/2005/xpath-functions}root()";
//        }

        final String namespaceURI = node.getNamespaceURI();
        return namespaceURI == null
                ? "Q{}" + node.getLocalName()
                : "Q{" + namespaceURI + "}" + node.getLocalName();
    }

    private String getFullAttributeName(final Node node) {
        final String namespaceURI = node.getNamespaceURI();
        return namespaceURI == null
                ? "@" + node.getLocalName()
                : "@Q{" + namespaceURI + "}" + node.getLocalName();
    }

    private Node getParent(final Node n) {
        if (n == null) {
            return null;
        } else if (n instanceof Attr) {
            return ((Attr) n).getOwnerElement();
        } else {
            return n.getParentNode();
        }
    }
}
