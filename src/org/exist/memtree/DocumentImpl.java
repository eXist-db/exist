/*
 * eXist Open Source Native XML Database Copyright (C) 2001-03 Wolfgang M. Meier
 * wolfgang@exist-db.org http://exist.sourceforge.net
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any
 * later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * $Id$
 */
package org.exist.memtree;

import java.util.Arrays;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.util.hashtable.NamePool;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * An in-memory implementation of Document.
 * 
 * This implementation stores all node data in the document object. Nodes from
 * another document, i.e. a persistent document in the database, can be stored
 * as reference nodes, i.e. the nodes are not copied into this document object.
 * Instead a reference is inserted which will only be expanded during
 * serialization.
 * 
 * @author wolf
 */
public class DocumentImpl extends NodeImpl implements Document {

    protected NamePool namePool = new NamePool();

    protected short[] nodeKind = null;

    protected short[] treeLevel;

    protected int[] next;

    protected int[] nodeName;

    protected int[] alpha;

    protected int[] alphaLen;

    protected char[] characters;

    protected int nextChar = 0;

    protected int[] attrName;

    protected int[] attrParent;

    protected String[] attrValue;

    protected int nextAttr = 0;

    protected int size = 1;

    protected int documentRootNode = -1;

    protected NodeProxy references[];

    protected int nextRef = 0;

    private final static int NODE_SIZE = 128;
    private final static int ATTR_SIZE = 64;
    private final static int CHAR_BUF_SIZE = 1024;
    private final static int REF_SIZE = 128;
    
    public DocumentImpl() {
        super(null, 0);
    }
    
    private void init() {
        nodeKind = new short[NODE_SIZE];
        treeLevel = new short[NODE_SIZE];
        next = new int[NODE_SIZE];
        Arrays.fill(next, -1);
        nodeName = new int[NODE_SIZE];
        alpha = new int[NODE_SIZE];
        alphaLen = new int[NODE_SIZE];

        characters = new char[CHAR_BUF_SIZE];

        attrName = new int[ATTR_SIZE];
        attrParent = new int[ATTR_SIZE];
        attrValue = new String[ATTR_SIZE];

        references = new NodeProxy[REF_SIZE];

        treeLevel[0] = 0;
        nodeKind[0] = Node.DOCUMENT_NODE;
        document = this;
    }

    public void reset() {
        size = 0;
        nextChar = 0;
        nextAttr = 0;
        nextRef = 0;
    }
    
    public int getSize() {
        return size;
    }
    
    public int addNode(short kind, short level, QName qname) {
        if (nodeKind == null) init();
        if (size == nodeKind.length) grow();
        nodeKind[size] = kind;
        treeLevel[size] = level;
        nodeName[size] = (qname != null ? namePool.add(qname) : -1);
        alpha[size] = -1; // undefined
        next[size] = -1;
        return size++;
    }

    public void addChars(int nodeNr, char[] ch, int start, int len) {
        if (nodeKind == null) init();
        if (nextChar + len >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if (newLen < nextChar + len) newLen = nextChar + len;
            char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alpha[nodeNr] = nextChar;
        alphaLen[nodeNr] = len;
        System.arraycopy(ch, start, characters, nextChar, len);
        nextChar += len;
    }

    public void addChars(int nodeNr, CharSequence s) {
        if (nodeKind == null) init();
        int len = s.length();
        if (nextChar + len >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if (newLen < nextChar + len) newLen = nextChar + len;
            char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alpha[nodeNr] = nextChar;
        alphaLen[nodeNr] = len;
        for (int i = 0; i < len; i++) {
            characters[nextChar++] = s.charAt(i);
        }
    }

    public void addReferenceNode(int nodeNr, NodeProxy proxy) {
        if (nodeKind == null) init();
        if (nextRef == references.length) growReferences();
        references[nextRef] = proxy;
        alpha[nodeNr] = nextRef++;
    }

    public int addAttribute(int nodeNr, QName qname, String value)
            throws DOMException {
        if (nodeKind == null) init();
        if (nextAttr == attrName.length) growAttributes();
        attrParent[nextAttr] = nodeNr;
        attrName[nextAttr] = namePool.add(qname);
        attrValue[nextAttr] = value;
        if (alpha[nodeNr] < 0) alpha[nodeNr] = nextAttr;
        return nextAttr++;
    }

    public int getLastNode() {
        return size - 1;
    }

    private void grow() {
        int newSize = (size * 3) / 2;

        short[] newNodeKind = new short[newSize];
        System.arraycopy(nodeKind, 0, newNodeKind, 0, size);
        nodeKind = newNodeKind;

        short[] newTreeLevel = new short[newSize];
        System.arraycopy(treeLevel, 0, newTreeLevel, 0, size);
        treeLevel = newTreeLevel;

        int[] newNext = new int[newSize];
        Arrays.fill(newNext, -1);
        System.arraycopy(next, 0, newNext, 0, size);
        next = newNext;

        int[] newNodeName = new int[newSize];
        System.arraycopy(nodeName, 0, newNodeName, 0, size);
        nodeName = newNodeName;

        int[] newAlpha = new int[newSize];
        System.arraycopy(alpha, 0, newAlpha, 0, size);
        alpha = newAlpha;

        int[] newAlphaLen = new int[newSize];
        System.arraycopy(alphaLen, 0, newAlphaLen, 0, size);
        alphaLen = newAlphaLen;
    }

    private void growAttributes() {
        int size = attrName.length;
        int newSize = (size * 3) / 2;

        int[] newAttrName = new int[newSize];
        System.arraycopy(attrName, 0, newAttrName, 0, size);
        attrName = newAttrName;

        int[] newAttrParent = new int[newSize];
        System.arraycopy(attrParent, 0, newAttrParent, 0, size);
        attrParent = newAttrParent;

        String[] newAttrValue = new String[newSize];
        System.arraycopy(attrValue, 0, newAttrValue, 0, size);
        attrValue = newAttrValue;
    }

    private void growReferences() {
        int size = references.length;
        int newSize = (size * 3) / 2;

        NodeProxy newReferences[] = new NodeProxy[newSize];
        System.arraycopy(references, 0, newReferences, 0, size);
        references = newReferences;
    }

    public NodeImpl getNode(int nodeNr) throws DOMException {
        if (nodeNr == 0) return this;
        if (nodeNr >= size)
                throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                        "node not found");
        NodeImpl node;
        switch (nodeKind[nodeNr]) {
            case Node.ELEMENT_NODE:
                node = new ElementImpl(this, nodeNr);
                break;
            case Node.TEXT_NODE:
                node = new TextImpl(this, nodeNr);
                break;
            case Node.COMMENT_NODE:
                node = new CommentImpl(this, nodeNr);
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                node = new ProcessingInstructionImpl(this, nodeNr);
                break;
            case NodeImpl.REFERENCE_NODE:
                node = new ReferenceNode(this, nodeNr);
                break;
            default:
                throw new DOMException(DOMException.NOT_FOUND_ERR,
                        "node not found");
        }
        return node;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Node#getParentNode()
     */
    public Node getParentNode() {
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#getDoctype()
     */
    public DocumentType getDoctype() {
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#getImplementation()
     */
    public DOMImplementation getImplementation() {
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#getDocumentElement()
     */
    public Element getDocumentElement() {
        if (size == 1) return null;
        int nodeNr = 1;
        while (nodeKind[nodeNr] != Node.ELEMENT_NODE) {
            if (next[nodeNr] < nodeNr) {
                return null;
            } else
                nodeNr = next[nodeNr];
        }
        return (Element) getNode(nodeNr);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Node#getFirstChild()
     */
    public Node getFirstChild() {
        if (size > 1)
            return getNode(1);
        else
            return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#createElement(java.lang.String)
     */
    public Element createElement(String arg0) throws DOMException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#createDocumentFragment()
     */
    public DocumentFragment createDocumentFragment() {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#createTextNode(java.lang.String)
     */
    public Text createTextNode(String arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#createComment(java.lang.String)
     */
    public Comment createComment(String arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#createCDATASection(java.lang.String)
     */
    public CDATASection createCDATASection(String arg0) throws DOMException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#createProcessingInstruction(java.lang.String,
     *           java.lang.String)
     */
    public ProcessingInstruction createProcessingInstruction(String arg0,
            String arg1) throws DOMException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#createAttribute(java.lang.String)
     */
    public Attr createAttribute(String arg0) throws DOMException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#createEntityReference(java.lang.String)
     */
    public EntityReference createEntityReference(String arg0)
            throws DOMException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#getElementsByTagName(java.lang.String)
     */
    public NodeList getElementsByTagName(String arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#importNode(org.w3c.dom.Node, boolean)
     */
    public Node importNode(Node arg0, boolean arg1) throws DOMException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#createElementNS(java.lang.String,
     *           java.lang.String)
     */
    public Element createElementNS(String arg0, String arg1)
            throws DOMException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#createAttributeNS(java.lang.String,
     *           java.lang.String)
     */
    public Attr createAttributeNS(String arg0, String arg1) throws DOMException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#getElementsByTagNameNS(java.lang.String,
     *           java.lang.String)
     */
    public NodeList getElementsByTagNameNS(String arg0, String arg1) {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Document#getElementById(java.lang.String)
     */
    public Element getElementById(String arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Node#getOwnerDocument()
     */
    public org.w3c.dom.Document getOwnerDocument() {
        return this;
    }

    /**
     * Copy the document fragment starting at the specified node to the given
     * receiver.
     * 
     * @param node
     * @param receiver
     */
    public void copyTo(NodeImpl node, Receiver receiver) throws SAXException {
        NodeImpl top = node;
        while (node != null) {
            startNode(node, receiver);
            NodeImpl nextNode = (NodeImpl) node.getFirstChild();
            while (nextNode == null) {
                endNode(node, receiver);
                if (top != null && top.nodeNumber == node.nodeNumber) break;
                nextNode = (NodeImpl) node.getNextSibling();
                if (nextNode == null) {
                    node = (NodeImpl) node.getParentNode();
                    if (node == null || (top != null && top.nodeNumber == node.nodeNumber)) {
                        endNode(node, receiver);
                        nextNode = null;
                        break;
                    }
                }
            }
            node = nextNode;
        }
    }

    private void startNode(NodeImpl node, Receiver receiver)
            throws SAXException {
        int nr = node.nodeNumber;
        switch (nodeKind[nr]) {
            case Node.ELEMENT_NODE:
                QName nodeName = (QName) document.namePool
                        .get(document.nodeName[nr]);
                receiver.startElement(nodeName);
                int attr = document.alpha[nr];
                if (-1 < attr) {
                    while (attr < document.nextAttr
                            && document.attrParent[attr] == nr) {
                        QName attrQName = (QName) document.namePool
                                .get(document.attrName[attr]);
                        receiver.attribute(attrQName, attrValue[attr]);
                        ++attr;
                    }
                }
                break;
            case Node.TEXT_NODE:
                receiver.characters(document.characters, document.alpha[nr],
                        document.alphaLen[nr]);
                break;
            case Node.COMMENT_NODE:
                receiver.comment(document.characters, document.alpha[nr],
                        document.alphaLen[nr]);
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                QName qn = (QName) document.namePool.get(document.nodeName[nr]);
                String data = new String(document.characters,
                        document.alpha[nr], document.alphaLen[nr]);
                receiver.processingInstruction(qn.getLocalName(), data);
                break;
            case NodeImpl.REFERENCE_NODE:
                receiver
                        .addReferenceNode(document.references[document.alpha[nr]]);
                break;
        }
    }
    
    private void endNode(NodeImpl node, Receiver receiver) throws SAXException {
        if(node.getNodeType() == Node.ELEMENT_NODE)
            receiver.endElement(node.getQName());
    }
}