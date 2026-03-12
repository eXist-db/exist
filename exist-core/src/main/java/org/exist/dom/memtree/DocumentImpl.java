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

import org.exist.Database;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.dom.NodeListImpl;
import org.exist.dom.QName;
import org.exist.dom.QName.IllegalQNameException;
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
import org.exist.xquery.Expression;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.dom.QName.Validity.ILLEGAL_FORMAT;


/**
 * An in-memory implementation of Document.
 *
 * Nodes are stored in a series of arrays which are all indexed by the {@code nodeNum} (Node Number).
 * Nodes are stored into the arrays in a Depth First Preorder (Root, Left, Right) Traversal.
 * The {@code nodeNum} starts at 0, the array {@link #nodeKind} indicates the type of the Node.
 * For example, for a complete XML Document, when {@code nodeNum == 0}, then {@code nodeKind[nodeNum] == org.w3c.dom.Node.DOCUMENT_NODE}.
 *
 * The array {@link #treeLevel} indicates at what level in the tree the node appears, the root of the tree is 0.
 * For example, for a complete XML Document, when {@code nodeNum == 0}, then {@code treeLevel[nodeNum] == 0}.
 *
 * The array {@link #next} gives the {@code nodeNum} of the next node in the tree,
 * for example {@code int nextNodeNum = next[nodeNum]}.
 *
 * The following arrays hold the data of the nodes themselves:
 *  * {@link #namespaceParent}
 *  * {@link #namespaceCode}
 *  * {@link #nodeName}
 *  * {@link #alpha}
 *  * {@link #alphaLen}
 *  * {@link #characters}
 *  * {@link #nodeId}
 *  * {@link #attrName}
 *  * {@link #attrType}
 *  * {@link #attrNodeId}
 *  * {@link #attrParent}
 *  * {@link #attrValue}
 *  * {@link #references}
 *
 * This implementation stores all node data in the document object. Nodes from another document, i.e. a persistent document in the database, can be
 * stored as reference nodes, i.e. the nodes are not copied into this document object. Instead a reference is inserted which will only be expanded
 * during serialization.
 */
public class DocumentImpl extends NodeImpl<DocumentImpl> implements Document {

    private static final AtomicLong nextDocId = new AtomicLong();

    private static final int NODE_SIZE = 16;
    private static final int ATTR_SIZE = 8;
    private static final int CHAR_BUF_SIZE = 256;
    private static final int REF_SIZE = 8;

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


    // Override for first-child lookup after in-memory mutations.
    // Maps parent node number -> first child node number when the first child
    // is no longer at the positional (parent + 1) slot due to insertions.
    private Map<Integer, Integer> firstChildOverride = null;

    protected XQueryContext context;
    protected final boolean explicitlyCreated;
    protected final long docId;
    private Database db = null;
    protected NamePool namePool;

    boolean replaceAttribute = false;


    public DocumentImpl(final XQueryContext context, final boolean explicitlyCreated) {
        this(null, context, explicitlyCreated);
    }


    public DocumentImpl(final Expression expression, final XQueryContext context, final boolean explicitlyCreated) {
        super(expression, null, 0);
        this.context = context;
        this.explicitlyCreated = explicitlyCreated;
        this.docId = nextDocId.incrementAndGet();
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

    public long getDocId() {
        return docId;
    }

    public boolean isExplicitlyCreated() {
        return explicitlyCreated;
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
                "err:XQTY0024: An attribute node cannot follow a node that is not an element or namespace node."));
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
        return new AttrImpl(getExpression(), this, nodeNum);
    }

    public NodeImpl getNamespaceNode(final int nodeNum) throws DOMException {
        return new NamespaceNode(getExpression(), this, nodeNum);
    }

    public NodeImpl getNode(final int nodeNum) throws DOMException {
        if(nodeNum == 0) {
            return this;
        }
        if(nodeNum >= size) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "node not found");
        }
        final NodeImpl node = switch (nodeKind[nodeNum]) {
            case Node.ELEMENT_NODE -> new ElementImpl(getExpression(), this, nodeNum);
            case Node.TEXT_NODE -> new TextImpl(getExpression(), this, nodeNum);
            case Node.COMMENT_NODE -> new CommentImpl(getExpression(), this, nodeNum);
            case Node.PROCESSING_INSTRUCTION_NODE -> new ProcessingInstructionImpl(getExpression(), this, nodeNum);
            case Node.CDATA_SECTION_NODE -> new CDATASectionImpl(getExpression(), this, nodeNum);
            case NodeImpl.REFERENCE_NODE -> new ReferenceNode(getExpression(), this, nodeNum);
            default -> throw new DOMException(DOMException.NOT_FOUND_ERR, "node not found");
        };
        return node;
    }

    public NodeImpl getLastAttr() {
        if(nextAttr == 0) {
            return null;
        }
        return new AttrImpl(getExpression(), this, nextAttr - 1);
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
        return new DOMImplementationImpl(getExpression());
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

    /**
     * Strip unused namespace declarations from an element and all its descendants.
     * A namespace declaration is "unused" if its prefix is not used by the element's
     * own name or any of its attribute names.
     *
     * <p>This implements the W3C copy-namespaces {@code no-preserve} semantics:
     * only namespace bindings that are used by element/attribute names are preserved.</p>
     *
     * <p>Works by invalidating unused namespace array entries (setting parent to -2)
     * and re-adding only used ones. The invalidated entries are dead space that
     * is cleaned up on the next {@link #compact()} call.</p>
     *
     * @param rootNodeNum the root element node number of the subtree to process
     */
    public void stripUnusedNamespacesInSubtree(final int rootNodeNum) {
        if (namespaceCode == null) {
            return;
        }
        // Walk the subtree: process rootNodeNum and all descendants at deeper levels
        final short rootLevel = treeLevel[rootNodeNum];
        for (int i = rootNodeNum; i < size; i++) {
            if (i > rootNodeNum && treeLevel[i] <= rootLevel) {
                break; // past the subtree
            }
            if (nodeKind[i] != Node.ELEMENT_NODE) {
                continue;
            }
            stripUnusedNamespacesForElement(i);
        }
    }

    private void stripUnusedNamespacesForElement(final int nodeNum) {
        int ns = alphaLen[nodeNum];
        if (ns < 0) {
            return; // no namespace declarations
        }

        // Collect used prefixes: element name + attribute names
        final java.util.Set<String> usedPrefixes = new java.util.HashSet<>();
        final QName elemName = nodeName[nodeNum];
        usedPrefixes.add(elemName.getPrefix() != null ? elemName.getPrefix() : "");
        int attr = alpha[nodeNum];
        if (attr >= 0) {
            while (attr < nextAttr && attrParent[attr] == nodeNum) {
                final QName aName = attrName[attr];
                if (aName.getPrefix() != null && !aName.getPrefix().isEmpty()) {
                    usedPrefixes.add(aName.getPrefix());
                }
                attr++;
            }
        }

        // Collect used namespace declarations (to re-add later)
        final java.util.List<QName> usedNs = new java.util.ArrayList<>();
        while (ns < nextNamespace && namespaceParent[ns] == nodeNum) {
            final QName nsQName = namespaceCode[ns];
            if (usedPrefixes.contains(nsQName.getLocalPart())) {
                usedNs.add(nsQName);
            }
            // Invalidate the old entry
            namespaceParent[ns] = -2;
            ns++;
        }

        // Reset alphaLen so addNamespace can set it fresh
        alphaLen[nodeNum] = -1;

        // Re-add only used namespace declarations
        for (final QName nsQName : usedNs) {
            addNamespace(nodeNum, nsQName);
        }
    }

    public int getChildCountFor(final int nr) {
        int count = 0;
        final short childLevel = (short) (treeLevel[nr] + 1);
        int nextNode = getFirstChildFor(nr);
        int steps = 0;
        while (nextNode >= 0 && steps < size) {
            if (nodeKind[nextNode] != -1 && treeLevel[nextNode] == childLevel) {
                ++count;
            }
            final int following = getNextSiblingFor(nextNode);
            if (following < 0) {
                break;
            }
            nextNode = following;
            steps++;
        }
        return count;
    }

    public int getFirstChildFor(final int nodeNumber) {
        // Check for override from in-memory mutations (e.g. insert as first)
        if (firstChildOverride != null) {
            final Integer override = firstChildOverride.get(nodeNumber);
            if (override != null) {
                return override;
            }
        }

        if (nodeNumber == 0) {
            // optimisation for document-node
            if (size > 1) {
                // skip soft-deleted nodes, but remember first deleted child
                int n = 1;
                int firstDeleted = -1;
                while (n < size && nodeKind[n] == -1) {
                    if (firstDeleted < 0) {
                        firstDeleted = n;
                    }
                    n++;
                }
                return n < size ? n : firstDeleted;
            } else {
                return -1;
            }
        }

        final short level = treeLevel[nodeNumber];
        int nextNode = nodeNumber + 1;
        int firstDeletedChild = -1;
        // Scan positional children (nodes immediately after parent in the array at a deeper level)
        while (nextNode < size && treeLevel[nextNode] > level) {
            if (nodeKind[nextNode] != -1) {
                return nextNode;  // found a non-deleted child
            }
            if (firstDeletedChild < 0) {
                firstDeletedChild = nextNode;
            }
            nextNode++;
        }
        // No non-deleted positional child found. Return the first deleted child
        // so callers can follow the next[] chain to find children that were
        // appended beyond the positional range via insertChildren().
        return firstDeletedChild;
    }

    public int getNextSiblingFor(final int nodeNumber) {
        final int nextNr = next[nodeNumber];
        if (nextNr < 0) {
            return -1;
        }
        if (nextNr < nodeNumber) {
            // Backwards reference: after in-memory mutations, siblings may be at
            // lower positions. Check tree level to distinguish sibling from parent.
            if (treeLevel[nextNr] == treeLevel[nodeNumber]) {
                return nextNr;
            }
            return -1;  // lower level = parent pointer, no next sibling
        }
        return nextNr;
    }

    public int getParentNodeFor(final int nodeNumber) {
        if (nodeNumber == 0) {
            return -1;
        }
        final short level = treeLevel[nodeNumber];
        int nextNode = next[nodeNumber];
        int steps = 0;
        while (nextNode >= 0 && steps < size) {
            if (treeLevel[nextNode] < level) {
                return nextNode;  // found a node at a lower level = parent
            }
            // same or higher level — keep walking the chain
            nextNode = next[nextNode];
            steps++;
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

    /**
     * Gets a specified node of this document.
     *
     * @param   id  the ID of the node to select
     * @return  the specified node of this document, or null if this document
     *          does not have the specified node
     */
    public NodeImpl selectById(final String id) {
        return selectById(id, false);
    }

    /**
     * Gets a specified node of this document.
     *
     * @param   id              the ID of the node to select
     * @param   typeConsidered  if true, this method should consider node
     *                          type attributes (i.e. <code>xsi:type="xs:ID"</code>);
     *                          if false, this method should not consider
     *                          node type attributes
     * @return  the specified node of this document, or null if this document
     *          does not have the specified node
     */
    public NodeImpl selectById(final String id, final boolean typeConsidered) {
        if(size == 1) {
            return null;
        }
        expand();
        final ElementImpl root = (ElementImpl) getDocumentElement();
        if (hasIdAttribute(root.getNodeNumber(), id)) {
            return root;
        }
        final int treeLevel = this.treeLevel[root.getNodeNumber()];
        int nextNode = root.getNodeNumber();
        while((++nextNode < document.size) && (document.treeLevel[nextNode] > treeLevel)) {
            if (document.nodeKind[nextNode] == Node.ELEMENT_NODE) {
                if (hasIdAttribute(nextNode, id)) {
                    return getNode(nextNode);
                } else if (hasIdTypeAttribute(nextNode, id)) {
                    return typeConsidered ? (NodeImpl) getNode(nextNode).getParentNode() : getNode(nextNode);
                } else if ("id".equalsIgnoreCase(getNode(nextNode).getNodeName()) &&
                        getNode(nextNode).getStringValue().equals(id)) {
                    return typeConsidered ? (NodeImpl) getNode(nextNode).getParentNode() : getNode(nextNode);
                }
            }
        }
        return null;
    }

    public NodeImpl selectByIdref(final String id) {
        if(size == 1) {
            return null;
        }
        expand();
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
                } else if ("id".equals(document.attrName[attr].getLocalPart()) &&
                           Objects.equals(document.attrValue[attr], id)) {
                    return true;
                }
                ++attr;
            }
        }
        return false;
    }

    private boolean hasIdTypeAttribute(final int nodeNumber, final String id) {
        int attr = document.alpha[nodeNumber];
        if(-1 < attr) {
            while((attr < document.nextAttr) && (document.attrParent[attr] == nodeNumber)) {
                if (document.attrName[attr].getStringValue().equals(Namespaces.XSI_TYPE_QNAME.getStringValue()) &&
                        document.attrValue[attr].equals(Namespaces.XS_ID_QNAME.getStringValue()) &&
                        document.getNode(nodeNumber).getStringValue().equals(id)) {
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
                    return new AttrImpl(getExpression(), this, attr);
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
        final QName qname;
        try {
            if (getContext() != null) {
                qname = QName.parse(getContext(), tagName);
            } else {
                qname = new QName(tagName);
            }
        } catch(final IllegalQNameException e) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, e.getMessage());
        }

        // check the QName is valid for use
        if(qname.isValid(false) != QName.Validity.VALID.val) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "name is invalid");
        }

        final int nodeNum = addNode(Node.ELEMENT_NODE, (short) 1, qname);
        return new ElementImpl(getExpression(), this, nodeNum);
    }

    @Override
    public Element createElementNS(final String namespaceURI, final String qualifiedName) throws DOMException {
        final QName qname;
        try {
            if (getContext() != null) {
                qname = QName.parse(getContext(), qualifiedName, namespaceURI);
            } else {
                qname = QName.parse(namespaceURI, qualifiedName);
            }
        } catch(final IllegalQNameException e) {
            final short errCode;
            if(e.getValidity() == ILLEGAL_FORMAT.val || (e.getValidity() & QName.Validity.INVALID_NAMESPACE.val) == QName.Validity.INVALID_NAMESPACE.val) {
                errCode = DOMException.NAMESPACE_ERR;
            } else {
                errCode = DOMException.INVALID_CHARACTER_ERR;
            }
            throw new DOMException(errCode, "qualified name is invalid");
        }

        // check the QName is valid for use
        final byte validity = qname.isValid(false);
        if((validity & QName.Validity.INVALID_LOCAL_PART.val) == QName.Validity.INVALID_LOCAL_PART.val) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "qualified name is invalid");
        } else if((validity & QName.Validity.INVALID_NAMESPACE.val) == QName.Validity.INVALID_NAMESPACE.val) {
            throw new DOMException(DOMException.NAMESPACE_ERR, "qualified name is invalid");
        }

        final int nodeNum = addNode(Node.ELEMENT_NODE, (short) 1, qname);
        return new ElementImpl(getExpression(), this, nodeNum);
    }

    @Override
    public DocumentFragment createDocumentFragment() {
        return new DocumentFragmentImpl(getExpression());
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
        final QName qname;
        try {
            if(getContext() != null) {
                qname = QName.parse(getContext(), name);
            } else {
                qname = new QName(name);
            }
        } catch (final IllegalQNameException e) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, e.getMessage());
        }

        // check the QName is valid for use
        if(qname.isValid(false) != QName.Validity.VALID.val) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "name is invalid");
        }

        // TODO(AR) implement this!
        throw unsupported();
    }

    @Override
    public Attr createAttributeNS(final String namespaceURI, final String qualifiedName) throws DOMException {
        final QName qname;
        try {
            if(getContext() != null) {
                qname = QName.parse(getContext(), qualifiedName, namespaceURI);
            } else {
                qname = QName.parse(namespaceURI, qualifiedName);
            }
        } catch (final IllegalQNameException e) {
            final short errCode;
            if(e.getValidity() == ILLEGAL_FORMAT.val || (e.getValidity() & QName.Validity.INVALID_NAMESPACE.val) == QName.Validity.INVALID_NAMESPACE.val) {
                errCode = DOMException.NAMESPACE_ERR;
            } else {
                errCode = DOMException.INVALID_CHARACTER_ERR;
            }
            throw new DOMException(errCode, "qualified name is invalid");
        }

        // check the QName is valid for use
        final byte validity = qname.isValid(false);
        if((validity & QName.Validity.INVALID_LOCAL_PART.val) == QName.Validity.INVALID_LOCAL_PART.val) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "qualified name is invalid");
        } else if((validity & QName.Validity.INVALID_NAMESPACE.val) == QName.Validity.INVALID_NAMESPACE.val) {
            throw new DOMException(DOMException.NAMESPACE_ERR, "qualified name is invalid");
        }

        // TODO(AR) implement this!
        throw unsupported();
    }

    @Override
    public EntityReference createEntityReference(final String name) throws DOMException {
        return null;
    }

    @Override
    public NodeList getElementsByTagName(final String tagname) {
        if(tagname != null && tagname.equals(QName.WILDCARD)) {
            return getElementsByTagName(new QName.WildcardLocalPartQName(XMLConstants.DEFAULT_NS_PREFIX));
        } else {
            final QName qname;
            try {
                if (document.getContext() != null) {
                    qname = QName.parse(document.context, tagname);

                } else {
                    qname = new QName(tagname);
                }
            } catch (final IllegalQNameException e) {
                throw new DOMException(DOMException.INVALID_CHARACTER_ERR, e.getMessage());
            }
            return getElementsByTagName(qname);
        }
    }

    @Override
    public NodeList getElementsByTagNameNS(final String namespaceURI, final String localName) {
        final boolean wildcardNS = namespaceURI != null && namespaceURI.equals(QName.WILDCARD);
        final boolean wildcardLocalPart = localName != null && localName.equals(QName.WILDCARD);

        if(wildcardNS && wildcardLocalPart) {
            return getElementsByTagName(QName.WildcardQName.getInstance());
        } else if(wildcardNS) {
            return getElementsByTagName(new QName.WildcardNamespaceURIQName(localName));
        } else if(wildcardLocalPart) {
            return getElementsByTagName(new QName.WildcardLocalPartQName(namespaceURI));
        } else {
            final QName qname;
            if (document.getContext() != null) {
                try {
                    qname = QName.parse(document.context, localName, namespaceURI);
                } catch (final IllegalQNameException e) {
                    throw new DOMException(DOMException.INVALID_CHARACTER_ERR, e.getMessage());
                }
            } else {
                qname = new QName(localName, namespaceURI);
            }
            return getElementsByTagName(qname);
        }
    }

    private NodeList getElementsByTagName(final QName qname) {
        final NodeListImpl nl = new NodeListImpl();
        for(int i = 1; i < size; i++) {
            if(nodeKind[i] == Node.ELEMENT_NODE) {
                final QName qn = nodeName[i];
                if(qn.matches(qname)) {
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
    public Element getElementById(final String elementId) {
        return null;
    }

    @Override
    public DocumentImpl getOwnerDocument() {
        return null;
    }

    /**
     * Copy the document fragment starting at the specified node to the given document builder.
     *
     * @param node node to provide document fragment
     * @param receiver document builder
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
                if (node != null) {
                    copyEndNode(node, receiver);
                }
                if((top != null) && (top.nodeNumber == node.nodeNumber)) {
                    break;
                }
                //No nextNode if the top node is a Document node
                nextNode = (NodeImpl) node.getNextSibling();
                if(nextNode == null) {
                    node = (NodeImpl) node.getParentNode();
                    if((node == null) || ((top != null) && (top.nodeNumber == node.nodeNumber))) {
                        if (node != null) {
                            copyEndNode(node, receiver);
                        }
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
                        final Serializer serializer = broker.borrowSerializer();
                        try {
                            serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
                            serializer.setReceiver(receiver);
                            serializer.toReceiver(document.references[document.alpha[nr]], false, false);
                        } finally {
                            broker.returnSerializer(serializer);
                        }
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
     *
     * This method creates a new copy of the document contents and expands all reference nodes.
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
            final MemTreeBuilder builder = new MemTreeBuilder(getExpression(), context);
            final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(getExpression(), builder);
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
        attrType = newDoc.attrType;
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
     * @param serializer the serializer
     * @param node node to be serialized
     * @param receiver the receiveer
     * @throws SAXException DOCUMENT ME
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
                        if (node != null) {
                            endNode(node, receiver);
                        }
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

    // this is DOM specific
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
        if (size == 1) {
            return new NodeListImpl(0);
        }

        final NodeListImpl children = new NodeListImpl(1);  // most likely a single element!
        int nextChildNodeNum = 1;
        while (nextChildNodeNum > 0) {
            final NodeImpl child = getNode(nextChildNodeNum);
            children.add(child);
            nextChildNodeNum = next[nextChildNodeNum];
        }

        return children;
    }

    @Override
    public String getInputEncoding() {
        return null;
    }

    @Override
    public String getXmlEncoding() {
        return UTF_8.name();    //TODO(AR) this should be recorded from the XML document and not hard coded
    }

    @Override
    public boolean getXmlStandalone() {
        return false;   //TODO(AR) this should be recorded from the XML document and not hard coded
    }

    @Override
    public void setXmlStandalone(final boolean xmlStandalone) throws DOMException {
    }

    @Override
    public String getXmlVersion() {
        return "1.0";   //TODO(AR) this should be recorded from the XML document and not hard coded
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
            if(context!=null && context.isBaseURIDeclared()) {
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

    @Override
    public Node appendChild(final Node newChild) throws DOMException {
        if(newChild.getNodeType() != Node.DOCUMENT_NODE && newChild.getOwnerDocument() != this) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "Owning document IDs do not match");
        }

        if(newChild == this) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "Cannot append a document to itself");
        }

        if(newChild.getNodeType() == DOCUMENT_NODE) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "A Document Node may not be appended to a Document Node");
        }

        if(newChild.getNodeType() == ELEMENT_NODE && getDocumentElement() != null) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "A Document Node may only have a single document element");
        }

        if(newChild.getNodeType() == DOCUMENT_TYPE_NODE && getDoctype() != null) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "A Document Node may only have a single document type");
        }

        throw unsupported();
    }

    // === W3C XQuery Update Facility 3.0 - In-memory mutation methods ===

    /**
     * Rename a node in this document.
     *
     * @param nodeNum the node number to rename
     * @param newName the new QName
     */
    public void renameNode(final int nodeNum, final QName newName) {
        final short kind = nodeKind[nodeNum];
        switch (kind) {
            case Node.ELEMENT_NODE:
            case Node.PROCESSING_INSTRUCTION_NODE:
                nodeName[nodeNum] = namePool.getSharedName(newName);
                break;
            default:
                throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                        "Cannot rename node of type " + kind);
        }
    }

    /**
     * Rename an attribute node. The attrNum parameter is an index into the
     * attribute arrays (attrName, attrValue, etc.), NOT the main node arrays.
     *
     * @param attrNum the attribute index
     * @param newName the new QName
     */
    public void renameAttribute(final int attrNum, final QName newName) {
        attrName[attrNum] = namePool.getSharedName(newName);
    }

    /**
     * Replace the string value of a node.
     *
     * @param nodeNum the node number
     * @param value the new string value
     */
    public void replaceValue(final int nodeNum, final String value) {
        final short kind = nodeKind[nodeNum];
        switch (kind) {
            case Node.TEXT_NODE:
            case Node.COMMENT_NODE:
            case Node.CDATA_SECTION_NODE:
            case Node.PROCESSING_INSTRUCTION_NODE: {
                // Replace the character content
                final char[] chars = value.toCharArray();
                if (characters == null) {
                    characters = new char[chars.length > CHAR_BUF_SIZE ? chars.length : CHAR_BUF_SIZE];
                } else if ((nextChar + chars.length) >= characters.length) {
                    int newLen = (characters.length * 3) / 2;
                    if (newLen < (nextChar + chars.length)) {
                        newLen = nextChar + chars.length;
                    }
                    final char[] nc = new char[newLen];
                    System.arraycopy(characters, 0, nc, 0, characters.length);
                    characters = nc;
                }
                alpha[nodeNum] = nextChar;
                alphaLen[nodeNum] = chars.length;
                System.arraycopy(chars, 0, characters, nextChar, chars.length);
                nextChar += chars.length;
                break;
            }
            case Node.ELEMENT_NODE: {
                // W3C replaceElementContent: replace all children with a single text node.
                // We must be careful to only modify THIS element's children, not nodes
                // belonging to sibling elements that happen to be adjacent in the array.
                final short childLevel = (short) (treeLevel[nodeNum] + 1);

                // Determine the boundary of this element's positional subtree.
                // Only nodes at positions nodeNum+1..subtreeEnd (where subtreeEnd is the
                // first position at the same or lower level) are this element's children.
                int subtreeEnd = nodeNum + 1;
                while (subtreeEnd < size && treeLevel[subtreeEnd] > treeLevel[nodeNum]) {
                    subtreeEnd++;
                }

                // Find and modify/create a text child within the positional range
                int firstTextChild = -1;
                for (int c = nodeNum + 1; c < subtreeEnd; c++) {
                    if (firstTextChild == -1 && treeLevel[c] == childLevel
                            && nodeKind[c] == Node.TEXT_NODE) {
                        firstTextChild = c;
                    } else if (c != firstTextChild) {
                        nodeKind[c] = -1;  // delete other children
                    }
                }

                // Also delete any chain-linked children (from previous insertions)
                if (firstChildOverride != null && firstChildOverride.containsKey(nodeNum)) {
                    int chainChild = firstChildOverride.get(nodeNum);
                    while (chainChild >= 0 && chainChild != nodeNum) {
                        if (chainChild >= subtreeEnd && nodeKind[chainChild] != -1) {
                            nodeKind[chainChild] = -1;  // delete appended children
                        }
                        final int nx = next[chainChild];
                        if (nx < 0 || nx == nodeNum) break;
                        chainChild = nx;
                    }
                    firstChildOverride.remove(nodeNum);
                }

                if (firstTextChild >= 0) {
                    // Modify existing text child in place
                    final char[] chars = value.toCharArray();
                    if ((nextChar + chars.length) >= characters.length) {
                        int newLen = (characters.length * 3) / 2;
                        if (newLen < (nextChar + chars.length)) {
                            newLen = nextChar + chars.length;
                        }
                        final char[] nc = new char[newLen];
                        System.arraycopy(characters, 0, nc, 0, characters.length);
                        characters = nc;
                    }
                    alpha[firstTextChild] = nextChar;
                    alphaLen[firstTextChild] = chars.length;
                    System.arraycopy(chars, 0, characters, nextChar, chars.length);
                    nextChar += chars.length;
                } else if (nodeNum + 1 < subtreeEnd) {
                    // No text child but has positional children — convert first to text
                    final int firstChild = nodeNum + 1;
                    nodeKind[firstChild] = Node.TEXT_NODE;
                    nodeName[firstChild] = null;
                    final char[] chars = value.toCharArray();
                    if ((nextChar + chars.length) >= characters.length) {
                        int newLen = (characters.length * 3) / 2;
                        if (newLen < (nextChar + chars.length)) {
                            newLen = nextChar + chars.length;
                        }
                        final char[] nc = new char[newLen];
                        System.arraycopy(characters, 0, nc, 0, characters.length);
                        characters = nc;
                    }
                    alpha[firstChild] = nextChar;
                    alphaLen[firstChild] = chars.length;
                    System.arraycopy(chars, 0, characters, nextChar, chars.length);
                    nextChar += chars.length;
                    // Mark remaining positional children as deleted
                    for (int c = firstChild + 1; c < subtreeEnd; c++) {
                        nodeKind[c] = -1;
                    }
                } else if (value != null && !value.isEmpty()) {
                    // Element has no positional children — insert via insertChildren
                    try {
                        final org.exist.xquery.value.StringValue textVal =
                                new org.exist.xquery.value.StringValue(value);
                        insertChildren(nodeNum, textVal, true);
                    } catch (final org.exist.xquery.XPathException e) {
                        throw new DOMException(DOMException.INVALID_STATE_ERR,
                                "Failed to insert text child: " + e.getMessage());
                    }
                }
                break;
            }
            default:
                throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
                        "Cannot replace value of node of type " + kind);
        }
    }

    /**
     * Replace the value of an attribute node. The attrNum parameter is an index
     * into the attribute arrays (attrName, attrValue, etc.), NOT the main node arrays.
     *
     * @param attrNum the attribute index
     * @param value the new value
     */
    public void replaceAttributeValue(final int attrNum, final String value) {
        attrValue[attrNum] = value;
    }

    /**
     * Remove an attribute from this document.
     * Compacts the attribute arrays by shifting subsequent entries down.
     * Also updates the alpha[] pointers for elements whose first attribute
     * index is affected.
     *
     * @param attrNum the attribute index to remove
     */
    /**
     * Find an attribute index by QName on a given element.
     *
     * @param elementNodeNum the element node number
     * @param qname the attribute QName to find
     * @return the attribute index, or -1 if not found
     */
    public int findAttribute(final int elementNodeNum, final QName qname) {
        int a = alpha[elementNodeNum];
        if (a < 0) {
            return -1;
        }
        while (a < nextAttr && attrParent[a] == elementNodeNum) {
            if (attrName[a].getLocalPart().equals(qname.getLocalPart())
                    && attrName[a].getNamespaceURI().equals(qname.getNamespaceURI())) {
                return a;
            }
            a++;
        }
        return -1;
    }

    public void removeAttribute(final int attrNum) {
        if (attrNum < 0 || attrNum >= nextAttr) {
            return;
        }

        // Shift all attribute arrays down by one
        final int remaining = nextAttr - attrNum - 1;
        if (remaining > 0) {
            System.arraycopy(attrName, attrNum + 1, attrName, attrNum, remaining);
            System.arraycopy(attrNodeId, attrNum + 1, attrNodeId, attrNum, remaining);
            System.arraycopy(attrParent, attrNum + 1, attrParent, attrNum, remaining);
            System.arraycopy(attrValue, attrNum + 1, attrValue, attrNum, remaining);
            System.arraycopy(attrType, attrNum + 1, attrType, attrNum, remaining);
        }
        nextAttr--;

        // Update alpha[] pointers: alpha[nodeNum] stores the first attribute index
        // for each element. If the removed attribute index is <= the element's
        // first attribute, we need to adjust.
        for (int i = 0; i < size; i++) {
            if (nodeKind[i] == Node.ELEMENT_NODE && alpha[i] >= 0) {
                if (alpha[i] > attrNum) {
                    alpha[i]--;
                } else if (alpha[i] == attrNum) {
                    // Check if this element still has attributes
                    if (attrNum < nextAttr && attrParent[attrNum] == i) {
                        // Still has attributes at the same index (shifted down)
                    } else {
                        alpha[i] = -1; // No more attributes for this element
                    }
                }
            }
        }
    }

    /**
     * Find any node whose next[] pointer targets the given node.
     * After in-memory mutations, predecessors may be at any array position,
     * so we must scan all nodes, not just those before targetNodeNum.
     *
     * @param targetNodeNum the node to find a predecessor for
     * @return the predecessor node number, or -1 if not found
     */
    private int findPredecessor(final int targetNodeNum) {
        final short targetLevel = treeLevel[targetNodeNum];
        // Search backward first (most common case for unmutated trees)
        for (int i = targetNodeNum - 1; i >= 0; i--) {
            if (next[i] == targetNodeNum && nodeKind[i] != -1 && treeLevel[i] == targetLevel) {
                return i;
            }
        }
        // Search forward (for nodes inserted after targetNodeNum in array order)
        for (int i = targetNodeNum + 1; i < size; i++) {
            if (next[i] == targetNodeNum && nodeKind[i] != -1 && treeLevel[i] == targetLevel) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Remove a node from this document.
     * This is a soft-delete: the node's kind is set to -1 to mark it as deleted.
     * This is sufficient for the copy-modify pattern where the document is
     * consumed once and not reused.
     *
     * @param nodeNum the node number to remove
     */
    public void removeNode(final int nodeNum) {
        if (nodeNum <= 0 || nodeNum >= size) {
            return;
        }

        // Find the parent and re-stitch the next[] chain to skip this node
        final int origNext = next[nodeNum];
        final short level = treeLevel[nodeNum];

        // Find the previous node that points to nodeNum
        final int prev = findPredecessor(nodeNum);

        if (prev >= 0) {
            // Find the next node after this node's subtree in the sibling chain.
            // Walk the next[] chain from nodeNum to find the first node that's
            // at the same or lower level (a sibling or the parent).
            int chainNode = origNext;
            int steps = 0;
            while (chainNode >= 0 && steps < size) {
                if (nodeKind[chainNode] == -1) {
                    // skip deleted nodes in chain
                    chainNode = next[chainNode];
                    steps++;
                    continue;
                }
                if (treeLevel[chainNode] <= level) {
                    // Found a sibling or parent
                    break;
                }
                chainNode = next[chainNode];
                steps++;
            }
            next[prev] = chainNode >= 0 ? chainNode : origNext;
        }

        // Mark the node and its subtree as deleted
        final short nodeLevel = treeLevel[nodeNum];
        nodeKind[nodeNum] = -1;
        for (int i = nodeNum + 1; i < size && treeLevel[i] > nodeLevel; i++) {
            nodeKind[i] = -1;
        }
    }

    /**
     * Merge adjacent text nodes throughout the document.
     * Per the W3C XQuery Update Facility spec, after applying updates,
     * adjacent text nodes among children of any element or document node
     * must be merged. Empty text nodes are removed.
     *
     * This walks all non-deleted nodes and for each parent (document or element),
     * finds runs of consecutive text node children and merges them.
     */
    public void mergeAdjacentTextNodes() {
        // Walk the document looking for parent nodes (document or element)
        for (int parent = 0; parent < size; parent++) {
            if (nodeKind[parent] == -1) {
                continue;
            }
            if (nodeKind[parent] != Node.DOCUMENT_NODE && nodeKind[parent] != Node.ELEMENT_NODE) {
                continue;
            }

            // Iterate through children of this parent using the next[] chain
            final short childLevel = (short) (treeLevel[parent] + 1);
            int child = getFirstChildFor(parent);
            if (child < 0) {
                continue;
            }

            int prevTextNode = -1;
            while (child >= 0 && child < size && treeLevel[child] >= childLevel) {
                if (nodeKind[child] == -1) {
                    // Skip deleted nodes — follow next[] chain
                    child = next[child];
                    if (child <= parent) break;
                    continue;
                }
                if (treeLevel[child] > childLevel) {
                    // Descendant, not direct child — skip
                    child = next[child];
                    if (child <= parent) break;
                    continue;
                }

                // Direct child at childLevel
                if (nodeKind[child] == Node.TEXT_NODE) {
                    if (prevTextNode >= 0) {
                        // Merge this text node into prevTextNode
                        final String prevText = new String(characters, alpha[prevTextNode], alphaLen[prevTextNode]);
                        final String thisText = new String(characters, alpha[child], alphaLen[child]);
                        final String merged = prevText + thisText;

                        // Store merged text in prevTextNode
                        final char[] chars = merged.toCharArray();
                        if ((nextChar + chars.length) >= characters.length) {
                            int newLen = (characters.length * 3) / 2;
                            if (newLen < (nextChar + chars.length)) {
                                newLen = nextChar + chars.length;
                            }
                            final char[] nc = new char[newLen];
                            System.arraycopy(characters, 0, nc, 0, characters.length);
                            characters = nc;
                        }
                        alpha[prevTextNode] = nextChar;
                        alphaLen[prevTextNode] = chars.length;
                        System.arraycopy(chars, 0, characters, nextChar, chars.length);
                        nextChar += chars.length;

                        // Soft-delete the merged text node and restitch
                        removeNode(child);

                        // Continue from prevTextNode's next (don't advance prevTextNode)
                        child = next[prevTextNode];
                        if (child <= parent) break;
                    } else {
                        // Check for empty text nodes
                        if (alphaLen[child] == 0) {
                            final int nextChild = next[child];
                            removeNode(child);
                            child = nextChild;
                            if (child <= parent) break;
                        } else {
                            prevTextNode = child;
                            child = next[child];
                            if (child <= parent) break;
                        }
                    }
                } else {
                    prevTextNode = -1;
                    child = next[child];
                    if (child <= parent) break;
                }
            }
        }

        // Invalidate cached node IDs since the structure changed
        if (nodeId != null) {
            nodeId[0] = null;
        }
    }

    /**
     * Insert children into an element node.
     * Uses the serialization rebuild approach for correctness.
     *
     * @param parentNodeNum the node number of the parent element
     * @param content the content to insert
     * @param asFirst if true, insert as first children; if false, as last
     * @throws XPathException if the content cannot be processed
     */
    public void insertChildren(final int parentNodeNum, final Sequence content, final boolean asFirst)
            throws XPathException {
        if (content == null || content.isEmpty()) {
            return;
        }

        final short childLevel = (short) (treeLevel[parentNodeNum] + 1);

        if (asFirst) {
            // Insert as first children: find the current first child and link new nodes before it
            final int firstChild = getFirstChildFor(parentNodeNum);

            int lastInserted = -1;
            int firstInserted = -1;
            for (final org.exist.xquery.value.SequenceIterator i = content.iterate(); i.hasNext(); ) {
                final org.exist.xquery.value.Item item = i.nextItem();
                final java.util.List<Integer> inserted = copyItemIntoDocument(item, parentNodeNum, childLevel);
                for (final int newNodeNum : inserted) {
                    if (firstInserted == -1) {
                        firstInserted = newNodeNum;
                    }
                    if (lastInserted >= 0) {
                        next[lastInserted] = newNodeNum;
                    }
                    lastInserted = newNodeNum;
                }
            }
            // Link last inserted to the old first child (or parent if no children)
            if (lastInserted >= 0) {
                next[lastInserted] = firstChild >= 0 ? firstChild : parentNodeNum;
            }
            // Override the first-child lookup so navigation finds the new nodes first
            if (firstInserted >= 0) {
                if (firstChildOverride == null) {
                    firstChildOverride = new HashMap<>();
                }
                firstChildOverride.put(parentNodeNum, firstInserted);
            }
        } else {
            // Insert as last children: find the last child and link after it
            // Walk the sibling chain from first child to find the last one
            int lastChild = -1;
            final int firstChild = getFirstChildFor(parentNodeNum);
            if (firstChild >= 0) {
                lastChild = firstChild;
                int nextSib = getNextSiblingFor(lastChild);
                while (nextSib >= 0) {
                    lastChild = nextSib;
                    nextSib = getNextSiblingFor(lastChild);
                }
            }

            int firstInsertedAsLast = -1;
            for (final org.exist.xquery.value.SequenceIterator i = content.iterate(); i.hasNext(); ) {
                final org.exist.xquery.value.Item item = i.nextItem();
                final java.util.List<Integer> inserted = copyItemIntoDocument(item, parentNodeNum, childLevel);
                for (final int newNodeNum : inserted) {
                    if (firstInsertedAsLast == -1) {
                        firstInsertedAsLast = newNodeNum;
                    }
                    if (lastChild >= 0) {
                        next[lastChild] = newNodeNum;
                    }
                    lastChild = newNodeNum;
                }
            }
            // If the parent had no visible children, the appended nodes are beyond
            // the positional scan range. Set firstChildOverride so they can be found.
            if (firstChild < 0 && firstInsertedAsLast >= 0) {
                if (firstChildOverride == null) {
                    firstChildOverride = new HashMap<>();
                }
                firstChildOverride.put(parentNodeNum, firstInsertedAsLast);
            }
        }
    }

    /**
     * Insert sibling nodes before or after a reference node.
     *
     * @param refNodeNum the reference node number
     * @param content the content to insert
     * @param before if true, insert before; if false, insert after
     * @throws XPathException if the content cannot be processed
     */
    public void insertSiblings(final int refNodeNum, final Sequence content, final boolean before)
            throws XPathException {
        if (content == null || content.isEmpty()) {
            return;
        }

        final short level = treeLevel[refNodeNum];
        // Find the parent using level-aware parent finding
        final int parentNum = getParentNodeFor(refNodeNum);
        if (parentNum < 0) {
            // Cannot insert siblings of the document node (no parent)
            return;
        }

        if (before) {
            // Insert before: find the node whose next[] points to refNodeNum and re-link
            final int prevNode = findPredecessor(refNodeNum);

            int lastInserted = -1;
            int firstInserted = -1;
            for (final org.exist.xquery.value.SequenceIterator i = content.iterate(); i.hasNext(); ) {
                final org.exist.xquery.value.Item item = i.nextItem();
                final java.util.List<Integer> inserted = copyItemIntoDocument(item, parentNum, level);
                for (final int newNodeNum : inserted) {
                    if (firstInserted == -1) {
                        firstInserted = newNodeNum;
                    }
                    if (prevNode >= 0 && lastInserted == -1) {
                        next[prevNode] = newNodeNum;
                    }
                    if (lastInserted >= 0) {
                        next[lastInserted] = newNodeNum;
                    }
                    lastInserted = newNodeNum;
                }
            }
            // Link last inserted to refNode
            if (lastInserted >= 0) {
                next[lastInserted] = refNodeNum;
            }
            // If no predecessor found, refNode was the first child (found positionally).
            // Set override so navigation finds the new nodes first.
            if (prevNode < 0 && firstInserted >= 0 && parentNum >= 0) {
                if (firstChildOverride == null) {
                    firstChildOverride = new HashMap<>();
                }
                firstChildOverride.put(parentNum, firstInserted);
            }
        } else {
            // Insert after: link new nodes after refNode
            final int origNext = next[refNodeNum];
            int lastInserted = refNodeNum;
            for (final org.exist.xquery.value.SequenceIterator i = content.iterate(); i.hasNext(); ) {
                final org.exist.xquery.value.Item item = i.nextItem();
                final java.util.List<Integer> inserted = copyItemIntoDocument(item, parentNum, level);
                for (final int newNodeNum : inserted) {
                    next[lastInserted] = newNodeNum;
                    lastInserted = newNodeNum;
                }
            }
            // Last inserted points to where refNode originally pointed
            if (lastInserted != refNodeNum) {
                next[lastInserted] = origNext;
            }
        }
    }

    /**
     * Insert attributes into an element.
     *
     * @param elementNodeNum the element node number
     * @param content the attribute nodes to insert
     * @throws XPathException if the content cannot be processed
     */
    public void insertAttributes(final int elementNodeNum, final Sequence content) throws XPathException {
        insertAttributes(elementNodeNum, content, true);
    }

    /**
     * Insert attributes into an element.
     *
     * @param elementNodeNum the target element's node number
     * @param content the attributes to insert
     * @param replaceExisting if true, replace existing attributes with the same name;
     *                        if false, always add as new attributes (for PUL application
     *                        where a DELETE may separately remove the original)
     */
    public void insertAttributes(final int elementNodeNum, final Sequence content,
                                  final boolean replaceExisting) throws XPathException {
        if (content == null || content.isEmpty()) {
            return;
        }

        // Collect new attributes to insert
        final java.util.List<Object[]> newAttrs = new java.util.ArrayList<>();
        for (final org.exist.xquery.value.SequenceIterator i = content.iterate(); i.hasNext(); ) {
            final org.exist.xquery.value.Item item = i.nextItem();
            if (org.exist.xquery.value.Type.subTypeOf(item.getType(), org.exist.xquery.value.Type.NODE)) {
                final Node node = ((org.exist.xquery.value.NodeValue) item).getNode();
                if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
                    final Attr attr = (Attr) node;
                    final QName qname = new QName(
                            attr.getLocalName() != null ? attr.getLocalName() : attr.getName(),
                            attr.getNamespaceURI() != null ? attr.getNamespaceURI() : "",
                            attr.getPrefix() != null ? attr.getPrefix() : "");
                    newAttrs.add(new Object[]{qname, attr.getValue()});
                }
            }
        }

        if (newAttrs.isEmpty()) {
            return;
        }

        // Check for duplicates and replace existing values (only when not in PUL mode)
        if (replaceExisting) {
            final java.util.Iterator<Object[]> it = newAttrs.iterator();
            while (it.hasNext()) {
                final Object[] entry = it.next();
                final QName qname = (QName) entry[0];
                final String value = (String) entry[1];
                if (alpha[elementNodeNum] >= 0) {
                    int a = alpha[elementNodeNum];
                    while (a < nextAttr && attrParent[a] == elementNodeNum) {
                        if (attrName[a].equals(qname)) {
                            // Replace existing attribute value
                            attrValue[a] = value;
                            it.remove();
                            break;
                        }
                        a++;
                    }
                }
            }
        }

        if (newAttrs.isEmpty()) {
            return;
        }

        final int count = newAttrs.size();

        // Find insertion point: right after the last contiguous attribute of this element
        int insertPos;
        if (alpha[elementNodeNum] >= 0) {
            insertPos = alpha[elementNodeNum];
            while (insertPos < nextAttr && attrParent[insertPos] == elementNodeNum) {
                insertPos++;
            }
        } else {
            // Element has no attrs yet — insert at nextAttr (already contiguous)
            insertPos = nextAttr;
        }

        // Ensure capacity
        while (nextAttr + count > attrName.length) {
            growAttributes();
        }

        // Shift everything from insertPos onwards to make room
        if (insertPos < nextAttr) {
            System.arraycopy(attrParent, insertPos, attrParent, insertPos + count, nextAttr - insertPos);
            System.arraycopy(attrName, insertPos, attrName, insertPos + count, nextAttr - insertPos);
            System.arraycopy(attrValue, insertPos, attrValue, insertPos + count, nextAttr - insertPos);
            System.arraycopy(attrType, insertPos, attrType, insertPos + count, nextAttr - insertPos);

            // Update alpha pointers for elements whose attrs shifted
            for (int n = 0; n < size; n++) {
                if (nodeKind[n] == Node.ELEMENT_NODE && alpha[n] >= insertPos && n != elementNodeNum) {
                    alpha[n] += count;
                }
            }
        }

        // Insert new attributes at the contiguous position
        for (int j = 0; j < count; j++) {
            final Object[] entry = newAttrs.get(j);
            final QName qname = (QName) entry[0];
            final String value = (String) entry[1];
            final QName attrQname = new QName(qname.getLocalPart(), qname.getNamespaceURI(), qname.getPrefix(), ElementValue.ATTRIBUTE);
            attrParent[insertPos + j] = elementNodeNum;
            this.attrName[insertPos + j] = namePool.getSharedName(attrQname);
            attrValue[insertPos + j] = value;
            attrType[insertPos + j] = AttrImpl.ATTR_CDATA_TYPE;
        }

        // Set alpha if element didn't have attrs before
        if (alpha[elementNodeNum] < 0) {
            alpha[elementNodeNum] = insertPos;
        }

        nextAttr += count;
    }

    /**
     * Replace a node with new content.
     *
     * @param nodeNum the node number to replace
     * @param content the replacement content
     * @throws XPathException if the content cannot be processed
     */
    public void replaceNode(final int nodeNum, final Sequence content) throws XPathException {
        if (content == null || content.isEmpty()) {
            removeNode(nodeNum);
            return;
        }

        final short level = treeLevel[nodeNum];
        final int parentNum = getParentNodeFor(nodeNum);

        // Find the predecessor that points to nodeNum
        final int prev = findPredecessor(nodeNum);

        // Find the next node after nodeNum's subtree (the node nodeNum's chain leads to
        // at the same or lower level)
        int afterNode = next[nodeNum];
        int steps = 0;
        while (afterNode >= 0 && steps < size) {
            if (nodeKind[afterNode] != -1 && treeLevel[afterNode] <= level) {
                break;
            }
            afterNode = next[afterNode];
            steps++;
        }

        // Copy new content nodes and link them into the chain.
        // Uses copyItemIntoDocument to handle document nodes and atomic values.
        int firstNew = -1;
        int lastNew = -1;
        try {
            for (final org.exist.xquery.value.SequenceIterator i = content.iterate(); i.hasNext(); ) {
                final org.exist.xquery.value.Item item = i.nextItem();
                final java.util.List<Integer> newNodes = copyItemIntoDocument(item, parentNum, level);
                for (final int newNodeNum : newNodes) {
                    if (firstNew == -1) {
                        firstNew = newNodeNum;
                    }
                    if (lastNew >= 0) {
                        next[lastNew] = newNodeNum;
                    }
                    lastNew = newNodeNum;
                }
            }
        } catch (final org.exist.xquery.XPathException e) {
            throw new DOMException(DOMException.INVALID_STATE_ERR, e.getMessage());
        }

        // Link new nodes into the chain
        if (prev >= 0 && firstNew >= 0) {
            next[prev] = firstNew;
        } else if (prev < 0 && firstNew >= 0 && parentNum >= 0) {
            // No same-level predecessor: the replaced node was the first child.
            // Set firstChildOverride so getFirstChildFor() can find the new nodes
            // (they're appended at the end of the array, beyond positional scan).
            if (firstChildOverride == null) {
                firstChildOverride = new HashMap<>();
            }
            firstChildOverride.put(parentNum, firstNew);
        }
        if (lastNew >= 0) {
            next[lastNew] = afterNode >= 0 ? afterNode : parentNum;
        }

        // Soft-delete the original node and its subtree
        final short nodeLevel = treeLevel[nodeNum];
        nodeKind[nodeNum] = -1;
        for (int i = nodeNum + 1; i < size && treeLevel[i] > nodeLevel; i++) {
            nodeKind[i] = -1;
        }
    }

    /**
     * Copy a DOM node into this document's arrays.
     * This is a simplified version for the copy-modify pattern.
     *
     * @return the node number of the top-level copied node
     */
    /**
     * Copy a content item into the document arrays, handling atomic values,
     * document nodes, and regular nodes per the W3C XQuery Update Facility spec.
     *
     * @param item the content item to copy
     * @param parentNodeNum the parent node number
     * @param level the tree level for the new node(s)
     * @return list of top-level node numbers that were inserted
     */
    private java.util.List<Integer> copyItemIntoDocument(final org.exist.xquery.value.Item item,
                                                          final int parentNodeNum, final short level)
            throws XPathException {
        // When no-inherit is active, pass an empty scope map to materialize namespaces
        // within inserted subtrees (so FunInScopePrefixes self-only mode still finds them)
        final java.util.Map<String, String> scopeNs =
                (context != null && !context.inheritNamespaces())
                        ? new java.util.LinkedHashMap<>() : null;

        final java.util.List<Integer> result = new java.util.ArrayList<>();
        if (org.exist.xquery.value.Type.subTypeOf(item.getType(), org.exist.xquery.value.Type.NODE)) {
            final Node node = ((org.exist.xquery.value.NodeValue) item).getNode();
            if (node.getNodeType() == Node.DOCUMENT_NODE) {
                // For document nodes: insert the document's children, not the document itself
                Node child = node.getFirstChild();
                while (child != null) {
                    result.add(copyNodeIntoDocument(child, parentNodeNum, level, scopeNs));
                    child = child.getNextSibling();
                }
            } else {
                result.add(copyNodeIntoDocument(node, parentNodeNum, level, scopeNs));
            }
        } else {
            // Atomic value: convert to text node per W3C spec
            final String text = item.getStringValue();
            if (!text.isEmpty()) {
                final int nodeNum = addNode(Node.TEXT_NODE, level, null);
                addChars(nodeNum, text.toCharArray(), 0, text.length());
                next[nodeNum] = parentNodeNum;
                result.add(nodeNum);
            }
        }
        return result;
    }

    private int copyNodeIntoDocument(final Node node, final int parentNodeNum, final short level) {
        return copyNodeIntoDocument(node, parentNodeNum, level, null);
    }

    /**
     * Copy a node into this document.
     *
     * @param node the source node
     * @param parentNodeNum the parent in this document
     * @param level tree level for the new node
     * @param scopeNamespaces when non-null, namespace bindings accumulated from ancestors
     *        within the current subtree (for no-inherit materialization). Each element gets
     *        explicit declarations for ancestor bindings not already declared on self.
     *        Pass null to skip materialization (normal copy behavior).
     */
    private int copyNodeIntoDocument(final Node node, final int parentNodeNum, final short level,
                                      final java.util.Map<String, String> scopeNamespaces) {
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE: {
                final String localName = node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
                final String nsUri = node.getNamespaceURI() != null ? node.getNamespaceURI() : "";
                final String prefix = node.getPrefix() != null ? node.getPrefix() : "";
                final QName qname = new QName(localName, nsUri, prefix);
                final int nodeNum = addNode(Node.ELEMENT_NODE, level, qname);
                next[nodeNum] = parentNodeNum;

                // Collect attribute prefixes (needed for no-preserve filtering)
                final NamedNodeMap attrs = node.getAttributes();
                final java.util.Set<String> usedPrefixes = new java.util.HashSet<>();
                usedPrefixes.add(prefix); // element prefix is always "used"

                // Copy attributes (skip xmlns declarations — handled separately below)
                if (attrs != null) {
                    for (int i = 0; i < attrs.getLength(); i++) {
                        final Attr attr = (Attr) attrs.item(i);
                        // Skip namespace declarations
                        if (javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())) {
                            continue;
                        }
                        final String attrLocal = attr.getLocalName() != null ? attr.getLocalName() : attr.getName();
                        final String attrNs = attr.getNamespaceURI() != null ? attr.getNamespaceURI() : "";
                        final String attrPrefix = attr.getPrefix() != null ? attr.getPrefix() : "";
                        usedPrefixes.add(attrPrefix);
                        addAttribute(nodeNum, new QName(attrLocal, attrNs, attrPrefix),
                                attr.getValue(), AttrImpl.ATTR_CDATA_TYPE);
                    }
                }

                // Check if no-preserve mode should strip unused namespace declarations
                final boolean noPreserve = context != null && !context.preserveNamespaces();

                // Collect this element's own namespace declarations
                final java.util.Map<String, String> selfNsDecls = new java.util.LinkedHashMap<>();

                // Copy namespace declarations (filtered by no-preserve if applicable)
                if (node instanceof ElementImpl memElement) {
                    // Memtree element: copy from namespace arrays
                    final java.util.Map<String, String> nsMap = memElement.getNamespaceMap();
                    for (final java.util.Map.Entry<String, String> e : nsMap.entrySet()) {
                        if (noPreserve && !usedPrefixes.contains(e.getKey())) {
                            continue; // strip unused namespace declaration
                        }
                        selfNsDecls.put(e.getKey(), e.getValue());
                        final QName nsQName = new QName(e.getKey(), e.getValue(),
                                javax.xml.XMLConstants.XMLNS_ATTRIBUTE);
                        addNamespace(nodeNum, nsQName);
                    }
                } else if (attrs != null) {
                    // DOM element: extract xmlns attributes
                    for (int i = 0; i < attrs.getLength(); i++) {
                        final Attr attr = (Attr) attrs.item(i);
                        if (javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())) {
                            final String nsPrefix = attr.getLocalName() != null
                                    && !javax.xml.XMLConstants.XMLNS_ATTRIBUTE.equals(attr.getLocalName())
                                    ? attr.getLocalName() : "";
                            if (noPreserve && !usedPrefixes.contains(nsPrefix)) {
                                continue; // strip unused namespace declaration
                            }
                            selfNsDecls.put(nsPrefix, attr.getValue());
                            final QName nsQName = new QName(nsPrefix, attr.getValue(),
                                    javax.xml.XMLConstants.XMLNS_ATTRIBUTE);
                            addNamespace(nodeNum, nsQName);
                        }
                    }
                }

                // No-inherit materialization: add ancestor namespace bindings from within
                // the subtree that are not already declared on this element
                if (scopeNamespaces != null) {
                    for (final java.util.Map.Entry<String, String> e : scopeNamespaces.entrySet()) {
                        if (!selfNsDecls.containsKey(e.getKey())) {
                            if (!noPreserve || usedPrefixes.contains(e.getKey())) {
                                final QName nsQName = new QName(e.getKey(), e.getValue(),
                                        javax.xml.XMLConstants.XMLNS_ATTRIBUTE);
                                addNamespace(nodeNum, nsQName);
                                selfNsDecls.put(e.getKey(), e.getValue());
                            }
                        }
                    }
                }

                // Build effective namespace scope for children
                final java.util.Map<String, String> childScope;
                if (scopeNamespaces != null) {
                    childScope = new java.util.LinkedHashMap<>(scopeNamespaces);
                    childScope.putAll(selfNsDecls);
                } else {
                    childScope = null;
                }

                // Copy children recursively, linking siblings together
                int prevChild = -1;
                Node child = node.getFirstChild();
                while (child != null) {
                    final int childNum = copyNodeIntoDocument(child, nodeNum, (short) (level + 1), childScope);
                    if (prevChild >= 0) {
                        next[prevChild] = childNum;
                    }
                    prevChild = childNum;
                    child = child.getNextSibling();
                }
                return nodeNum;
            }
            case Node.TEXT_NODE: {
                final String text = node.getTextContent();
                final int nodeNum = addNode(Node.TEXT_NODE, level, null);
                addChars(nodeNum, text.toCharArray(), 0, text.length());
                next[nodeNum] = parentNodeNum;
                return nodeNum;
            }
            case Node.COMMENT_NODE: {
                final String text = node.getTextContent();
                final int nodeNum = addNode(Node.COMMENT_NODE, level, null);
                addChars(nodeNum, text.toCharArray(), 0, text.length());
                next[nodeNum] = parentNodeNum;
                return nodeNum;
            }
            case Node.PROCESSING_INSTRUCTION_NODE: {
                final String target = node.getNodeName();
                final String data = node.getNodeValue() != null ? node.getNodeValue() : "";
                final QName qname = new QName(target, "", "");
                final int nodeNum = addNode(Node.PROCESSING_INSTRUCTION_NODE, level, qname);
                addChars(nodeNum, data.toCharArray(), 0, data.length());
                next[nodeNum] = parentNodeNum;
                return nodeNum;
            }
            case Node.CDATA_SECTION_NODE: {
                final String text = node.getTextContent();
                final int nodeNum = addNode(Node.CDATA_SECTION_NODE, level, null);
                addChars(nodeNum, text.toCharArray(), 0, text.length());
                next[nodeNum] = parentNodeNum;
                return nodeNum;
            }
            default:
                return -1;
        }
    }

    /**
     * Compact the document by rebuilding all internal arrays from the logical
     * tree structure. After in-memory mutations (insert, delete, replace),
     * nodes may be appended at the end of the arrays, breaking the positional
     * invariant that the XQuery engine relies on for document order. This method
     * serializes the mutated tree into a fresh document and replaces the internal
     * arrays, restoring correct positional ordering.
     *
     * Must be called after all mutations and text merging are complete.
     */
    public void compact() {
        try {
            final MemTreeBuilder builder = new MemTreeBuilder(context);
            builder.startDocument();
            final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder, true);
            receiver.setSuppressWhitespace(false);

            // Walk the document tree in logical order using chain-aware traversal
            int child = getFirstChildFor(0);
            while (child >= 0) {
                if (nodeKind[child] != -1) {
                    final NodeImpl node = getNode(child);
                    copyTo(node, receiver, false);
                }
                child = getNextSiblingFor(child);
            }

            builder.endDocument();
            final DocumentImpl newDoc = builder.getDocument();

            // Replace internal arrays with the rebuilt document's arrays
            this.nodeKind = newDoc.nodeKind;
            this.treeLevel = newDoc.treeLevel;
            this.next = newDoc.next;
            this.nodeName = newDoc.nodeName;
            this.nodeId = newDoc.nodeId;
            this.alpha = newDoc.alpha;
            this.alphaLen = newDoc.alphaLen;
            this.characters = newDoc.characters;
            this.nextChar = newDoc.nextChar;
            this.attrName = newDoc.attrName;
            this.attrType = newDoc.attrType;
            this.attrNodeId = newDoc.attrNodeId;
            this.attrParent = newDoc.attrParent;
            this.attrValue = newDoc.attrValue;
            this.nextAttr = newDoc.nextAttr;
            this.namespaceParent = newDoc.namespaceParent;
            this.namespaceCode = newDoc.namespaceCode;
            this.nextNamespace = newDoc.nextNamespace;
            this.size = newDoc.size;
            this.references = newDoc.references;
            this.nextReferenceIdx = newDoc.nextReferenceIdx;
            this.firstChildOverride = null;
        } catch (final SAXException e) {
            throw new RuntimeException("Failed to compact document after mutations", e);
        }
    }
}
