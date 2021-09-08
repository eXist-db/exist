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
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.Receiver;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import javax.xml.XMLConstants;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.dom.QName.Validity.ILLEGAL_FORMAT;


/**
 * An in-memory implementation of Document.
 */
public class DocumentImpl extends NodeImpl<DocumentImpl> implements Document {

    private static final AtomicLong nextDocId = new AtomicLong();

    private Memtree memtree;

    private Database db;
    protected XQueryContext context;

    protected String documentURI = null;
    protected final long docId;  // TODO(AR) do we need a docId field?
    protected final boolean explicitlyCreated;

    public DocumentImpl(final Memtree memtree, final XQueryContext context, final boolean explicitlyCreated) {
        this(null, memtree, context, explicitlyCreated);
    }

    public DocumentImpl(final Expression expression, final Memtree memtree, final XQueryContext context, final boolean explicitlyCreated) {
        super(expression, null, 0);
        this.memtree = memtree;
        this.context = context;
        this.explicitlyCreated = explicitlyCreated;
        this.docId = nextDocId.incrementAndGet();
        if (context != null) {
            db = context.getDatabase();
        } else {
            db = null;
        }
        document = this;  // TODO(AR) should we remove this? probably yes as we transition to MemtreeImpl
    }

    private Database getDatabase() {
        if (db == null) {
            try {
                db = BrokerPool.getInstance();
            } catch (final EXistException e) {
                throw new RuntimeException(e);
            }
        }
        return db;
    }

    public long getDocId() {
        return docId;
    }

    public boolean isExplicitlyCreated() {
        return explicitlyCreated;
    }

    @Override
    public String getStringValue() {
        if (document == null) {
            return "";
        }
        return super.getStringValue();
    }

    public NodeImpl getAttribute(final int nodeNum) throws DOMException {
        return new AttrImpl(getExpression(), this, nodeNum);
    }

    public NodeImpl getNamespaceNode(final int nodeNum) throws DOMException {
        return new NamespaceNode(getExpression(), this, nodeNum);
    }

    public NodeImpl getNode(final int nodeNum) throws DOMException {
        if (nodeNum == 0) {
            return this;
        }

        if (nodeNum >= memtree.getSize()) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, "node not found");
        }

        // TODO(AR) we could cache the most frequently used to avoid recreating the objects? -- where else do we contruct the DOM objects - could we also do that there?
        final NodeImpl node;
        switch (memtree.getNodeType(nodeNum)) {
            case Node.ELEMENT_NODE:
                node = new ElementImpl(getExpression(), this, nodeNum);
                break;

            case Node.TEXT_NODE:
                node = new TextImpl(getExpression(), this, nodeNum);
                break;

            case Node.COMMENT_NODE:
                node = new CommentImpl(getExpression(), this, nodeNum);
                break;

            case Node.PROCESSING_INSTRUCTION_NODE:
                node = new ProcessingInstructionImpl(getExpression(), this, nodeNum);
                break;

            case Node.CDATA_SECTION_NODE:
                node = new CDATASectionImpl(getExpression(), this, nodeNum);
                break;

            case NodeImpl.REFERENCE_NODE:
                node = new ReferenceNode(getExpression(), this, nodeNum);
                break;

            default:
                throw new DOMException(DOMException.NOT_FOUND_ERR, "node not found");
        }
        return node;
    }

    public NodeImpl getLastAttr() {
        final int attrNum = memtree.getLastAttr();
        if (attrNum == -1) {
            return null;
        }
        return new AttrImpl(getExpression(), this, attrNum);
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
        final int nodeNum = memtree.getDocumentElement();
        if (nodeNum == -1) {
            return null;
        }
        return (Element) getNode(nodeNum);
    }

    @Override
    public Node getFirstChild() {
        if (memtree.getSize() > 1) {
            return getNode(1);
        }
        return null;
    }

    @Override
    public Node getLastChild() {
        if (memtree.getSize() > 1) {
            return getNode(memtree.getLastChildFor(0));
        }
        return null;
    }

    @Override
    public void selectChildren(final NodeTest test, final Sequence result) throws XPathException {
        if (memtree.getSize() == 1) {
            return;
        }

        NodeImpl next = (NodeImpl) getFirstChild();
        while (next != null) {
            if (test.matches(next)) {
                result.add(next);
            }
            next = (NodeImpl) next.getNextSibling();
        }
    }

    @Override
    public void selectDescendants(final boolean includeSelf, final NodeTest test, final Sequence result)
            throws XPathException {
        if (includeSelf && test.matches(this)) {
            result.add(this);
        }

        if (memtree.getSize() == 1) {
            return;
        }

        NodeImpl next = (NodeImpl) getFirstChild();
        while (next != null) {
            if (test.matches(next)) {
                result.add(next);
            }
            next.selectDescendants(includeSelf, test, result);
            next = (NodeImpl) next.getNextSibling();
        }
    }

    @Override
    public void selectDescendantAttributes(final NodeTest test, final Sequence result)
            throws XPathException {
        if (memtree.getSize() == 1) {
            return;
        }

        NodeImpl next = (NodeImpl) getFirstChild();
        while (next != null) {
            if (test.matches(next)) {
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
        if (memtree.getSize() == 1) {
            return null;
        }

        expand();

        final ElementImpl root = (ElementImpl) getDocumentElement();
        if (memtree.getIdAttribute(root.getNodeNumber(), id) != -1) {
            return root;
        }

        final int treeLevel = memtree.getTreeLevel(root.getNodeNumber());
        int nextNode = root.getNodeNumber();
        while (++nextNode < memtree.getSize() && memtree.getTreeLevel(nextNode) > treeLevel) {
            if (memtree.getNodeType(nextNode) == Node.ELEMENT_NODE && memtree.getIdAttribute(nextNode, id) != -1) {
                if (hasIdAttribute(nextNode, id)) {
                    return getNode(nextNode);
                } else if (memtree.getIdAttribute(nextNode, id) != -1) {
                    return typeConsidered ? (NodeImpl) getNode(nextNode).getParentNode() : getNode(nextNode);
                } else if (getNode(nextNode).getNodeName().equalsIgnoreCase("id") &&
                        getNode(nextNode).getStringValue().equals(id)) {
                    return typeConsidered ? (NodeImpl) getNode(nextNode).getParentNode() : getNode(nextNode);
                }
            }
        }

        return null;
    }

    public NodeImpl selectByIdref(final String id) {
        if (memtree.getSize() == 1) {
            return null;
        }

        expand();

        final ElementImpl root = (ElementImpl) getDocumentElement();
        AttrImpl attr = getIdrefAttribute(root.getNodeNumber(), id);
        if (attr != null) {
            return attr;
        }

        final int treeLevel = memtree.getTreeLevel(root.getNodeNumber());
        int nextNode = root.getNodeNumber();
        while (++nextNode < memtree.getSize() && memtree.getTreeLevel(nextNode) > treeLevel) {
            if (memtree.getNodeType(nextNode) == Node.ELEMENT_NODE) {
                attr = getIdrefAttribute(nextNode, id);
                if (attr != null) {
                    return attr;
                }
            }
        }

        return null;
    }

    private AttrImpl getIdrefAttribute(final int nodeNumber, final String id) {
        final int attr = memtree.getIdrefAttribute(nodeNumber, id);
        if (attr == -1) {
            return null;
        }
        return new AttrImpl(getExpression(), this, attr);
    }

    @Override
    public boolean matchChildren(final NodeTest test) {
        if (memtree.getSize() == 1) {
            return false;
        }

        NodeImpl next = (NodeImpl) getFirstChild();
        while (next != null) {
            if (test.matches(next)) {
                return true;
            }
            next = (NodeImpl) next.getNextSibling();
        }

        return false;
    }

    @Override
    public boolean matchDescendants(final boolean includeSelf, final NodeTest test) throws XPathException {
        if (includeSelf && test.matches(this)) {
            return true;
        }
        if (memtree.getSize() == 1) {
            return true;
        }
        NodeImpl next = (NodeImpl) getFirstChild();
        while (next != null) {
            if (test.matches(next)) {
                return true;
            }
            if (next.matchDescendants(includeSelf, test)) {
                return true;
            }
            next = (NodeImpl) next.getNextSibling();
        }
        return false;
    }

    @Override
    public boolean matchDescendantAttributes(final NodeTest test) throws XPathException {
        if (memtree.getSize() == 1) {
            return false;
        }
        NodeImpl next = (NodeImpl) getFirstChild();
        while (next != null) {
            if (test.matches(next)) {
                return true;
            }
            if (next.matchDescendantAttributes(test)) {
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
        } catch (final IllegalQNameException e) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, e.getMessage());
        }

        // check the QName is valid for use
        if (qname.isValid(false) != QName.Validity.VALID.val) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "name is invalid");
        }

        final int nodeNum = memtree.addNode(Node.ELEMENT_NODE, (short) 1, qname);
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
        } catch (final IllegalQNameException e) {
            final short errCode;
            if (e.getValidity() == ILLEGAL_FORMAT.val || (e.getValidity() & QName.Validity.INVALID_NAMESPACE.val) == QName.Validity.INVALID_NAMESPACE.val) {
                errCode = DOMException.NAMESPACE_ERR;
            } else {
                errCode = DOMException.INVALID_CHARACTER_ERR;
            }
            throw new DOMException(errCode, "qualified name is invalid");
        }

        // check the QName is valid for use
        final byte validity = qname.isValid(false);
        if ((validity & QName.Validity.INVALID_LOCAL_PART.val) == QName.Validity.INVALID_LOCAL_PART.val) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "qualified name is invalid");
        } else if ((validity & QName.Validity.INVALID_NAMESPACE.val) == QName.Validity.INVALID_NAMESPACE.val) {
            throw new DOMException(DOMException.NAMESPACE_ERR, "qualified name is invalid");
        }

        final int nodeNum = memtree.addNode(Node.ELEMENT_NODE, (short) 1, qname);
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
            if (getContext() != null) {
                qname = QName.parse(getContext(), name);
            } else {
                qname = new QName(name);
            }
        } catch (final IllegalQNameException e) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, e.getMessage());
        }

        // check the QName is valid for use
        if (qname.isValid(false) != QName.Validity.VALID.val) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "name is invalid");
        }

        // TODO(AR) implement this!
        throw unsupported();
    }

    @Override
    public Attr createAttributeNS(final String namespaceURI, final String qualifiedName) throws DOMException {
        final QName qname;
        try {
            if (getContext() != null) {
                qname = QName.parse(getContext(), qualifiedName, namespaceURI);
            } else {
                qname = QName.parse(namespaceURI, qualifiedName);
            }
        } catch (final IllegalQNameException e) {
            final short errCode;
            if (e.getValidity() == ILLEGAL_FORMAT.val || (e.getValidity() & QName.Validity.INVALID_NAMESPACE.val) == QName.Validity.INVALID_NAMESPACE.val) {
                errCode = DOMException.NAMESPACE_ERR;
            } else {
                errCode = DOMException.INVALID_CHARACTER_ERR;
            }
            throw new DOMException(errCode, "qualified name is invalid");
        }

        // check the QName is valid for use
        final byte validity = qname.isValid(false);
        if ((validity & QName.Validity.INVALID_LOCAL_PART.val) == QName.Validity.INVALID_LOCAL_PART.val) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "qualified name is invalid");
        } else if ((validity & QName.Validity.INVALID_NAMESPACE.val) == QName.Validity.INVALID_NAMESPACE.val) {
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
        if (tagname != null && tagname.equals(QName.WILDCARD)) {
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

        if (wildcardNS && wildcardLocalPart) {
            return getElementsByTagName(QName.WildcardQName.getInstance());
        } else if (wildcardNS) {
            return getElementsByTagName(new QName.WildcardNamespaceURIQName(localName));
        } else if (wildcardLocalPart) {
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
        if (memtree.getSize() < 2) {
            return new NodeListImpl();
        }

        final NodeListImpl nl = new NodeListImpl(1);  // likely to have at least one element
        for (int i = 1; i < memtree.getSize(); i++) {
            if (memtree.getNodeType(i) == Node.ELEMENT_NODE) {
                final QName qn = memtree.getNodeName(i);
                if (qn.matches(qname)) {
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
        memtree.copyTo(node.getNodeNumber(), receiver, null);
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
        if (memtree.getSize() == 0) {
            return;
        }

        final DocumentImpl newDoc = expandRefs(null);
        if (newDoc != this) {
            copyDocContents(newDoc);
        }
    }

    public DocumentImpl expandRefs(final NodeImpl rootNode) throws DOMException {
        if (!memtree.hasReferenceNodes()) {
            final NodeIdFactory nodeFactory = getDatabase().getNodeFactory();
            memtree.computeNodeIds(nodeFactory);
            return this;
        }

        final MemTreeBuilder builder = new MemTreeBuilder(getExpression(), context);
        final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(getExpression(), builder);
        final ConsumerE<NodeProxy, SAXException> referenceNodeReceiver = referenceNode -> {
            try (final DBBroker broker = getDatabase().getBroker()) {
                final Serializer serializer = broker.borrowSerializer();
                try {
                    serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
                    serializer.setReceiver(receiver);
                    serializer.toReceiver(referenceNode, false, false);
                } finally {
                    broker.returnSerializer(serializer);
                }
            } catch (final EXistException e) {
                throw new SAXException(e);
            }
        };

        try {
            builder.startDocument();
            NodeImpl node = (rootNode == null) ? (NodeImpl) getFirstChild() : rootNode;
            while (node != null) {
                memtree.copyTo(node.getNodeNumber(), receiver, referenceNodeReceiver);
                node = (NodeImpl) node.getNextSibling();
            }
            receiver.endDocument();
        } catch (final SAXException e) {
            throw new DOMException(DOMException.INVALID_STATE_ERR, e.getMessage());
        }

        final Memtree newDoc = builder.getMemtree();
        final NodeIdFactory nodeFactory = getDatabase().getNodeFactory();
        newDoc.computeNodeIds(nodeFactory);
        return new DocumentImpl(newDoc, context, explicitlyCreated);
    }

    public NodeImpl getNodeById(final NodeId id) {

        expand();

        for (int i = 0; i < memtree.getSize(); i++) {
            if (id.equals(memtree.getNodeId(i))) {
                return getNode(i);
            }
        }
        return null;
    }

    /**
     * DOCUMENT ME!
     *
     * @param newDoc
     */
    private void copyDocContents(final DocumentImpl newDoc) {
        this.memtree = newDoc.memtree;
    }

    /**
     * Stream the specified document fragment to a receiver. This method
     * is called by the serializer to output in-memory nodes.
     *
     * @param serializer the serializer
     * @param node       node to be serialized
     * @param receiver   the receiver
     * @throws SAXException if an error occurs
     */
    public void streamTo(final Serializer serializer, NodeImpl node, final Receiver receiver) throws SAXException {
        memtree.streamTo(serializer, node.getNodeNumber(), receiver);
    }

    public org.exist.dom.persistent.DocumentImpl makePersistent() throws XPathException {
        if (memtree.getSize() > 1) {
            return null;
        }
        return context.storeTemporaryDoc(this);
    }

    // this is DOM specific
    public int getChildCount() {
        return memtree.getChildCountFor(0);
    }

    @Override
    public boolean hasChildNodes() {
        return getChildCount() > 0;
    }

    @Override
    public NodeList getChildNodes() {
        if (memtree.getSize() == 1) {
            return new NodeListImpl(0);
        }

        final NodeListImpl children = new NodeListImpl(1);  // most likely a single element!
        int nextChildNodeNum = 1;
        while (nextChildNodeNum > 0) {
            final NodeImpl child = getNode(nextChildNodeNum);
            children.add(child);
            nextChildNodeNum = memtree.getNextSiblingFor(nextChildNodeNum);
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
        if (el != null) {
            final String baseURI = getDocumentElement().getAttributeNS(Namespaces.XML_NS, "base");
            if (baseURI != null) {
                return baseURI;
            }
        }
        final String docURI = getDocumentURI();
        if (docURI != null) {
            return docURI;
        } else {
            if (context != null && context.isBaseURIDeclared()) {
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
        if (memtree.getSize() != 1) {
            int nodeNum = 1;
            while (true) {
                result.append(getNode(nodeNum).toString());
                final int nextNodeNum = memtree.getNextSiblingFor(nodeNum);
                if (nextNodeNum < nodeNum) {
                    break;
                }
                nodeNum = nextNodeNum;
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
        if (newChild.getNodeType() != Node.DOCUMENT_NODE && newChild.getOwnerDocument() != this) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "Owning document IDs do not match");
        }

        if (newChild == this) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "Cannot append a document to itself");
        }

        if (newChild.getNodeType() == DOCUMENT_NODE) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "A Document Node may not be appended to a Document Node");
        }

        if (newChild.getNodeType() == ELEMENT_NODE && getDocumentElement() != null) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "A Document Node may only have a single document element");
        }

        if (newChild.getNodeType() == DOCUMENT_TYPE_NODE && getDoctype() != null) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "A Document Node may only have a single document type");
        }

        throw unsupported();
    }
}
