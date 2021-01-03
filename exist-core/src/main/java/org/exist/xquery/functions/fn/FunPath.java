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

import org.exist.dom.memtree.DocumentImpl;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Objects;

import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.returnsOpt;
import static org.exist.xquery.functions.fn.FnModule.functionSignature;

/**
 * Implementation of fn:path
 *
 * @author Dannes Wessels
 */
public class FunPath extends Function {

    public static final String XPATH_FUNCTIONS_ROOT = "Q{http://www.w3.org/2005/xpath-functions}root()";
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

        // Fetch data
        final Item item = seq.itemAt(0);
        final NodeValue nodeValue = (NodeValue) item;
        Node node = nodeValue.getNode();

        // Quick escape for Document node
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            return new StringValue("/");
        }

        // Collect all steps
        ArrayDeque<String> steps = new ArrayDeque<>();

        // Flag used to find the root node.
        boolean isRootNode;

        try {
            do {
                Node parentNode = getParent(node);
                isRootNode = (parentNode == null);

                if (node.getNodeType() == Node.DOCUMENT_NODE) {
                    if(!isNodeCreatedAsInMemDocument(node)) {
                        // The last added element must be removed, as the spec
                        // does not want to show root element info for constructed
                        // elements, only for document {} constructed nodes.
                        steps.removeLast();
                        steps.add(XPATH_FUNCTIONS_ROOT);
                    }

                } else {
                    steps.add(nodeToXPath(node));
                }

                // follow parent nodes
                node = parentNode;

            } while (!isRootNode);

        } catch (XPathException ex) {
            throw new XPathException(this, ErrorCodes.ERROR, ex.getMessage());
        }

        // Wrap up results
        StringBuilder buf = new StringBuilder();
        for (Iterator<String> step = steps.descendingIterator(); step.hasNext(); ) {
            buf.append(step.next());
        }
        Sequence result = new StringValue(buf.toString());

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;
    }

    /**
     * Workaround for https://github.com/eXist-db/exist/issues/1463.
     * With this trick it is possible to check the scenario where a
     * DocumentNode was be created by document {} or erroneously
     * by an element without a document.
     *
     * @param node The document node
     * @return TRUE when the document is constructed via document {}.
     */
    private boolean isNodeCreatedAsInMemDocument(Node node) {
        if (node instanceof DocumentImpl) {
            DocumentImpl di = (DocumentImpl) node;
            return di.isExplicitlyCreated();
        }
        return false;
    }

    private String getNodeSibblingIndex(Node currentNode) {
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

        return "[" + count + "]";
    }

    private String nodeToXPath(final Node node) throws XPathException {

        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
                return getFullElementName(node) + getNodeSibblingIndex(node);

            case Node.ATTRIBUTE_NODE:
                return getFullAttributeName(node);

            case Node.TEXT_NODE:
                return "/text()" + getNodeSibblingIndex(node);

            case Node.COMMENT_NODE:
                return "/comment()" + getNodeSibblingIndex(node);

            case Node.PROCESSING_INSTRUCTION_NODE:
                final String target = ((ProcessingInstruction) node).getTarget();
                return "/processing-instruction(" + target + ")" + getNodeSibblingIndex(node);

            default:
                throw new XPathException(ErrorCodes.ERROR, "Unable to process node type " + node.getNodeType());
        }
    }

    private String getFullElementName(final Node node) {
        final String namespaceURI = node.getNamespaceURI();
        return namespaceURI == null
                ? "/Q{}" + node.getLocalName()
                : "/Q{" + namespaceURI + "}" + node.getLocalName();
    }

    private String getFullAttributeName(final Node node) {
        final String namespaceURI = node.getNamespaceURI();
        return namespaceURI == null
                ? "/@" + node.getLocalName()
                : "/@Q{" + namespaceURI + "}" + node.getLocalName();
    }

    private Node getParent(final Node node) {
        if (node == null) {
            return null;
        } else if (node instanceof Attr) {
            return ((Attr) node).getOwnerElement();
        } else {
            return node.getParentNode();
        }
    }
}
