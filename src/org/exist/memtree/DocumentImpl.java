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

import org.exist.dom.NodeListImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.storage.ElementValue;
import org.exist.storage.serializers.Serializer;
import org.exist.util.hashtable.NamePool;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
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

import java.util.Arrays;

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

    private static long nextDocId = 0;

    private static long createDocId() {
        return nextDocId++;
    }

    protected XQueryContext context;
	
    protected NamePool namePool;

    // holds the node type of a node
    protected short[] nodeKind = null;

    // the tree level of a node
    protected short[] treeLevel;

    // the node number of the next sibling
    protected int[] next;

    // pointer into the namePool
    protected int[] nodeName;

    protected NodeId[] nodeId;
    
    protected int[] alpha;

    protected int[] alphaLen;

    protected char[] characters = null;

    protected int nextChar = 0;

    // attributes
    protected int[] attrName;

    protected int[] attrType;

    protected NodeId[] attrNodeId;
    
    protected int[] attrParent;

    protected String[] attrValue;

    protected int nextAttr = 0;

    // namespaces
    protected int[] namespaceParent = null;
    
    protected int[] namespaceCode = null;
    
    protected int nextNamespace = 0;
    
    // the current number of nodes in the doc
    protected int size = 1;

    protected int documentRootNode = -1;

    protected String documentURI = null;

    // reference nodes (link to an external, persistent document fragment)
    protected NodeProxy references[] = null;

    protected int nextRef = 0;

    protected long docId;

    private final static int NODE_SIZE = 16;
    private final static int ATTR_SIZE = 8;
    private final static int CHAR_BUF_SIZE = 256;
    private final static int REF_SIZE = 8;
    boolean explicitCreation = false;
    
    public DocumentImpl(XQueryContext context, boolean explicitCreation) {
        super(null, 0);
        this.context = context;
        this.explicitCreation = explicitCreation;
        this.docId = createDocId();

        if (context == null)
            namePool = new NamePool();
        else
            namePool = context.getSharedNamePool();
    }
    
    private void init() {
        nodeKind = new short[NODE_SIZE];
        treeLevel = new short[NODE_SIZE];
        next = new int[NODE_SIZE];
        Arrays.fill(next, -1);
        nodeName = new int[NODE_SIZE];
        nodeId = new NodeId[NODE_SIZE];
        alpha = new int[NODE_SIZE];
        alphaLen = new int[NODE_SIZE];
        Arrays.fill(alphaLen, -1);

        attrName = new int[ATTR_SIZE];
        attrParent = new int[ATTR_SIZE];
        attrValue = new String[ATTR_SIZE];
        attrType = new int[ATTR_SIZE];
        attrNodeId = new NodeId[NODE_SIZE];

        treeLevel[0] = 0;
        nodeKind[0] = Node.DOCUMENT_NODE;
        document = this;
    }

    public void reset() {
        size = 0;
        nextChar = 0;
        nextAttr = 0;
        nextRef = 0;
        references = null;
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
        if (characters == null)
            characters = new char[len > CHAR_BUF_SIZE ? len : CHAR_BUF_SIZE];
        else if (nextChar + len >= characters.length) {
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
        if (characters == null)
            characters = new char[len > CHAR_BUF_SIZE ? len : CHAR_BUF_SIZE];
        else if (nextChar + len >= characters.length) {
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
        if (characters == null)
            characters = new char[len > CHAR_BUF_SIZE ? len : CHAR_BUF_SIZE];
        else if (nextChar + len >= characters.length) {
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
        if (characters == null)
            characters = new char[len > CHAR_BUF_SIZE ? len : CHAR_BUF_SIZE];
        else if (nextChar + len >= characters.length) {
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
        if (references == null || nextRef == references.length) growReferences();
        references[nextRef] = proxy;
        alpha[nodeNr] = nextRef++;
    }

    public void replaceReferenceNode(int nodeNr, CharSequence ch) {
        nodeKind[nodeNr] = Node.TEXT_NODE;
        references[alpha[nodeNr]] = null;
        addChars(nodeNr, ch);
    }

    public boolean hasReferenceNodes() {
        return references != null && references[0] != null;
    }

    public int addAttribute(int nodeNr, QName qname, String value, int type)
            throws DOMException {
        if (nodeKind == null) init();
        if (nodeNr > 0 && nodeKind[nodeNr] != Node.ELEMENT_NODE)
            throw new DOMException(DOMException.INUSE_ATTRIBUTE_ERR,
                    "XQTY0024: An attribute node cannot follow a node that is not an attribute node.");
        int prevAttr = nextAttr - 1;
        // check if an attribute with the same qname exists in the parent element
        while (nodeNr > 0 && prevAttr > -1 && attrParent[prevAttr] == nodeNr) {
            QName prevQn = (QName) namePool.get(attrName[prevAttr--]);
            if (prevQn.equalsSimple(qname))
                throw new DOMException(DOMException.INUSE_ATTRIBUTE_ERR,
                        "Error XQDY0025: element has more than one attribute '" + qname + "'");
        }
        if (nextAttr == attrName.length) growAttributes();
        qname.setNameType(ElementValue.ATTRIBUTE);
        attrParent[nextAttr] = nodeNr;
        attrName[nextAttr] = namePool.add(qname);
        attrValue[nextAttr] = value;
        attrType[nextAttr] = type;
        if (alpha[nodeNr] < 0) alpha[nodeNr] = nextAttr;
        return nextAttr++;
    }

    public int addNamespace(int nodeNr, QName qname) {
    	if(nodeKind == null) init();
    	if(namespaceCode == null || nextNamespace == namespaceCode.length) growNamespaces();
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

    public DocumentImpl getDocument() {
        return this;
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

        NodeId[] newNodeId = new NodeId[newSize];
        System.arraycopy(nodeId, 0, newNodeId, 0, size);
        nodeId = newNodeId;

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

        int[] newAttrType = new int[newSize];
        System.arraycopy(attrType, 0, newAttrType, 0, size);
        attrType = newAttrType;

        NodeId[] newNodeId = new NodeId[newSize];
        System.arraycopy(attrNodeId, 0, newNodeId, 0, size);
        attrNodeId = newNodeId;
    }

    private void growReferences() {
        if (references == null)
            references = new NodeProxy[REF_SIZE];
        else {
            int size = references.length;
            int newSize = (size * 3) / 2;

            NodeProxy newReferences[] = new NodeProxy[newSize];
            System.arraycopy(references, 0, newReferences, 0, size);
            references = newReferences;
        }
    }

    private void growNamespaces() {
        if (namespaceCode == null) {
            namespaceCode = new int[5];
            namespaceParent = new int[5];
        } else {
            int size = namespaceCode.length;
            int newSize = (size * 3) / 2;

            int[] newCodes = new int[newSize];
            System.arraycopy(namespaceCode, 0, newCodes, 0, size);
            namespaceCode = newCodes;

            int[] newParents = new int[newSize];
            System.arraycopy(namespaceParent, 0, newParents, 0, size);
            namespaceParent = newParents;
        }
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

    public NodeImpl getLastAttr() {
        if (nextAttr == 0)
            return null;
        return new AttributeImpl(this, nextAttr - 1);
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
       return new DOMImplementation() {
 			public Document createDocument(String namespaceURI, String qualifiedName, DocumentType doctype) throws DOMException {
 				return null;
 			}
 			public DocumentType createDocumentType(String qualifiedName, String publicId, String systemId) throws DOMException {
 				return null;
 			}
 			public Object getFeature(String feature, String version) {
 				return null;
 			}
 		    public boolean hasFeature(String feature, String version) {
 		        return "XML".equals(feature) && ("1.0".equals(version) || "2.0".equals(version));
 		    } 
         };
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

    public void selectChildren(NodeTest test, Sequence result) throws XPathException {
        if (size == 1) return;
        NodeImpl next = (NodeImpl) getFirstChild();
        while (next != null) {
            if (test.matches(next))
                result.add(next);
            next = (NodeImpl) next.getNextSibling();
        }
    }

    public void selectDescendants(boolean includeSelf, NodeTest test, Sequence result) throws XPathException {
        if (includeSelf && test.matches(this))
            result.add(this);
        if (size == 1) return;
        NodeImpl next = (NodeImpl) getFirstChild();
        while (next != null) {
            if (test.matches(next))
                result.add(next);
            next.selectDescendants(includeSelf, test, result);
            next = (NodeImpl) next.getNextSibling();
        }
    }

    public void selectDescendantAttributes(NodeTest test, Sequence result) throws XPathException {
        if (size == 1) return;
        NodeImpl next = (NodeImpl) getFirstChild();
        while (next != null) {
            if (test.matches(next))
                result.add(next);
            next.selectDescendantAttributes(test, result);
            next = (NodeImpl) next.getNextSibling();
        }
    }

    public NodeImpl selectById(String id) throws XPathException {
        if (size == 1) return null;
        ElementImpl root = (ElementImpl) getDocumentElement();
        if (hasIdAttribute(root.getNodeNumber(), id))
            return root;
        int treeLevel = this.treeLevel[root.getNodeNumber()];
        int nextNode = root.getNodeNumber();
        while (++nextNode < document.size && document.treeLevel[nextNode] > treeLevel) {
            if (document.nodeKind[nextNode] == Node.ELEMENT_NODE &&
                    hasIdAttribute(nextNode, id))
                return getNode(nextNode);
        }
        return null;
    }

    public NodeImpl selectByIdref(String id) throws XPathException {
        if (size == 1) return null;
        ElementImpl root = (ElementImpl) getDocumentElement();
        AttributeImpl attr = getIdrefAttribute(root.getNodeNumber(), id);
        if (attr != null)
            return attr;
        int treeLevel = this.treeLevel[root.getNodeNumber()];
        int nextNode = root.getNodeNumber();
        while (++nextNode < document.size && document.treeLevel[nextNode] > treeLevel) {
            if (document.nodeKind[nextNode] == Node.ELEMENT_NODE) {
                attr = getIdrefAttribute(nextNode, id);
                if (attr != null)
                    return attr;
            }
        }
        return null;
    }

    private boolean hasIdAttribute(int nodeNumber, String id) {
        int attr = document.alpha[nodeNumber];
        if (-1 < attr) {
            while (attr < document.nextAttr
                    && document.attrParent[attr] == nodeNumber) {
                if (document.attrType[attr] == AttributeImpl.ATTR_ID_TYPE &&
                        id.equals(document.attrValue[attr]))
                    return true;
                ++attr;
            }
        }
        return false;
    }

    private AttributeImpl getIdrefAttribute(int nodeNumber, String id) {
        int attr = document.alpha[nodeNumber];
        if (-1 < attr) {
            while (attr < document.nextAttr
                    && document.attrParent[attr] == nodeNumber) {
                if (document.attrType[attr] == AttributeImpl.ATTR_IDREF_TYPE &&
                        id.equals(document.attrValue[attr]))
                    return new AttributeImpl(this, attr);
                ++attr;
            }
        }
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
                	while (ns < document.nextNamespace	&& document.namespaceParent[ns] == nr) {
                		QName nsQName = (QName) document.namePool.get(document.namespaceCode[ns]);
                		receiver.addNamespaceNode(nsQName);
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
                    serializer.toReceiver(document.references[document.alpha[nr]], false, false);
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
        if (size == 0)
            return;
        DocumentImpl newDoc = expandRefs(null);
        copyDocContents(newDoc);
    }
    
    public DocumentImpl expandRefs(NodeImpl rootNode) throws DOMException {
        if(nextRef == 0) {
            computeNodeIds();
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
        DocumentImpl newDoc = builder.getDocument();
        newDoc.computeNodeIds();
        return newDoc;
    }

    public NodeImpl getNodeById(NodeId id) {
        expand();
        for (int i = 0; i < size; i++) {
            if (id.equals(nodeId[i]))
                return getNode(i);
        }
        return null;
    }
    
    private void computeNodeIds() {
        if (nodeId[0] != null)
            return;
        nodeId[0] = context.getBroker().getBrokerPool().getNodeFactory().documentNodeId();
        if (size == 1) return;
        NodeId nextId = context.getBroker().getBrokerPool().getNodeFactory().createInstance();
        NodeImpl next = (NodeImpl) getFirstChild();
        while (next != null) {
            computeNodeIds(nextId, next.nodeNumber);
            next = (NodeImpl) next.getNextSibling();
            nextId = nextId.nextSibling();
        }
    }

    private void computeNodeIds(NodeId id, int nodeNr) {
        nodeId[nodeNr] = id;
        if (nodeKind[nodeNr] == Node.ELEMENT_NODE) {
            NodeId nextId = id.newChild();
            int attr = document.alpha[nodeNr];
            if(-1 < attr) {
                while (attr < document.nextAttr && document.attrParent[attr] == nodeNr) {
                    attrNodeId[attr] = nextId;
                    nextId = nextId.nextSibling();
                    ++attr;
                }
            }

            int nextNode = getFirstChildFor(nodeNr);
            while (nextNode > nodeNr) {
                computeNodeIds(nextId, nextNode);
                nextNode = document.next[nextNode];
                if (nextNode > nodeNr)
                    nextId = nextId.nextSibling();
            }
        }
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
        nodeId = newDoc.nodeId;
        alpha = newDoc.alpha;
        alphaLen = newDoc.alphaLen;
        characters = newDoc.characters;
        nextChar = newDoc.nextChar;
        attrName = newDoc.attrName;
        attrNodeId = newDoc.attrNodeId;
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
	    			QName attrQName = (QName) document.namePool.get(document.attrName[attr]);
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
    		serializer.toReceiver(document.references[document.alpha[nr]], true, false);
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
    
    public org.exist.dom.DocumentImpl makePersistent() throws XPathException {
         if (size <= 1)
            return null;
        return context.storeTemporaryDoc(this);
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

    public boolean hasChildNodes() {
        return getChildCount() > 0;
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
		return documentURI;
	}

	/** ? @see org.w3c.dom.Document#setDocumentURI(java.lang.String)
	 */
	public void setDocumentURI(String documentURI) {
		this.documentURI = documentURI;
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
	
    public XQueryContext getContext() {
        return context;
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

	public int getItemType() {
		return Type.DOCUMENT;
	}    

	public String toString() {
    	StringBuilder result = new StringBuilder();
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
