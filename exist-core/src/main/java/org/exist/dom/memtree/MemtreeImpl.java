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

import com.evolvedbinary.j8fu.function.ConsumerE;
import org.exist.dom.QName;
import org.exist.dom.persistent.NodeProxy;
import org.exist.numbering.NodeId;
import org.exist.numbering.NodeIdFactory;
import org.exist.storage.ElementValue;
import org.exist.storage.serializers.Serializer;
import org.exist.util.hashtable.NamePool;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import javax.xml.XMLConstants;
import java.util.Arrays;

/**
 * An in-memory representation of a Node Tree for DOM purposes.
 * <p>
 * Nodes are stored in a series of arrays which are all indexed by the {@code nodeNum} (Node Number).
 * Nodes are stored into the arrays in a Depth First Preorder (Root, Left, Right) Traversal.
 * The {@code nodeNum} starts at 0, the array {@link #nodeKind} indicates the type of the Node.
 * For example, for a complete XML Document, when {@code nodeNum == 0}, then {@code nodeKind[nodeNum] == org.w3c.dom.Node.DOCUMENT_NODE}.
 * <p>
 * The array {@link #treeLevel} indicates at what level in the tree the node appears, the root of the tree is 0.
 * For example, for a complete XML Document, when {@code nodeNum == 0}, then {@code treeLevel[nodeNum] == 0}.
 * <p>
 * The array {@link #next} gives the {@code nodeNum} of the next node in the tree,
 * for example {@code int nextNodeNum = next[nodeNum]}.
 * <p>
 * The following arrays hold the data of the nodes themselves:
 * * {@link #namespaceParent}
 * * {@link #namespaceCode}
 * * {@link #nodeName}
 * * {@link #alpha}
 * * {@link #alphaLen}
 * * {@link #characters}
 * * {@link #nodeId}
 * * {@link #attrName}
 * * {@link #attrType}
 * * {@link #attrNodeId}
 * * {@link #attrParent}
 * * {@link #attrValue}
 * * {@link #references}
 * <p>
 * This implementation stores all node data in the document object. Nodes from another document, i.e. a persistent document in the database, can be
 * stored as reference nodes, i.e. the nodes are not copied into this document object. Instead a reference is inserted which will only be expanded
 * during serialization.
 */
public class MemtreeImpl implements Memtree {

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

    protected NodeId[] nodeId;  // TODO(AR) each of these could be computed the first time they are needed from the level and nodeNum

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

    // reference nodes (link to an external, persistent document fragment)
    protected NodeProxy[] references = null;
    protected int nextReferenceIdx = 0;
    // end reference nodes

    protected NamePool namePool;

    boolean replaceAttribute = false;

    public MemtreeImpl() {
        this(null);
    }

    public MemtreeImpl(@Nullable final NamePool namePool) {
        if (namePool == null) {
            this.namePool = new NamePool();
        } else {
            this.namePool = namePool;
        }
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
    }

    @Override
    public void reset() {
        size = 0;
        nextChar = 0;
        nextAttr = 0;
        nextReferenceIdx = 0;
        references = null;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public short getTreeLevel(final int nodeNumber) {
        return treeLevel[nodeNumber];
    }

    @Override
    public short getNodeType(final int nodeNumber) {
        if (nodeKind == null || nodeNumber < 0) {
            return -1;
        }
        return nodeKind[nodeNumber];
    }

    @Override
    public QName getNodeName(final int nodeNumber) {
        return nodeName[nodeNumber];
    }

    @Override
    public NodeId getNodeId(final int nodeNumber) {
        return nodeId[nodeNumber];
    }

    @Override
    public int getDocumentElement() {
        if (size == 1) {
            return -1;
        }

        int nodeNumber = 1;
        while (nodeKind[nodeNumber] != Node.ELEMENT_NODE) {
            if (next[nodeNumber] < nodeNumber) {
                return -1;
            }
            nodeNumber = next[nodeNumber];
        }
        return nodeNumber;
    }

    @Override
    public int getAttributesCountFor(final int nodeNumber) {
        int count = 0;
        int attr = alpha[nodeNumber];
        if (attr > -1) {
            while (attr < nextAttr && attrParent[attr++] == nodeNumber) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int getNamespacesCountFor(final int nodeNumber) {
        int count = 0;
        int ns = alphaLen[nodeNumber];
        if (ns > -1) {
            while (ns < nextNamespace && namespaceParent[ns++] == nodeNumber) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int getChildCountFor(final int nodeNumber) {
        int count = 0;
        int nextNode = getFirstChildFor(nodeNumber);
        while (nextNode > nodeNumber) {
            count++;
            nextNode = next[nextNode];
        }
        return count;
    }

    @Override
    public int getFirstChildFor(final int nodeNumber) {
        if (nodeNumber == 0) {
            // optimisation for document-node
            if (size > 1) {
                return 1;
            } else {
                return -1;
            }
        }

        final short level = treeLevel[nodeNumber];
        final int nextNode = nodeNumber + 1;
        if (nextNode < size && treeLevel[nextNode] > level) {
            return nextNode;
        }
        return -1;
    }

    @Override
    public int getLastChildFor(final int nodeNumber) {
        int lastChild = -1;
        final short childLevel = (short) (treeLevel[nodeNumber] + 1);
        int nextNode = nodeNumber + 1;
        while (nextNode < size && treeLevel[nextNode] >= childLevel) {
            if (treeLevel[nextNode] == childLevel) {
                lastChild = nextNode;
            }
            nextNode++;
        }
        return lastChild;
    }

    @Override
    public int getNextSiblingFor(final int nodeNumber) {
        final int nextNode = next[nodeNumber];
        return nextNode < nodeNumber ? -1 : nextNode;
    }

    @Override
    public int getParentNodeFor(final int nodeNumber) {
        int nextNode = next[nodeNumber];
        while (nextNode > nodeNumber) {
            nextNode = next[nextNode];
        }
        return nextNode;
    }

    @Override
    public int getIdAttribute(final int nodeNumber, final String id) {
        int attr = alpha[nodeNumber];
        if (attr > -1) {
            while (attr < nextAttr && attrParent[attr] == nodeNumber) {
                if (attrType[attr] == AttrImpl.ATTR_ID_TYPE && id.equals(attrValue[attr])) {
                    return attr;
                }
                attr++;
            }
        }
        return -1;
    }

    @Override
    public int getIdrefAttribute(final int nodeNumber, final String id) {
        int attr = alpha[nodeNumber];
        if (attr > -1) {
            while (attr < nextAttr && attrParent[attr] == nodeNumber) {
                if (attrType[attr] == AttrImpl.ATTR_IDREF_TYPE && id.equals(attrValue[attr])) {
                    return attr;
                }
                attr++;
            }
        }

        return -1;
    }

    @Override
    public int getLastNode() {
        return size - 1;
    }

    @Override
    public int getLastAttr() {
        if (nextAttr == 0) {
            return -1;
        }
        return nextAttr - 1;
    }

    @Override
    public int addNode(final short kind, final short level, final QName qname) {
        if (nodeKind == null) {
            init();
        }
        if (size == nodeKind.length) {
            grow();
        }
        nodeKind[size] = kind;
        treeLevel[size] = level;
        nodeName[size] = qname != null ? namePool.getSharedName(qname) : null;
        alpha[size] = -1; // undefined
        next[size] = -1;
        return size++;
    }

    @Override
    public int addNamespace(final int nodeNumber, final QName qname) {
        if (nodeKind == null) {
            init();
        }
        if (namespaceCode == null || nextNamespace == namespaceCode.length) {
            growNamespaces();
        }
        namespaceCode[nextNamespace] = namePool.getSharedName(qname);
        namespaceParent[nextNamespace] = nodeNumber;
        if (alphaLen[nodeNumber] < 0) {
            alphaLen[nodeNumber] = nextNamespace;
        }
        return nextNamespace++;
    }

    @Override
    public int addAttribute(final int nodeNumber, final QName qname, final String value, final int type) throws DOMException {
        if (nodeKind == null) {
            init();
        }
        if (nodeNumber > 0 && !(nodeKind[nodeNumber] == Node.ELEMENT_NODE || nodeKind[nodeNumber] == NodeImpl.NAMESPACE_NODE)) {
            throw new DOMException(DOMException.INUSE_ATTRIBUTE_ERR,
                    "err:XQTY0024: An attribute node cannot follow a node that is not an element or namespace node.");
        }
        int prevAttr = nextAttr - 1;
        int attrN;
        //Check if an attribute with the same qname exists in the parent element
        while (nodeNumber > 0 && prevAttr > -1 && attrParent[prevAttr] == nodeNumber) {
            attrN = prevAttr--;
            final QName prevQn = attrName[attrN];
            if (prevQn.equals(qname)) {
                if (replaceAttribute) {
                    attrValue[attrN] = value;
                    attrType[attrN] = type;
                    return attrN;
                } else {
                    throw new DOMException(DOMException.INUSE_ATTRIBUTE_ERR,
                            "err:XQDY0025: element has more than one attribute '" + qname + "'");
                }
            }
        }
        if (nextAttr == attrName.length) {
            growAttributes();
        }
        final QName attrQname = new QName(qname.getLocalPart(), qname.getNamespaceURI(), qname.getPrefix(), ElementValue.ATTRIBUTE);
        attrParent[nextAttr] = nodeNumber;
        attrName[nextAttr] = namePool.getSharedName(attrQname);
        attrValue[nextAttr] = value;
        attrType[nextAttr] = type;
        if (alpha[nodeNumber] < 0) {
            alpha[nodeNumber] = nextAttr;
        }
        return nextAttr++;
    }

    @Override
    public void addChars(final int nodeNumber, final char[] ch, final int start, final int len) {
        if (nodeKind == null) {
            init();
        }
        if (characters == null) {
            characters = new char[Math.max(len, CHAR_BUF_SIZE)];
        } else if (nextChar + len >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if (newLen < nextChar + len) {
                newLen = nextChar + len;
            }
            final char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alpha[nodeNumber] = nextChar;
        alphaLen[nodeNumber] = len;
        System.arraycopy(ch, start, characters, nextChar, len);
        nextChar += len;
    }

    @Override
    public void addChars(final int nodeNumber, final CharSequence s) {
        if (nodeKind == null) {
            init();
        }
        int len = (s == null) ? 0 : s.length();
        if (characters == null) {
            characters = new char[Math.max(len, CHAR_BUF_SIZE)];
        } else if (nextChar + len >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if (newLen < nextChar + len) {
                newLen = nextChar + len;
            }
            final char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alpha[nodeNumber] = nextChar;
        alphaLen[nodeNumber] = len;
        for (int i = 0; i < len; i++) {
            characters[nextChar++] = s.charAt(i);
        }
    }

    @Override
    public void appendChars(final int nodeNumber, final char[] ch, final int start, final int len) {
        if (characters == null) {
            characters = new char[Math.max(len, CHAR_BUF_SIZE)];
        } else if (nextChar + len >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if (newLen < nextChar + len) {
                newLen = nextChar + len;
            }
            final char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alphaLen[nodeNumber] = alphaLen[nodeNumber] + len;
        System.arraycopy(ch, start, characters, nextChar, len);
        nextChar += len;
    }

    @Override
    public void appendChars(final int nodeNumber, final CharSequence s) {
        final int len = s.length();
        if (characters == null) {
            characters = new char[Math.max(len, CHAR_BUF_SIZE)];
        } else if (nextChar + len >= characters.length) {
            int newLen = (characters.length * 3) / 2;
            if (newLen < nextChar + len) {
                newLen = nextChar + len;
            }
            final char[] nc = new char[newLen];
            System.arraycopy(characters, 0, nc, 0, characters.length);
            characters = nc;
        }
        alphaLen[nodeNumber] = alphaLen[nodeNumber] + len;
        for (int i = 0; i < len; i++) {
            characters[nextChar++] = s.charAt(i);
        }
    }

    @Override
    public void addReferenceNode(final int nodeNumber, final NodeProxy proxy) {
        if (nodeKind == null) {
            init();
        }
        if (references == null || nextReferenceIdx == references.length) {
            growReferences();
        }
        references[nextReferenceIdx] = proxy;
        alpha[nodeNumber] = nextReferenceIdx++;
    }

    @Override
    public boolean hasReferenceNodes() {
        return nextReferenceIdx > 0;
    }

    @Override
    public void replaceReferenceNode(final int nodeNumber, final CharSequence ch) {
        nodeKind[nodeNumber] = Node.TEXT_NODE;
        references[alpha[nodeNumber]] = null;
        addChars(nodeNumber, ch);
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
        if (references == null) {
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
        if (namespaceCode == null) {
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

    @Override
    public void computeNodeIds(final NodeIdFactory nodeIdFactory) {
        if (nodeId[0] != null) {
            return;
        }

        nodeId[0] = nodeIdFactory.documentNodeId();
        if (size == 1) {
            return;
        }

        NodeId nextId = nodeIdFactory.createInstance();
        int next = 1;
        while (next > 0) {
            computeNodeIds(nextId, next);
            next = getNextSiblingFor(next);
            nextId = nextId.nextSibling();
        }
    }

    private void computeNodeIds(final NodeId id, final int nodeNumber) {
        nodeId[nodeNumber] = id;
        if (nodeKind[nodeNumber] == Node.ELEMENT_NODE) {
            NodeId nextId = id.newChild();
            int attr = alpha[nodeNumber];
            if (attr > -1) {
                while (attr < nextAttr && attrParent[attr] == nodeNumber) {
                    attrNodeId[attr] = nextId;
                    nextId = nextId.nextSibling();
                    attr++;
                }
            }
            int nextNode = getFirstChildFor(nodeNumber);
            while (nextNode > nodeNumber) {
                computeNodeIds(nextId, nextNode);
                nextNode = next[nextNode];
                if (nextNode > nodeNumber) {
                    nextId = nextId.nextSibling();
                }
            }
        }
    }

    @Override
    public void copyTo(int nodeNumber, final DocumentBuilderReceiver receiver, @Nullable final ConsumerE<NodeProxy, SAXException> referenceNodeReceiver) throws SAXException {
        final int topNodeNumber = nodeNumber;
        while (nodeNumber > -1) {
            copyStartNode(nodeNumber, receiver, referenceNodeReceiver);

            int nextNode;
            if (nodeKind[nodeNumber] == NodeImpl.REFERENCE_NODE) {
                //Nothing more to stream ?
                nextNode = -1;
            } else {
                nextNode = getFirstChildFor(nodeNumber);
            }

            while (nextNode == -1) {
                copyEndNode(nodeNumber, receiver);
                if (topNodeNumber == nodeNumber) {
                    break;
                }
                //No nextNode if the top node is a Document node
                nextNode = getNextSiblingFor(nodeNumber);
                if (nextNode == -1) {
                    nodeNumber = getParentNodeFor(nodeNumber);
                    if (nodeNumber == -1 || topNodeNumber == nodeNumber) {
                        copyEndNode(nodeNumber, receiver);
                        break;
                    }
                }
            }
            nodeNumber = nextNode;
        }
    }

    private void copyStartNode(final int nodeNumber, final DocumentBuilderReceiver receiver, @Nullable final ConsumerE<NodeProxy, SAXException> referenceNodeReceiver) throws SAXException {
        switch (nodeKind[nodeNumber]) {
            case Node.ELEMENT_NODE: {
                final QName qn = nodeName[nodeNumber];
                receiver.startElement(qn, null);
                int attr = alpha[nodeNumber];
                if (attr > -1) {
                    while (attr < nextAttr && attrParent[attr] == nodeNumber) {
                        final QName attrQName = attrName[attr];
                        receiver.attribute(attrQName, attrValue[attr]);
                        attr++;
                    }
                }
                int ns = alphaLen[nodeNumber];
                if (ns > -1) {
                    while (ns < nextNamespace && namespaceParent[ns] == nodeNumber) {
                        final QName nsQName = namespaceCode[ns];
                        receiver.addNamespaceNode(nsQName);
                        ns++;
                    }
                }
                break;
            }

            case Node.TEXT_NODE:
                receiver.characters(characters, alpha[nodeNumber], alphaLen[nodeNumber]);
                break;

            case Node.CDATA_SECTION_NODE:
                receiver.cdataSection(characters, alpha[nodeNumber], alphaLen[nodeNumber]);
                break;

            case Node.ATTRIBUTE_NODE:
                final QName attrQName = attrName[nodeNumber];
                receiver.attribute(attrQName, attrValue[nodeNumber]);
                break;

            case Node.COMMENT_NODE:
                receiver.comment(characters, alpha[nodeNumber], alphaLen[nodeNumber]);
                break;

            case Node.PROCESSING_INSTRUCTION_NODE:
                final QName piQName = nodeName[nodeNumber];
                final String data = new String(characters, alpha[nodeNumber], alphaLen[nodeNumber]);
                receiver.processingInstruction(piQName.getLocalPart(), data);
                break;

            case NodeImpl.NAMESPACE_NODE:
                receiver.addNamespaceNode(namespaceCode[nodeNumber]);
                break;

            case NodeImpl.REFERENCE_NODE:
                if (referenceNodeReceiver != null) {
                    referenceNodeReceiver.accept(references[alpha[nodeNumber]]);
                } else {
                    receiver.addReferenceNode(references[alpha[nodeNumber]]);
                }
                break;
        }
    }

    private void copyEndNode(final int nodeNumber, final DocumentBuilderReceiver receiver) throws SAXException {
        if (nodeKind[nodeNumber] == Node.ELEMENT_NODE) {
            receiver.endElement(nodeName[nodeNumber]);
        }
    }

    @Override
    public void streamTo(final Serializer serializer, int nodeNumber, final Receiver receiver) throws SAXException {
        final int topNodeNumber = nodeNumber;
        while (nodeNumber > -1) {
            startNode(serializer, nodeNumber, receiver);
            int nextNode;
            if (nodeKind[nodeNumber] == NodeImpl.REFERENCE_NODE) {
                //Nothing more to stream ?
                nextNode = -1;
            } else {
                nextNode = getFirstChildFor(nodeNumber);
            }

            while (nextNode == -1) {
                endNode(nodeNumber, receiver);
                if (topNodeNumber == nodeNumber) {
                    break;
                }
                nextNode = getNextSiblingFor(nodeNumber);
                if (nextNode == -1) {
                    nodeNumber = getParentNodeFor(nodeNumber);
                    if (nodeNumber == -1 || topNodeNumber == nodeNumber) {
                        endNode(nodeNumber, receiver);
                        break;
                    }
                }
            }
            nodeNumber = nextNode;
        }
    }

    private void startNode(final Serializer serializer, final int nodeNumber, final Receiver receiver) throws SAXException {
        switch (nodeKind[nodeNumber]) {
            case Node.ELEMENT_NODE:
                final QName qn = nodeName[nodeNumber];
                //Output required namespace declarations
                int ns = alphaLen[nodeNumber];
                if (ns > -1) {
                    while ((ns < nextNamespace) && (namespaceParent[ns] == nodeNumber)) {
                        final QName nsQName = namespaceCode[ns];
                        if (XMLConstants.XMLNS_ATTRIBUTE.equals(nsQName.getLocalPart())) {
                            receiver.startPrefixMapping(XMLConstants.DEFAULT_NS_PREFIX, nsQName.getNamespaceURI());
                        } else {
                            receiver.startPrefixMapping(nsQName.getLocalPart(), nsQName.getNamespaceURI());
                        }
                        ns++;
                    }
                }
                //Create the attribute list
                AttrList attribs = null;
                int attr = alpha[nodeNumber];
                if (attr > -1) {
                    attribs = new AttrList();
                    while ((attr < nextAttr) && (attrParent[attr] == nodeNumber)) {
                        final QName attrQName = attrName[attr];
                        attribs.addAttribute(attrQName, attrValue[attr]);
                        attr++;
                    }
                }
                receiver.startElement(qn, attribs);
                break;

            case Node.TEXT_NODE:
                receiver.characters(new String(characters, alpha[nodeNumber], alphaLen[nodeNumber]));
                break;

            case Node.ATTRIBUTE_NODE:
                final QName attrQName = attrName[nodeNumber];
                receiver.attribute(attrQName, attrValue[nodeNumber]);
                break;

            case Node.COMMENT_NODE:
                receiver.comment(characters, alpha[nodeNumber], alphaLen[nodeNumber]);
                break;

            case Node.PROCESSING_INSTRUCTION_NODE:
                final QName piQName = nodeName[nodeNumber];
                final String data = new String(characters, alpha[nodeNumber], alphaLen[nodeNumber]);
                receiver.processingInstruction(piQName.getLocalPart(), data);
                break;

            case Node.CDATA_SECTION_NODE:
                receiver.cdataSection(characters, alpha[nodeNumber], alphaLen[nodeNumber]);
                break;

            case NodeImpl.REFERENCE_NODE:
                serializer.toReceiver(references[alpha[nodeNumber]], true, false);
                break;
        }
    }

    private void endNode(final int nodeNumber, final Receiver receiver) throws SAXException {
        if (nodeKind[nodeNumber] == Node.ELEMENT_NODE) {
            receiver.endElement(nodeName[nodeNumber]);
            //End all prefix mappings used for the element
            int ns = alphaLen[nodeNumber];
            if (ns > -1) {
                while (ns < nextNamespace && namespaceParent[ns] == nodeNumber) {
                    final QName nsQName = namespaceCode[ns];
                    if (XMLConstants.XMLNS_ATTRIBUTE.equals(nsQName.getLocalPart())) {
                        receiver.endPrefixMapping(XMLConstants.DEFAULT_NS_PREFIX);
                    } else {
                        receiver.endPrefixMapping(nsQName.getLocalPart());
                    }
                    ns++;
                }
            }
        }
    }
}
