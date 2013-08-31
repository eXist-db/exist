/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2010 The eXist Project
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

import org.exist.Database;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.dom.DocumentAtExist;
import org.exist.dom.NodeListImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.numbering.NodeIdFactory;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
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

import java.util.Arrays;


/**
 * An in-memory implementation of Document.
 *
 * <p>This implementation stores all node data in the document object. Nodes from another document, i.e. a persistent document in the database, can be
 * stored as reference nodes, i.e. the nodes are not copied into this document object. Instead a reference is inserted which will only be expanded
 * during serialization.</p>
 *
 * @author  wolf
 */
public class DocumentImpl extends NodeImpl implements DocumentAtExist {

    private static long        nextDocId        = 0;

    private final static int   NODE_SIZE        = 16;
    private final static int   ATTR_SIZE        = 8;
    private final static int   CHAR_BUF_SIZE    = 256;
    private final static int   REF_SIZE         = 8;

    protected XQueryContext    context;

    protected NamePool         namePool;

    // holds the node type of a node
    protected short[]          nodeKind = null;

    // the tree level of a node
    protected short[]          treeLevel;

    // the node number of the next sibling
    protected int[]            next;

    // pointer into the namePool
    protected QName[]          nodeName;

    protected NodeId[]         nodeId;

    protected int[]            alpha;

    protected int[]            alphaLen;

    protected char[]           characters = null;

    protected int              nextChar = 0;

    // attributes
    protected QName[]          attrName;

    protected int[]            attrType;

    protected NodeId[]         attrNodeId;

    protected int[]            attrParent;

    protected String[]         attrValue;

    protected int              nextAttr         = 0;

    // namespaces
    protected int[]            namespaceParent  = null;

    protected QName[]          namespaceCode    = null;

    protected int              nextNamespace    = 0;

    // the current number of nodes in the doc
    protected int              size             = 1;

    protected int              documentRootNode = -1;

    protected String           documentURI      = null;

    // reference nodes (link to an external, persistent document fragment)
    protected NodeProxy[]      references       = null;
    protected int              nextRef          = 0;
    protected long             docId;
    boolean                    explicitCreation = false;
    
    boolean replaceAttribute = false;
    
    private Database db = null;

    public DocumentImpl(XQueryContext context, boolean explicitCreation) {
        super(null, 0);
        this.context = context;
        this.explicitCreation = explicitCreation;
        this.docId = createDocId();
        if(context == null) {
            namePool = new NamePool();
        } else {
            db = context.getDatabase();
            namePool = context.getSharedNamePool();
        }
    }

    public Database getDatabase() {
        if (db == null)
            try {
                db = BrokerPool.getInstance();
            } catch (final EXistException e) {
                throw new NullPointerException();
            }
        return db;
    }

    private static long createDocId() {
        return( nextDocId++ );
    }

    private void init() {
        nodeKind  = new short[NODE_SIZE];
        treeLevel = new short[NODE_SIZE];
        next      = new int[NODE_SIZE];
        Arrays.fill(next, -1);
        nodeName = new QName[NODE_SIZE];
        nodeId   = new NodeId[NODE_SIZE];
        alpha    = new int[NODE_SIZE];
        alphaLen = new int[NODE_SIZE];
        Arrays.fill(alphaLen, -1);
        attrName     = new QName[ATTR_SIZE];
        attrParent   = new int[ATTR_SIZE];
        attrValue    = new String[ATTR_SIZE];
        attrType     = new int[ATTR_SIZE];
        attrNodeId   = new NodeId[NODE_SIZE];
        treeLevel[0] = 0;
        nodeKind[0]  = Node.DOCUMENT_NODE;
        document     = this;
    }

    public void reset() {
        size       = 0;
        nextChar   = 0;
        nextAttr   = 0;
        nextRef    = 0;
        references = null;
    }

    public int getSize() {
        return( size );
    }

    public int addNode(short kind, short level, QName qname) {
        if (nodeKind == null) {
            init();
        }
        if (size == nodeKind.length) {
            grow();
        }
        nodeKind[size]  = kind;
        treeLevel[size] = level;
        nodeName[size]  = qname != null ? namePool.getSharedName(qname) : null;
        alpha[size]     = -1; // undefined
        next[size]      = -1;
        return(size++);
    }

    public void addChars(int nodeNum, char[] ch, int start, int len) {
        if (nodeKind == null) {
            init();
        }
        if(characters == null) {
            characters = new char[len > CHAR_BUF_SIZE ? len : CHAR_BUF_SIZE];
        } else if ((nextChar + len) >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if (newLen < (nextChar + len)) {
                newLen = nextChar + len;
            }
            char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alpha[nodeNum] = nextChar;
        alphaLen[nodeNum] = len;
        System.arraycopy(ch, start, characters, nextChar, len);
        nextChar += len;
    }

    public void addChars(int nodeNum, CharSequence s) {
        if (nodeKind == null) {
            init();
        }
        int len = (s == null) ? 0 : s.length();
        if (characters == null) {
            characters = new char[(len > CHAR_BUF_SIZE) ? len : CHAR_BUF_SIZE];
        } else if ((nextChar + len) >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if (newLen < (nextChar + len)) {
                newLen = nextChar + len;
            }
            char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alpha[nodeNum] = nextChar;
        alphaLen[nodeNum] = len;
        for (int i = 0; i < len; i++) {
            characters[nextChar++] = s.charAt( i );
        }
    }

    public void appendChars(int nodeNum, char[] ch, int start, int len) {
        if (characters == null) {
            characters = new char[(len > CHAR_BUF_SIZE) ? len : CHAR_BUF_SIZE];
        } else if((nextChar + len) >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if (newLen < (nextChar + len)) {
                newLen = nextChar + len;
            }
            char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alphaLen[nodeNum] = alphaLen[nodeNum] + len;
        System.arraycopy(ch, start, characters, nextChar, len);
        nextChar += len;
    }

    public void appendChars(int nodeNum, CharSequence s) {
        final int len = s.length();
        if (characters == null) {
            characters = new char[(len > CHAR_BUF_SIZE) ? len : CHAR_BUF_SIZE];
        } else if ((nextChar + len) >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if (newLen < (nextChar + len)) {
                newLen = nextChar + len;
            }
            char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alphaLen[nodeNum] = alphaLen[nodeNum] + len;
        for (int i = 0; i < len; i++) {
            characters[nextChar++] = s.charAt(i);
        }
    }

    public void addReferenceNode(int nodeNum, NodeProxy proxy) {
        if (nodeKind == null) {
            init();
        }
        if (( references == null ) || ( nextRef == references.length)) {
            growReferences();
        }
        references[nextRef] = proxy;
        alpha[nodeNum] = nextRef++;
    }

    public void replaceReferenceNode(int nodeNum, CharSequence ch) {
        nodeKind[nodeNum] = Node.TEXT_NODE;
        references[alpha[nodeNum]] = null;
        addChars(nodeNum, ch);
    }

    public boolean hasReferenceNodes() {
        return (references != null) && (references[0] != null);
    }

    public int addAttribute(int nodeNum, QName qname, String value, int type) throws DOMException {
        if (nodeKind == null) {
            init();
        }
        if ((nodeNum > 0 ) && !(nodeKind[nodeNum] == Node.ELEMENT_NODE || nodeKind[nodeNum] == NodeImpl.NAMESPACE_NODE)) {
            throw( new DOMException( DOMException.INUSE_ATTRIBUTE_ERR,
                "err:XQTY0024: An attribute node cannot follow a node that is not an attribute node."));
        }
        int prevAttr = nextAttr - 1;
        int attrN;
        //Check if an attribute with the same qname exists in the parent element
        while ((nodeNum > 0) && (prevAttr > -1) && (attrParent[prevAttr] == nodeNum)) {
            attrN = prevAttr--;
            final QName prevQn = attrName[attrN];
            if (prevQn.equalsSimple(qname)) {
                if (replaceAttribute) {
                    attrValue[attrN] = value;
                    attrType[attrN] = type;
                    return attrN;
                } else
                    {throw new DOMException(DOMException.INUSE_ATTRIBUTE_ERR,
                        "err:XQDY0025: element has more than one attribute '" + qname + "'");}
            }
        }
        if (nextAttr == attrName.length) {
            growAttributes();
        }
        qname.setNameType(ElementValue.ATTRIBUTE);
        attrParent[nextAttr] = nodeNum;
        attrName[nextAttr]   = namePool.getSharedName(qname);
        attrValue[nextAttr]  = value;
        attrType[nextAttr]   = type;
        if (alpha[nodeNum] < 0) {
            alpha[nodeNum] = nextAttr;
        }
        return( nextAttr++ );
    }

    public int addNamespace(int nodeNum, QName qname) {
        if (nodeKind == null) {
            init();
        }
        if ((namespaceCode == null) || (nextNamespace == namespaceCode.length)) {
            growNamespaces();
        }
        namespaceCode[nextNamespace] = namePool.getSharedName(qname);
        namespaceParent[nextNamespace] = nodeNum;
        if (alphaLen[nodeNum] < 0) {
            alphaLen[nodeNum] = nextNamespace;
        }
        return nextNamespace++;
    }

    public short getTreeLevel(int nodeNum) {
        return treeLevel[nodeNum];
    }

    public int getLastNode() {
        return size - 1;
    }

    @Override
    public DocumentImpl getDocument() {
        return this;
    }

    public short getNodeType(int nodeNum) {
        if ((nodeKind == null) || (nodeNum < 0)) {
            return -1;
        }
        return nodeKind[nodeNum];
    }

    public String getStringValue() {
    	if (document == null)
    		{return "";}
    	
    	return super.getStringValue();
    }
    
    private void grow() {
        final int newSize = (size * 3) / 2;
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
        QName[] newNodeName = new QName[newSize];
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
        final int size = attrName.length;
        final int newSize = (size * 3) / 2;
        QName[] newAttrName = new QName[newSize];
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
        if (references == null) {
            references = new NodeProxy[REF_SIZE];
        } else {
            final int size = references.length;
            final int newSize = (size * 3) / 2;
            NodeProxy[] newReferences = new NodeProxy[newSize];
            System.arraycopy(references, 0, newReferences, 0, size);
            references = newReferences;
        }
    }

    private void growNamespaces() {
        if (namespaceCode == null) {
            namespaceCode = new QName[5];
            namespaceParent = new int[5];
        } else {
            final int size = namespaceCode.length;
            final int newSize = (size * 3) / 2;
            QName[] newCodes = new QName[newSize];
            System.arraycopy(namespaceCode, 0, newCodes, 0, size);
            namespaceCode = newCodes;
            int[] newParents = new int[newSize];
            System.arraycopy(namespaceParent, 0, newParents, 0, size);
            namespaceParent = newParents;
        }
    }

    public NodeImpl getAttribute( int nodeNum ) throws DOMException {
        return new AttributeImpl(this, nodeNum);
    }

    public NodeImpl getNamespaceNode( int nodeNum ) throws DOMException {
        return new NamespaceNode(this, nodeNum);
    }

    public NodeImpl getNode( int nodeNum ) throws DOMException {
        if (nodeNum == 0) {
            return this;
        }
        if (nodeNum >= size) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "node not found");
        }
        NodeImpl node;
        switch (nodeKind[nodeNum]) {
        case Node.ELEMENT_NODE:
            node = new ElementImpl(this, nodeNum);
            break;
        case Node.TEXT_NODE:
            node = new TextImpl(this, nodeNum);
            break;
        case Node.COMMENT_NODE:
            node = new CommentImpl(this, nodeNum);
            break;
        case Node.PROCESSING_INSTRUCTION_NODE:
            node = new ProcessingInstructionImpl(this, nodeNum);
            break;
        case Node.CDATA_SECTION_NODE:
            node = new CDATASectionImpl(this, nodeNum);
            break;
        case NodeImpl.REFERENCE_NODE:
            node = new ReferenceNode(this, nodeNum);
            break;
        default:
            throw new DOMException(DOMException.NOT_FOUND_ERR, "node not found");
        }
        return node;
    }

    public NodeImpl getLastAttr() {
        if (nextAttr == 0) {
            return null;
        }
        return new AttributeImpl(this, nextAttr - 1);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.w3c.dom.Node#getParentNode()
     */
    @Override
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
        return (
            new DOMImplementation() {

                public Document createDocument(String namespaceURI, 
                    String qualifiedName, DocumentType doctype) throws DOMException {
                    return null;
                }

                public DocumentType createDocumentType( String qualifiedName, 
                        String publicId, String systemId ) throws DOMException {
                    return null;
                }

                public Object getFeature( String feature, String version ) {
                    return null;
                }

                public boolean hasFeature( String feature, String version ) {
                    return ("XML".equals(feature) && ("1.0".equals(version) ||
                        "2.0".equals(version)));
                }
            }
        );
    }

    /*
     * (non-Javadoc)
     *
     * @see org.w3c.dom.Document#getDocumentElement()
     */
    public Element getDocumentElement() {
        if( size == 1 ) {
            return null;
        }
        int nodeNum = 1;
        while (nodeKind[nodeNum] != Node.ELEMENT_NODE) {
            if (next[nodeNum] < nodeNum)
                {return null;}
            nodeNum = next[nodeNum];
        }
        return (Element)getNode(nodeNum);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.w3c.dom.Node#getFirstChild()
     */
    @Override
    public Node getFirstChild() {
        if (size > 1)
            {return getNode(1);}
        return null;
    }

    public Node getLastChild() {
        return getFirstChild();
    }

    public int getAttributesCountFor(int nodeNumber) {
        int count = 0;
        int attr = alpha[nodeNumber];
        if(-1 < attr) {
            while ((attr < nextAttr) && (attrParent[attr++] == nodeNumber)) {
                ++count;
            }
        }
        return count;
    }

    public int getNamespacesCountFor(int nodeNumber) {
        int count = 0;
        int ns = alphaLen[nodeNumber];
        if(-1 < ns) {
            while((ns < nextNamespace) && (namespaceParent[ns++] == nodeNumber)) {
                ++count;
            }
        }
        return count;
    }

    public int getChildCountFor(int nr) {
        int count = 0;
        int nextNode = getFirstChildFor(nr);
        while (nextNode > nr) {
            ++count;
            nextNode = next[nextNode];
        }
        return count;
    }

    public int getFirstChildFor(int nodeNumber) {
        final short level = treeLevel[nodeNumber];
        final int nextNode = nodeNumber + 1;
        if ((nextNode < size) && (treeLevel[nextNode] > level))
            {return nextNode;}
        return -1;
    }

    public int getNextSiblingFor(int nodeNumber) {
        final int nextNr = next[nodeNumber];
        return (nextNr < nodeNumber ) ? -1 : nextNr;
    }

    /**
     * The method <code>getParentNodeFor.</code>
     *
     * @param   nodeNumber  an <code>int</code> value
     *
     * @return  an <code>int</code> value
     */
    public int getParentNodeFor(int nodeNumber) {
        int nextNode = next[nodeNumber];
        while (nextNode > nodeNumber) {
            nextNode = next[nextNode];
        }
        return nextNode;
    }

    @Override
    public void selectChildren(NodeTest test, Sequence result) throws XPathException {
        if (size == 1) {
            return;
        }
        NodeImpl next = (NodeImpl)getFirstChild();
        while (next != null) {
            if (test.matches(next)) {
                result.add(next);
            }
            next = (NodeImpl)next.getNextSibling();
        }
    }

    @Override
    public void selectDescendants(boolean includeSelf, NodeTest test, Sequence result)
            throws XPathException {
        if (includeSelf && test.matches(this)) {
            result.add(this);
        }
        if (size == 1) {
            return;
        }
        NodeImpl next = (NodeImpl)getFirstChild();
        while (next != null) {
            if (test.matches(next)) {
                result.add(next);
            }
            next.selectDescendants(includeSelf, test, result);
            next = (NodeImpl)next.getNextSibling();
        }
    }

    @Override
    public void selectDescendantAttributes(NodeTest test, Sequence result)
            throws XPathException {
        if (size == 1) {
            return;
        }
        NodeImpl next = (NodeImpl)getFirstChild();
        while (next != null) {
            if (test.matches(next)) {
                result.add(next);
            }
            next.selectDescendantAttributes(test, result);
            next = (NodeImpl)next.getNextSibling();
        }
    }

    public NodeImpl selectById(String id) {
        if (size == 1) {
            return null;
        }
        final ElementImpl root = (ElementImpl)getDocumentElement();
        if (hasIdAttribute(root.getNodeNumber(), id)) {
            return root;
        }
        final int treeLevel = this.treeLevel[root.getNodeNumber()];
        int nextNode  = root.getNodeNumber();
        while ((++nextNode < document.size) && (document.treeLevel[nextNode] > treeLevel)) {
            if ((document.nodeKind[nextNode] == Node.ELEMENT_NODE) &&
                    hasIdAttribute(nextNode, id)) {
                return getNode(nextNode);
            }
        }
        return null;
    }

    public NodeImpl selectByIdref(String id) {
        if (size == 1) {
            return null;
        }
        final ElementImpl root = (ElementImpl)getDocumentElement();
        AttributeImpl attr = getIdrefAttribute(root.getNodeNumber(), id);
        if (attr != null) {
            return attr;
        }
        final int treeLevel = this.treeLevel[root.getNodeNumber()];
        int nextNode  = root.getNodeNumber();
        while ((++nextNode < document.size ) && (document.treeLevel[nextNode] > treeLevel)) {
            if (document.nodeKind[nextNode] == Node.ELEMENT_NODE) {
                attr = getIdrefAttribute(nextNode, id);
                if (attr != null) {
                    return attr;
                }
            }
        }
        return null;
    }

    private boolean hasIdAttribute(int nodeNumber, String id) {
        int attr = document.alpha[nodeNumber];
        if (-1 < attr) {
            while ((attr < document.nextAttr) && (document.attrParent[attr] == nodeNumber)) {
                if ((document.attrType[attr] == AttributeImpl.ATTR_ID_TYPE) &&
                        id.equals(document.attrValue[attr])) {
                    return true;
                }
                ++attr;
            }
        }
        return false;
    }

    private AttributeImpl getIdrefAttribute(int nodeNumber, String id) {
        int attr = document.alpha[nodeNumber];
        if (-1 < attr) {
            while ((attr < document.nextAttr) && (document.attrParent[attr] == nodeNumber)) {
                if ((document.attrType[attr] == AttributeImpl.ATTR_IDREF_TYPE) &&
                        id.equals(document.attrValue[attr])) {
                    return new AttributeImpl(this, attr);
                }
                ++attr;
            }
        }
        return null;
    }

    @Override
    public boolean matchChildren(NodeTest test) throws XPathException {
        if (size == 1) {
            return false;
        }
        NodeImpl next = (NodeImpl)getFirstChild();
        while (next != null) {
            if (test.matches(next)) {
                return true;
            }
            next = (NodeImpl)next.getNextSibling();
        }
        return false;
    }

    @Override
    public boolean matchDescendants(boolean includeSelf, NodeTest test) throws XPathException {
        if (includeSelf && test.matches(this)) {
            return true;
        }
        if (size == 1) {
            return true;
        }
        NodeImpl next = (NodeImpl)getFirstChild();
        while (next != null) {
            if (test.matches(next)) {
                return true;
            }
            if (next.matchDescendants(includeSelf, test)) {
                return true;
            }
            next = (NodeImpl)next.getNextSibling();
        }
        return false;
    }

    @Override
    public boolean matchDescendantAttributes(NodeTest test) throws XPathException {
        if (size == 1) {
            return false;
        }
        NodeImpl next = (NodeImpl)getFirstChild();
        while (next != null) {
            if (test.matches(next)) {
                return true;
            }
            if (next.matchDescendantAttributes(test)) {
                return true;
            }
            next = (NodeImpl)next.getNextSibling();
        }
        return false;
    }

    /*
    * (non-Javadoc)
    *
    * @see org.w3c.dom.Document#createElement(java.lang.String)
    */
    public Element createElement(String tagName) throws DOMException {
        QName qn;
        try {
            qn = QName.parse(getContext(), tagName);
        }
        catch (final XPathException e) {
            throw new DOMException(DOMException.NAMESPACE_ERR, e.getMessage());
        }
        final int nodeNum = addNode(Node.ELEMENT_NODE, (short)1, qn);
        return new ElementImpl(this, nodeNum);
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
    public ProcessingInstruction createProcessingInstruction(String arg0, String arg1)
            throws DOMException {
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
    public EntityReference createEntityReference(String arg0) throws DOMException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.w3c.dom.Document#getElementsByTagName(java.lang.String)
     */
    public NodeList getElementsByTagName(String name) {
        final NodeListImpl nl = new NodeListImpl();
        for (int i = 1; i < size; i++) {
            if (nodeKind[i] == Node.ELEMENT_NODE) {
                final QName qn = nodeName[i];
                if (qn.getStringValue().equals(name)) {
                    nl.add(getNode(i));
                }
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
    public Element createElementNS(String arg0, String arg1) throws DOMException {
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
    public NodeList getElementsByTagNameNS(String namespaceURI, String localName) {
        final NodeListImpl nl = new NodeListImpl();
        for (int i = 1; i < size; i++) {
            if (nodeKind[i] == Node.ELEMENT_NODE) {
                final QName qn = nodeName[i];
                if (qn.getNamespaceURI().equals(namespaceURI) && qn.getLocalName().equals(localName)) {
                    nl.add(getNode(i));
                }
            }
        }
        return nl;
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
    @Override
    public org.w3c.dom.Document getOwnerDocument() {
        return this;
    }

    /**
     * Copy the document fragment starting at the specified node to the given document builder.
     *
     * @param   node
     * @param   receiver
     *
     * @throws  SAXException  DOCUMENT ME!
     */
    public void copyTo(NodeImpl node, DocumentBuilderReceiver receiver) throws SAXException {
        copyTo(node, receiver, false);
    }

    protected void copyTo(NodeImpl node, DocumentBuilderReceiver receiver, boolean expandRefs)
            throws SAXException {
        final NodeImpl top = node;
        while (node != null) {
            copyStartNode(node, receiver, expandRefs);
            NodeImpl nextNode;
            if (node instanceof ReferenceNode) {
                //Nothing more to stream ?
                nextNode = null;
            } else {
                nextNode = (NodeImpl)node.getFirstChild();
            }
            while (nextNode == null) {
                copyEndNode(node, receiver);
                if ((top != null) && (top.nodeNumber == node.nodeNumber)) {
                    break;
                }
                //No nextNode if the top node is a Document node
                nextNode = (NodeImpl)node.getNextSibling();
                if (nextNode == null) {
                    node = (NodeImpl)node.getParentNode();
                    if ((node == null) || ((top != null) && (top.nodeNumber == node.nodeNumber))) {
                        copyEndNode(node, receiver);
                        break;
                    }
                }
            }
            node = nextNode;
        }
    }

    private void copyStartNode(NodeImpl node, DocumentBuilderReceiver receiver, boolean expandRefs)
            throws SAXException {
        final int nr = node.nodeNumber;
        switch(node.getNodeType()) {
        case Node.ELEMENT_NODE: {
            final QName nodeName = document.nodeName[nr];
            receiver.startElement(nodeName, null);
            int attr = document.alpha[nr];
            if(-1 < attr) {
                while ((attr < document.nextAttr) && (document.attrParent[attr] == nr)) {
                    final QName attrQName = document.attrName[attr];
                    receiver.attribute( attrQName, attrValue[attr] );
                    ++attr;
                }
            }
            int ns = document.alphaLen[nr];
            if (-1 < ns) {
                while ((ns < document.nextNamespace) && (document.namespaceParent[ns] == nr)) {
                    final QName nsQName = document.namespaceCode[ns];
                    receiver.addNamespaceNode(nsQName);
                    ++ns;
                }
            }
            break;
        }
        case Node.TEXT_NODE:
            receiver.characters(document.characters, document.alpha[nr], document.alphaLen[nr]);
            break;
        case Node.CDATA_SECTION_NODE:
            receiver.cdataSection(document.characters, document.alpha[nr], document.alphaLen[nr]);
            break;
        case Node.ATTRIBUTE_NODE:
            final QName attrQName = document.attrName[nr];
            receiver.attribute(attrQName, attrValue[nr]);
            break;
        case Node.COMMENT_NODE:
            receiver.comment(document.characters, document.alpha[nr], document.alphaLen[nr]);
            break;
        case Node.PROCESSING_INSTRUCTION_NODE:
            final QName qn   = document.nodeName[nr];
            final String data = new String(document.characters, document.alpha[nr], document.alphaLen[nr]);
            receiver.processingInstruction(qn.getLocalName(), data);
            break;
        case NodeImpl.NAMESPACE_NODE:
            receiver.addNamespaceNode(document.namespaceCode[nr]);
            break;
        case NodeImpl.REFERENCE_NODE:
            if (expandRefs) {
                DBBroker broker = null;
                try {
                    broker = getDatabase().get(null);
                    final Serializer serializer = broker.getSerializer();
                    serializer.reset();
                    serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
                    serializer.setReceiver(receiver);
                    serializer.toReceiver(document.references[document.alpha[nr]], false, false);
                } catch (final EXistException e) {
                    throw new SAXException(e);
                } finally {
                    getDatabase().release(broker);
                }
            } else {
                receiver.addReferenceNode(document.references[document.alpha[nr]]);
            }
            break;
        }
    }

    private void copyEndNode(NodeImpl node, DocumentBuilderReceiver receiver)
            throws SAXException {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            receiver.endElement(node.getQName());
        }
    }

    /**
     * Expand all reference nodes in the current document, i.e. replace them by real nodes. Reference nodes are just pointers to nodes from other
     * documents stored in the database. The XQuery engine uses reference nodes to speed up the creation of temporary doc fragments.
     *
     * <p>This method creates a new copy of the document contents and expands all reference nodes.</p>
     *
     * @throws  DOMException  DOCUMENT ME!
     */
    @Override
    public void expand() throws DOMException {
        if (size == 0) {
            return;
        }
        final DocumentImpl newDoc = expandRefs(null);
        copyDocContents(newDoc);
    }

    public DocumentImpl expandRefs(NodeImpl rootNode) throws DOMException {
        try {
            if (nextRef == 0) {
                computeNodeIds();
                return( this );
            }
            final MemTreeBuilder builder = new MemTreeBuilder(context);
            final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
            try {
                builder.startDocument();
                NodeImpl node = (rootNode == null) ? (NodeImpl)getFirstChild() : rootNode;
                while (node != null) {
                    copyTo(node, receiver, true);
                    node = (NodeImpl)node.getNextSibling();
                }
                receiver.endDocument();
            } catch (final SAXException e) {
                throw new DOMException(DOMException.INVALID_STATE_ERR, e.getMessage());
            }
            final DocumentImpl newDoc = builder.getDocument();
            newDoc.computeNodeIds();
            return newDoc;
        } catch (final EXistException e) {
            throw new DOMException(DOMException.INVALID_STATE_ERR, e.getMessage());
        }
    }

    public NodeImpl getNodeById(NodeId id) {
        expand();
        for (int i = 0; i < size; i++) {
            if (id.equals( nodeId[i])) {
                return getNode(i);
            }
        }
        return null;
    }

    private void computeNodeIds() throws EXistException {
        if (nodeId[0] != null) {
            return;
        }
        final NodeIdFactory nodeFactory = getDatabase().getNodeFactory();
        nodeId[0] = nodeFactory.documentNodeId();
        if (size == 1) {
            return;
        }
        NodeId nextId = nodeFactory.createInstance();
        NodeImpl next = (NodeImpl)getFirstChild();
        while (next != null) {
            computeNodeIds(nextId, next.nodeNumber);
            next = (NodeImpl)next.getNextSibling();
            nextId = nextId.nextSibling();
        }
    }

    private void computeNodeIds(NodeId id, int nodeNum) {
        nodeId[nodeNum] = id;
        if (nodeKind[nodeNum] == Node.ELEMENT_NODE) {
            NodeId nextId = id.newChild();
            int attr = document.alpha[nodeNum];
            if (-1 < attr) {
                while ((attr < document.nextAttr) && (document.attrParent[attr] == nodeNum)) {
                    attrNodeId[attr] = nextId;
                    nextId = nextId.nextSibling();
                    ++attr;
                }
            }
            int nextNode = getFirstChildFor(nodeNum);
            while (nextNode > nodeNum) {
                computeNodeIds(nextId, nextNode);
                nextNode = document.next[nextNode];
                if (nextNode > nodeNum) {
                    nextId = nextId.nextSibling();
                }
            }
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param  newDoc
     */
    private void copyDocContents( DocumentImpl newDoc ) {
        namePool         = newDoc.namePool;
        nodeKind         = newDoc.nodeKind;
        treeLevel        = newDoc.treeLevel;
        next             = newDoc.next;
        nodeName         = newDoc.nodeName;
        nodeId           = newDoc.nodeId;
        alpha            = newDoc.alpha;
        alphaLen         = newDoc.alphaLen;
        characters       = newDoc.characters;
        nextChar         = newDoc.nextChar;
        attrName         = newDoc.attrName;
        attrNodeId       = newDoc.attrNodeId;
        attrParent       = newDoc.attrParent;
        attrValue        = newDoc.attrValue;
        nextAttr         = newDoc.nextAttr;
        namespaceParent  = newDoc.namespaceParent;
        namespaceCode    = newDoc.namespaceCode;
        nextNamespace    = newDoc.nextNamespace;
        size             = newDoc.size;
        documentRootNode = newDoc.documentRootNode;
        references       = newDoc.references;
        nextRef          = newDoc.nextRef;
    }

    /**
     * Stream the specified document fragment to a receiver. This method
     * is called by the serializer to output in-memory nodes.
     *
     * @param   serializer
     * @param   node
     * @param   receiver
     *
     * @throws  SAXException
     */
    public void streamTo( Serializer serializer, NodeImpl node, Receiver receiver )
            throws SAXException {
        final NodeImpl top = node;
        while (node != null) {
            startNode(serializer, node, receiver);
            NodeImpl nextNode;
            if (node instanceof ReferenceNode) {
                //Nothing more to stream ?
                nextNode = null;
            } else {
                nextNode = (NodeImpl)node.getFirstChild();
            }
            while (nextNode == null) {
                endNode(node, receiver);
                if ((top != null) && (top.nodeNumber == node.nodeNumber)) {
                    break;
                }
                nextNode = (NodeImpl)node.getNextSibling();
                if (nextNode == null) {
                    node = (NodeImpl)node.getParentNode();
                    if ((node == null) || ((top != null) && (top.nodeNumber == node.nodeNumber))) {
                        endNode(node, receiver);
                        break;
                    }
                }
            }
            node = nextNode;
        }
    }

    private void startNode(Serializer serializer, NodeImpl node, Receiver receiver)
            throws SAXException {
        final int nr = node.nodeNumber;
        switch (node.getNodeType()) {
        case Node.ELEMENT_NODE:
            final QName nodeName = document.nodeName[nr];
            //Output required namespace declarations
            int ns = document.alphaLen[nr];
            if (-1 < ns) {
                while ((ns < document.nextNamespace) && (document.namespaceParent[ns] == nr)) {
                    final QName nsQName = document.namespaceCode[ns];
                    if ("xmlns".equals(nsQName.getLocalName())) {
                        receiver.startPrefixMapping("", nsQName.getNamespaceURI());
                    } else {
                        receiver.startPrefixMapping(nsQName.getLocalName(), nsQName.getNamespaceURI());
                    }
                    ++ns;
                }
            }
            //Create the attribute list
            AttrList attribs = null;
            int attr = document.alpha[nr];
            if (-1 < attr) {
                attribs = new AttrList();
                while ((attr < document.nextAttr) && (document.attrParent[attr] == nr)) {
                    final QName attrQName = document.attrName[attr];
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
            final QName attrQName = document.attrName[nr];
            receiver.attribute(attrQName, attrValue[nr]);
            break;
        case Node.COMMENT_NODE:
            receiver.comment(document.characters, document.alpha[nr], document.alphaLen[nr]);
            break;
        case Node.PROCESSING_INSTRUCTION_NODE:
            final QName qn = document.nodeName[nr];
            final String data = new String(document.characters, document.alpha[nr], document.alphaLen[nr]);
            receiver.processingInstruction(qn.getLocalName(), data);
            break;
        case Node.CDATA_SECTION_NODE:
            receiver.cdataSection(document.characters, document.alpha[nr], document.alphaLen[nr]);
            break;
        case NodeImpl.REFERENCE_NODE:
            serializer.toReceiver(document.references[document.alpha[nr]], true, false);
            break;
        }
    }

    private void endNode(NodeImpl node, Receiver receiver) throws SAXException {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            receiver.endElement(node.getQName());
            //End all prefix mappings used for the element
            final int nr = node.nodeNumber;
            int ns = document.alphaLen[nr];
            if (-1 < ns) {
                while ((ns < document.nextNamespace) && (document.namespaceParent[ns] == nr)) {
                    final QName nsQName = document.namespaceCode[ns];
                    if ("xmlns".equals( nsQName.getLocalName())) {
                        receiver.endPrefixMapping("");
                    } else {
                        receiver.endPrefixMapping(nsQName.getLocalName());
                    }
                    ++ns;
                }
            }
        }
    }

    public org.exist.dom.DocumentImpl makePersistent() throws XPathException {
        if (size <= 1) {
            return null;
        }
        return context.storeTemporaryDoc(this);
    }

    public int getChildCount() {
        int count = 0;
        int top = (size > 1) ? 1 : -1;
        while (top > 0) {
            ++count;
            top = getNextSiblingFor(top);
        }
        return count;
    }

    @Override
    public boolean hasChildNodes() {
        return (getChildCount() > 0);
    }

    public NodeList getChildNodes() {
        final NodeListImpl nl = new NodeListImpl(1);
        final Element el = getDocumentElement();
        if (el != null)
            {nl.add(el);}
        return nl;
    }

    @Override
    public String getLocalName() {
        return "";
    }

    @Override
    public String getNamespaceURI() {
        return "";
    }

    /**
     * ? @see org.w3c.dom.Document#getInputEncoding()
     *
     * @return  DOCUMENT ME!
     */
    public String getInputEncoding() {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return null;
    }

    /**
     * ? @see org.w3c.dom.Document#getXmlEncoding()
     *
     * @return  DOCUMENT ME!
     */
    public String getXmlEncoding() {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return null;
    }

    /**
     * ? @see org.w3c.dom.Document#getXmlStandalone()
     *
     * @return  DOCUMENT ME!
     */
    public boolean getXmlStandalone() {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return false;
    }

    /**
     * ? @see org.w3c.dom.Document#setXmlStandalone(boolean)
     *
     * @param   xmlStandalone  DOCUMENT ME!
     *
     * @throws  DOMException  DOCUMENT ME!
     */
    public void setXmlStandalone( boolean xmlStandalone ) throws DOMException {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
    }

    /**
     * ? @see org.w3c.dom.Document#getXmlVersion()
     *
     * @return  DOCUMENT ME!
     */
    public String getXmlVersion() {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return null;
    }

    /**
     * ? @see org.w3c.dom.Document#setXmlVersion(java.lang.String)
     *
     * @param   xmlVersion  DOCUMENT ME!
     *
     * @throws  DOMException  DOCUMENT ME!
     */
    public void setXmlVersion(String xmlVersion) throws DOMException {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
    }

    /**
     * ? @see org.w3c.dom.Document#getStrictErrorChecking()
     *
     * @return  DOCUMENT ME!
     */
    public boolean getStrictErrorChecking() {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return false;
    }

    /**
     * ? @see org.w3c.dom.Document#setStrictErrorChecking(boolean)
     *
     * @param  strictErrorChecking  DOCUMENT ME!
     */
    public void setStrictErrorChecking( boolean strictErrorChecking ) {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
    }

    /**
     * ? @see org.w3c.dom.Document#getDocumentURI()
     *
     * @return  DOCUMENT ME!
     */
    public String getDocumentURI() {
        return documentURI;
    }

    /**
     * ? @see org.w3c.dom.Document#setDocumentURI(java.lang.String)
     *
     * @param  documentURI  DOCUMENT ME!
     */
    public void setDocumentURI(String documentURI) {
        this.documentURI = documentURI;
    }

    /**
     * ? @see org.w3c.dom.Document#adoptNode(org.w3c.dom.Node)
     *
     * @param   source  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  DOMException  DOCUMENT ME!
     */
    public Node adoptNode(Node source) throws DOMException {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return null;
    }

    /**
     * ? @see org.w3c.dom.Document#getDomConfig()
     *
     * @return  DOCUMENT ME!
     */
    public DOMConfiguration getDomConfig() {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return null;
    }

    /**
     * ? @see org.w3c.dom.Document#normalizeDocument()
     */
    public void normalizeDocument() {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
    }

    /**
     * ? @see org.w3c.dom.Document#renameNode(org.w3c.dom.Node, java.lang.String, java.lang.String)
     *
     * @param   n              DOCUMENT ME!
     * @param   namespaceURI   DOCUMENT ME!
     * @param   qualifiedName  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  DOMException  DOCUMENT ME!
     */
    public Node renameNode(Node n, String namespaceURI, String qualifiedName)
            throws DOMException {
        // maybe _TODO_ - new DOM interfaces - Java 5.0
        return null;
    }

    public void setContext(XQueryContext context) {
        this.context = context;
    }

    public XQueryContext getContext() {
        return context;
    }

    /**
     * ? @see org.w3c.dom.Node#getBaseURI()
     *
     * @return  DOCUMENT ME!
     */
    @Override
    public String getBaseURI() {
        final Element el = getDocumentElement();
        if (el != null) {
            final String baseURI = getDocumentElement().getAttributeNS(Namespaces.XML_NS, "base");
            if (baseURI != null)
                {return baseURI;}
        }
        final String docURI = getDocumentURI();
        if (docURI != null)
            {return docURI;}
        else {
            if (context.isBaseURIDeclared()) {
                try {
                    return context.getBaseURI().getStringValue();
                } catch (final XPathException e) {
                    //TODO : make something !
                }
            }
            return XmldbURI.EMPTY_URI.toString();
        }
    }

    @Override
    public int getItemType() {
        return Type.DOCUMENT;
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("in-memory#");
        result.append("document {");
        if (size != 1) {
            int nodeNum = 1;
            while (true) {
                result.append(getNode(nodeNum).toString());
                if (next[nodeNum] < nodeNum) {
                    break;
                }
                nodeNum = next[nodeNum];
            }
        }
        result.append("} ");
        return(result.toString());
    }

    public int getNextNodeNumber(int nextNode) throws DOMException {
        return document.next[nextNode];
    }

    @Override
    public XmldbURI getURI() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void selectAttributes(NodeTest test, Sequence result)
            throws XPathException {
        // TODO Auto-generated method stub
    }

    @Override
    public int getDocId() {
        return 0;
    }

	@Override
	public Object getUUID() {
		return null;
	}
}
