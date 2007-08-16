/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.memtree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.exist.dom.NodeListImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.storage.ElementValue;
import org.exist.storage.serializers.Serializer;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.util.hashtable.NamePool;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
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

	protected XQueryContext context;
	
    protected NamePool namePool = new NamePool();

    // holds the node type of a node
    protected short[] nodeKind = null;

    // the tree level of a node
    protected short[] treeLevel;

    // the node number of the next sibling
    protected int[] next;

    // pointer into the namePool
    protected int[] nodeName;

    protected int[] alpha;

    protected int[] alphaLen;

    protected char[] characters;

    protected int nextChar = 0;

    // attributes
    protected int[] attrName;

    protected int[] attrParent;

    protected String[] attrValue;

    protected int nextAttr = 0;

    // namespaces
    protected int[] namespaceParent;
    
    protected int[] namespaceCode;
    
    protected int nextNamespace = 0;
    
    // the current number of nodes in the doc
    protected int size = 1;

    protected int documentRootNode = -1;

    // reference nodes (link to an external, persistent document fragment)
    protected NodeProxy references[];

    protected int nextRef = 0;
    
    private final static int NODE_SIZE = 128;
    private final static int ATTR_SIZE = 64;
    private final static int CHAR_BUF_SIZE = 1024;
    private final static int REF_SIZE = 128;

	private Int2ObjectHashMap storedNodes = null;
    
    public DocumentImpl(XQueryContext context) {
        super(null, 0);
        this.context = context;
    }
    
    private void init() {
        nodeKind = new short[NODE_SIZE];
        treeLevel = new short[NODE_SIZE];
        next = new int[NODE_SIZE];
        Arrays.fill(next, -1);
        nodeName = new int[NODE_SIZE];
        alpha = new int[NODE_SIZE];
        alphaLen = new int[NODE_SIZE];
        Arrays.fill(alphaLen, -1);
        
        characters = new char[CHAR_BUF_SIZE];

        attrName = new int[ATTR_SIZE];
        attrParent = new int[ATTR_SIZE];
        attrValue = new String[ATTR_SIZE];

        namespaceCode = new int[5];
        namespaceParent = new int[5];
        
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

    public void appendChars(int nodeNr, char[] ch, int start, int len) {
        if (nextChar + len >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if (newLen < nextChar + len) newLen = nextChar + len;
            char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alphaLen[nodeNr] = alphaLen[nodeNr] + len;
        System.arraycopy(ch, start, characters, nextChar, len);
        nextChar += len;
    }
    
    public void appendChars(int nodeNr, CharSequence s) {
        int len = s.length();
        if (nextChar + len >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if (newLen < nextChar + len) newLen = nextChar + len;
            char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alphaLen[nodeNr] = alphaLen[nodeNr] + len;
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

    public void replaceReferenceNode(int nodeNr, CharSequence ch) {
        nodeKind[nodeNr] = Node.TEXT_NODE;
        references[alpha[nodeNr]] = null;
        addChars(nodeNr, ch);
    }

    public int addAttribute(int nodeNr, QName qname, String value)
            throws DOMException {
        if (nodeKind == null) init();
        if (nextAttr == attrName.length) growAttributes();
        qname.setNameType(ElementValue.ATTRIBUTE);
        attrParent[nextAttr] = nodeNr;
        attrName[nextAttr] = namePool.add(qname);
        attrValue[nextAttr] = value;
        if (alpha[nodeNr] < 0) alpha[nodeNr] = nextAttr;
        return nextAttr++;
    }

    public int addNamespace(int nodeNr, QName qname) {
    	if(nodeKind == null) init();
    	if(nextNamespace == namespaceCode.length) growNamespaces();
    	namespaceCode[nextNamespace] = namePool.add(qname);
    	namespaceParent[nextNamespace] = nodeNr;
    	if(alphaLen[nodeNr] < 0) { 
    		alphaLen[nodeNr] = nextNamespace;
    	}
    	return nextNamespace++;
    }
    
    public short getTreeLevel(int nodeNr) {
    	return treeLevel[nodeNr];
    }
    
    public int getLastNode() {
        return size - 1;
    }
    
    public short getNodeType(int nodeNr) {
    	if (nodeKind == null || nodeNr < 0)
            return -1;
        return nodeKind[nodeNr];
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
        Arrays.fill(newAlphaLen, -1);
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

    private void growNamespaces() {
    	int size = namespaceCode.length;
    	int newSize = (size * 3) / 2;
    	
    	int[] newCodes = new int[newSize];
    	System.arraycopy(namespaceCode, 0, newCodes, 0, size);
    	namespaceCode = newCodes;
    	
    	int[] newParents = new int[newSize];
    	System.arraycopy(namespaceParent, 0, newParents, 0, size);
    	namespaceParent = newParents;
    }
    
    public NodeImpl getAttribute(int nodeNr) throws DOMException {
        return new AttributeImpl(this, nodeNr);
    }
    
    public NodeImpl getNamespaceNode(int nodeNr) throws DOMException {
    	return new NamespaceNode(this, nodeNr);
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
            case Node.CDATA_SECTION_NODE:
                node = new CDATASectionImpl(this, nodeNr);
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

    public int getAttributesCountFor(int nodeNumber) {
        int count = 0;
        int attr = alpha[nodeNumber];
        if (-1 < attr) {
            while (attr < nextAttr
                    && attrParent[attr++] == nodeNumber) {
                ++count;
            }
        }
        return count;
    }
    
    public int getNamespacesCountFor(int nodeNumber) {
        int count = 0;
        int ns = alphaLen[nodeNumber];
        if (-1 < ns) {
            while (ns < nextNamespace
                    && namespaceParent[ns++] == nodeNumber) {
                ++count;
            }
        }
        return count;
    }
    
    public int getChildCountFor(int nr) {
        int count = 0;
        //short level = (short)(treeLevel[nr] + 1);
        int nextNode = getFirstChildFor(nr);
        while (nextNode > nr) {
            ++count;
            nextNode = next[nextNode];
        }
        return count;
    }
    
    public int getFirstChildFor(int nodeNumber) {
        short level = treeLevel[nodeNumber];
        int nextNode = nodeNumber + 1;
        if (nextNode < size && treeLevel[nextNode] > level) {
            return nextNode;
        } else
            return -1;
    }
    
    public int getNextSiblingFor(int nodeNumber) {
        int nextNr = next[nodeNumber];
        return nextNr < nodeNumber ? -1 : nextNr;
    }
    
    /**
     * The method <code>getParentNodeFor</code>
     *
     * @param nodeNumber an <code>int</code> value
     * @return an <code>int</code> value
     */
    public int getParentNodeFor(int nodeNumber) {
        int nextNode = next[nodeNumber];
        while (nextNode > nodeNumber) {
            nextNode = next[nextNode];
        }
        return nextNode;
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
    public NodeList getElementsByTagName(String name) {
    	NodeListImpl nl = new NodeListImpl();
    	//int nodeNr = 1;
    	for(int i = 1; i < size; i++) {
    		if (nodeKind[i] == Node.ELEMENT_NODE) {
    			QName qn = (QName) namePool.get(nodeName[i]);
    			if(qn.getStringValue().equals(name))
    				nl.add(getNode(i));
    		}
    	}
        return nl;
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
     * document builder.
     * 
     * @param node
     * @param receiver
     */
    public void copyTo(NodeImpl node, DocumentBuilderReceiver receiver) throws SAXException {
        copyTo(node, receiver, false);
    }
    
    protected void copyTo(NodeImpl node, DocumentBuilderReceiver receiver, boolean expandRefs) throws SAXException {
        NodeImpl top = node;
        while (node != null) {
            copyStartNode(node, receiver, expandRefs);
            NodeImpl nextNode;
            if (node instanceof ReferenceNode)
                //Nothing more to stream ?
                nextNode = null;
            else
                nextNode = (NodeImpl) node.getFirstChild();
            while (nextNode == null) {
                copyEndNode(node, receiver);
                if (top != null && top.nodeNumber == node.nodeNumber)
                    break;
                //No nextNode if the top node is a Document node
//                if (top != null && top.nodeNumber == 0)
//                    break;
                nextNode = (NodeImpl) node.getNextSibling();
                if (nextNode == null) {
                    node = (NodeImpl) node.getParentNode();
                    if (node == null || (top != null && top.nodeNumber == node.nodeNumber)) {
                        copyEndNode(node, receiver);
                        nextNode = null;
                        break;
                    }
                }
            }
            node = nextNode;
        }
    }
 
    private void copyStartNode(NodeImpl node, DocumentBuilderReceiver receiver, boolean expandRefs)
            throws SAXException {
        int nr = node.nodeNumber;
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
                QName nodeName = (QName) document.namePool.get(document.nodeName[nr]);
                receiver.startElement(nodeName, null);
                int attr = document.alpha[nr];
                if (-1 < attr) {
                    while (attr < document.nextAttr && document.attrParent[attr] == nr) {
                        QName attrQName = (QName) document.namePool.get(document.attrName[attr]);
                        receiver.attribute(attrQName, attrValue[attr]);
                        ++attr;
                    }
                }
                int ns = document.alphaLen[nr];
                if (-1 < ns) {
                	XQueryContext context = receiver.getContext();
                	while (ns < document.nextNamespace	&& document.namespaceParent[ns] == nr) {
                		QName nsQName = (QName) document.namePool.get(document.namespaceCode[ns]);
                		receiver.addNamespaceNode(nsQName);
                        if ("xmlns".equals(nsQName.getLocalName()))
                            context.declareInScopeNamespace("", nsQName.getNamespaceURI());
                        else
                            context.declareInScopeNamespace(nsQName.getLocalName(), nsQName.getNamespaceURI());
                		++ns;
                	}
                }
                break;
            case Node.TEXT_NODE:
                receiver.characters(document.characters, document.alpha[nr],
                        document.alphaLen[nr]);
                break;
            case Node.CDATA_SECTION_NODE:
            	receiver.cdataSection(document.characters, document.alpha[nr],
                        document.alphaLen[nr]);
            	break;
            case Node.ATTRIBUTE_NODE:
                QName attrQName = (QName) document.namePool.get(document.attrName[nr]);
                receiver.attribute(attrQName, attrValue[nr]);
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
                if(expandRefs) {
                    Serializer serializer = context.getBroker().getSerializer();
                    serializer.reset();
                    serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
                    serializer.setReceiver(receiver);
                    serializer.toReceiver(document.references[document.alpha[nr]]);
                } else {
                    receiver.addReferenceNode(document.references[document.alpha[nr]]);
                }
                break;
        }
    }
    
    private void copyEndNode(NodeImpl node, DocumentBuilderReceiver receiver) throws SAXException {
        if(node.getNodeType() == Node.ELEMENT_NODE)
            receiver.endElement(node.getQName());
    }
    
    /**
     * Expand all reference nodes in the current document, i.e. replace them by
     * real nodes. Reference nodes are just pointers to nodes from other documents 
     * stored in the database. The XQuery engine uses reference nodes to speed 
     * up the creation of temporary doc fragments.
     * 
     * This method creates a new copy of the document contents and expands all
     * reference nodes.
     */
    public void expand() throws DOMException {
        DocumentImpl newDoc = expandRefs(null);
        copyDocContents(newDoc);
    }
    
    public DocumentImpl expandRefs(NodeImpl rootNode) throws DOMException {
        if(nextRef == 0) {
            return this;
        }
        MemTreeBuilder builder = new MemTreeBuilder(context);
        DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
        try {
            builder.startDocument();
            NodeImpl node = rootNode == null ? (NodeImpl) getFirstChild() : rootNode;
            while(node != null) {
                copyTo(node, receiver, true);
                node = (NodeImpl) node.getNextSibling();
            }
            receiver.endDocument();
        } catch (SAXException e) {
            throw new DOMException(DOMException.INVALID_STATE_ERR, e.getMessage());
        }
        return builder.getDocument();
    }
    
    /**
     * @param newDoc
     */
    private void copyDocContents(DocumentImpl newDoc) {
        namePool = newDoc.namePool;
        nodeKind = newDoc.nodeKind;
        treeLevel = newDoc.treeLevel;
        next = newDoc.next;
        nodeName = newDoc.nodeName;
        alpha = newDoc.alpha;
        alphaLen = newDoc.alphaLen;
        characters = newDoc.characters;
        nextChar = newDoc.nextChar;
        attrName = newDoc.attrName;
        attrParent = newDoc.attrParent;
        attrValue = newDoc.attrValue;
        nextAttr = newDoc.nextAttr;
        namespaceParent = newDoc.namespaceParent;
        namespaceCode = newDoc.namespaceCode;
        nextNamespace = newDoc.nextNamespace;
        size = newDoc.size;
        documentRootNode = newDoc.documentRootNode;
        references = newDoc.references;
        nextRef = newDoc.nextRef;
    }
    
    /**
     * Stream the specified document fragment to a receiver. This method
     * is called by the serializer to output in-memory nodes.
     * 
     * @param serializer
     * @param node
     * @param receiver
     * @throws SAXException
     */
    public void streamTo(Serializer serializer, NodeImpl node, Receiver receiver) throws SAXException {
        NodeImpl top = node;
        while (node != null) {
            startNode(serializer, node, receiver);
            NodeImpl nextNode;
        	if (node instanceof ReferenceNode)
        		//Nothing more to stream ?
        		nextNode = null;
        	else
        		nextNode = (NodeImpl) node.getFirstChild();
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
    
    private void startNode(Serializer serializer, NodeImpl node, Receiver receiver)
    throws SAXException {
    	int nr = node.nodeNumber;
    	switch (node.getNodeType()) {
    	case Node.ELEMENT_NODE:
    		QName nodeName = (QName) document.namePool.get(document.nodeName[nr]);
    	
    		// output required namespace declarations
	    	int ns = document.alphaLen[nr];
	        if (-1 < ns) {
	        	while (ns < document.nextNamespace
	        			&& document.namespaceParent[ns] == nr) {
	        		QName nsQName = (QName) document.namePool
	                	.get(document.namespaceCode[ns]);
                    if ("xmlns".equals(nsQName.getLocalName()))
                        receiver.startPrefixMapping("", nsQName.getNamespaceURI());
                    else
                        receiver.startPrefixMapping(nsQName.getLocalName(), nsQName.getNamespaceURI());
	        		++ns;
	        	}
	        }
	        // create the attribute list
	    	AttrList attribs = null;
	    	int attr = document.alpha[nr];
	    	if (-1 < attr) {
	    		attribs = new AttrList();
	    		while (attr < document.nextAttr
	    				&& document.attrParent[attr] == nr) {
	    			QName attrQName = (QName) document.namePool
					.get(document.attrName[attr]);
	    			attribs.addAttribute(attrQName, attrValue[attr]);
	    			++attr;
	    		}
	    	}
	    	receiver.startElement(nodeName, attribs);
	    	break;
    	case Node.TEXT_NODE:
    		receiver.characters(new String(document.characters, document.alpha[nr],
    				document.alphaLen[nr]));
    	break;
    	case Node.ATTRIBUTE_NODE:
    		QName attrQName = (QName) document.namePool.get(document.attrName[nr]);
    	receiver.attribute(attrQName, attrValue[nr]);
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
        case Node.CDATA_SECTION_NODE:
            receiver.cdataSection(document.characters, document.alpha[nr],
                    document.alphaLen[nr]);
            break;
    	case NodeImpl.REFERENCE_NODE:
    		serializer.toReceiver(document.references[document.alpha[nr]]);
    	break;
    	}
    }
    
    private void endNode(NodeImpl node, Receiver receiver) throws SAXException {
    	if(node.getNodeType() == Node.ELEMENT_NODE) {
    		receiver.endElement(node.getQName());
    		// end all prefix mappings used for the element
    		int nr = node.nodeNumber;
	    	int ns = document.alphaLen[nr];
	        if (-1 < ns) {
	        	while (ns < document.nextNamespace
	        			&& document.namespaceParent[ns] == nr) {
	        		QName nsQName = (QName) document.namePool
	                	.get(document.namespaceCode[ns]);
                    if ("xmlns".equals(nsQName.getLocalName()))
                        receiver.endPrefixMapping("");
                    else
                        receiver.endPrefixMapping(nsQName.getLocalName());
	        		++ns;
	        	}
	        }
    	}
    }
    
    public Int2ObjectHashMap makePersistent() throws XPathException {
        if (size <= 1)
            return null;
        List oldIds = new ArrayList(20);
        int top = 1;
        while (top > 0) {
            oldIds.add(new Integer(top));
            top = getNextSiblingFor(top);
        }
        DocumentImpl expandedDoc = expandRefs(null);
        org.exist.dom.DocumentImpl doc = context.storeTemporaryDoc(expandedDoc);
        org.exist.dom.ElementImpl root = (org.exist.dom.ElementImpl) doc.getDocumentElement();
        NodeList cl = root.getChildNodes();
        storedNodes = new Int2ObjectHashMap();
        storedNodes.put(0, new NodeProxy(doc, root.getNodeId(), 
        		root.getNodeType(), root.getInternalAddress()));
        top = 1;
        int i = 0;
        while(top > 0 && i < cl.getLength()) {
            StoredNode node = (StoredNode) cl.item(i);
            NodeProxy proxy = new NodeProxy(doc, node.getNodeId(), 
            		node.getNodeType(), node.getInternalAddress());
            int old = ((Integer)oldIds.get(i)).intValue();
            storedNodes.put(old, proxy);
            top = expandedDoc.getNextSiblingFor(top);
            i++;
        }
        return storedNodes;
    }
    
    public int getChildCount() {
    	int count = 0;
    	int top = size > 1 ? 1 : -1;
        while(top > 0) {
            ++count;
            top = getNextSiblingFor(top);
        }
        return count;
    }
    
	public String getLocalName() {		
        return "";
	}   
	
	public String getNamespaceURI() {
        return "";
	}	
    
	/** ? @see org.w3c.dom.Document#getInputEncoding()
	 */
	public String getInputEncoding() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Document#getXmlEncoding()
	 */
	public String getXmlEncoding() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Document#getXmlStandalone()
	 */
	public boolean getXmlStandalone() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return false;
	}

	/** ? @see org.w3c.dom.Document#setXmlStandalone(boolean)
	 */
	public void setXmlStandalone(boolean xmlStandalone) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Document#getXmlVersion()
	 */
	public String getXmlVersion() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Document#setXmlVersion(java.lang.String)
	 */
	public void setXmlVersion(String xmlVersion) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Document#getStrictErrorChecking()
	 */
	public boolean getStrictErrorChecking() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return false;
	}

	/** ? @see org.w3c.dom.Document#setStrictErrorChecking(boolean)
	 */
	public void setStrictErrorChecking(boolean strictErrorChecking) {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Document#getDocumentURI()
	 */
	public String getDocumentURI() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Document#setDocumentURI(java.lang.String)
	 */
	public void setDocumentURI(String documentURI) {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Document#adoptNode(org.w3c.dom.Node)
	 */
	public Node adoptNode(Node source) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Document#getDomConfig()
	 */
	public DOMConfiguration getDomConfig() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Document#normalizeDocument()
	 */
	public void normalizeDocument() {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Document#renameNode(org.w3c.dom.Node, java.lang.String, java.lang.String)
	 */
	public Node renameNode(Node n, String namespaceURI, String qualifiedName) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}
	
	public void setContext(XQueryContext context) {
		this.context = context;
	}
	

    /** ? @see org.w3c.dom.Node#getBaseURI()
     */
    public String getBaseURI() {
        if (context.isBaseURIDeclared()) {
            try {
                return context.getBaseURI() + "";
            } catch (Exception e) {
                System.out.println("memtree/DocumentImpl::getBaseURI() exception catched: ");   
            }
        }
        return XmldbURI.EMPTY_URI.toString();
        //return XmldbURI.ROOT_COLLECTION_URI.toString();
    }

   public String toString() {
    	StringBuffer result = new StringBuffer();
    	result.append("in-memory#");
    	result.append("document {");
        if (size != 1) {
        	int nodeNr = 1;
        	while (true) {
	        	result.append(getNode(nodeNr).toString());
	            if (next[nodeNr] < nodeNr) {
	                break;
	            } else
	                nodeNr = next[nodeNr];
        	}
        }       
        result.append("} ");    	
    	return result.toString();
    }    	
}
