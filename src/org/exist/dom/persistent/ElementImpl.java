/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2014 The eXist-db Project
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
 */
package org.exist.dom.persistent;

import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.dom.NamedNodeMapImpl;
import org.exist.dom.QName;
import org.exist.indexing.StreamListener;
import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.stax.IEmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.NodePath;
import org.exist.storage.RangeIndexSpec;
import org.exist.storage.Signatures;
import org.exist.storage.btree.Value;
import org.exist.storage.dom.INodeIterator;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.ByteArrayPool;
import org.exist.util.ByteConversion;
import org.exist.util.UTF8;
import org.exist.util.pool.NodePool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.exist.xquery.value.StringValue;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.w3c.dom.TypeInfo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

/**
 * ElementImpl.java
 *
 * @author Wolfgang Meier
 */
public class ElementImpl extends NamedNode implements Element {

    public static final int LENGTH_ELEMENT_CHILD_COUNT = 4; //sizeof int
    public static final int LENGTH_ATTRIBUTES_COUNT = 2; //sizeof short
    public static final int LENGTH_NS_ID = 2; //sizeof short
    public static final int LENGTH_PREFIX_LENGTH = 2; //sizeof short

    private short attributes = 0;
    private int children = 0;

    private int position = 0;
    private Map<String, String> namespaceMappings = null;
    private int indexType = RangeIndexSpec.NO_INDEX;
    private boolean preserveWS = false;
    private boolean isDirty = false;

    public ElementImpl() {
        super(Node.ELEMENT_NODE);
    }

    /**
     * Constructor for the ElementImpl object
     *
     * @param nodeName Description of the Parameter
     */
    public ElementImpl(final QName nodeName, final SymbolTable symbols) throws DOMException {
        super(Node.ELEMENT_NODE, nodeName);
        this.nodeName = nodeName;
        if(symbols.getSymbol(nodeName.getLocalPart()) < 0) {
            throw new DOMException(DOMException.INVALID_ACCESS_ERR,
                "Too many element/attribute names registered in the database. No of distinct names is limited to 16bit. Aborting store.");
        }
    }

    public ElementImpl(final ElementImpl other) {
        super(other);
        this.children = other.children;
        this.attributes = other.attributes;
        this.namespaceMappings = other.namespaceMappings;
        this.indexType = other.indexType;
        this.position = other.position;
    }

    /**
     * Reset this element to its initial state.
     */
    @Override
    public void clear() {
        super.clear();
        attributes = 0;
        children = 0;
        position = 0;
        namespaceMappings = null;
        //TODO : reset below as well ? -pb
        //indexType
        //preserveWS
    }

    public void setIndexType(final int idxType) {
        this.indexType = idxType;
    }

    public int getIndexType() {
        return indexType;
    }

    @Override
    public boolean isDirty() {
        return isDirty;
    }

    @Override
    public void setDirty(final boolean dirty) {
        this.isDirty = dirty;
    }

    public void setPosition(final int position) {
        this.position = position;
    }

    public int getPosition() {
        return position;
    }

    public boolean declaresNamespacePrefixes() {
        if(namespaceMappings == null) {
            return false;
        }
        return namespaceMappings.size() > 0;
    }

    /**
     * Serializes a (persistent DOM) Element to a byte array
     *
     * data = signature childCount nodeIdUnitsLength nodeId attributesCount localNameId namespace? prefixData?
     *
     * signature = [byte] 0x20 | localNameType | hasNamespace? | isDirty?
     *
     * localNameType = noContent OR intContent OR shortContent OR byteContent
     * noContent = 0x0
     * intContent = 0x1
     * shortContent = 0x2
     * byteContent = 0x3
     *
     * hasNamespace = 0x10
     *
     * isDirty = 0x8
     *
     * childCount = [int] (4 bytes) The number of child nodes
     *
     * nodeIdUnitsLength = [short] (2 bytes) The number of units of the element's NodeId
     * nodeId = @see org.exist.numbering.DLNBase#serialize(byte[], int)
     *
     * attributesCount = [short] (2 bytes) The number of attributes
     *
     * localNameId = [int] (4 bytes) | [short] (2 bytes) | [byte] 1 byte. The Id of the element's local name from SymbolTable (symbols.dbx)
     *
     * namespace = namespaceUriId namespacePrefixLength elementNamespacePrefix?
     * namespaceUriId = [short] (2 bytes) The Id of the namespace URI from SymbolTable (symbols.dbx)
     * namespacePrefixLength = [short] (2 bytes)
     * elementNamespacePrefix = eUtf8
     *
     * eUtf8 = {@see org.exist.util.UTF8#encode(java.lang.String, byte[], int)}
     *
     * prefixData = namespaceMappingsCount namespaceMapping+
     * namespaceMappingsCount = [short] (2 bytes)
     * namespaceMapping = namespacePrefix namespaceUriId
     * namespacePrefix = jUtf8
     *
     * jUtf8 = {@see java.io.DataOutputStream#writeUTF(java.lang.String)}
     */
    @Override
    public byte[] serialize() {
        if(nodeId == null) {
            throw new RuntimeException("nodeId = null for element: " +
                getQName().getStringValue());
        }
        try {
            final SymbolTable symbols = ownerDocument.getBrokerPool().getSymbols();
            byte[] prefixData = null;
            // serialize namespace prefixes declared in this element
            if(declaresNamespacePrefixes()) {
                final ByteArrayOutputStream bout = new ByteArrayOutputStream();
                final DataOutputStream out = new DataOutputStream(bout);
                out.writeShort(namespaceMappings.size());
                for(final Iterator<Map.Entry<String, String>> i =
                        namespaceMappings.entrySet().iterator(); i.hasNext(); ) {
                    final Map.Entry<String, String> entry = i.next();
                    out.writeUTF(entry.getKey());
                    final short nsId = symbols.getNSSymbol(entry.getValue());
                    out.writeShort(nsId);
                }
                prefixData = bout.toByteArray();
            }

            final short id = symbols.getSymbol(this);
            final boolean hasNamespace = nodeName.hasNamespace();
            short nsId = 0;
            if(hasNamespace) {
                nsId = symbols.getNSSymbol(nodeName.getNamespaceURI());
            }
            final byte idSizeType = Signatures.getSizeType(id);
            byte signature = (byte) ((Signatures.Elem << 0x5) | idSizeType);
            int prefixLen = 0;
            if(hasNamespace) {
                if(nodeName.getPrefix() != null && nodeName.getPrefix().length() > 0) {
                    prefixLen = UTF8.encoded(nodeName.getPrefix());
                }
                signature |= 0x10;
            }
            if(isDirty) {
                signature |= 0x8;
            }
            final int nodeIdLen = nodeId.size();
            final byte[] data =
                ByteArrayPool.getByteArray(
                    StoredNode.LENGTH_SIGNATURE_LENGTH + LENGTH_ELEMENT_CHILD_COUNT +
                        NodeId.LENGTH_NODE_ID_UNITS +
                        nodeIdLen + LENGTH_ATTRIBUTES_COUNT +
                        Signatures.getLength(idSizeType) +
                        (hasNamespace ? prefixLen + 4 : 0) +
                        (prefixData != null ? prefixData.length : 0)
                );
            int next = 0;
            data[next] = signature;
            next += StoredNode.LENGTH_SIGNATURE_LENGTH;
            ByteConversion.intToByte(children, data, next);
            next += LENGTH_ELEMENT_CHILD_COUNT;
            ByteConversion.shortToByte((short) nodeId.units(), data, next);
            next += NodeId.LENGTH_NODE_ID_UNITS;
            nodeId.serialize(data, next);
            next += nodeIdLen;
            ByteConversion.shortToByte(attributes, data, next);
            next += LENGTH_ATTRIBUTES_COUNT;
            Signatures.write(idSizeType, id, data, next);
            next += Signatures.getLength(idSizeType);
            if(hasNamespace) {
                ByteConversion.shortToByte(nsId, data, next);
                next += LENGTH_NS_ID;
                ByteConversion.shortToByte((short) prefixLen, data, next);
                next += LENGTH_PREFIX_LENGTH;
                if(nodeName.getPrefix() != null && nodeName.getPrefix().length() > 0) {
                    UTF8.encode(nodeName.getPrefix(), data, next);
                }
                next += prefixLen;
            }
            if(prefixData != null) {
                System.arraycopy(prefixData, 0, data, next, prefixData.length);
            }
            return data;
        } catch(final IOException e) {
            return null;
        }
    }

    public static StoredNode deserialize(final byte[] data, final int start, final int len,
            final DocumentImpl doc, final boolean pooled) {
        final int end = start + len;
        int pos = start;
        final byte idSizeType = (byte) (data[pos] & 0x03);
        boolean isDirty = (data[pos] & 0x8) == 0x8;
        final boolean hasNamespace = (data[pos] & 0x10) == 0x10;
        pos += StoredNode.LENGTH_SIGNATURE_LENGTH;
        final int children = ByteConversion.byteToInt(data, pos);
        pos += LENGTH_ELEMENT_CHILD_COUNT;
        final int dlnLen = ByteConversion.byteToShort(data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        final NodeId dln = doc.getBrokerPool().getNodeFactory().createFromData(dlnLen, data, pos);
        pos += dln.size();
        final short attributes = ByteConversion.byteToShort(data, pos);
        pos += LENGTH_ATTRIBUTES_COUNT;
        final short id = (short) Signatures.read(idSizeType, data, pos);
        pos += Signatures.getLength(idSizeType);
        short nsId = 0;
        String prefix = null;
        if(hasNamespace) {
            nsId = ByteConversion.byteToShort(data, pos);
            pos += LENGTH_NS_ID;
            int prefixLen = ByteConversion.byteToShort(data, pos);
            pos += LENGTH_PREFIX_LENGTH;
            if(prefixLen > 0) {
                prefix = UTF8.decode(data, pos, prefixLen).toString();
            }
            pos += prefixLen;
        }
        final String name = doc.getBrokerPool().getSymbols().getName(id);
        String namespace = XMLConstants.NULL_NS_URI;
        if(nsId != 0) {
            namespace = doc.getBrokerPool().getSymbols().getNamespace(nsId);
        }

        final ElementImpl node;
        if(pooled) {
            node = (ElementImpl) NodePool.getInstance().borrowNode(Node.ELEMENT_NODE);
        } else {
            node = new ElementImpl();
        }
        node.setNodeId(dln);
        node.nodeName = doc.getBrokerPool().getSymbols().getQName(Node.ELEMENT_NODE, namespace, name, prefix);
        node.children = children;
        node.attributes = attributes;
        node.isDirty = isDirty;
        node.setOwnerDocument(doc);
        //TO UNDERSTAND : why is this code here ?
        if(end > pos) {
            final byte[] pfxData = new byte[end - pos];
            System.arraycopy(data, pos, pfxData, 0, end - pos);
            final InputStream bin = new ByteArrayInputStream(pfxData);
            final DataInputStream in = new DataInputStream(bin);
            try {
                final short prefixCount = in.readShort();
                for(int i = 0; i < prefixCount; i++) {
                    prefix = in.readUTF();
                    nsId = in.readShort();
                    node.addNamespaceMapping(prefix, doc.getBrokerPool().getSymbols().getNamespace(nsId));
                }
            } catch(final IOException e) {
                LOG.error(e);
            }
        }
        return node;
    }

    public static QName readQName(final Value value, final DocumentImpl document, final NodeId nodeId) {
        final byte[] data = value.data();
        int offset = value.start();
        final byte idSizeType = (byte) (data[offset] & 0x03);
        final boolean hasNamespace = (data[offset] & 0x10) == 0x10;
        offset += StoredNode.LENGTH_SIGNATURE_LENGTH;
        offset += LENGTH_ELEMENT_CHILD_COUNT;
        offset += NodeId.LENGTH_NODE_ID_UNITS;
        offset += nodeId.size();
        offset += LENGTH_ATTRIBUTES_COUNT;
        final short id = (short) Signatures.read(idSizeType, data, offset);
        offset += Signatures.getLength(idSizeType);
        short nsId = 0;
        String prefix = null;
        if(hasNamespace) {
            nsId = ByteConversion.byteToShort(data, offset);
            offset += LENGTH_NS_ID;
            int prefixLen = ByteConversion.byteToShort(data, offset);
            offset += LENGTH_PREFIX_LENGTH;
            if(prefixLen > 0) {
                prefix = UTF8.decode(data, offset, prefixLen).toString();
            }
            offset += prefixLen;
        }
        final String name = document.getBrokerPool().getSymbols().getName(id);
        final String namespace;
        if(nsId != 0) {
            namespace = document.getBrokerPool().getSymbols().getNamespace(nsId);
        } else {
            namespace = XMLConstants.NULL_NS_URI;
        }
        return new QName(name, namespace, prefix == null ? XMLConstants.DEFAULT_NS_PREFIX : prefix);
    }

    public static void readNamespaceDecls(final List<String[]> namespaces, final Value value, final DocumentImpl document, final NodeId nodeId) {
        final byte[] data = value.data();
        int offset = value.start();
        final int end = offset + value.getLength();
        final byte idSizeType = (byte) (data[offset] & 0x03);
        final boolean hasNamespace = (data[offset] & 0x10) == 0x10;
        offset += StoredNode.LENGTH_SIGNATURE_LENGTH;
        offset += LENGTH_ELEMENT_CHILD_COUNT;
        offset += NodeId.LENGTH_NODE_ID_UNITS;
        offset += nodeId.size();
        offset += LENGTH_ATTRIBUTES_COUNT;
        offset += Signatures.getLength(idSizeType);
        if(hasNamespace) {
            offset += LENGTH_NS_ID;
            int prefixLen = ByteConversion.byteToShort(data, offset);
            offset += LENGTH_PREFIX_LENGTH;
            offset += prefixLen;
        }
        if(end > offset) {
            final byte[] pfxData = new byte[end - offset];
            System.arraycopy(data, offset, pfxData, 0, end - offset);
            final InputStream bin = new ByteArrayInputStream(pfxData);
            final DataInputStream in = new DataInputStream(bin);
            try {
                final short prefixCount = in.readShort();
                String prefix;
                short nsId;
                for(int i = 0; i < prefixCount; i++) {
                    prefix = in.readUTF();
                    nsId = in.readShort();
                    namespaces.add(new String[]{prefix, document.getBrokerPool().getSymbols().getNamespace(nsId)});
                }
            } catch(final IOException e) {
                LOG.error(e);
            }
        }
    }

    public void addNamespaceMapping(final String prefix, final String ns) {
        if(prefix == null) {
            return;
        }

        if(namespaceMappings == null) {
            namespaceMappings = new HashMap<>(1);
        } else if(namespaceMappings.containsKey(prefix)) {
            return;
        }

        namespaceMappings.put(prefix, ns);
    }

    /**
     * Append a child to this node. This method does not rearrange the
     * node tree and is only used internally by the parser.
     *
     * @param child
     * @throws DOMException
     */
    public void appendChildInternal(final IStoredNode prevNode, final NodeHandle child) throws DOMException {
        final NodeId childId;
        if(prevNode == null) {
            childId = getNodeId().newChild();
        } else {
            if(prevNode.getNodeId() == null) {
                LOG.warn(getQName() + " : " + prevNode.getNodeName());
            }
            childId = prevNode.getNodeId().nextSibling();
        }
        child.setNodeId(childId);
        ++children;
    }

    @Override
    public Node appendChild(final Node child) throws DOMException {
        final TransactionManager transact = ownerDocument.getBrokerPool().getTransactionManager();
        final org.exist.dom.NodeListImpl nl = new org.exist.dom.NodeListImpl();
        nl.add(child);
        try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker();
                final Txn transaction = transact.beginTransaction()) {
            appendChildren(transaction, nl, 0);
            broker.storeXMLResource(transaction, getOwnerDocument());
            transact.commit(transaction); // bugID 3419602
            return getLastChild();
        } catch(final Exception e) {
            throw new DOMException(DOMException.INVALID_STATE_ERR, e.getMessage());
        }
    }

    private void appendAttributes(final Txn transaction, final NodeList attribs) throws DOMException {
        final NodeList duplicateAttrs = findDupAttributes(attribs);
        removeAppendAttributes(transaction, duplicateAttrs, attribs);
    }

    private NodeList checkForAttributes(final Txn transaction, final NodeList nodes) throws DOMException {
        org.exist.dom.NodeListImpl attribs = null;
        org.exist.dom.NodeListImpl rest = null;
        for(int i = 0; i < nodes.getLength(); i++) {
            final Node next = nodes.item(i);
            if(next.getNodeType() == Node.ATTRIBUTE_NODE) {
                if(!next.getNodeName().startsWith(XMLConstants.XMLNS_ATTRIBUTE)) {
                    if(attribs == null) {
                        attribs = new org.exist.dom.NodeListImpl();
                    }
                    attribs.add(next);
                }
            } else if(attribs != null) {
                if(rest == null) {
                    rest = new org.exist.dom.NodeListImpl();
                }
                rest.add(next);
            }
        }
        if(attribs == null) {
            return nodes;
        }
        appendAttributes(transaction, attribs);
        return rest;
    }

    @Override
    public void appendChildren(final Txn transaction, NodeList nodes, final int child) throws DOMException {
        // attributes are handled differently. Call checkForAttributes to extract them.
        nodes = checkForAttributes(transaction, nodes);
        if(nodes == null || nodes.getLength() == 0) {
            return;
        }

        try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker()) {
            final NodePath path = getPath();
            StreamListener listener = null;
            //May help getReindexRoot() to make some useful things
            broker.getIndexController().setDocument(ownerDocument);
            IStoredNode reindexRoot = broker.getIndexController().getReindexRoot(this, path, true, true);
            broker.getIndexController().setMode(StreamListener.STORE);
            // only reindex if reindexRoot is an ancestor of the current node
            if(reindexRoot == null) {
                listener = broker.getIndexController().getStreamListener();
            }
            if(children == 0) {
                // no children: append a new child
                appendChildren(transaction, nodeId.newChild(), null, new NodeImplRef(this), path, nodes, listener);
            } else {
                if(child == 1) {
                    final Node firstChild = getFirstChild();
                    insertBefore(transaction, nodes, firstChild);
                } else {
                    if(child > 1 && child <= children) {
                        final NodeList cl = getChildNodes();
                        final IStoredNode<?> last = (IStoredNode<?>) cl.item(child - 2);
                        insertAfter(transaction, nodes, last);
                    } else {
                        final IStoredNode<?> last = (IStoredNode<?>) getLastChild();
                        appendChildren(transaction, last.getNodeId().nextSibling(), null,
                            new NodeImplRef(getLastNode(last)), path, nodes, listener);
                    }
                }
            }
            broker.updateNode(transaction, this, false);
            broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            broker.flush();
        } catch(final EXistException e) {
            LOG.warn("Exception while appending child node: " + e.getMessage(), e);
        }
    }

    /**
     * Internal append.
     *
     * @throws DOMException
     */
    private void appendChildren(final Txn transaction,
            NodeId newNodeId, final NodeId followingId,
            final NodeImplRef last, final NodePath lastPath,
            final NodeList nodes, final StreamListener listener) throws DOMException {

        if(last == null || last.getNode() == null || last.getNode().getOwnerDocument() == null) {
            throw new DOMException(DOMException.INVALID_MODIFICATION_ERR, "invalid node");
        }
        children += nodes.getLength();
        for(int i = 0; i < nodes.getLength(); i++) {
            final Node child = nodes.item(i);
            appendChild(transaction, newNodeId, last, lastPath, child, listener);
            NodeId next = newNodeId.nextSibling();
            if(followingId != null && next.equals(followingId)) {
                next = newNodeId.insertNode(followingId);
                if(LOG.isDebugEnabled()) {
                    LOG.debug("Node ID collision on " + followingId + ". Using " + next + " instead.");
                }
            }
            newNodeId = next;
        }
    }

    private Node appendChild(final Txn transaction, final NodeId newNodeId, final NodeImplRef last, final NodePath lastPath, final Node child, final StreamListener listener)
        throws DOMException {
        if(last == null || last.getNode() == null) {
            throw new DOMException(DOMException.INVALID_MODIFICATION_ERR, "invalid node");
        }
        final DocumentImpl owner = getOwnerDocument();
        try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker()) {
            switch(child.getNodeType()) {

                case Node.DOCUMENT_FRAGMENT_NODE:
                    appendChildren(transaction, newNodeId, null, last, lastPath,
                        child.getChildNodes(), listener);
                    return null; // TODO: implement document fragments so
                //we can return all newly appended children

                case Node.ELEMENT_NODE:
                    // create new element
                    final ElementImpl elem =
                        new ElementImpl(
                            new QName(child.getLocalName() == null ?
                                child.getNodeName() : child.getLocalName(),
                                child.getNamespaceURI(),
                                child.getPrefix()),
                            broker.getBrokerPool().getSymbols()
                        );
                    elem.setNodeId(newNodeId);
                    elem.setOwnerDocument(owner);
                    final org.exist.dom.NodeListImpl ch = new org.exist.dom.NodeListImpl();
                    final NamedNodeMap attribs = child.getAttributes();
                    int numActualAttribs = 0;
                    for(int i = 0; i < attribs.getLength(); i++) {
                        final Attr attr = (Attr) attribs.item(i);
                        if(!attr.getNodeName().startsWith(XMLConstants.XMLNS_ATTRIBUTE)) {
                            ch.add(attr);
                            numActualAttribs++;
                        } else {
                            final String xmlnsDecl = attr.getNodeName();
                            final String prefix = xmlnsDecl.length() == 5 ? XMLConstants.DEFAULT_NS_PREFIX : xmlnsDecl.substring(6);
                            elem.addNamespaceMapping(prefix, attr.getNodeValue());
                        }
                    }
                    final NodeList cl = child.getChildNodes();
                    for(int i = 0; i < cl.getLength(); i++) {
                        final Node n = cl.item(i);
                        if(n.getNodeType() != Node.ATTRIBUTE_NODE) {
                            ch.add(n);
                        }
                    }
                    elem.setChildCount(ch.getLength());
                    if(numActualAttribs != (short) numActualAttribs) {
                        throw new DOMException(DOMException.INVALID_MODIFICATION_ERR, "Too many attributes");
                    }
                    elem.setAttributes((short) numActualAttribs);
                    lastPath.addComponent(elem.getQName());
                    // insert the node
                    broker.insertNodeAfter(transaction, last.getNode(), elem);
                    broker.indexNode(transaction, elem, lastPath);
                    broker.getIndexController().indexNode(transaction, elem, lastPath, listener);
                    elem.setChildCount(0);
                    last.setNode(elem);
                    //process child nodes
                    elem.appendChildren(transaction, newNodeId.newChild(), null, last, lastPath, ch, listener);
                    broker.endElement(elem, lastPath, null);
                    broker.getIndexController().endElement(transaction, elem, lastPath, listener);
                    lastPath.removeLastComponent();
                    return elem;

                case Node.TEXT_NODE:
                    final TextImpl text = new TextImpl(newNodeId, ((Text)child).getData());
                    text.setOwnerDocument(owner);
                    // insert the node
                    broker.insertNodeAfter(transaction, last.getNode(), text);
                    broker.indexNode(transaction, text, lastPath);
                    broker.getIndexController().indexNode(transaction, text, lastPath, listener);
                    last.setNode(text);
                    return text;

                case Node.CDATA_SECTION_NODE:
                    final CDATASectionImpl cdata = new CDATASectionImpl(newNodeId, ((CDATASection)child).getData());
                    cdata.setOwnerDocument(owner);
                    // insert the node
                    broker.insertNodeAfter(transaction, last.getNode(), cdata);
                    broker.indexNode(transaction, cdata, lastPath);
                    last.setNode(cdata);
                    return cdata;

                case Node.ATTRIBUTE_NODE:
                    final Attr attr = (Attr) child;
                    final String ns = attr.getNamespaceURI();
                    final String prefix = (Namespaces.XML_NS.equals(ns) ? XMLConstants.XML_NS_PREFIX : attr.getPrefix());
                    String name = attr.getLocalName();
                    if(name == null) {
                        name = attr.getName();
                    }
                    final QName attrName = new QName(name, ns, prefix);
                    final AttrImpl attrib = new AttrImpl(attrName, attr.getValue(), broker.getBrokerPool().getSymbols());
                    attrib.setNodeId(newNodeId);
                    attrib.setOwnerDocument(owner);
                    if(ns != null && attrName.compareTo(Namespaces.XML_ID_QNAME) == Constants.EQUAL) {
                        // an xml:id attribute. Normalize the attribute and set its type to ID
                        attrib.setValue(StringValue.trimWhitespace(StringValue.collapseWhitespace(attrib.getValue())));
                        attrib.setType(AttrImpl.ID);
                    } else {
                        attrib.setQName(new QName(attrib.getQName(), ElementValue.ATTRIBUTE));
                    }
                    broker.insertNodeAfter(transaction, last.getNode(), attrib);
                    broker.indexNode(transaction, attrib, lastPath);
                    broker.getIndexController().indexNode(transaction, attrib, lastPath, listener);
                    last.setNode(attrib);
                    return attrib;

                case Node.COMMENT_NODE:
                    final CommentImpl comment = new CommentImpl(((Comment)child).getData());
                    comment.setNodeId(newNodeId);
                    comment.setOwnerDocument(owner);
                    // insert the node
                    broker.insertNodeAfter(transaction, last.getNode(), comment);
                    broker.indexNode(transaction, comment, lastPath);
                    last.setNode(comment);
                    return comment;

                case Node.PROCESSING_INSTRUCTION_NODE:
                    final ProcessingInstructionImpl pi =
                        new ProcessingInstructionImpl(newNodeId,
                            ((ProcessingInstruction)child).getTarget(),
                            ((ProcessingInstruction)child).getData());
                    pi.setOwnerDocument(owner);
                    //insert the node
                    broker.insertNodeAfter(transaction, last.getNode(), pi);
                    broker.indexNode(transaction, pi, lastPath);
                    last.setNode(pi);
                    return pi;

                default:
                    throw new DOMException(DOMException.INVALID_MODIFICATION_ERR, "Unknown node type: " + child.getNodeType() + " " + child.getNodeName());
            }
        } catch(final EXistException e) {
            LOG.warn("Exception while appending node: " + e.getMessage(), e);
        }

        return null;
    }

    @Override
    public boolean hasAttributes() {
        return attributes > 0;
    }

    public void setAttributes(final short attribNum) {
        attributes = attribNum;
    }

    @Override
    public String getAttribute(final String name) {
        final Attr attr = findAttribute(name);
        return attr != null ? attr.getValue() : "";
    }

    @Override
    public String getAttributeNS(final String namespaceURI, final String localName) {
        final Attr attr = findAttribute(new QName(localName, namespaceURI));
        return attr != null ? attr.getValue() : XMLConstants.NULL_NS_URI;
        //XXX: if not present must return null
    }

    @Deprecated //move as soon as getAttributeNS null issue resolved 
    public String _getAttributeNS(final String namespaceURI, final String localName) {
        final Attr attr = findAttribute(new QName(localName, namespaceURI));
        return attr != null ? attr.getValue() : null;
    }

    @Override
    public Attr getAttributeNode(final String name) {
        return findAttribute(name);
    }

    @Override
    public Attr getAttributeNodeNS(final String namespaceURI, final String localName) {
        return findAttribute(new QName(localName, namespaceURI));
    }

    @Override
    public NamedNodeMap getAttributes() {
        final org.exist.dom.NamedNodeMapImpl map = new NamedNodeMapImpl();
        if(hasAttributes()) {
            try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker();
                final INodeIterator iterator = broker.getNodeIterator(this)) {

                iterator.next();
                final int childCount = getChildCount();
                for(int i = 0; i < childCount; i++) {
                    final IStoredNode next = iterator.next();
                    if(next.getNodeType() != Node.ATTRIBUTE_NODE) {
                        break;
                    }
                    map.setNamedItem(next);
                }
            } catch(final EXistException | IOException e) {
                LOG.warn("Exception while retrieving attributes: " + e.getMessage());
            }
        }
        if(declaresNamespacePrefixes()) {
            for(final Iterator<Map.Entry<String, String>> i =
                    namespaceMappings.entrySet().iterator(); i.hasNext(); ) {
                final Map.Entry<String, String> entry = i.next();
                final String prefix = entry.getKey().toString();
                final String ns = entry.getValue().toString();
                final QName attrName = new QName(prefix, Namespaces.XMLNS_NS, XMLConstants.XMLNS_ATTRIBUTE);
                final AttrImpl attr = new AttrImpl(attrName, ns, null);
                attr.setOwnerDocument(ownerDocument);
                map.setNamedItem(attr);
            }
        }
        return map;
    }

    private AttrImpl findAttribute(final String qname) {
        try(final DBBroker broker  = ownerDocument.getBrokerPool().getBroker();
                final INodeIterator iterator = broker.getNodeIterator(this)) {
            iterator.next();
            return findAttribute(qname, iterator, this);
        } catch(final EXistException | IOException e) {
            LOG.warn("Exception while retrieving attributes: " + e.getMessage());
        }
        return null;
    }

    private AttrImpl findAttribute(final String qname, final INodeIterator iterator, final IStoredNode current) {
        final int childCount = current.getChildCount();
        IStoredNode next;
        for(int i = 0; i < childCount; i++) {
            next = iterator.next();
            if(next.getNodeType() != Node.ATTRIBUTE_NODE) {
                break;
            }
            if(next.getNodeName().equals(qname)) {
                return (AttrImpl) next;
            }
        }
        return null;
    }

    private AttrImpl findAttribute(final QName qname) {
        try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker();
                final INodeIterator iterator = broker.getNodeIterator(this)) {
            iterator.next();
            return findAttribute(qname, iterator, this);
        } catch(final EXistException | IOException e) {
            LOG.warn("Exception while retrieving attributes: " + e.getMessage());
        }
        return null;
    }

    private AttrImpl findAttribute(final QName qname, final INodeIterator iterator, final IStoredNode current) {
        final int childCount = current.getChildCount();
        for(int i = 0; i < childCount; i++) {
            final IStoredNode next = iterator.next();
            if(next.getNodeType() != Node.ATTRIBUTE_NODE) {
                break;
            }
            if(next.getQName().equals(qname)) {
                return (AttrImpl) next;
            }
        }
        return null;
    }

    /**
     * Returns a list of all attribute nodes in attrs that are already present
     * in the current element.
     *
     * @param attrs
     * @return The attributes list
     * @throws DOMException
     */
    private NodeList findDupAttributes(final NodeList attrs) throws DOMException {
        org.exist.dom.NodeListImpl dupList = null;
        final NamedNodeMap map = getAttributes();
        for(int i = 0; i < attrs.getLength(); i++) {
            final Node attr = attrs.item(i);
            //Workaround: Xerces sometimes returns null for getLocalPart() !!!!
            String localName = attr.getLocalName();
            if(localName == null) {
                localName = attr.getNodeName();
            }
            final Node duplicate = map.getNamedItemNS(attr.getNamespaceURI(), localName);
            if(duplicate != null) {
                if(dupList == null) {
                    dupList = new org.exist.dom.NodeListImpl();
                }
                dupList.add(duplicate);
            }
        }
        return dupList;
    }

    @Override
    public int getChildCount() {
        return children;
    }

    @Override
    public NodeList getChildNodes() {
        final org.exist.dom.NodeListImpl childList = new org.exist.dom.NodeListImpl(1);
        try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker()) {
            for(final IEmbeddedXMLStreamReader reader = broker.getXMLStreamReader(this, true);
                reader.hasNext(); ) {
                final int status = reader.next();
                if(status != XMLStreamConstants.END_ELEMENT && ((NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID)).isChildOf(nodeId)) {
                    childList.add(reader.getNode());
                }
            }
        } catch(final IOException e) {
            LOG.warn("Internal error while reading child nodes: " + e.getMessage(), e);
        } catch(final XMLStreamException e) {
            LOG.warn("Internal error while reading child nodes: " + e.getMessage(), e);
        } catch(final EXistException e) {
            LOG.warn("Internal error while reading child nodes: " + e.getMessage(), e);
        }
        return childList;
    }

    @Override
    public NodeList getElementsByTagName(final String tagName) {
        final QName qname = new QName(tagName);
        return getOwnerDocument().findElementsByTagName(this, qname);
    }

    @Override
    public NodeList getElementsByTagNameNS(final String namespaceURI, final String localName) {
        final QName qname = new QName(localName, namespaceURI);
        return getOwnerDocument().findElementsByTagName(this, qname);
    }

    @Override
    public Node getFirstChild() {
        if(!hasChildNodes() || getChildCount() == attributes) {
            return null;
        }

        try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker();
                final INodeIterator iterator = broker.getNodeIterator(this)) {
            iterator.next();
            IStoredNode next;
            for(int i = 0; i < getChildCount(); i++) {
                next = iterator.next();
                if(next.getNodeType() != Node.ATTRIBUTE_NODE) {
                    return next;
                }
            }
        } catch(final EXistException | IOException e) {
            LOG.warn("Exception while retrieving child node: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public Node getLastChild() {
        if(!hasChildNodes()) {
            return null;
        }
        Node node = null;
        if(!isDirty) {
            final NodeId child = nodeId.getChild(children);
            node = ownerDocument.getNode(new NodeProxy(ownerDocument, child));
        }
        if(node == null) {
            final NodeList cl = getChildNodes();
            return cl.item(cl.getLength() - 1);
        }
        return node;
    }

    @Override
    public String getTagName() {
        return nodeName.getStringValue();
    }

    @Override
    public boolean hasAttribute(final String name) {
        return findAttribute(name) != null;
    }

    @Override
    public boolean hasAttributeNS(final String namespaceURI, final String localName) {
        return findAttribute(new QName(localName, namespaceURI)) != null;
    }

    /**
     * @see org.w3c.dom.Node#getNodeValue()
     */
    //TODO getNodeValue() on org.exist.dom.persistent.ElementImpl should return null according to W3C spec, and getTextContent() should be implemented!
    @Override
    public String getNodeValue() throws DOMException {
        //TODO : parametrize the boolean value ?
        try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker()) {
            return broker.getNodeValue(this, false);
        } catch(final EXistException e) {
            LOG.warn("Exception while reading node value: " + e.getMessage(), e);
        }
        return "";
    }

    /**
     * @see org.w3c.dom.Element#removeAttribute(java.lang.String)
     */
    @Override
    public void removeAttribute(final String name) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "removeAttribute(String name) not implemented on class " + getClass().getName());
    }

    /**
     * @see org.w3c.dom.Element#removeAttributeNS(java.lang.String, java.lang.String)
     */
    @Override
    public void removeAttributeNS(final String namespaceURI, final String name) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "removeAttributeNS(String namespaceURI, String name) not implemented on class " + getClass().getName());
    }

    @Override
    public Attr removeAttributeNode(final Attr oldAttr) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "removeAttributeNode(Attr oldAttr) not implemented on class " + getClass().getName());
    }

    @Override
    public void setAttribute(final String name, final String value) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "setAttribute(String name, String value) not implemented on class " + getClass().getName());
    }

    @Override
    public void setAttributeNS(final String namespaceURI, final String qualifiedName, final String value) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "setAttributeNS(String namespaceURI, String qualifiedName," +
                "String value) not implemented on class " + getClass().getName());
    }

    @Override
    public Attr setAttributeNode(final Attr newAttr) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "setAttributeNode(Attr newAttr) not implemented on class " + getClass().getName());
    }

    @Override
    public Attr setAttributeNodeNS(final Attr newAttr) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "setAttributeNodeNS(Attr newAttr) not implemented on class " + getClass().getName());
    }

    public void setChildCount(final int count) {
        children = count;
    }

    public void setNamespaceMappings(final Map<String, String> map) {
        this.namespaceMappings = new HashMap<>(map);
        for(final String ns : namespaceMappings.values()) {
            ownerDocument.getBrokerPool().getSymbols().getNSSymbol(ns);
        }
    }

    public Iterator<String> getPrefixes() {
        return namespaceMappings.keySet().iterator();
    }

    public String getNamespaceForPrefix(final String prefix) {
        return namespaceMappings.get(prefix);
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return toString(true);
    }

    /**
     */
    @Override
    public String toString(final boolean top) {
        return toString(top, new TreeSet<String>());
    }

    /**
     * Method toString.
     */
    private String toString(final boolean top, final Set<String> namespaces) {
        final StringBuilder buf = new StringBuilder();
        final StringBuilder attributes = new StringBuilder();
        final StringBuilder children = new StringBuilder();
        buf.append('<');
        buf.append(nodeName);
        //Remove false to have a verbose output
        //if (top && false) {
        //buf.append(" xmlns:exist=\""+ Namespaces.EXIST_NS + "\"");
        //buf.append(" exist:id=\"");
        //buf.append(getNodeId());
        //buf.append("\" exist:document=\"");
        //buf.append(((DocumentImpl)getOwnerDocument()).getFileURI());
        //buf.append("\"");
        //}
        if(declaresNamespacePrefixes()) {
            // declare namespaces used by this element
            Map.Entry<String, String> entry;
            String namespace, prefix;
            for(final Iterator<Map.Entry<String, String>>
                    i = namespaceMappings.entrySet().iterator(); i.hasNext(); ) {
                entry = i.next();
                prefix = entry.getKey();
                namespace = entry.getValue();
                buf.append(" ").append(XMLConstants.XMLNS_ATTRIBUTE);
                if(prefix.length() == 0) {
                    buf.append("\"");
                    //.append(namespace);
                } else {
                    buf.append(":")
                    .append(prefix)
                    .append("=\"");
                    //.append(namespace);
                }
                buf.append("...\" ");
                namespaces.add(namespace);
            }
        }
        if(nodeName.getNamespaceURI().length() > 0
            && (!namespaces.contains(nodeName.getNamespaceURI()))) {
            buf.append(" ")
                .append(XMLConstants.XMLNS_ATTRIBUTE)
                .append(":")
                .append(nodeName.getPrefix()).append("=\"")
                .append(nodeName.getNamespaceURI())
                .append("\" ");
        }
        final NodeList childNodes = getChildNodes();
        for(int i = 0; i < childNodes.getLength(); i++) {
            final Node child = childNodes.item(i);
            switch(child.getNodeType()) {

                case Node.ATTRIBUTE_NODE:
                    attributes.append(' ')
                        .append(((Attr) child).getName())
                        .append("=\"")
                        .append(escapeXml(child))
                        .append("\"");
                    break;

                case Node.ELEMENT_NODE:
                    children.append(((ElementImpl) child).toString(false, namespaces));
                    break;

                default:
                    children.append(child.toString());
            }
        }

        if(attributes.length() > 0) {
            buf.append(attributes.toString());
        }

        if(childNodes.getLength() > 0) {
            buf.append(">");
            buf.append(children.toString());
            buf.append("</");
            buf.append(nodeName);
            buf.append(">");
        } else {
            buf.append("/>");
        }

        return buf.toString();
    }

    @Override
    public Node insertBefore(final Node newChild, final Node refChild) throws DOMException {
        if(refChild == null) {
            return appendChild(newChild);
        } else if(!(refChild instanceof IStoredNode)) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "Wrong node type");
        }

        final org.exist.dom.NodeListImpl nl = new org.exist.dom.NodeListImpl();
        nl.add(newChild);
        final TransactionManager transact = ownerDocument.getBrokerPool().getTransactionManager();

        try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker();
                final Txn transaction = transact.beginTransaction();) {
            insertBefore(transaction, nl, refChild);
            broker.storeXMLResource(transaction, getOwnerDocument());
            transact.commit(transaction);
            return refChild.getPreviousSibling();
        } catch(final TransactionException e) {
            throw new DOMException(DOMException.NO_MODIFICATION_ALLOWED_ERR, e.getMessage());
        } catch(final EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Insert a list of nodes at the position before the reference
     * child.
     */
    @Override
    public void insertBefore(final Txn transaction, final NodeList nodes, final Node refChild) throws DOMException {
        if(refChild == null) {
            //TODO : use NodeImpl.UNKNOWN_NODE_IMPL_GID ? -pb
            appendChildren(transaction, nodes, -1);
            return;
        } else if(!(refChild instanceof IStoredNode)) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        }

        try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker()) {
            final NodePath path = getPath();
            //May help getReindexRoot() to make some useful things
            broker.getIndexController().setDocument(ownerDocument);
            final IStoredNode reindexRoot = broker.getIndexController().getReindexRoot(this, path, true, true);
            broker.getIndexController().setMode(StreamListener.STORE);

            final StreamListener listener;
            if(reindexRoot == null) {
                listener = broker.getIndexController().getStreamListener();
            } else {
                listener = null;
            }

            final IStoredNode<?> following = (IStoredNode<?>) refChild;
            final IStoredNode<?> previous = (IStoredNode<?>) following.getPreviousSibling();
            if(previous == null) {
                // there's no sibling node before the new node
                final NodeId newId = following.getNodeId().insertBefore();
                appendChildren(transaction, newId, following.getNodeId(), new NodeImplRef(this),
                    path, nodes, listener);
            } else {
                // insert the new node between the preceding and following sibling
                final NodeId newId = previous.getNodeId().insertNode(following.getNodeId());
                appendChildren(transaction, newId, following.getNodeId(),
                    new NodeImplRef(getLastNode(previous)), path, nodes, listener);
            }
            setDirty(true);
            broker.updateNode(transaction, this, true);
            broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            broker.flush();
        } catch(final EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        }
    }

    /**
     * Insert a list of nodes at the position following the reference
     * child.
     */
    @Override
    public void insertAfter(final Txn transaction, final NodeList nodes, final Node refChild) throws DOMException {
        if(refChild == null) {
            //TODO : use NodeImpl.UNKNOWN_NODE_IMPL_GID ? -pb
            appendChildren(null, nodes, -1);
            return;
        } else if(!(refChild instanceof IStoredNode)) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type: ");
        }

        try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker()) {
            final NodePath path = getPath();
            //May help getReindexRoot() to make some useful things
            broker.getIndexController().setDocument(ownerDocument);
            final IStoredNode reindexRoot = broker.getIndexController().getReindexRoot(this, path, true, true);
            broker.getIndexController().setMode(StreamListener.STORE);

            final StreamListener listener;
            if(reindexRoot == null) {
                listener = broker.getIndexController().getStreamListener();
            } else {
                listener = null;
            }

            final IStoredNode<?> previous = (IStoredNode<?>) refChild;
            final IStoredNode<?> following = (IStoredNode<?>) previous.getNextSibling();
            final NodeId followingId = following == null ? null : following.getNodeId();
            final NodeId newNodeId = previous.getNodeId().insertNode(followingId);
            appendChildren(transaction, newNodeId, followingId, new NodeImplRef(getLastNode(previous)), path, nodes, listener);
            setDirty(true);
            broker.updateNode(transaction, this, true);
            broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            broker.flush();
        } catch(final EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        }
    }

    /**
     * Update the contents of this element. The passed list of nodes
     * becomes the new content.
     *
     * @param newContent
     * @throws DOMException
     */
    public void update(final Txn transaction, final NodeList newContent) throws DOMException {
        final NodePath path = getPath();
        // remove old child nodes
        final NodeList nodes = getChildNodes();
        //May help getReindexRoot() to make some useful things
        try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker()) {
            broker.getIndexController().setDocument(ownerDocument);
            final IStoredNode reindexRoot = broker.getIndexController().getReindexRoot(this, path, true, true);
            broker.getIndexController().setMode(StreamListener.REMOVE_SOME_NODES);
            final StreamListener listener;
            if(reindexRoot == null) {
                listener = broker.getIndexController().getStreamListener();
            } else {
                listener = null;
                broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.REMOVE_SOME_NODES);
            }
            // TODO: fix once range index has been moved to new architecture
            final IStoredNode valueReindexRoot = broker.getValueIndex().getReindexRoot(this, path);
            broker.getValueIndex().reindex(valueReindexRoot);
            IStoredNode last = this;
            int i = nodes.getLength();
            for(; i > 0; i--) {
                IStoredNode<?> child = (IStoredNode<?>) nodes.item(i - 1);
                if(child.getNodeType() == Node.ATTRIBUTE_NODE) {
                    last = child;
                    break;
                }
                if(child.getNodeType() == Node.ELEMENT_NODE) {
                    path.addComponent(child.getQName());
                }
                broker.removeAllNodes(transaction, child, path, listener);
                if(child.getNodeType() == Node.ELEMENT_NODE) {
                    path.removeLastComponent();
                }
            }
            broker.getIndexController().flush();
            broker.getIndexController().setMode(StreamListener.STORE);
            broker.getIndexController().getStreamListener();
            broker.endRemove(transaction);
            children = i;
            final NodeId newNodeId = last == this ? nodeId.newChild() : last.getNodeId().nextSibling();
            //Append new content
            appendChildren(transaction, newNodeId, null, new NodeImplRef(last), path, newContent, listener);
            broker.updateNode(transaction, this, false);
            broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            broker.getValueIndex().reindex(valueReindexRoot);
            broker.flush();
        } catch(final EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        }
    }

    /**
     * Update a child node. This method will only update the child node
     * but not its potential descendant nodes.
     *
     * @param oldChild
     * @param newChild
     * @throws DOMException
     */
    @Override
    public IStoredNode updateChild(final Txn transaction, final Node oldChild, final Node newChild) throws DOMException {
        if((!(oldChild instanceof IStoredNode)) || (!(newChild instanceof IStoredNode))) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "Wrong node type");
        }

        final IStoredNode<?> oldNode = (IStoredNode<?>) oldChild;
        final IStoredNode<?> newNode = (IStoredNode<?>) newChild;
        if(!oldNode.getNodeId().getParentId().equals(nodeId)) {
            throw new DOMException(DOMException.NOT_FOUND_ERR,
                "Node is not a child of this element");
        }

        if(newNode.getNodeType() == Node.ATTRIBUTE_NODE && newNode.getQName().equals(Namespaces.XML_ID_QNAME)) {
            // an xml:id attribute. Normalize the attribute and set its type to ID
            final AttrImpl attr = (AttrImpl) newNode;
            attr.setValue(StringValue.trimWhitespace(StringValue.collapseWhitespace(attr.getValue())));
            attr.setType(AttrImpl.ID);
        }
        IStoredNode<?> previousNode = (IStoredNode<?>) oldNode.getPreviousSibling();
        if(previousNode == null) {
            previousNode = this;
        } else {
            previousNode = getLastNode(previousNode);
        }
        final NodePath currentPath = getPath();
        final NodePath oldPath = oldNode.getPath(currentPath);

        try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker()) {
            //May help getReindexRoot() to make some useful things
            broker.getIndexController().setDocument(ownerDocument);
            //Check if the change affects any ancestor nodes, which then need to be reindexed later
            IStoredNode reindexRoot = broker.getIndexController().getReindexRoot(oldNode, oldPath, false);
            //Remove indexes
            if(reindexRoot == null) {
                reindexRoot = oldNode;
            }
            broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.REMOVE_SOME_NODES);
            //TODO: fix once range index has been moved to new architecture
            final IStoredNode valueReindexRoot = broker.getValueIndex().getReindexRoot(this, oldPath);
            broker.getValueIndex().reindex(valueReindexRoot);
            //Remove the actual node data
            broker.removeNode(transaction, oldNode, oldPath, null);
            broker.endRemove(transaction);
            newNode.setNodeId(oldNode.getNodeId());
            //Reinsert the new node data
            broker.insertNodeAfter(transaction, previousNode, newNode);
            final NodePath path = newNode.getPath(currentPath);
            broker.indexNode(transaction, newNode, path);
            if(newNode.getNodeType() == Node.ELEMENT_NODE) {
                broker.endElement(newNode, path, null);
            }
            broker.updateNode(transaction, this, true);
            //Recreate indexes on ancestor nodes
            broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            broker.getValueIndex().reindex(valueReindexRoot);
            broker.flush();
        } catch(final EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        }
        return newNode;
    }

    @Override
    public Node removeChild(final Txn transaction, final Node oldChild) throws DOMException {
        if(!(oldChild instanceof IStoredNode)) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        }

        final IStoredNode<?> oldNode = (IStoredNode<?>) oldChild;
        if(!oldNode.getNodeId().getParentId().equals(nodeId)) {
            throw new DOMException(DOMException.NOT_FOUND_ERR,
                "node is not a child of this element");
        }

        final NodePath oldPath = oldNode.getPath();
        try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker()) {
            //May help getReindexRoot() to make some useful things
            broker.getIndexController().setDocument(ownerDocument);
            final IStoredNode reindexRoot = broker.getIndexController().getReindexRoot(oldNode, oldPath, false);
            broker.getIndexController().setMode(StreamListener.REMOVE_SOME_NODES);
            final StreamListener listener;
            if(reindexRoot == null) {
                listener = broker.getIndexController().getStreamListener();
            } else {
                listener = null;
                broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.REMOVE_SOME_NODES);
            }
            broker.removeAllNodes(transaction, oldNode, oldPath, listener);
            --children;
            if(oldChild.getNodeType() == Node.ATTRIBUTE_NODE) {
                --attributes;
            }
            broker.endRemove(transaction);
            setDirty(true);
            broker.updateNode(transaction, this, false);
            broker.flush();
            if(reindexRoot != null && !reindexRoot.getNodeId().equals(oldNode.getNodeId())) {
                broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            }
        } catch(final EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        }
        return oldNode;
    }

    public void removeAppendAttributes(final Txn transaction, final NodeList removeList, final NodeList appendList) {

        try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker()) {
            if(removeList != null) {
                try {
                    for(int i = 0; i < removeList.getLength(); i++) {
                        final Node oldChild = removeList.item(i);
                        if(!(oldChild instanceof IStoredNode)) {
                            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "Wrong node type");
                        }
                        final IStoredNode<?> old = (IStoredNode<?>) oldChild;
                        if(!old.getNodeId().isChildOf(nodeId)) {
                            throw new DOMException(DOMException.NOT_FOUND_ERR, "node " +
                                old.getNodeId().getParentId() +
                                " is not a child of element " + nodeId);
                        }
                        final NodePath oldPath = old.getPath();
                        // remove old custom indexes
                        broker.getIndexController().reindex(transaction, old,
                            StreamListener.REMOVE_SOME_NODES);
                        broker.removeNode(transaction, old, oldPath, null);
                        children--;
                        attributes--;
                    }
                } finally {
                    broker.endRemove(transaction);
                }
            }
            final NodePath path = getPath();
            broker.getIndexController().setDocument(ownerDocument, StreamListener.STORE);
            IStoredNode reindexRoot = broker.getIndexController().getReindexRoot(this, path, true, true);
            final StreamListener listener = reindexRoot == null ? broker.getIndexController().getStreamListener() : null;
            if (children == 0) {
                appendChildren(transaction, nodeId.newChild(), null,
                    new NodeImplRef(this), path, appendList, listener);
            } else {
                if(attributes == 0) {
                    final IStoredNode<?> firstChild = (IStoredNode<?>) getFirstChild();
                    final NodeId newNodeId = firstChild.getNodeId().insertBefore();
                    appendChildren(transaction, newNodeId, firstChild.getNodeId(),
                        new NodeImplRef(this), path, appendList, listener);
                } else {
                    final AttribVisitor visitor = new AttribVisitor();
                    accept(visitor);
                    final NodeId firstChildId = visitor.firstChild == null ? null : visitor.firstChild.getNodeId();
                    final NodeId newNodeId = visitor.lastAttrib.getNodeId().insertNode(firstChildId);
                    appendChildren(transaction, newNodeId, firstChildId, new NodeImplRef(visitor.lastAttrib),
                        path, appendList, listener);
                }
                setDirty(true);
            }
            attributes += appendList.getLength();

            broker.updateNode(transaction, this, true);
            broker.flush();
            broker.getIndexController().reindex(transaction, reindexRoot,
                    StreamListener.STORE);
        } catch (final EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        }
    }

    private class AttribVisitor implements NodeVisitor {
        private IStoredNode lastAttrib = null;
        private IStoredNode firstChild = null;

        @Override
        public boolean visit(final IStoredNode node) {
            if(node.getNodeType() == Node.ATTRIBUTE_NODE) {
                lastAttrib = node;
            } else if(node.getNodeId().isChildOf(ElementImpl.this.nodeId)) {
                firstChild = node;
                return false;
            }
            return true;
        }
    }

    /**
     * Replaces the oldNode with the newChild
     *
     * @param transaction
     * @param newChild
     * @param oldChild
     * @return The new node (this differs from the {@link org.w3c.dom.Node#replaceChild(Node, Node)} specification)
     * @see org.w3c.dom.Node#replaceChild(org.w3c.dom.Node, org.w3c.dom.Node)
     */
    @Override
    public Node replaceChild(final Txn transaction, final Node newChild, final Node oldChild) throws DOMException {
        if(!(oldChild instanceof IStoredNode)) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "Wrong node type");
        }
        final IStoredNode<?> oldNode = (IStoredNode<?>) oldChild;
        if(!oldNode.getNodeId().getParentId().equals(nodeId)) {
            throw new DOMException(DOMException.NOT_FOUND_ERR,
                "Node is not a child of this element");
        }

        IStoredNode<?> previous = (IStoredNode<?>) oldNode.getPreviousSibling();
        if(previous == null) {
            previous = this;
        } else {
            previous = getLastNode(previous);
        }
        final NodePath oldPath = oldNode.getPath();
        StreamListener listener = null;
        //May help getReindexRoot() to make some useful things
        Node newNode = null;

        try(final DBBroker broker = ownerDocument.getBrokerPool().getBroker()) {
            broker.getIndexController().setDocument(ownerDocument);
            final IStoredNode reindexRoot = broker.getIndexController().getReindexRoot(oldNode, oldPath, false);
            broker.getIndexController().setMode(StreamListener.REMOVE_SOME_NODES);
            if(reindexRoot == null) {
                listener = broker.getIndexController().getStreamListener();
            } else {
                broker.getIndexController().reindex(transaction, reindexRoot,
                    StreamListener.REMOVE_SOME_NODES);
            }
            broker.removeAllNodes(transaction, oldNode, oldPath, listener);
            broker.endRemove(transaction);
            broker.flush();
            broker.getIndexController().setMode(StreamListener.STORE);
            listener = broker.getIndexController().getStreamListener();
            newNode = appendChild(transaction, oldNode.getNodeId(), new NodeImplRef(previous),
                getPath(), newChild, listener);
            //Reindex if required
            broker.storeXMLResource(transaction, getOwnerDocument());
            broker.updateNode(transaction, this, false);
            broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            broker.flush();
        } catch(final EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        }
        //return oldChild;	// method is spec'd to return the old child, even though that's probably useless in this case
        return newNode; //returning the newNode is more sensible than returning the oldNode
    }

    private String escapeXml(final Node child) {
        final String str = ((Attr) child).getValue();
        StringBuilder buffer = null;
        String entity;
        char ch;
        for(int i = 0; i < str.length(); i++) {
            ch = str.charAt(i);
            switch(ch) {
                case '"':
                    entity = "&quot;";
                    break;
                case '<':
                    entity = "&lt;";
                    break;
                case '>':
                    entity = "&gt;";
                    break;
                case '\'':
                    entity = "&apos;";
                    break;
                default:
                    entity = null;
                    break;
            }

            if(buffer == null) {
                if(entity != null) {
                    buffer = new StringBuilder(str.length() + 20);
                    buffer.append(str.substring(0, i));
                    buffer.append(entity);
                }
            } else {
                if(entity == null) {
                    buffer.append(ch);
                } else {
                    buffer.append(entity);
                }
            }
        }
        return buffer == null ? str : buffer.toString();
    }

    public void setPreserveSpace(final boolean preserveWS) {
        this.preserveWS = preserveWS;
    }

    public boolean preserveSpace() {
        return preserveWS;
    }

    @Override
    public TypeInfo getSchemaTypeInfo() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "getSchemaTypeInfo() not implemented on class " + getClass().getName());
    }

    @Override
    public void setIdAttribute(final String name, final boolean isId) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "setIdAttribute(String name, boolean isId) not implemented on class " + getClass().getName());
    }

    @Override
    public void setIdAttributeNS(final String namespaceURI, final String localName, final boolean isId) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "setIdAttributeNS(String namespaceURI, String localName," +
                " boolean isId) not implemented on class " + getClass().getName());
    }

    @Override
    public void setIdAttributeNode(final Attr idAttr, final boolean isId) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "setIdAttributeNode(Attr idAttr, boolean isId) not implemented on class " + getClass().getName());
    }

    @Override
    public String getBaseURI() {
        final XmldbURI baseURI = calculateBaseURI();
        if(baseURI != null) {
            return baseURI.toString();
        }

        return ""; //UNDERSTAND: is it ok?
    }

    //TODO Please, keep in sync with org.exist.dom.memtree.ElementImpl
    private XmldbURI calculateBaseURI() {
        XmldbURI baseURI = null;

        final String nodeBaseURI = _getAttributeNS(Namespaces.XML_NS, "base");
        if(nodeBaseURI != null) {
            baseURI = XmldbURI.create(nodeBaseURI, false);
            if(baseURI.isAbsolute()) {
                return baseURI;
            }
        }

        final IStoredNode parent = getParentStoredNode();
        if(parent != null) {
            if(nodeBaseURI == null) {
                baseURI = ((ElementImpl) parent).calculateBaseURI();
            } else {
                XmldbURI parentsBaseURI = ((ElementImpl) parent).calculateBaseURI();
                if(nodeBaseURI.isEmpty()) {
                    baseURI = parentsBaseURI;
                } else {
                    baseURI = parentsBaseURI.append(baseURI);
                }
            }
        } else {
            if(nodeBaseURI == null) {
                return XmldbURI.create(getOwnerDocument().getBaseURI(), false);
            } else {
                final String docBaseURI = getOwnerDocument().getBaseURI();
                if(docBaseURI.endsWith("/")) {
                    baseURI = XmldbURI.create(getOwnerDocument().getBaseURI(), false);
                    baseURI.append(baseURI);
                } else {
                    baseURI = XmldbURI.create(getOwnerDocument().getBaseURI(), false);
                    baseURI = baseURI.removeLastSegment();
                    baseURI.append(baseURI);
                }
            }
        }
        return baseURI;
    }

    @Override
    public boolean isSameNode(final Node other) {
        // This function is used by Saxon in some circumstances, and this partial implementation is required for proper Saxon operation.
        if(other instanceof IStoredNode) {
            return (this.nodeId == ((IStoredNode<?>) other).getNodeId() &&
                this.ownerDocument.getDocId() == ((IStoredNode<? extends IStoredNode>) other).getOwnerDocument().getDocId());
        }
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR,
            "isSameNode(Node other) not implemented on other class " + other.getClass().getName());
    }

    @Override
    public boolean accept(final INodeIterator iterator, final NodeVisitor visitor) {
        if(!visitor.visit(this)) {
            return false;
        }

        if(hasChildNodes()) {
            final int childCount = getChildCount();
            for(int i = 0; i < childCount; i++) {
                final IStoredNode next = iterator.next();
                if(!next.accept(iterator, visitor)) {
                    return false;
                }
            }
        }
        return true;
    }
}
