/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.memtree;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.exist.EXistException;
import org.exist.dom.*;
import org.exist.dom.CommentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.ProcessingInstructionImpl;
import org.exist.dom.TextImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.Txn;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;

/**
 * Helper class to make a in-memory document fragment persistent.
 * The class directly accesses the in-memory document structure and writes
 * it into a temporary doc on the database. This is much faster than first serializing the
 * document tree to SAX and passing it to 
 * {@link org.exist.collections.Collection#store(org.exist.storage.txn.Txn, org.exist.storage.DBBroker, org.exist.collections.IndexInfo, org.xml.sax.InputSource, boolean)}.
 * 
 * As the in-memory document fragment may not be a well-formed XML doc (having more
 * than one root element), a wrapper element is put around the content nodes.
 * 
 * @author wolf
 */
public class DOMIndexer {

	public final static QName ROOT_QNAME = new QName("temp", Serializer.EXIST_NS, "exist");
	
    private DBBroker broker;
    private Txn transaction;
    private DocumentImpl doc;
    private org.exist.dom.DocumentImpl targetDoc;
    private Stack stack = new Stack();
    private TextImpl text = new TextImpl();
    private StoredNode prevNode = null;
    
    public DOMIndexer(DBBroker broker, Txn transaction, DocumentImpl doc, org.exist.dom.DocumentImpl targetDoc) {
        this.broker = broker;
        this.transaction = transaction;
        this.doc = doc;
        this.targetDoc = targetDoc;
    }
    
    /**
     * Scan the DOM tree once to determine its structure.
     * 
     * @throws EXistException
     */
    public void scan() throws EXistException {
        //Creates a dummy DOCTYPE
        final DocumentTypeImpl dt = new DocumentTypeImpl("temp", null, "");
        targetDoc.setDocumentType(dt);        
        targetDoc.setTreeLevelOrder(1, doc.getChildCount());
        for (int i = 1; i < doc.size; i++) {
            if (doc.treeLevel[i] + 1 > targetDoc.getMaxDepth())
                targetDoc.setMaxDepth(doc.treeLevel[i] + 1);
            if (doc.nodeKind[i] == Node.ELEMENT_NODE) {
                int length = doc.getChildCountFor(i) + doc.getAttributesCountFor(i);
                if (length > targetDoc.getTreeLevelOrder(doc.treeLevel[i] + 1))
                    targetDoc.setTreeLevelOrder(doc.treeLevel[i] + 1, length);
            }
        }
        // increase computed max depth by one
        targetDoc.setMaxDepth(targetDoc.getMaxDepth() + 1);
        targetDoc.calculateTreeLevelStartPoints(true);
    }
    
    /**
     * Store the nodes.
     *
     */
    public void store() {
    	// create a wrapper element as root node
    	ElementImpl elem = new ElementImpl(1, ROOT_QNAME);
        elem.setNodeId(broker.getBrokerPool().getNodeFactory().createInstance());
    	elem.setOwnerDocument(targetDoc);
        elem.setChildCount(doc.getChildCount());
        elem.addNamespaceMapping("exist", Serializer.EXIST_NS);
        NodePath path = new NodePath();
        path.addComponent(ROOT_QNAME);
        
        stack.push(elem);
        System.out.println("ID: " + elem.getNodeId());
        broker.storeNode(transaction, elem, path);
        targetDoc.appendChild(elem);
        elem.setChildCount(0);
        
        // store the document nodes
        int top = doc.size > 1 ? 1 : -1;
        while(top > 0) {
            store(top, path);
            top = doc.getNextSiblingFor(top);
        }
        
        // close the wrapper element
        stack.pop();
        broker.endElement(elem, path, null);
        path.removeLastComponent();
    }
    
    private void store(int top, NodePath currentPath) {
        int nodeNr = top;
        while (nodeNr > 0) {
            startNode(nodeNr, currentPath);
            int nextNode = doc.getFirstChildFor(nodeNr);
            while (nextNode == -1) {
                endNode(nodeNr, currentPath);
                if (top == nodeNr)
                    break;
                nextNode = doc.getNextSiblingFor(nodeNr);
                if (nextNode == -1) {
                    nodeNr = doc.getParentNodeFor(nodeNr);
                    if (nodeNr == -1 || top == nodeNr) {
                        endNode(nodeNr, currentPath);
                        nextNode = -1;
                        break;
                    }
                }
            }
            nodeNr = nextNode;
        }
    }

    /**
     * @param nodeNr
     */
    private void startNode(int nodeNr, NodePath currentPath) {
        if (doc.nodeKind[nodeNr] == Node.ELEMENT_NODE) {
            ElementImpl elem = new ElementImpl(1);
            if(stack.empty()) {
                elem.setNodeId(broker.getBrokerPool().getNodeFactory().createInstance());
                initElement(nodeNr, elem);
                stack.push(elem);
                broker.storeNode(transaction, elem, currentPath);
                targetDoc.appendChild(elem);
                elem.setChildCount(0);
            } else {
                ElementImpl last = (ElementImpl) stack.peek();
                initElement(nodeNr, elem);
                last.appendChildInternal(prevNode, elem);
                stack.push(elem);
                broker.storeNode(transaction, elem, currentPath);
                elem.setChildCount(0);
            }
            prevNode = null;
            currentPath.addComponent(elem.getQName());
            storeAttributes(nodeNr, elem, currentPath);
        } else if (doc.nodeKind[nodeNr] == Node.TEXT_NODE) {
            ElementImpl last = (ElementImpl) stack.peek();
            text.setData(new String(doc.characters, doc.alpha[nodeNr], doc.alphaLen[nodeNr]));
            text.setOwnerDocument(targetDoc);
            last.appendChildInternal(prevNode, text);
            prevNode = text;
            broker.storeNode(transaction, text, null);
            text.clear();
        } else if (doc.nodeKind[nodeNr] == Node.COMMENT_NODE) {
            CommentImpl comment = new CommentImpl(new String(doc.characters, doc.alpha[nodeNr], 
                    doc.alphaLen[nodeNr]));
            comment.setOwnerDocument(targetDoc);
            if (stack.empty()) {
                comment.setGID(1);
                broker.storeNode(transaction, comment, null);
                targetDoc.appendChild(comment);
            } else {
                ElementImpl last = (ElementImpl) stack.peek();
                last.appendChildInternal(prevNode, comment);
                prevNode = comment;
                broker.storeNode(transaction, comment, null);
            }
        } else if (doc.nodeKind[nodeNr] == Node.PROCESSING_INSTRUCTION_NODE) {
            ProcessingInstructionImpl pi = new ProcessingInstructionImpl();
            pi.setOwnerDocument(targetDoc);
            QName qn = (QName)doc.namePool.get(doc.nodeName[nodeNr]);
            pi.setTarget(qn.getLocalName());
            pi.setData(new String(doc.characters, doc.alpha[nodeNr], doc.alphaLen[nodeNr]));
            if (stack.empty()) {
                pi.setGID(1);
                broker.storeNode(transaction, pi, null);
                targetDoc.appendChild(pi);
            } else {
                ElementImpl last = (ElementImpl) stack.peek();
                last.appendChildInternal(prevNode, pi);
                prevNode = pi;
                broker.storeNode(transaction, pi, null);
            }
        }
    }

    /**
     * @param nodeNr
     * @param elem
     */
    private void initElement(int nodeNr, ElementImpl elem) {
        short attribs = (short) doc.getAttributesCountFor(nodeNr);
        elem.setOwnerDocument(targetDoc);
        elem.setAttributes(attribs);
        elem.setChildCount(doc.getChildCountFor(nodeNr) + attribs);
        elem.setNodeName((QName) doc.namePool.get(doc.nodeName[nodeNr]));
        Map ns = getNamespaces(nodeNr);
        if (ns != null)
            elem.setNamespaceMappings(ns);
    }

    private Map getNamespaces(int nodeNr) {
        int ns = doc.alphaLen[nodeNr];
        if (ns < 0)
            return null;
        Map map = new HashMap();
        while (ns < doc.nextNamespace && doc.namespaceParent[ns] == nodeNr) {
            QName qn = (QName)doc.namePool.get(doc.namespaceCode[ns]);
            if ("xmlns".equals(qn.getLocalName()))
                map.put("", qn.getNamespaceURI());
            else
                map.put(qn.getLocalName(), qn.getNamespaceURI());
            ++ns;
        }
        return map;
    }
    
    /**
     * @param nodeNr
     * @param elem
     * @throws DOMException
     */
    private void storeAttributes(int nodeNr, ElementImpl elem, NodePath path) throws DOMException {
        int attr = doc.alpha[nodeNr];
        if(-1 < attr) {
            while (attr < doc.nextAttr && doc.attrParent[attr] == nodeNr) {
                QName qn = (QName)doc.namePool.get(doc.attrName[attr]);
                AttrImpl attrib = new AttrImpl(qn, doc.attrValue[attr]);
                attrib.setOwnerDocument(targetDoc);
                elem.appendChildInternal(prevNode, attrib);
                prevNode = attrib;
                broker.storeNode(transaction, attrib, path);
                ++attr;
            }
        }
    }
    
    /**
     * @param nodeNr
     */
    private void endNode(int nodeNr, NodePath currentPath) {
        if (doc.nodeKind[nodeNr] == Node.ELEMENT_NODE) {
            ElementImpl last = (ElementImpl) stack.pop();
            broker.endElement(last, currentPath, null);
            currentPath.removeLastComponent();
            prevNode = last;
        }
    }
}
