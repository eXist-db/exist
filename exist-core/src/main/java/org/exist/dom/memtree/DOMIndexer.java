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
package org.exist.dom.memtree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.collections.CollectionConfiguration;
import org.exist.dom.QName;
import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.CDATASectionImpl;
import org.exist.dom.persistent.CommentImpl;
import org.exist.dom.persistent.DocumentTypeImpl;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.ProcessingInstructionImpl;
import org.exist.dom.persistent.StoredNode;
import org.exist.dom.persistent.TextImpl;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;
import org.exist.util.pool.NodePool;
import org.exist.xquery.Expression;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to make a in-memory document fragment persistent. The class
 * directly accesses the in-memory document structure and writes it into a
 * temporary doc on the database. This is much faster than first serializing
 * the document tree to SAX and passing it to {@link org.exist.collections.Collection#store(org.exist.storage.txn.Txn, org.exist.storage.DBBroker, org.exist.collections.IndexInfo, org.xml.sax.InputSource)}.
 *
 * As the in-memory document fragment may not be a well-formed XML doc (having more than one root element), a wrapper element is put around the
 * content nodes.
 *
 * @author wolf
 */
public class DOMIndexer {

    private static final Logger LOG = LogManager.getLogger(DOMIndexer.class);
    private static final int NO_NODE = -1;
    private static final int FIRST_CHILD_NODE = 1;
    private static final QName ROOT_QNAME = new QName("temp", Namespaces.EXIST_NS, Namespaces.EXIST_NS_PREFIX);

    private final DBBroker broker;
    private final Txn transaction;
    private final DocumentImpl doc;
    private final org.exist.dom.persistent.DocumentImpl targetDoc;
    private final IndexSpec indexSpec;

    private final Deque<ElementImpl> stack = new ArrayDeque<>();
    private StoredNode prevNode = null;

    private final TextImpl text = new TextImpl((Expression) null);
    private final CommentImpl comment = new CommentImpl((Expression) null);
    private final ProcessingInstructionImpl pi = new ProcessingInstructionImpl(null);

    /**
     * Constructs a new DOMIndexer.
     *
     * @param broker      the database broker used for storage operations
     * @param transaction the current transaction
     * @param doc         the in-memory source document to be persisted
     * @param targetDoc   the persistent target document to store nodes into
     */
    public DOMIndexer(final DBBroker broker, final Txn transaction, final DocumentImpl doc,
                      final org.exist.dom.persistent.DocumentImpl targetDoc) {
        this.broker = broker;
        this.transaction = transaction;
        this.doc = doc;
        this.targetDoc = targetDoc;
        final CollectionConfiguration config = targetDoc.getCollection().getConfiguration(broker);
        if(config != null) {
            this.indexSpec = config.getIndexConfiguration();
        } else {
            this.indexSpec = null;
        }
    }

    /**
     * Scans the DOM tree once to determine its structure and sets up the target document type.
     *
     * @throws EXistException if an error occurs during scanning
     */
    public void scan() throws EXistException {
        // Creates a dummy DOCTYPE for the temporary persistent wrapper document.
        final Expression expression = doc == null ? null : doc.getExpression();
        final DocumentTypeImpl dt = new DocumentTypeImpl(expression, "temp", null, "");
        targetDoc.setDocumentType(dt);
    }

    /**
     * Stores all nodes from the in-memory document into the persistent target document,
     * wrapping them in a temporary root element.
     */
    public void store() {
        //Create a wrapper element as root node
        final ElementImpl elem = new ElementImpl(null, ROOT_QNAME, broker.getBrokerPool().getSymbols());
        elem.setNodeId(broker.getBrokerPool().getNodeFactory().createInstance());
        elem.setOwnerDocument(targetDoc);
        elem.setChildCount(doc.getChildCount());
        elem.addNamespaceMapping(Namespaces.EXIST_NS_PREFIX, Namespaces.EXIST_NS);
        final NodePath path = new NodePath();
        path.addComponent(ROOT_QNAME);
        stack.push(elem);
        broker.storeNode(transaction, elem, path, indexSpec);
        targetDoc.appendChild((NodeHandle) elem);
        elem.setChildCount(0);
        // Store the source document nodes beneath the wrapper.
        int rootNodeNr = doc.size > FIRST_CHILD_NODE ? FIRST_CHILD_NODE : NO_NODE;
        while(rootNodeNr > 0) {
            storeSubtree(rootNodeNr, path);
            rootNodeNr = doc.getNextSiblingFor(rootNodeNr);
        }
        // Close the wrapper element.
        stack.pop();
        broker.endElement(elem, path, null);
        path.removeLastComponent();
    }

    /**
     * Stores a subtree rooted at {@code rootNodeNr} using depth-first traversal.
     *
     * @param rootNodeNr  the node number of the subtree root in the in-memory document
     * @param currentPath the current node path, updated as the traversal descends and ascends
     */
    private void storeSubtree(final int rootNodeNr, final NodePath currentPath) {
        int currentNodeNr = rootNodeNr;

        while(currentNodeNr > 0) {
            startNode(currentNodeNr, currentPath);
            int nextNodeNr = doc.getFirstChildFor(currentNodeNr);

            while(nextNodeNr == NO_NODE) {
                endNode(currentNodeNr, currentPath);

                if(rootNodeNr == currentNodeNr) {
                    break;
                }
                nextNodeNr = doc.getNextSiblingFor(currentNodeNr);

                if(nextNodeNr == NO_NODE) {
                    currentNodeNr = doc.getParentNodeFor(currentNodeNr);

                    if((currentNodeNr == NO_NODE) || (rootNodeNr == currentNodeNr)) {
                        endNode(currentNodeNr, currentPath);
                        nextNodeNr = NO_NODE;
                        break;
                    }
                }
            }
            currentNodeNr = nextNodeNr;
        }
    }

    /**
     * Handles storing a node when first encountered during traversal.
     *
     * @param nodeNr      the index of the in-memory node to store
     * @param currentPath the current node path, updated when descending into element nodes
     */
    private void startNode(final int nodeNr, final NodePath currentPath) {
        switch(doc.nodeKind[nodeNr]) {

            case Node.ELEMENT_NODE: {
                final ElementImpl elem = (ElementImpl) NodePool.getInstance().borrowNode(Node.ELEMENT_NODE);
                if(stack.isEmpty()) {
                    elem.setNodeId(broker.getBrokerPool().getNodeFactory().createInstance());
                    initElement(nodeNr, elem);
                    stack.push(elem);
                    broker.storeNode(transaction, elem, currentPath, indexSpec);
                    targetDoc.appendChild((NodeHandle) elem);
                    elem.setChildCount(0);
                } else {
                    final ElementImpl last = stack.peek();
                    initElement(nodeNr, elem);
                    last.appendChildInternal(prevNode, elem);
                    stack.push(elem);
                    broker.storeNode(transaction, elem, currentPath, indexSpec);
                    elem.setChildCount(0);
                }
                setPrevious(null);
                currentPath.addComponent(elem.getQName());
                storeAttributes(nodeNr, elem, currentPath);
                break;
            }

            case Node.TEXT_NODE: {
                if((prevNode != null) && ((prevNode.getNodeType() == Node.TEXT_NODE) || (prevNode.getNodeType() == Node.CDATA_SECTION_NODE))) {
                    break;
                }
                final ElementImpl last = stack.peek();
                text.setData(new String(doc.characters, doc.alpha[nodeNr], doc.alphaLen[nodeNr]));
                text.setOwnerDocument(targetDoc);
                last.appendChildInternal(prevNode, text);
                setPrevious(text);
                broker.storeNode(transaction, text, null, indexSpec);
                break;
            }

            case Node.CDATA_SECTION_NODE: {
                final ElementImpl last = stack.peek();
                final CDATASectionImpl cdata = (CDATASectionImpl) NodePool.getInstance().borrowNode(Node.CDATA_SECTION_NODE);
                cdata.setData(doc.characters, doc.alpha[nodeNr], doc.alphaLen[nodeNr]);
                cdata.setOwnerDocument(targetDoc);
                last.appendChildInternal(prevNode, cdata);
                setPrevious(cdata);
                broker.storeNode(transaction, cdata, null, indexSpec);
                break;
            }

            case Node.COMMENT_NODE: {
                comment.setData(doc.characters, doc.alpha[nodeNr], doc.alphaLen[nodeNr]);
                comment.setOwnerDocument(targetDoc);
                if(stack.isEmpty()) {
                    comment.setNodeId(NodeId.DOCUMENT_NODE);
                    targetDoc.appendChild((NodeHandle) comment);
                    broker.storeNode(transaction, comment, null, indexSpec);
                } else {
                    final ElementImpl last = stack.peek();
                    last.appendChildInternal(prevNode, comment);
                    broker.storeNode(transaction, comment, null, indexSpec);
                    setPrevious(comment);
                }
                break;
            }

            case Node.PROCESSING_INSTRUCTION_NODE: {
                final QName qn = doc.nodeName[nodeNr];
                pi.setTarget(qn.getLocalPart());
                pi.setData(new String(doc.characters, doc.alpha[nodeNr], doc.alphaLen[nodeNr]));
                pi.setOwnerDocument(targetDoc);
                if(stack.isEmpty()) {
                    pi.setNodeId(NodeId.DOCUMENT_NODE);
                    targetDoc.appendChild((NodeHandle) pi);
                } else {
                    final ElementImpl last = stack.peek();
                    last.appendChildInternal(prevNode, pi);
                    setPrevious(pi);
                }
                broker.storeNode(transaction, pi, null, indexSpec);
                break;
            }

            default: {
                LOG.debug("Skipped indexing of in-memory node of type {}", doc.nodeKind[nodeNr]);
            }
        }
    }

    /**
     * Initializes a persistent element from the in-memory node metadata.
     *
     * @param nodeNr the index of the in-memory element node
     * @param elem   the persistent element to initialize
     */
    private void initElement(final int nodeNr, final ElementImpl elem) {
        final short attribs = (short) doc.getAttributesCountFor(nodeNr);
        elem.setOwnerDocument(targetDoc);
        elem.setAttributes(attribs);
        elem.setChildCount(doc.getChildCountFor(nodeNr) + attribs);
        elem.setNodeName(doc.nodeName[nodeNr], broker.getBrokerPool().getSymbols());
        final Map<String, String> ns = getNamespaces(nodeNr);
        if(ns != null) {
            elem.setNamespaceMappings(ns);
        }
    }

    /**
     * Collects namespace declarations associated with the given in-memory element node.
     *
     * @param nodeNr the index of the in-memory element node
     * @return a map of namespace prefix to namespace URI, or {@code null} if the node has no namespace declarations
     */
    private Map<String, String> getNamespaces(final int nodeNr) {
        int ns = doc.alphaLen[nodeNr];

        if(ns < 0) {
            return null;
        }

        final Map<String, String> map = new HashMap<>();

        while((ns < doc.nextNamespace) && (doc.namespaceParent[ns] == nodeNr)) {
            final QName qn = doc.namespaceCode[ns];

            if(XMLConstants.XMLNS_ATTRIBUTE.equals(qn.getLocalPart())) {
                map.put(XMLConstants.DEFAULT_NS_PREFIX, qn.getNamespaceURI());
            } else {
                map.put(qn.getLocalPart(), qn.getNamespaceURI());
            }
            ++ns;
        }

        return map;
    }

    /**
     * Stores all attributes belonging to the given in-memory element node.
     *
     * @param nodeNr the index of the in-memory element node whose attributes are to be stored
     * @param elem   the persistent element to which the attributes are appended
     * @param path   the current node path of the element
     * @throws DOMException if an error occurs while appending an attribute to the element
     */
    private void storeAttributes(final int nodeNr, final ElementImpl elem, final NodePath path) throws DOMException {
        int attr = doc.alpha[nodeNr];
        if(attr > -1) {
            while((attr < doc.nextAttr) && (doc.attrParent[attr] == nodeNr)) {
                final QName qn = doc.attrName[attr];
                final AttrImpl attrib = (AttrImpl) NodePool.getInstance().borrowNode(Node.ATTRIBUTE_NODE);
                attrib.setNodeName(qn, broker.getBrokerPool().getSymbols());
                attrib.setValue(doc.attrValue[attr]);
                attrib.setOwnerDocument(targetDoc);
                elem.appendChildInternal(prevNode, attrib);
                setPrevious(attrib);
                broker.storeNode(transaction, attrib, path, indexSpec);
                ++attr;
            }
        }
    }

    /**
     * Handles closing logic for a node when traversal moves back up.
     *
     * @param nodeNr      the index of the in-memory node being closed
     * @param currentPath the current node path, updated when closing element nodes
     */
    private void endNode(final int nodeNr, final NodePath currentPath) {
        if(doc.nodeKind[nodeNr] == Node.ELEMENT_NODE) {
            final ElementImpl last = stack.pop();
            broker.endElement(last, currentPath, null);
            currentPath.removeLastComponent();
            setPrevious(last);
        }
    }

    /**
     * Updates the reference to the previously stored node, releasing reusable inline nodes when appropriate.
     *
     * @param previous the node that was most recently stored, or {@code null} if there is no previous node
     */
    private void setPrevious(final StoredNode previous) {
        if(prevNode != null && isReusableInlineNodeType(prevNode.getNodeType())) {
            if(previous == null || prevNode.getNodeType() != previous.getNodeType()) {
                prevNode.clear();
            }
        }
        prevNode = previous;
    }

    /**
     * Returns whether the given node type is a reusable inline node type that can be cleared and reused.
     *
     * @param nodeType the DOM node type constant
     * @return {@code true} if the node type is text, comment, or processing instruction; {@code false} otherwise
     */
    private boolean isReusableInlineNodeType(final short nodeType) {
        return nodeType == Node.TEXT_NODE
                || nodeType == Node.COMMENT_NODE
                || nodeType == Node.PROCESSING_INSTRUCTION_NODE;
    }
}
