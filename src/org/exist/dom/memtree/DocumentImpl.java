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
package org.exist.dom.memtree;

import org.exist.Database;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.dom.NodeListImpl;
import org.exist.dom.QName;
import org.exist.dom.persistent.NodeProxy;
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

import javax.xml.XMLConstants;
import java.util.Arrays;


/**
 * An in-memory implementation of Document.
 * <p/>
 * <p>This implementation stores all node data in the document object. Nodes from another document, i.e. a persistent document in the database, can be
 * stored as reference nodes, i.e. the nodes are not copied into this document object. Instead a reference is inserted which will only be expanded
 * during serialization.</p>
 *
 * @author wolf
 */
public class DocumentImpl extends NodeImpl<DocumentImpl> implements Document {

    private static final int NODE_SIZE = 16;
    private static final int ATTR_SIZE = 8;
    private static final int CHAR_BUF_SIZE = 256;
    private static final int REF_SIZE = 8;

    private static long nextDocId = 0;

    // holds the node type of a node
    protected short[] nodeKind = null;

    // the tree level of a node
    protected short[] treeLevel;

    // the node number of the next sibling
    protected int[] next;

    // pointer into the namePool
    protected QName[] nodeName;

    protected NodeId[] nodeId;

    //alphanumeric content
    protected int[] alpha;
    protected int[] alphaLen;
    protected char[] characters = null;
    protected int nextChar = 0;

    // attributes
    protected QName[] attrName;
    protected int[] attrType;
    protected NodeId[] attrNodeId;
    protected int[] attrParent;
    protected String[] attrValue;
    protected int nextAttr = 0;

    // namespaces
    protected int[] namespaceParent = null;
    protected QName[] namespaceCode = null;
    protected int nextNamespace = 0;

    // the current number of nodes in the doc
    protected int size = 1;

    protected int documentRootNode = -1;

    protected String documentURI = null;

    // reference nodes (link to an external, persistent document fragment)
    protected NodeProxy[] references = null;
    protected int nextReferenceIdx = 0;
    // end reference nodes


    protected XQueryContext context;
    protected final boolean explicitlyCreated;
    protected final long docId;
    private Database db = null;
    protected NamePool namePool;

    boolean replaceAttribute = false;


    public DocumentImpl(final XQueryContext context, final boolean explicitlyCreated) {
        super(null, 0);
        this.context = context;
        this.explicitlyCreated = explicitlyCreated;
        this.docId = createDocId();
        if(context == null) {
            namePool = new NamePool();
        } else {
            db = context.getDatabase();
            namePool = context.getSharedNamePool();
        }
    }

    private Database getDatabase() {
        if(db == null) {
            try {
                db = BrokerPool.getInstance();
            } catch(final EXistException e) {
                throw new RuntimeException(e);
            }
        }
        return db;
    }

    private static long createDocId() {
        return nextDocId++;
    }

    private void init() {
        nodeKind = new short[NODE_SIZE];
        treeLevel = new short[NODE_SIZE];
        next = new int[NODE_SIZE];
        Arrays.fill(next, -1);
        nodeName = new QName[NODE_SIZE];
        nodeId = new NodeId[NODE_SIZE];
        alpha = new int[NODE_SIZE];
        alphaLen = new int[NODE_SIZE];
        Arrays.fill(alphaLen, -1);
        attrName = new QName[ATTR_SIZE];
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
        nextReferenceIdx = 0;
        references = null;
    }

    public int getSize() {
        return size;
    }

    public int addNode(final short kind, final short level, final QName qname) {
        if(nodeKind == null) {
            init();
        }
        if(size == nodeKind.length) {
            grow();
        }
        nodeKind[size] = kind;
        treeLevel[size] = level;
        nodeName[size] = qname != null ? namePool.getSharedName(qname) : null;
        alpha[size] = -1; // undefined
        next[size] = -1;
        return (size++);
    }

    public void addChars(final int nodeNum, final char[] ch, final int start, final int len) {
        if(nodeKind == null) {
            init();
        }
        if(characters == null) {
            characters = new char[len > CHAR_BUF_SIZE ? len : CHAR_BUF_SIZE];
        } else if((nextChar + len) >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if(newLen < (nextChar + len)) {
                newLen = nextChar + len;
            }
            final char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alpha[nodeNum] = nextChar;
        alphaLen[nodeNum] = len;
        System.arraycopy(ch, start, characters, nextChar, len);
        nextChar += len;
    }

    public void addChars(final int nodeNum, final CharSequence s) {
        if(nodeKind == null) {
            init();
        }
        int len = (s == null) ? 0 : s.length();
        if(characters == null) {
            characters = new char[(len > CHAR_BUF_SIZE) ? len : CHAR_BUF_SIZE];
        } else if((nextChar + len) >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if(newLen < (nextChar + len)) {
                newLen = nextChar + len;
            }
            final char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alpha[nodeNum] = nextChar;
        alphaLen[nodeNum] = len;
        for(int i = 0; i < len; i++) {
            characters[nextChar++] = s.charAt(i);
        }
    }

    public void appendChars(final int nodeNum, final char[] ch, final int start, final int len) {
        if(characters == null) {
            characters = new char[(len > CHAR_BUF_SIZE) ? len : CHAR_BUF_SIZE];
        } else if((nextChar + len) >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if(newLen < (nextChar + len)) {
                newLen = nextChar + len;
            }
            final char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alphaLen[nodeNum] = alphaLen[nodeNum] + len;
        System.arraycopy(ch, start, characters, nextChar, len);
        nextChar += len;
    }

    public void appendChars(final int nodeNum, final CharSequence s) {
        final int len = s.length();
        if(characters == null) {
            characters = new char[(len > CHAR_BUF_SIZE) ? len : CHAR_BUF_SIZE];
        } else if((nextChar + len) >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if(newLen < (nextChar + len)) {
                newLen = nextChar + len;
            }
            final char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alphaLen[nodeNum] = alphaLen[nodeNum] + len;
        for(int i = 0; i < len; i++) {
            characters[nextChar++] = s.charAt(i);
        }
    }

    public void addReferenceNode(final int nodeNum, final NodeProxy proxy) {
        if(nodeKind == null) {
            init();
        }
        if((references == null) || (nextReferenceIdx == references.length)) {
            growReferences();
        }
        references[nextReferenceIdx] = proxy;
        alpha[nodeNum] = nextReferenceIdx++;
    }

    public boolean hasReferenceNodes() {
        return references != null && references[0] != null;
    }

    public void replaceReferenceNode(final int nodeNum, final CharSequence ch) {
        nodeKind[nodeNum] = Node.TEXT_NODE;
        references[alpha[nodeNum]] = null;
        addChars(nodeNum, ch);
    }

    public int addAttribute(final int nodeNum, final QName qname, final String value, final int type) throws DOMException {
        if(nodeKind == null) {
            init();
        }
        if((nodeNum > 0) && !(nodeKind[nodeNum] == Node.ELEMENT_NODE || nodeKind[nodeNum] == NodeImpl.NAMESPACE_NODE)) {
            throw (new DOMException(DOMException.INUSE_ATTRIBUTE_ERR,
                "err:XQTY0024: An attribute node cannot follow a node that is not an attribute node."));
        }
        int prevAttr = nextAttr - 1;
        int attrN;
        //Check if an attribute with the same qname exists in the parent element
        while((nodeNum > 0) && (prevAttr > -1) && (attrParent[prevAttr] == nodeNum)) {
            attrN = prevAttr--;
            final QName prevQn = attrName[attrN];
            if(prevQn.equals(qname)) {
                if(replaceAttribute) {
                    attrValue[attrN] = value;
                    attrType[attrN] = type;
                    return attrN;
                } else {
                    throw new DOMException(DOMException.INUSE_ATTRIBUTE_ERR,
                        "err:XQDY0025: element has more than one attribute '" + qname + "'");
                }
            }
        }
        if(nextAttr == attrName.length) {
            growAttributes();
        }
        final QName attrQname = new QName(qname.getLocalPart(), qname.getNamespaceURI(), qname.getPrefix(), ElementValue.ATTRIBUTE);
        attrParent[nextAttr] = nodeNum;
        attrName[nextAttr] = namePool.getSharedName(attrQname);
        attrValue[nextAttr] = value;
        attrType[nextAttr] = type;
        if(alpha[nodeNum] < 0) {
            alpha[nodeNum] = nextAttr;
        }
        return (nextAttr++);
    }

    public int addNamespace(final int nodeNum, final QName qname) {
        if(nodeKind == null) {
            init();
        }
        if((namespaceCode == null) || (nextNamespace == namespaceCode.length)) {
            growNamespaces();
        }
        namespaceCode[nextNamespace] = namePool.getSharedName(qname);
        namespaceParent[nextNamespace] = nodeNum;
        if(alphaLen[nodeNum] < 0) {
            alphaLen[nodeNum] = nextNamespace;
        }
        return nextNamespace++;
    }

    public short getTreeLevel(final int nodeNum) {
        return treeLevel[nodeNum];
    }

    public int getLastNode() {
        return size - 1;
    }

    public short getNodeType(final int nodeNum) {
        if((nodeKind == null) || (nodeNum < 0)) {
            return -1;
        }
        return nodeKind[nodeNum];
    }

    @Override
    public String getStringValue() {
        if(document == null) {
            return "";
        }
        return super.getStringValue();
    }

    private void grow() {
        final int newSize = (size * 3) / 2;

        final short[] newNodeKind = new short[newSize];
        System.arraycopy(nodeKind, 0, newNodeKind, 0, size);
        nodeKind = newNodeKind;

        final short[] newTreeLevel = new short[newSize];
        System.arraycopy(treeLevel, 0, newTreeLevel, 0, size);
        treeLevel = newTreeLevel;

        final int[] newNext = new int[newSize];
        Arrays.fill(newNext, -1);
        System.arraycopy(next, 0, newNext, 0, size);
        next = newNext;

        final QName[] newNodeName = new QName[newSize];
        System.arraycopy(nodeName, 0, newNodeName, 0, size);
        nodeName = newNodeName;

        final NodeId[] newNodeId = new NodeId[newSize];
        System.arraycopy(nodeId, 0, newNodeId, 0, size);
        nodeId = newNodeId;

        final int[] newAlpha = new int[newSize];
        System.arraycopy(alpha, 0, newAlpha, 0, size);
        alpha = newAlpha;

        final int[] newAlphaLen = new int[newSize];
        Arrays.fill(newAlphaLen, -1);
        System.arraycopy(alphaLen, 0, newAlphaLen, 0, size);
        alphaLen = newAlphaLen;
    }

    private void growAttributes() {
        final int size = attrName.length;
        final int newSize = (size * 3) / 2;

        final QName[] newAttrName = new QName[newSize];
        System.arraycopy(attrName, 0, newAttrName, 0, size);
        attrName = newAttrName;

        final int[] newAttrParent = new int[newSize];
        System.arraycopy(attrParent, 0, newAttrParent, 0, size);
        attrParent = newAttrParent;

        final String[] newAttrValue = new String[newSize];
        System.arraycopy(attrValue, 0, newAttrValue, 0, size);
        attrValue = newAttrValue;

        final int[] newAttrType = new int[newSize];
        System.arraycopy(attrType, 0, newAttrType, 0, size);
        attrType = newAttrType;

        final NodeId[] newNodeId = new NodeId[newSize];
        System.arraycopy(attrNodeId, 0, newNodeId, 0, size);
        attrNodeId = newNodeId;
    }

    private void growReferences() {
        if(references == null) {
            references = new NodeProxy[REF_SIZE];
        } else {
            final int size = references.length;
            final int newSize = (size * 3) / 2;
            final NodeProxy[] newReferences = new NodeProxy[newSize];
            System.arraycopy(references, 0, newReferences, 0, size);
            references = newReferences;
        }
    }

    private void growNamespaces() {
        if(namespaceCode == null) {
            namespaceCode = new QName[5];
            namespaceParent = new int[5];
        } else {
            final int size = namespaceCode.length;
            final int newSize = (size * 3) / 2;

            final QName[] newCodes = new QName[newSize];
            System.arraycopy(namespaceCode, 0, newCodes, 0, size);
            namespaceCode = newCodes;

            final int[] newParents = new int[newSize];
            System.arraycopy(namespaceParent, 0, newParents, 0, size);
            namespaceParent = newParents;
        }
    }

    public NodeImpl getAttribute(final int nodeNum) throws DOMException {
        return new AttrImpl(this, nodeNum);
    }

    public NodeImpl getNamespaceNode(final int nodeNum) throws DOMException {
        return new NamespaceNode(this, nodeNum);
    }

    public NodeImpl getNode(final int nodeNum) throws DOMException {
        if(nodeNum == 0) {
            return this;
        }
        if(nodeNum >= size) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "node not found");
        }
        final NodeImpl node;
        switch(nodeKind[nodeNum]) {
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
        if(nextAttr == 0) {
            return null;
        }
        return new AttrImpl(this, nextAttr - 1);
    }

    @Override
    public Node getParentNode() {
        return null;
    }


    @Override
    public DocumentType getDoctype() {
        return null;
    }

    @Override
    public DOMImplementation getImplementation() {
        return new DOMImplementation() {

                @Override
                public Document createDocument(final String namespaceURI,
                        final String qualifiedName, final DocumentType doctype) throws DOMException {
                    return null;
                }

                @Override
                public DocumentType createDocumentType(final String qualifiedName,
                        final String publicId, final String systemId) throws DOMException {
                    return null;
                }

                @Override
                public Object getFeature(final String feature, final String version) {
                    return null;
                }

                @Override
                public boolean hasFeature(final String feature, final String version) {
                    return ("XML".equals(feature) && ("1.0".equals(version) ||
                        "2.0".equals(version)));
                }
            };
    }

    @Override
    public Element getDocumentElement() {
        if(size == 1) {
            return null;
        }
        int nodeNum = 1;
        while(nodeKind[nodeNum] != Node.ELEMENT_NODE) {
            if(next[nodeNum] < nodeNum) {
                return null;
            }
            nodeNum = next[nodeNum];
        }
        return (Element)getNode(nodeNum);
    }

    @Override
    public Node getFirstChild() {
        if(size > 1) {
            return getNode(1);
        }
        return null;
    }

    @Override
    public Node getLastChild() {
        return getFirstChild();
    }

    public int getAttributesCountFor(final int nodeNumber) {
        int count = 0;
        int attr = alpha[nodeNumber];
        if(-1 < attr) {
            while((attr < nextAttr) && (attrParent[attr++] == nodeNumber)) {
                ++count;
            }
        }
        return count;
    }

    public int getNamespacesCountFor(final int nodeNumber) {
        int count = 0;
        int ns = alphaLen[nodeNumber];
        if(-1 < ns) {
            while((ns < nextNamespace) && (namespaceParent[ns++] == nodeNumber)) {
                ++count;
            }
        }
        return count;
    }

    public int getChildCountFor(final int nr) {
        int count = 0;
        int nextNode = getFirstChildFor(nr);
        while(nextNode > nr) {
            ++count;
            nextNode = next[nextNode];
        }
        return count;
    }

    public int getFirstChildFor(final int nodeNumber) {
        final short level = treeLevel[nodeNumber];
        final int nextNode = nodeNumber + 1;
        if((nextNode < size) && (treeLevel[nextNode] > level)) {
            return nextNode;
        }
        return -1;
    }

    public int getNextSiblingFor(final int nodeNumber) {
        final int nextNr = next[nodeNumber];
        return nextNr < nodeNumber ? -1 : nextNr;
    }

    public int getParentNodeFor(final int nodeNumber) {
        int nextNode = next[nodeNumber];
        while(nextNode > nodeNumber) {
            nextNode = next[nextNode];
        }
        return nextNode;
    }

    @Override
    public void selectChildren(final NodeTest test, final Sequence result) throws XPathException {
        if(size == 1) {
            return;
        }
        NodeImpl next = (NodeImpl) getFirstChild();
        while(next != null) {
            if(test.matches(next)) {
                result.add(next);
            }
            next = (NodeImpl) next.getNextSibling();
        }
    }

    @Override
    public void selectDescendants(final boolean includeSelf, final NodeTest test, final Sequence result)
        throws XPathException {
        if(includeSelf && test.matches(this)) {
            result.add(this);
        }
        if(size == 1) {
            return;
        }
        NodeImpl next = (NodeImpl) getFirstChild();
        while(next != null) {
            if(test.matches(next)) {
                result.add(next);
            }
            next.selectDescendants(includeSelf, test, result);
            next = (NodeImpl) next.getNextSibling();
        }
    }

    @Override
    public void selectDescendantAttributes(final NodeTest test, final Sequence result)
        throws XPathException {
        if(size == 1) {
            return;
        }
        NodeImpl next = (NodeImpl) getFirstChild();
        while(next != null) {
            if(test.matches(next)) {
                result.add(next);
            }
            next.selectDescendantAttributes(test, result);
            next = (NodeImpl) next.getNextSibling();
        }
    }

    public NodeImpl selectById(final String id) {
        if(size == 1) {
            return null;
        }
        final ElementImpl root = (ElementImpl) getDocumentElement();
        if(hasIdAttribute(root.getNodeNumber(), id)) {
            return root;
        }
        final int treeLevel = this.treeLevel[root.getNodeNumber()];
        int nextNode = root.getNodeNumber();
        while((++nextNode < document.size) && (document.treeLevel[nextNode] > treeLevel)) {
            if((document.nodeKind[nextNode] == Node.ELEMENT_NODE) &&
                hasIdAttribute(nextNode, id)) {
                return getNode(nextNode);
            }
        }
        return null;
    }

    public NodeImpl selectByIdref(final String id) {
        if(size == 1) {
            return null;
        }
        final ElementImpl root = (ElementImpl) getDocumentElement();
        AttrImpl attr = getIdrefAttribute(root.getNodeNumber(), id);
        if(attr != null) {
            return attr;
        }
        final int treeLevel = this.treeLevel[root.getNodeNumber()];
        int nextNode = root.getNodeNumber();
        while((++nextNode < document.size) && (document.treeLevel[nextNode] > treeLevel)) {
            if(document.nodeKind[nextNode] == Node.ELEMENT_NODE) {
                attr = getIdrefAttribute(nextNode, id);
                if(attr != null) {
                    return attr;
                }
            }
        }
        return null;
    }

    private boolean hasIdAttribute(final int nodeNumber, final String id) {
        int attr = document.alpha[nodeNumber];
        if(-1 < attr) {
            while((attr < document.nextAttr) && (document.attrParent[attr] == nodeNumber)) {
                if((document.attrType[attr] == AttrImpl.ATTR_ID_TYPE) &&
                    id.equals(document.attrValue[attr])) {
                    return true;
                }
                ++attr;
            }
        }
        return false;
    }

    private AttrImpl getIdrefAttribute(final int nodeNumber, final String id) {
        int attr = document.alpha[nodeNumber];
        if(-1 < attr) {
            while((attr < document.nextAttr) && (document.attrParent[attr] == nodeNumber)) {
                if((document.attrType[attr] == AttrImpl.ATTR_IDREF_TYPE) &&
                    id.equals(document.attrValue[attr])) {
                    return new AttrImpl(this, attr);
                }
                ++attr;
            }
        }
        return null;
    }

    @Override
    public boolean matchChildren(final NodeTest test) throws XPathException {
        if(size == 1) {
            return false;
        }
        NodeImpl next = (NodeImpl) getFirstChild();
        while(next != null) {
            if(test.matches(next)) {
                return true;
            }
            next = (NodeImpl) next.getNextSibling();
        }
        return false;
    }

    @Override
    public boolean matchDescendants(final boolean includeSelf, final NodeTest test) throws XPathException {
        if(includeSelf && test.matches(this)) {
            return true;
        }
        if(size == 1) {
            return true;
        }
        NodeImpl next = (NodeImpl) getFirstChild();
        while(next != null) {
            if(test.matches(next)) {
                return true;
            }
            if(next.matchDescendants(includeSelf, test)) {
                return true;
            }
            next = (NodeImpl) next.getNextSibling();
        }
        return false;
    }

    @Override
    public boolean matchDescendantAttributes(final NodeTest test) throws XPathException {
        if(size == 1) {
            return false;
        }
        NodeImpl next = (NodeImpl) getFirstChild();
        while(next != null) {
            if(test.matches(next)) {
                return true;
            }
            if(next.matchDescendantAttributes(test)) {
                return true;
            }
            next = (NodeImpl) next.getNextSibling();
        }
        return false;
    }

    @Override
    public Element createElement(final String tagName) throws DOMException {
        try {
            final QName qn = QName.parse(getContext(), tagName);
            final int nodeNum = addNode(Node.ELEMENT_NODE, (short) 1, qn);
            return new ElementImpl(this, nodeNum);
        } catch(final XPathException e) {
            throw new DOMException(DOMException.NAMESPACE_ERR, e.getMessage());
        }
    }

    @Override
    public DocumentFragment createDocumentFragment() {
        return null;
    }

    @Override
    public Text createTextNode(final String data) {
        return null;
    }

    @Override
    public Comment createComment(final String data) {
        return null;
    }

    @Override
    public CDATASection createCDATASection(final String data) throws DOMException {
        return null;
    }

    @Override
    public ProcessingInstruction createProcessingInstruction(final String target, final String data)
            throws DOMException {
        return null;
    }

    @Override
    public Attr createAttribute(final String name) throws DOMException {
        return null;
    }

    @Override
    public EntityReference createEntityReference(final String name) throws DOMException {
        return null;
    }

    @Override
    public NodeList getElementsByTagName(final String tagname) {
        final NodeListImpl nl = new NodeListImpl();
        for(int i = 1; i < size; i++) {
            if(nodeKind[i] == Node.ELEMENT_NODE) {
                final QName qn = nodeName[i];
                if(qn.getStringValue().equals(tagname)) {
                    nl.add(getNode(i));
                }
            }
        }
        return nl;
    }

    @Override
    public Node importNode(final Node importedNode, final boolean deep) throws DOMException {
        return null;
    }

    @Override
    public Element createElementNS(final String namespaceURI, final String qualifiedName) throws DOMException {
        return null;
    }

    @Override
    public Attr createAttributeNS(final String namespaceURI, final String qualifiedName) throws DOMException {
        return null;
    }

    @Override
    public NodeList getElementsByTagNameNS(final String namespaceURI, final String localName) {
        final NodeListImpl nl = new NodeListImpl();
        for(int i = 1; i < size; i++) {
            if(nodeKind[i] == Node.ELEMENT_NODE) {
                final QName qn = nodeName[i];
                if(qn.getNamespaceURI().equals(namespaceURI) && qn.getLocalPart().equals(localName)) {
                    nl.add(getNode(i));
                }
            }
        }
        return nl;
    }

    @Override
    public Element getElementById(final String elementId) {
        return null;
    }

    @Override
    public DocumentImpl getOwnerDocument() {
        return this;
    }

    /**
     * Copy the document fragment starting at the specified node to the given document builder.
     *
     * @param node
     * @param receiver
     * @throws SAXException DOCUMENT ME!
     */
    public void copyTo(final NodeImpl node, final DocumentBuilderReceiver receiver) throws SAXException {
        copyTo(node, receiver, false);
    }

    protected void copyTo(NodeImpl node, final DocumentBuilderReceiver receiver, final boolean expandRefs)
        throws SAXException {
        final NodeImpl top = node;
        while(node != null) {
            copyStartNode(node, receiver, expandRefs);
            NodeImpl nextNode;
            if(node instanceof ReferenceNode) {
                //Nothing more to stream ?
                nextNode = null;
            } else {
                nextNode = (NodeImpl) node.getFirstChild();
            }
            while(nextNode == null) {
                copyEndNode(node, receiver);
                if((top != null) && (top.nodeNumber == node.nodeNumber)) {
                    break;
                }
                //No nextNode if the top node is a Document node
                nextNode = (NodeImpl) node.getNextSibling();
                if(nextNode == null) {
                    node = (NodeImpl) node.getParentNode();
                    if((node == null) || ((top != null) && (top.nodeNumber == node.nodeNumber))) {
                        copyEndNode(node, receiver);
                        break;
                    }
                }
            }
            node = nextNode;
        }
    }

    private void copyStartNode(final NodeImpl node, final DocumentBuilderReceiver receiver, final boolean expandRefs)
        throws SAXException {
        final int nr = node.nodeNumber;
        switch(node.getNodeType()) {
            case Node.ELEMENT_NODE: {
                final QName nodeName = document.nodeName[nr];
                receiver.startElement(nodeName, null);
                int attr = document.alpha[nr];
                if(-1 < attr) {
                    while((attr < document.nextAttr) && (document.attrParent[attr] == nr)) {
                        final QName attrQName = document.attrName[attr];
                        receiver.attribute(attrQName, attrValue[attr]);
                        ++attr;
                    }
                }
                int ns = document.alphaLen[nr];
                if(-1 < ns) {
                    while((ns < document.nextNamespace) && (document.namespaceParent[ns] == nr)) {
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
                final QName piQName = document.nodeName[nr];
                final String data = new String(document.characters, document.alpha[nr], document.alphaLen[nr]);
                receiver.processingInstruction(piQName.getLocalPart(), data);
                break;
            case NodeImpl.NAMESPACE_NODE:
                receiver.addNamespaceNode(document.namespaceCode[nr]);
                break;
            case NodeImpl.REFERENCE_NODE:
                if(expandRefs) {
                    try(final DBBroker broker = getDatabase().getBroker()) {
                        final Serializer serializer = broker.getSerializer();
                        serializer.reset();
                        serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
                        serializer.setReceiver(receiver);
                        serializer.toReceiver(document.references[document.alpha[nr]], false, false);
                    } catch(final EXistException e) {
                        throw new SAXException(e);
                    }
                } else {
                    receiver.addReferenceNode(document.references[document.alpha[nr]]);
                }
                break;
        }
    }

    private void copyEndNode(final NodeImpl node, final DocumentBuilderReceiver receiver)
        throws SAXException {
        if(node.getNodeType() == Node.ELEMENT_NODE) {
            receiver.endElement(node.getQName());
        }
    }

    /**
     * Expand all reference nodes in the current document, i.e. replace them by real nodes. Reference nodes are just pointers to nodes from other
     * documents stored in the database. The XQuery engine uses reference nodes to speed up the creation of temporary doc fragments.
     * <p/>
     * <p>This method creates a new copy of the document contents and expands all reference nodes.</p>
     *
     * @throws DOMException DOCUMENT ME!
     */
    @Override
    public void expand() throws DOMException {
        if(size == 0) {
            return;
        }
        final DocumentImpl newDoc = expandRefs(null);
        copyDocContents(newDoc);
    }

    public DocumentImpl expandRefs(final NodeImpl rootNode) throws DOMException {
        try {
            if(nextReferenceIdx == 0) {
                computeNodeIds();
                return this;
            }
            final MemTreeBuilder builder = new MemTreeBuilder(context);
            final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
            try {
                builder.startDocument();
                NodeImpl node = (rootNode == null) ? (NodeImpl) getFirstChild() : rootNode;
                while(node != null) {
                    copyTo(node, receiver, true);
                    node = (NodeImpl) node.getNextSibling();
                }
                receiver.endDocument();
            } catch(final SAXException e) {
                throw new DOMException(DOMException.INVALID_STATE_ERR, e.getMessage());
            }
            final DocumentImpl newDoc = builder.getDocument();
            newDoc.computeNodeIds();
            return newDoc;
        } catch(final EXistException e) {
            throw new DOMException(DOMException.INVALID_STATE_ERR, e.getMessage());
        }
    }

    public NodeImpl getNodeById(final NodeId id) {
        expand();
        for(int i = 0; i < size; i++) {
            if(id.equals(nodeId[i])) {
                return getNode(i);
            }
        }
        return null;
    }

    private void computeNodeIds() throws EXistException {
        if(nodeId[0] != null) {
            return;
        }
        final NodeIdFactory nodeFactory = getDatabase().getNodeFactory();
        nodeId[0] = nodeFactory.documentNodeId();
        if(size == 1) {
            return;
        }
        NodeId nextId = nodeFactory.createInstance();
        NodeImpl next = (NodeImpl) getFirstChild();
        while(next != null) {
            computeNodeIds(nextId, next.nodeNumber);
            next = (NodeImpl) next.getNextSibling();
            nextId = nextId.nextSibling();
        }
    }

    private void computeNodeIds(final NodeId id, final int nodeNum) {
        nodeId[nodeNum] = id;
        if(nodeKind[nodeNum] == Node.ELEMENT_NODE) {
            NodeId nextId = id.newChild();
            int attr = document.alpha[nodeNum];
            if(-1 < attr) {
                while((attr < document.nextAttr) && (document.attrParent[attr] == nodeNum)) {
                    attrNodeId[attr] = nextId;
                    nextId = nextId.nextSibling();
                    ++attr;
                }
            }
            int nextNode = getFirstChildFor(nodeNum);
            while(nextNode > nodeNum) {
                computeNodeIds(nextId, nextNode);
                nextNode = document.next[nextNode];
                if(nextNode > nodeNum) {
                    nextId = nextId.nextSibling();
                }
            }
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param newDoc
     */
    private void copyDocContents(final DocumentImpl newDoc) {
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
        nextReferenceIdx = newDoc.nextReferenceIdx;
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
    public void streamTo(final Serializer serializer, NodeImpl node, final Receiver receiver)
        throws SAXException {
        final NodeImpl top = node;
        while(node != null) {
            startNode(serializer, node, receiver);
            NodeImpl nextNode;
            if(node instanceof ReferenceNode) {
                //Nothing more to stream ?
                nextNode = null;
            } else {
                nextNode = (NodeImpl) node.getFirstChild();
            }
            while(nextNode == null) {
                endNode(node, receiver);
                if((top != null) && (top.nodeNumber == node.nodeNumber)) {
                    break;
                }
                nextNode = (NodeImpl) node.getNextSibling();
                if(nextNode == null) {
                    node = (NodeImpl) node.getParentNode();
                    if((node == null) || ((top != null) && (top.nodeNumber == node.nodeNumber))) {
                        endNode(node, receiver);
                        break;
                    }
                }
            }
            node = nextNode;
        }
    }

    private void startNode(final Serializer serializer, final NodeImpl node, final Receiver receiver)
        throws SAXException {
        final int nr = node.nodeNumber;
        switch(node.getNodeType()) {
            case Node.ELEMENT_NODE:
                final QName nodeName = document.nodeName[nr];
                //Output required namespace declarations
                int ns = document.alphaLen[nr];
                if(ns > -1) {
                    while((ns < document.nextNamespace) && (document.namespaceParent[ns] == nr)) {
                        final QName nsQName = document.namespaceCode[ns];
                        if(XMLConstants.XMLNS_ATTRIBUTE.equals(nsQName.getLocalPart())) {
                            receiver.startPrefixMapping(XMLConstants.DEFAULT_NS_PREFIX, nsQName.getNamespaceURI());
                        } else {
                            receiver.startPrefixMapping(nsQName.getLocalPart(), nsQName.getNamespaceURI());
                        }
                        ++ns;
                    }
                }
                //Create the attribute list
                AttrList attribs = null;
                int attr = document.alpha[nr];
                if(attr > -1) {
                    attribs = new AttrList();
                    while((attr < document.nextAttr) && (document.attrParent[attr] == nr)) {
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
                receiver.processingInstruction(qn.getLocalPart(), data);
                break;
            case Node.CDATA_SECTION_NODE:
                receiver.cdataSection(document.characters, document.alpha[nr], document.alphaLen[nr]);
                break;
            case NodeImpl.REFERENCE_NODE:
                serializer.toReceiver(document.references[document.alpha[nr]], true, false);
                break;
        }
    }

    private void endNode(final NodeImpl node, final Receiver receiver) throws SAXException {
        if(node.getNodeType() == Node.ELEMENT_NODE) {
            receiver.endElement(node.getQName());
            //End all prefix mappings used for the element
            final int nr = node.nodeNumber;
            int ns = document.alphaLen[nr];
            if(ns > -1) {
                while((ns < document.nextNamespace) && (document.namespaceParent[ns] == nr)) {
                    final QName nsQName = document.namespaceCode[ns];
                    if(XMLConstants.XMLNS_ATTRIBUTE.equals(nsQName.getLocalPart())) {
                        receiver.endPrefixMapping(XMLConstants.DEFAULT_NS_PREFIX);
                    } else {
                        receiver.endPrefixMapping(nsQName.getLocalPart());
                    }
                    ++ns;
                }
            }
        }
    }

    public org.exist.dom.persistent.DocumentImpl makePersistent() throws XPathException {
        if(size <= 1) {
            return null;
        }
        return context.storeTemporaryDoc(this);
    }

    public int getChildCount() {
        int count = 0;
        int top = (size > 1) ? 1 : -1;
        while(top > 0) {
            ++count;
            top = getNextSiblingFor(top);
        }
        return count;
    }

    @Override
    public boolean hasChildNodes() {
        return getChildCount() > 0;
    }

    @Override
    public NodeList getChildNodes() {
        final NodeListImpl nl = new NodeListImpl(1);
        final Element el = getDocumentElement();
        if(el != null) {
            nl.add(el);
        }
        return nl;
    }

    @Override
    public String getInputEncoding() {
        return null;
    }

    @Override
    public String getXmlEncoding() {
        return null;
    }

    @Override
    public boolean getXmlStandalone() {
        return false;
    }

    @Override
    public void setXmlStandalone(final boolean xmlStandalone) throws DOMException {
    }

    @Override
    public String getXmlVersion() {
        return "1.0";
    }

    @Override
    public void setXmlVersion(final String xmlVersion) throws DOMException {
    }

    @Override
    public boolean getStrictErrorChecking() {
        return false;
    }

    @Override
    public void setStrictErrorChecking(final boolean strictErrorChecking) {
    }

    @Override
    public String getDocumentURI() {
        return documentURI;
    }

    @Override
    public void setDocumentURI(final String documentURI) {
        this.documentURI = documentURI;
    }

    @Override
    public Node adoptNode(final Node source) throws DOMException {
        return null;
    }

    @Override
    public DOMConfiguration getDomConfig() {
        return null;
    }

    @Override
    public void normalizeDocument() {
    }

    @Override
    public Node renameNode(final Node n, final String namespaceURI, final String qualifiedName)
        throws DOMException {
        return null;
    }

    public void setContext(final XQueryContext context) {
        this.context = context;
    }

    public XQueryContext getContext() {
        return context;
    }

    @Override
    public String getBaseURI() {
        final Element el = getDocumentElement();
        if(el != null) {
            final String baseURI = getDocumentElement().getAttributeNS(Namespaces.XML_NS, "base");
            if(baseURI != null) {
                return baseURI;
            }
        }
        final String docURI = getDocumentURI();
        if(docURI != null) {
            return docURI;
        } else {
            if(context.isBaseURIDeclared()) {
                try {
                    return context.getBaseURI().getStringValue();
                } catch(final XPathException e) {
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
        if(size != 1) {
            int nodeNum = 1;
            while(true) {
                result.append(getNode(nodeNum).toString());
                if(next[nodeNum] < nodeNum) {
                    break;
                }
                nodeNum = next[nodeNum];
            }
        }
        result.append("} ");
        return result.toString();
    }

    @Override
    public void selectAttributes(final NodeTest test, final Sequence result)
        throws XPathException {
    }
}
