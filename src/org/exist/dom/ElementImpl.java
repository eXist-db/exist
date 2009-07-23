/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist team
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
package org.exist.dom;

import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.indexing.StreamListener;
import org.exist.numbering.NodeId;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.NodePath;
import org.exist.storage.RangeIndexSpec;
import org.exist.storage.Signatures;
import org.exist.storage.btree.Value;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.ByteArrayPool;
import org.exist.util.ByteConversion;
import org.exist.util.UTF8;
import org.exist.util.pool.NodePool;
import org.exist.xquery.Constants;
import org.exist.xquery.value.StringValue;

import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;

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
import org.w3c.dom.UserDataHandler;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

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
    private Map namespaceMappings = null;
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
    public ElementImpl(QName nodeName) {
        super(Node.ELEMENT_NODE, nodeName);
        this.nodeName = nodeName;
    }

    public ElementImpl(ElementImpl other) {
        super(other);
        this.children = other.children;
        this.attributes = other.attributes;
        this.namespaceMappings = other.namespaceMappings;
        this.indexType = other.indexType;
        this.position = other.position;
    }

    /**
     * Reset this element to its initial state.
     *
     */
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

    public void setIndexType(int idxType) {
        this.indexType = idxType;
    }

    public int getIndexType() {
        return indexType;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getPosition() {
        return position;
    }

    public boolean declaresNamespacePrefixes() {
        if (namespaceMappings == null)
            return false;
        return (namespaceMappings.size() > 0);
    }

    public byte[] serialize() {
    	 if (nodeId == null)
             throw new RuntimeException("nodeId = null for element: " +
                 getQName().getStringValue());
        try {
            final SymbolTable symbols = ownerDocument.getBrokerPool().getSymbols();
            byte[] prefixData = null;
            // serialize namespace prefixes declared in this element
            if (declaresNamespacePrefixes()) {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bout);
                out.writeShort(namespaceMappings.size());
                for (Iterator i = namespaceMappings.entrySet().iterator(); i.hasNext();) {
                    Map.Entry entry = (Map.Entry) i.next();
                    out.writeUTF((String) entry.getKey());
                    short nsId = symbols.getNSSymbol((String) entry.getValue());
                    out.writeShort(nsId);
                }
                prefixData = bout.toByteArray();
            }
            final short id = symbols.getSymbol(this);
            final boolean hasNamespace = nodeName.needsNamespaceDecl();
            short nsId = 0;
            if (hasNamespace)
                nsId =  symbols.getNSSymbol(nodeName.getNamespaceURI());
            final byte idSizeType = Signatures.getSizeType(id);
            byte signature = (byte) ((Signatures.Elem << 0x5) | idSizeType);
            int prefixLen = 0;
            if (hasNamespace) {
                if (nodeName.getPrefix() != null && nodeName.getPrefix().length() > 0)
                	prefixLen = UTF8.encoded(nodeName.getPrefix());
                signature |= 0x10;
            }
            if (isDirty)
                signature |= 0x8;
            final int nodeIdLen = nodeId.size();
            byte[] data =
                    ByteArrayPool.getByteArray(
                    		StoredNode.LENGTH_SIGNATURE_LENGTH + LENGTH_ELEMENT_CHILD_COUNT + NodeId.LENGTH_NODE_ID_UNITS + 
                    + nodeIdLen + LENGTH_ATTRIBUTES_COUNT
                    + Signatures.getLength(idSizeType)
                    + (hasNamespace ? prefixLen + 4 : 0)
                    + (prefixData != null ? prefixData.length : 0)
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
            if (hasNamespace) {
                ByteConversion.shortToByte(nsId, data, next);
                next += LENGTH_NS_ID;
                ByteConversion.shortToByte((short) prefixLen, data, next);
                next += LENGTH_PREFIX_LENGTH;
                if (nodeName.getPrefix() != null && nodeName.getPrefix().length() > 0)
                    UTF8.encode(nodeName.getPrefix(), data, next);
                next += prefixLen;
            }
            if (prefixData != null)
                System.arraycopy(prefixData, 0, data, next, prefixData.length);
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    public static StoredNode deserialize(byte[] data,
                                       int start,
                                       int len,
                                       DocumentImpl doc,
                                       boolean pooled) {
        int end = start + len;
        int pos = start;
        byte idSizeType = (byte) (data[pos] & 0x03);
        boolean isDirty = (data[pos] & 0x8) == 0x8;
        boolean hasNamespace = (data[pos] & 0x10) == 0x10;
        pos += StoredNode.LENGTH_SIGNATURE_LENGTH;
        int children = ByteConversion.byteToInt(data, pos);
        pos += LENGTH_ELEMENT_CHILD_COUNT;
        int dlnLen = ByteConversion.byteToShort(data, pos);
        pos += NodeId.LENGTH_NODE_ID_UNITS;
        NodeId dln = doc.getBrokerPool().getNodeFactory().createFromData(dlnLen, data, pos);
        pos += dln.size();
        short attributes = ByteConversion.byteToShort(data, pos);
        pos += LENGTH_ATTRIBUTES_COUNT;
        short id = (short) Signatures.read(idSizeType, data, pos);
        pos += Signatures.getLength(idSizeType);
        short nsId = 0;
        String prefix = null;
        if (hasNamespace) {
            nsId = ByteConversion.byteToShort(data, pos);
            pos += LENGTH_NS_ID;
            int prefixLen = ByteConversion.byteToShort(data, pos);
            pos += LENGTH_PREFIX_LENGTH;
            if (prefixLen > 0)
                prefix = UTF8.decode(data, pos, prefixLen).toString();
            pos += prefixLen;
        }
        String name = doc.getBrokerPool().getSymbols().getName(id);
        String namespace = "";
        if (nsId != 0)
            namespace = doc.getBrokerPool().getSymbols().getNamespace(nsId);
        
        ElementImpl node;
        if (pooled)
            node = (ElementImpl) NodePool.getInstance().borrowNode(Node.ELEMENT_NODE);
//            node = (ElementImpl) NodeObjectPool.getInstance().borrowNode(ElementImpl.class);
        else
            node = new ElementImpl();
        node.setNodeId(dln);
        node.nodeName = doc.getBrokerPool().getSymbols().getQName(Node.ELEMENT_NODE, namespace, name, prefix);
        node.children = children;
        node.attributes = attributes;
        node.isDirty = isDirty;
        node.setOwnerDocument(doc);
        //TO UNDERSTAND : why is this code here ?  
        if (end > pos) {
            byte[] pfxData = new byte[end - pos];
            System.arraycopy(data, pos, pfxData, 0, end - pos);
            ByteArrayInputStream bin = new ByteArrayInputStream(pfxData);
            DataInputStream in = new DataInputStream(bin);
            try {
                short prefixCount = in.readShort();
                for (int i = 0; i < prefixCount; i++) {
                    prefix = in.readUTF();
                    nsId = in.readShort();                    
                    node.addNamespaceMapping(prefix, doc.getBrokerPool().getSymbols().getNamespace(nsId));
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        return node;
    }

    public static QName readQName(Value value, DocumentImpl document, NodeId nodeId) {
        final byte[] data = value.data();
        int offset = value.start();
        byte idSizeType = (byte) (data[offset] & 0x03);
        boolean hasNamespace = (data[offset] & 0x10) == 0x10;
        offset += StoredNode.LENGTH_SIGNATURE_LENGTH;
        offset += LENGTH_ELEMENT_CHILD_COUNT;
        offset += NodeId.LENGTH_NODE_ID_UNITS;
        offset += nodeId.size();
        offset += LENGTH_ATTRIBUTES_COUNT;
        short id = (short) Signatures.read(idSizeType, data, offset);
        offset += Signatures.getLength(idSizeType);
        short nsId = 0;
        String prefix = null;
        if (hasNamespace) {
            nsId = ByteConversion.byteToShort(data, offset);
            offset += LENGTH_NS_ID;
            int prefixLen = ByteConversion.byteToShort(data, offset);
            offset += LENGTH_PREFIX_LENGTH;
            if (prefixLen > 0)
                prefix = UTF8.decode(data, offset, prefixLen).toString();
            offset += prefixLen;
        }
        String name = document.getBrokerPool().getSymbols().getName(id);
        String namespace = "";
        if (nsId != 0)
            namespace = document.getBrokerPool().getSymbols().getNamespace(nsId);
        return new QName(name, namespace, prefix == null ? "" : prefix);
    }

    public static void readNamespaceDecls(List namespaces, Value value, DocumentImpl document, NodeId nodeId) {
        final byte[] data = value.data();
        int offset = value.start();
        int end = offset + value.getLength();
        byte idSizeType = (byte) (data[offset] & 0x03);
        boolean hasNamespace = (data[offset] & 0x10) == 0x10;
        offset += StoredNode.LENGTH_SIGNATURE_LENGTH;
        offset += LENGTH_ELEMENT_CHILD_COUNT;
        offset += NodeId.LENGTH_NODE_ID_UNITS;
        offset += nodeId.size();
        offset += LENGTH_ATTRIBUTES_COUNT;
        offset += Signatures.getLength(idSizeType);
        if (hasNamespace) {
            offset += LENGTH_NS_ID;
            int prefixLen = ByteConversion.byteToShort(data, offset);
            offset += LENGTH_PREFIX_LENGTH;
            offset += prefixLen;
        }
        if (end > offset) {
            byte[] pfxData = new byte[end - offset];
            System.arraycopy(data, offset, pfxData, 0, end - offset);
            ByteArrayInputStream bin = new ByteArrayInputStream(pfxData);
            DataInputStream in = new DataInputStream(bin);
            try {
                short prefixCount = in.readShort();
                String prefix;
                short nsId;
                for (int i = 0; i < prefixCount; i++) {
                    prefix = in.readUTF();
                    nsId = in.readShort();
                    namespaces.add(new String[] { prefix, document.getBrokerPool().getSymbols().getNamespace(nsId) });
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void addNamespaceMapping(String prefix, String ns) {
        if (prefix == null)
            return;
        if (namespaceMappings == null)
            namespaceMappings = new HashMap(1);
        else if (namespaceMappings.containsKey(prefix))
            return;
        namespaceMappings.put(prefix, ns);
    }

    /**
     * Append a child to this node. This method does not rearrange the
     * node tree and is only used internally by the parser.
     *
     * @param child
     * @throws DOMException
     */
    public void appendChildInternal(StoredNode prevNode, StoredNode child) throws DOMException {
    	NodeId childId;
    	if (prevNode == null) {
    		childId = getNodeId().newChild();
        } else {
    		if (prevNode.getNodeId() == null) {
    			LOG.warn(getQName() + " : " + prevNode.getNodeName());
    		}
    		childId = prevNode.getNodeId().nextSibling();
    	}
    	child.setNodeId(childId);
    	++children;
    }

    /**
     * @see org.w3c.dom.Node#appendChild(org.w3c.dom.Node)
     */
    public Node appendChild(Node child) throws DOMException {
        TransactionManager transact = ownerDocument.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        NodeListImpl nl = new NodeListImpl();
        nl.add(child);
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            appendChildren(transaction, nl, 0);
            broker.storeXMLResource(transaction, (DocumentImpl) getOwnerDocument());
            return getLastChild();
        } catch (Exception e) {
            transact.abort(transaction);
            throw new DOMException(DOMException.INVALID_STATE_ERR, e.getMessage());
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
    }

    public void appendAttributes(Txn transaction, NodeList attribs) throws DOMException {
    	NodeList duplicateAttrs = findDupAttributes(attribs);
    	removeAppendAttributes(transaction, duplicateAttrs, attribs);
    }

    private NodeList checkForAttributes(Txn transaction, NodeList nodes) throws DOMException {
        NodeListImpl attribs = null;
        NodeListImpl rest = null;
        for (int i = 0; i < nodes.getLength(); i++) {
            Node next = nodes.item(i);
            if (next.getNodeType() == Node.ATTRIBUTE_NODE) {
                if (!next.getNodeName().startsWith("xmlns")) {
                    if (attribs == null)
                        attribs = new NodeListImpl();
                    attribs.add(next);
                }
            } else if (attribs != null) {
                if (rest == null) rest = new NodeListImpl();
                rest.add(next);
            }
        }
        if (attribs == null)
            return nodes;
        appendAttributes(transaction, attribs);
        return rest;
    }

    public void appendChildren(Txn transaction, NodeList nodes, int child) throws DOMException {
        // attributes are handled differently. Call checkForAttributes to extract them.
        nodes = checkForAttributes(transaction, nodes);
        if (nodes == null || nodes.getLength() == 0)
            return;
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            NodePath path = getPath();
            StreamListener listener = null;
            //May help getReindexRoot() to make some useful things
            broker.getIndexController().setDocument(ownerDocument);
            StoredNode reindexRoot = broker.getIndexController().getReindexRoot(this, path);
            broker.getIndexController().setMode(StreamListener.STORE);
            if (reindexRoot == null) {
                listener = broker.getIndexController().getStreamListener();
            } else {
                broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            }

            if (children == 0) {
                // no children: append a new child
                appendChildren(transaction, nodeId.newChild(), null, new NodeImplRef(this), path, nodes, listener);
            } else {
                if (child == 1) {
                    Node firstChild = getFirstChild();
                    insertBefore(transaction, nodes, firstChild);
                }
                else {
                    if (child > 1 && child <= children) {
                        NodeList cl = getChildNodes();
                        StoredNode last = (StoredNode) cl.item(child - 2);
                        insertAfter(transaction, nodes, last);
                    } else {
                        StoredNode last = (StoredNode) getLastChild();
                        appendChildren(transaction, last.getNodeId().nextSibling(), null,
                                new NodeImplRef(getLastNode(last)), path, nodes, listener);
                    }
                }
            }
            broker.updateNode(transaction, this, false);
            broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            broker.flush();
        } catch (EXistException e) {
            LOG.warn("Exception while appending child node: " + e.getMessage(), e);
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
    }

    /**
     * Internal append.
     *    
     * @throws DOMException
     */
    protected void appendChildren(Txn transaction, NodeId newNodeId, NodeId followingId, NodeImplRef last, NodePath lastPath, NodeList nodes, StreamListener listener) throws DOMException {
        if (last == null || last.getNode() == null || last.getNode().getOwnerDocument() == null)
            throw new DOMException(DOMException.INVALID_MODIFICATION_ERR, "invalid node");
        children += nodes.getLength();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node child = nodes.item(i);
            appendChild(transaction, newNodeId, last, lastPath, child, listener);
            NodeId next = newNodeId.nextSibling();
            if (followingId != null && next.equals(followingId)) {
                next = newNodeId.insertNode(followingId);
                if (LOG.isDebugEnabled())
                    LOG.debug("Node ID collision on " + followingId + ". Using " + next + " instead.");
            }
            newNodeId = next;
        }
    }

    private Node appendChild(Txn transaction, NodeId newNodeId, NodeImplRef last, NodePath lastPath, Node child, StreamListener listener)
            throws DOMException {
        if (last == null || last.getNode() == null)
            //TODO : same test as above ? -pb
            throw new DOMException(DOMException.INVALID_MODIFICATION_ERR, "invalid node");
        final DocumentImpl owner = (DocumentImpl)getOwnerDocument();
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            switch (child.getNodeType()) {
                case Node.DOCUMENT_FRAGMENT_NODE :
                    appendChildren(transaction, newNodeId, null, last, lastPath, child.getChildNodes(), listener);
                    return null;    // TODO: implement document fragments so we can return all newly appended children
                case Node.ELEMENT_NODE :
                    // create new element
                    final ElementImpl elem =
                        new ElementImpl(
                            new QName(child.getLocalName() == null ? child.getNodeName() : child.getLocalName(),
                            child.getNamespaceURI(),
                            child.getPrefix())
                        );
                    elem.setNodeId(newNodeId);
                    elem.setOwnerDocument(owner);
                    final NodeListImpl ch = new NodeListImpl();
                    final NamedNodeMap attribs = child.getAttributes();
                    int numActualAttribs = 0;
                    for (int i = 0; i < attribs.getLength(); i++) {
                        Attr attr = (Attr) attribs.item(i);
                        if (!attr.getNodeName().startsWith("xmlns")) {
                            ch.add(attr);
                            numActualAttribs++;
                        } else {
                            String xmlnsDecl = attr.getNodeName();
                            String prefix = xmlnsDecl.length()==5 ? "" : xmlnsDecl.substring(6);
                            elem.addNamespaceMapping(prefix,attr.getNodeValue());
                        }
                    }
                    NodeList cl = child.getChildNodes();
                    for (int i = 0; i < cl.getLength(); i++) {
                        Node n = cl.item(i);
                        if (n.getNodeType() != Node.ATTRIBUTE_NODE)
                            ch.add(n);
                    }
                    elem.setChildCount(ch.getLength());
                    if (numActualAttribs != (short) numActualAttribs)
                        throw new DOMException(DOMException.INVALID_MODIFICATION_ERR, "too many attributes");
                    elem.setAttributes((short) numActualAttribs);
                    lastPath.addComponent(elem.getQName());
                    // insert the node
                    broker.insertNodeAfter(transaction, last.getNode(), elem);
                
                    broker.indexNode(transaction, elem, lastPath);
                    broker.getIndexController().indexNode(transaction, elem, lastPath, listener);
                    //getBroker().getIndexController().startElement(transaction, elem, lastPath, listener);

                    elem.setChildCount(0);
                    last.setNode(elem);
                    //process child nodes
                    elem.appendChildren(transaction, newNodeId.newChild(), null, last, lastPath, ch, listener);

                    broker.endElement(elem, lastPath, null);
                    broker.getIndexController().endElement(transaction, elem, lastPath, listener);
                    lastPath.removeLastComponent();
                    return elem;
                case Node.TEXT_NODE :
                    final TextImpl text = new TextImpl(newNodeId, ((Text) child).getData());
                    text.setOwnerDocument(owner);
                    // insert the node
                    broker.insertNodeAfter(transaction, last.getNode(), text);
                    broker.indexNode(transaction, text, lastPath);
                    broker.getIndexController().indexNode(transaction, text, lastPath, listener);
                    //getBroker().getIndexController().characters(transaction, text, lastPath, listener);
                    last.setNode(text);
                    return text;
                case Node.CDATA_SECTION_NODE :
                    final CDATASectionImpl cdata = new CDATASectionImpl(newNodeId, ((CDATASection) child).getData());
                    cdata.setOwnerDocument(owner);
                    // insert the node
                    broker.insertNodeAfter(transaction, last.getNode(), cdata);
                    broker.indexNode(transaction, cdata, lastPath);
                    last.setNode(cdata);
                    return cdata;
                case Node.ATTRIBUTE_NODE:
                    Attr attr = (Attr) child;
                    String ns = attr.getNamespaceURI();
                    String prefix = (Namespaces.XML_NS.equals(ns) ? "xml" : attr.getPrefix());
                    String name = attr.getLocalName();
                    if (name == null) name = attr.getName();
                    QName attrName = new QName(name, ns, prefix);
                    final AttrImpl attrib = new AttrImpl(attrName, attr.getValue());
                    attrib.setNodeId(newNodeId);
                    attrib.setOwnerDocument(owner);
                    if (ns != null && attrName.compareTo(Namespaces.XML_ID_QNAME) == Constants.EQUAL) {
                        // an xml:id attribute. Normalize the attribute and set its type to ID
                        attrib.setValue(StringValue.trimWhitespace(StringValue.collapseWhitespace(attrib.getValue())));
                        attrib.setType(AttrImpl.ID);
                    } else
                        attrName.setNameType(ElementValue.ATTRIBUTE);
                    broker.insertNodeAfter(transaction, last.getNode(), attrib);
                
                    broker.indexNode(transaction, attrib, lastPath);
                    broker.getIndexController().indexNode(transaction, attrib, lastPath, listener);
                    //getBroker().getIndexController().attribute(transaction, attrib, lastPath, listener);

                    last.setNode(attrib);
                    return attrib;
                case Node.COMMENT_NODE:
                    final CommentImpl comment = new CommentImpl(((Comment) child).getData());
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
                                ((ProcessingInstruction) child).getTarget(),
                                ((ProcessingInstruction) child).getData());
                    pi.setOwnerDocument(owner);
                    //          insert the node
                    broker.insertNodeAfter(transaction, last.getNode(), pi);
                    broker.indexNode(transaction, pi, lastPath);
                    last.setNode(pi);
                    return pi;
                default :
                    throw new DOMException(DOMException.INVALID_MODIFICATION_ERR,
                        "unknown node type: "
                        + child.getNodeType()
                        + " "
                        + child.getNodeName());
            }
        } catch (EXistException e) {
            LOG.warn("Exception while appending node: " + e.getMessage(), e);
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
        return null;
    }

    public short getAttributesCount() {
        return attributes;
    }

    /**
     * Set the attributes that belong to this node.
     *
     * @param attribNum The new attributes value
     */
    public void setAttributes(short attribNum) {
        attributes = attribNum;
    }

    /**
     * @see org.w3c.dom.Element#getAttribute(java.lang.String)
     */
    public String getAttribute(String name) {
        Attr attr = findAttribute(name);
        return attr != null ? attr.getValue() : "";
    }

    /**
     * @see org.w3c.dom.Element#getAttributeNS(java.lang.String, java.lang.String)
     */
    public String getAttributeNS(String namespaceURI, String localName) {
    	Attr attr = findAttribute(new QName(localName, namespaceURI));
    	return attr != null ? attr.getValue() : "";
    }

    /**
     * @see org.w3c.dom.Element#getAttributeNode(java.lang.String)
     */
    public Attr getAttributeNode(String name) {
    	return findAttribute(name);
    }
    
    /**
     * @see org.w3c.dom.Element#getAttributeNodeNS(java.lang.String, java.lang.String)
     */
    public Attr getAttributeNodeNS(String namespaceURI, String localName) {
    	return findAttribute(new QName(localName, namespaceURI));
    }

    /**
     * @see org.w3c.dom.Node#getAttributes()
     */
    public NamedNodeMap getAttributes() {
        NamedNodeMapImpl map = new NamedNodeMapImpl();
        if (getAttributesCount() > 0) {
            DBBroker broker = null;
            try {
                broker = ownerDocument.getBrokerPool().get(null);
                final Iterator iterator = broker.getNodeIterator(this);
                iterator.next();
                final int ccount = getChildCount();
                for (int i = 0; i < ccount; i++) {
                    StoredNode next = (StoredNode) iterator.next();
                    if (next.getNodeType() != Node.ATTRIBUTE_NODE)
                        break;
                    map.setNamedItem(next);
                }
            } catch (EXistException e) {
                LOG.warn("Exception while retrieving attributes: " + e.getMessage());
            } finally {
                ownerDocument.getBrokerPool().release(broker);
            }
        }
        if(declaresNamespacePrefixes()) {
            for(Iterator i = namespaceMappings.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry entry = (Map.Entry) i.next();
                String prefix = entry.getKey().toString();
                String ns = entry.getValue().toString();
                QName attrName = new QName(prefix, Namespaces.XMLNS_NS, "xmlns");
                AttrImpl attr = new AttrImpl(attrName, ns);
                map.setNamedItem(attr);
            }
        }
        return map;
    }
    
    private AttrImpl findAttribute(String qname) {
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            final Iterator iterator = broker.getNodeIterator(this);
            iterator.next();
            return findAttribute(qname, iterator, this);
        } catch (EXistException e) {
            LOG.warn("Exception while retrieving attributes: " + e.getMessage());
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
        return null;
    }

    private AttrImpl findAttribute(String qname, Iterator iterator, StoredNode current) {
    	final int ccount = current.getChildCount();
        StoredNode next;
        for (int i = 0; i < ccount; i++) {
            next = (StoredNode) iterator.next();
            if (next.getNodeType() != Node.ATTRIBUTE_NODE)
            	break;
            if (next.getNodeName().equals(qname))
            	return (AttrImpl) next;
        }
        return null;
    }
    
    private AttrImpl findAttribute(QName qname) {
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            final Iterator iterator = broker.getNodeIterator(this);
            iterator.next();
            return findAttribute(qname, iterator, this);
        } catch (EXistException e) {
            LOG.warn("Exception while retrieving attributes: " + e.getMessage());
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
        return null;
    }

    private AttrImpl findAttribute(QName qname, Iterator iterator, StoredNode current) {
    	final int ccount = current.getChildCount();        
        for (int i = 0; i < ccount; i++) {
        	StoredNode next = (StoredNode) iterator.next();
            if (next.getNodeType() != Node.ATTRIBUTE_NODE)
            	break;
    		if (next.getQName().equalsSimple(qname))
    			return (AttrImpl) next;     
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
    private NodeList findDupAttributes(NodeList attrs) throws DOMException {
    	NodeListImpl dupList = null;
    	NamedNodeMap map = getAttributes();
    	for (int i = 0; i < attrs.getLength(); i++) {
    		Node attr = attrs.item(i);
            // Workaround: xerces sometimes returns null for getLocalName() !!!!
            String localName = attr.getLocalName();
            if (localName == null)
                localName = attr.getNodeName();
            Node duplicate = map.getNamedItemNS(attr.getNamespaceURI(), localName);
    		if (duplicate != null) {
    			if (dupList == null)
            		dupList = new NodeListImpl();
            	dupList.add(duplicate);
    		}
    	}
        return dupList;
    }

    /**
     * @see org.exist.dom.NodeImpl#getChildCount()
     */
    public int getChildCount() {
        return children;
    }

    public NodeList getChildNodes() {
        final NodeListImpl childList = new NodeListImpl(1);
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            for (EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(this, true); reader.hasNext(); ) {
                int status = reader.next();
                if (status != XMLStreamReader.END_ELEMENT) {
                    if (((NodeId) reader.getProperty(EmbeddedXMLStreamReader.PROPERTY_NODE_ID)).isChildOf(nodeId))
                        childList.add(reader.getNode());
                }
            }
        } catch (IOException e) {
            LOG.warn("Internal error while reading child nodes: " + e.getMessage(), e);
        } catch (XMLStreamException e) {
            LOG.warn("Internal error while reading child nodes: " + e.getMessage(), e);
        } catch (EXistException e) {
            LOG.warn("Internal error while reading child nodes: " + e.getMessage(), e);
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
//        accept(new NodeVisitor() {
//            public boolean visit(StoredNode node) {
//                if(node.nodeId.isChildOf(nodeId))
//                    childList.add(node);
//                return true;
//            }
//        });
        return childList;
    }
    
    /**
     * @see org.w3c.dom.Element#getElementsByTagName(java.lang.String)
     */
    public NodeList getElementsByTagName(String tagName) {
        QName qname = new QName(tagName, "", null);
        return ((DocumentImpl)getOwnerDocument()).findElementsByTagName(this, qname);
    }

    /**
     * @see org.w3c.dom.Element#getElementsByTagNameNS(java.lang.String, java.lang.String)
     */
    public NodeList getElementsByTagNameNS(String namespaceURI, String localName) {
        QName qname = new QName(localName, namespaceURI, null);
        return ((DocumentImpl)getOwnerDocument()).findElementsByTagName(this, qname);
    }

    /**
     * @see org.w3c.dom.Node#getFirstChild()
     */
    public Node getFirstChild() {
        if (!hasChildNodes() || getChildCount() == getAttributesCount())
            return null;
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            final Iterator iterator = broker.getNodeIterator(this);
            iterator.next();
            StoredNode next;
            for (int i = 0; i < getChildCount(); i++) {
                next = (StoredNode) iterator.next();
                if (next.getNodeType() != Node.ATTRIBUTE_NODE)
                    return next;
            }
        } catch (EXistException e) {
            LOG.warn("Exception while retrieving child node: " + e.getMessage(), e);
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
        return null;
    }
    
    public Node getLastChild() {
        if (!hasChildNodes())
            return null;
        Node node = null;
        if (!isDirty) {
            NodeId child = nodeId.getChild(children);
            node = ownerDocument.getNode(new NodeProxy(ownerDocument, child));
        }
        if (node == null) {
            NodeList cl = getChildNodes();
            return cl.item(cl.getLength() - 1);
        }
        return node;
    }

    /**
     * @see org.w3c.dom.Element#getTagName()
     */
    public String getTagName() {
        return nodeName.getStringValue();
    }

    /**
     * @see org.w3c.dom.Element#hasAttribute(java.lang.String)
     */
    public boolean hasAttribute(String name) {
    	return findAttribute(name) != null;
    }

    /**
     * @see org.w3c.dom.Element#hasAttributeNS(java.lang.String, java.lang.String)
     */
    public boolean hasAttributeNS(String namespaceURI, String localName) {
    	return findAttribute(new QName(localName, namespaceURI)) != null;
    }

    /**
     * @see org.w3c.dom.Node#hasAttributes()
     */
    public boolean hasAttributes() {
        return (getAttributesCount() > 0);
    }

    /**
     * @see org.w3c.dom.Node#hasChildNodes()
     */
    public boolean hasChildNodes() {
        return children > 0;
    }

    /**
     * @see org.w3c.dom.Node#getNodeValue()
     */
    public String getNodeValue() /*throws DOMException*/ {
    	//TODO : parametrize the boolea value ?
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            return broker.getNodeValue(this, false);
        } catch (EXistException e) {
            LOG.warn("Exception while reading node value: " + e.getMessage(), e);
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
        return "";
    }

    /**
     * @see org.w3c.dom.Element#removeAttribute(java.lang.String)
     */
    public void removeAttribute(String name) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "removeAttribute(String name) not implemented on class " + getClass().getName());
    }

    /**
     * @see org.w3c.dom.Element#removeAttributeNS(java.lang.String, java.lang.String)
     */
    public void removeAttributeNS(String namespaceURI, String name) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "removeAttributeNS(String namespaceURI, String name) not implemented on class " + getClass().getName());
    }

    public Attr removeAttributeNode(Attr oldAttr) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "removeAttributeNode(Attr oldAttr) not implemented on class " + getClass().getName());
    }

    public void setAttribute(String name, String value) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setAttribute(String name, String value) not implemented on class " + getClass().getName());
    }

    public void setAttributeNS(String namespaceURI, String qualifiedName, String value) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setAttributeNS(String namespaceURI, String qualifiedName, String value) not implemented on class " + getClass().getName());
    }

    public Attr setAttributeNode(Attr newAttr) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setAttributeNode(Attr newAttr) not implemented on class " + getClass().getName());
    }

    public Attr setAttributeNodeNS(Attr newAttr) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setAttributeNodeNS(Attr newAttr) not implemented on class " + getClass().getName());
    }

    public void setChildCount(int count) {
        children = count;
    }

    public void setNamespaceMappings(Map map) {
        namespaceMappings = new HashMap(map);
        String ns;
        for (Iterator i = namespaceMappings.values().iterator(); i.hasNext();) {
            ns = (String) i.next();
            ownerDocument.getBrokerPool().getSymbols().getNSSymbol(ns);
        }
    }

    public Iterator getPrefixes() {
        return namespaceMappings.keySet().iterator();
    }

    public String getNamespaceForPrefix(String prefix) {
        return (String) namespaceMappings.get(prefix);
    }

    public int getPrefixCount() {
        return namespaceMappings.size();
    }

    /**
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return toString(true);
    }

    /**
     */
    public String toString(boolean top) {
        return toString(top, new TreeSet());
    }

    /**
     * Method toString.
     *
     */
    public String toString(boolean top, TreeSet namespaces) {
        StringBuilder buf = new StringBuilder();
        StringBuilder attributes = new StringBuilder();
        StringBuilder children = new StringBuilder();
        buf.append('<');
        buf.append(nodeName);
        //Remove false to have a verbose output
        if (top && false) {
            buf.append(" xmlns:exist=\""+ Namespaces.EXIST_NS + "\"");
            buf.append(" exist:id=\"");
            buf.append(getNodeId());
            buf.append("\" exist:document=\"");
            buf.append(((DocumentImpl)getOwnerDocument()).getFileURI());
            buf.append("\"");
        }
        if (declaresNamespacePrefixes()) {
            // declare namespaces used by this element
            Map.Entry entry;
            String namespace, prefix;
            for (Iterator i = namespaceMappings.entrySet().iterator(); i.hasNext();) {
                entry = (Map.Entry) i.next();
                prefix = (String) entry.getKey();
                namespace = (String) entry.getValue();
                if (prefix.length() == 0) {
                    buf.append(" xmlns=\"");
                    //buf.append(namespace);
                    buf.append("...");
                }
                else {
                    buf.append(" xmlns:");
                    buf.append(prefix);
                    buf.append("=\"");
                    //buf.append(namespace);
                    buf.append("...");
                }
                buf.append("\" ");
                namespaces.add(namespace);
            }
        }
        if (nodeName.getNamespaceURI().length() > 0
                && (!namespaces.contains(nodeName.getNamespaceURI()))) {
            buf.append(" xmlns:").append(nodeName.getPrefix()).append("=\"");
            buf.append(nodeName.getNamespaceURI());
            buf.append("\" ");
        }
        NodeList childNodes = getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
        	Node child = childNodes.item(i);
            switch (child.getNodeType()) {
                case Node.ATTRIBUTE_NODE:
                    attributes.append(' ');
                    attributes.append(((Attr) child).getName());
                    attributes.append("=\"");
                    attributes.append(escapeXml(child));
                    attributes.append("\"");
                    break;
                case Node.ELEMENT_NODE:
                    children.append(((ElementImpl) child).toString(false, namespaces));
                    break;
                default :
                    children.append(child.toString());
            }
        }
        
        if (attributes.length() > 0)
            buf.append(attributes.toString());

        if (childNodes.getLength() > 0) {
            buf.append(">");
            buf.append(children.toString());
            buf.append("</");
            buf.append(nodeName);
            buf.append(">");
        }
        else
            buf.append("/>");

        return buf.toString();
    }

    /**
     * @see org.w3c.dom.Node#insertBefore(org.w3c.dom.Node, org.w3c.dom.Node)
     */
    public Node insertBefore(Node newChild, Node refChild) throws DOMException {
        if (refChild == null)
            return appendChild(newChild);
        if (!(refChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        NodeListImpl nl = new NodeListImpl();
        nl.add(newChild);
        
        TransactionManager transact = ownerDocument.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            insertBefore(transaction, nl, refChild);
            broker.storeXMLResource(transaction, (DocumentImpl) getOwnerDocument());
            transact.commit(transaction);
            return refChild.getPreviousSibling();
        } catch(TransactionException e) {
            transact.abort(transaction);
            throw new DOMException(DOMException.NO_MODIFICATION_ALLOWED_ERR, e.getMessage());
        } catch (EXistException e) {
            transact.abort(transaction);
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
        return null;
    }

    /**
     * Insert a list of nodes at the position before the reference
     * child.
     */
    public void insertBefore(Txn transaction, NodeList nodes, Node refChild) throws DOMException {
        if (refChild == null) {
            //TODO : use NodeImpl.UNKNOWN_NODE_IMPL_GID ? -pb
            appendChildren(transaction, nodes, -1);
            return;
        }
        if (!(refChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            NodePath path = getPath();
            StreamListener listener = null;
            //May help getReindexRoot() to make some useful things
            broker.getIndexController().setDocument(ownerDocument);
            StoredNode reindexRoot = broker.getIndexController().getReindexRoot(this, path, true);
            broker.getIndexController().setMode(StreamListener.STORE);
            if (reindexRoot == null) {
                listener = broker.getIndexController().getStreamListener();
            } else {
                broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            }
            StoredNode following = (StoredNode) refChild;
            StoredNode previous = (StoredNode) following.getPreviousSibling();
            if (previous == null) {
                // there's no sibling node before the new node
                NodeId newId = following.getNodeId().insertBefore();
                appendChildren(transaction, newId, following.getNodeId(), new NodeImplRef(this), path, nodes, listener);
            } else {
                // insert the new node between the preceding and following sibling
                NodeId newId = previous.getNodeId().insertNode(following.getNodeId());
                appendChildren(transaction, newId, following.getNodeId(), new NodeImplRef(getLastNode(previous)),
                        path, nodes, listener);
            }
            setDirty(true);
            broker.updateNode(transaction, this, true);
            broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            broker.flush();
        } catch (EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
    }

    /**
     * Insert a list of nodes at the position following the reference
     * child.
     */
    public void insertAfter(Txn transaction, NodeList nodes, Node refChild) throws DOMException {
        if (refChild == null) {
            //TODO : use NodeImpl.UNKNOWN_NODE_IMPL_GID ? -pb
            appendChildren(null, nodes, -1);
            return;
        }
        if (!(refChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type: ");
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            NodePath path = getPath();
            StreamListener listener = null;
            //May help getReindexRoot() to make some useful things
            broker.getIndexController().setDocument(ownerDocument);
            StoredNode reindexRoot = broker.getIndexController().getReindexRoot(this, path, true);
            broker.getIndexController().setMode(StreamListener.STORE);
            if (reindexRoot == null) {
                listener = broker.getIndexController().getStreamListener();
            } else {
                broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            }
            StoredNode previous = (StoredNode) refChild;
            StoredNode following = (StoredNode) previous.getNextSibling();
            NodeId followingId = following == null ? null : following.getNodeId();
            NodeId newNodeId = previous.getNodeId().insertNode(followingId);
            appendChildren(transaction, newNodeId, followingId, new NodeImplRef(getLastNode(previous)), path, nodes, listener);
            setDirty(true);
            broker.updateNode(transaction, this, true);
            broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            broker.flush();
        } catch (EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
    }

    /**
     * Update the contents of this element. The passed list of nodes
     * becomes the new content.
     *
     * @param newContent
     * @throws DOMException
     */
    public void update(Txn transaction, NodeList newContent) throws DOMException {
        final NodePath path = getPath();        
        // remove old child nodes
        NodeList nodes = getChildNodes();

        StreamListener listener = null;
        DBBroker broker = null;
        //May help getReindexRoot() to make some useful things
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            broker.getIndexController().setDocument(ownerDocument);
            StoredNode reindexRoot = broker.getIndexController().getReindexRoot(this, path, true);
            broker.getIndexController().setMode(StreamListener.REMOVE_SOME_NODES);
            if (reindexRoot == null) {
                listener = broker.getIndexController().getStreamListener();
            } else {
                broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.REMOVE_SOME_NODES);
            }
            // TODO: fix once range index has been moved to new architecture
            StoredNode valueReindexRoot = broker.getValueIndex().getReindexRoot(this, path);
            broker.getValueIndex().reindex(valueReindexRoot);
        
            StoredNode last = this;
            int i = nodes.getLength();
            for (; i > 0; i--) {
                StoredNode child = (StoredNode) nodes.item(i - 1);
                if (child.getNodeType() == Node.ATTRIBUTE_NODE) {
                    last = child;
                    break;
                }
                if (child.getNodeType() == Node.ELEMENT_NODE)
                    path.addComponent(child.getQName());

                broker.removeAllNodes(transaction, child, path, listener);
                if (child.getNodeType() == Node.ELEMENT_NODE)
                    path.removeLastComponent();
            }
            broker.getIndexController().flush();
            broker.getIndexController().setMode(StreamListener.STORE);
            broker.getIndexController().getStreamListener();
            broker.endRemove(transaction);
            children = i;
            NodeId newNodeId = last == this ? nodeId.newChild() : last.nodeId.nextSibling();
            // append new content
            appendChildren(transaction, newNodeId, null, new NodeImplRef(last), path, newContent, listener);
            broker.updateNode(transaction, this, false);
            broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            broker.getValueIndex().reindex(valueReindexRoot);
            broker.flush();
        } catch (EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        } finally {
            ownerDocument.getBrokerPool().release(broker);
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
    public StoredNode updateChild(Txn transaction, Node oldChild, Node newChild) throws DOMException {
        if (!(oldChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        if (!(newChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        StoredNode oldNode = (StoredNode) oldChild;
        StoredNode newNode = (StoredNode) newChild;
        if (!oldNode.nodeId.getParentId().equals(nodeId))
            throw new DOMException(DOMException.NOT_FOUND_ERR,
                    "node is not a child of this element");
        if (newNode.getNodeType() == Node.ATTRIBUTE_NODE) {
        	if (newNode.getQName().equalsSimple(Namespaces.XML_ID_QNAME)) {
				// an xml:id attribute. Normalize the attribute and set its type to ID
        		AttrImpl attr = (AttrImpl) newNode;
        		attr.setValue(StringValue.trimWhitespace(StringValue.collapseWhitespace(attr.getValue())));
        		attr.setType(AttrImpl.ID);
        	}
        }
        StoredNode previousNode = (StoredNode) oldNode.getPreviousSibling();
        if (previousNode == null)
            previousNode = this;
        else
            previousNode = getLastNode(previousNode);
        final NodePath currentPath = getPath();
        final NodePath oldPath = oldNode.getPath(currentPath);

        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            //May help getReindexRoot() to make some useful things
            broker.getIndexController().setDocument(ownerDocument);
            // check if the change affects any ancestor nodes, which then need to be reindexed later
            StoredNode reindexRoot = broker.getIndexController().getReindexRoot(oldNode, oldPath);
            // Remove indexes
            if (reindexRoot == null)
                reindexRoot = oldNode;
            broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.REMOVE_SOME_NODES);
            // TODO: fix once range index has been moved to new architecture
            StoredNode valueReindexRoot = broker.getValueIndex().getReindexRoot(this, oldPath);
            broker.getValueIndex().reindex(valueReindexRoot);

            // Remove the actual node data
            broker.removeNode(transaction, oldNode, oldPath, null);
            broker.endRemove(transaction);
        
            newNode.nodeId = oldNode.nodeId;
            // Reinsert the new node data
            broker.insertNodeAfter(transaction, previousNode, newNode);
            final NodePath path = newNode.getPath(currentPath);
            broker.indexNode(transaction, newNode, path);
            if (newNode.getNodeType() == Node.ELEMENT_NODE)
            broker.endElement(newNode, path, null);
            broker.updateNode(transaction, this, true);
            // Recreate indexes on ancestor nodes
            broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            broker.getValueIndex().reindex(valueReindexRoot);
            broker.flush();
        } catch (EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
        return newNode;
    }
    
    /**
     * @see org.w3c.dom.Node#removeChild(org.w3c.dom.Node)
     */
    public Node removeChild(Txn transaction, Node oldChild) throws DOMException {
        if (!(oldChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        final StoredNode oldNode = (StoredNode) oldChild;
        if (!oldNode.nodeId.getParentId().equals(nodeId))
            throw new DOMException(DOMException.NOT_FOUND_ERR,
                    "node is not a child of this element");
        NodePath oldPath = oldNode.getPath();
        StreamListener listener = null;
        DBBroker broker = null;
        try {
            //May help getReindexRoot() to make some useful things
            broker = ownerDocument.getBrokerPool().get(null);
            broker.getIndexController().setDocument(ownerDocument);
            StoredNode reindexRoot = broker.getIndexController().getReindexRoot(oldNode, oldPath);
            broker.getIndexController().setMode(StreamListener.REMOVE_SOME_NODES);
            if (reindexRoot == null) {
                listener = broker.getIndexController().getStreamListener();
            } else {
                broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.REMOVE_SOME_NODES);
            }
            broker.removeAllNodes(transaction, oldNode, oldPath, listener);
            --children;
            if (oldChild.getNodeType() == Node.ATTRIBUTE_NODE)
                --attributes;
            broker.endRemove(transaction);
            setDirty(true);
            broker.updateNode(transaction, this, false);
            broker.flush();
            broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
        } catch (EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
        return oldNode;
    }
	
	public void removeAppendAttributes(Txn transaction, NodeList removeList, NodeList appendList) {
        DBBroker broker = null;
		try {
            broker = ownerDocument.getBrokerPool().get(null);
			if (removeList != null) {
				try {
					for (int i=0; i<removeList.getLength(); i++) {
						Node oldChild = removeList.item(i);
						if (!(oldChild instanceof StoredNode))
							throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
						StoredNode old = (StoredNode) oldChild;
						if (!old.nodeId.isChildOf(nodeId))
							throw new DOMException(DOMException.NOT_FOUND_ERR, "node " + old.nodeId.getParentId() + 
									" is not a child of element " + nodeId);
                        final NodePath oldPath = old.getPath();
                        // remove old custom indexes
                        broker.getIndexController().reindex(transaction, old, StreamListener.REMOVE_SOME_NODES);
                        broker.removeNode(transaction, old, oldPath, null);
						children--;
						attributes--;
					}
				} finally {
					broker.endRemove(transaction);
				}
			}
            NodePath path = getPath();
            broker.getIndexController().setDocument(ownerDocument, StreamListener.STORE);
            StreamListener listener = broker.getIndexController().getStreamListener();
            if (children == 0) {
			   appendChildren(transaction, nodeId.newChild(), null,
					   new NodeImplRef(this), path, appendList, listener);
			} else {
		        if (attributes == 0) {
		        	StoredNode firstChild = (StoredNode) getFirstChild();
		        	NodeId newNodeId = firstChild.nodeId.insertBefore();
                    appendChildren(transaction, newNodeId, firstChild.getNodeId(), new NodeImplRef(this), path, appendList, listener);
		        } else {
		        	AttribVisitor visitor = new AttribVisitor();
			        accept(visitor);
			        NodeId firstChildId = visitor.firstChild == null ? null : visitor.firstChild.nodeId;
			        NodeId newNodeId = visitor.lastAttrib.nodeId.insertNode(firstChildId);
                    appendChildren(transaction, newNodeId, firstChildId, new NodeImplRef(visitor.lastAttrib),
                    		path, appendList, listener);
		        }
                setDirty(true);
            }
			attributes += appendList.getLength();
        } catch (EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        } finally {
            broker.updateNode(transaction, this, true);
            broker.flush();
            ownerDocument.getBrokerPool().release(broker);
		}
	}

	@Deprecated
	private class AttribVisitor implements NodeVisitor {
		private StoredNode lastAttrib = null;
		private StoredNode firstChild = null;
		
		public boolean visit(StoredNode node) {
        	if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
        		lastAttrib = node;
        	} else if (node.nodeId.isChildOf(ElementImpl.this.nodeId)) {
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
     * 
     * @return The new node (this differs from the {@link org.w3c.dom.Node#replaceChild(Node, Node)} specification)
     * 
     * @see org.w3c.dom.Node#replaceChild(org.w3c.dom.Node, org.w3c.dom.Node)
     */
    public Node replaceChild(Txn transaction, Node newChild, Node oldChild) throws DOMException {
        if (!(oldChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        StoredNode oldNode = (StoredNode) oldChild;
        if (!oldNode.nodeId.getParentId().equals(nodeId))
            throw new DOMException(DOMException.NOT_FOUND_ERR,
                    "node is not a child of this element");        
        StoredNode previous = (StoredNode) oldNode.getPreviousSibling();
        if (previous == null)
            previous = this;
        else
            previous = getLastNode(previous);

        NodePath oldPath = oldNode.getPath();
        StreamListener listener = null;
        //May help getReindexRoot() to make some useful things
        Node newNode = null;
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            broker.getIndexController().setDocument(ownerDocument);
            StoredNode reindexRoot = broker.getIndexController().getReindexRoot(oldNode, oldPath);
            broker.getIndexController().setMode(StreamListener.REMOVE_SOME_NODES);
            if (reindexRoot == null) {
                listener = broker.getIndexController().getStreamListener();
            } else {
                broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.REMOVE_SOME_NODES);
            }

            broker.removeAllNodes(transaction, oldNode, oldPath, listener);
            broker.endRemove(transaction);
            broker.flush();
            broker.getIndexController().setMode(StreamListener.STORE);
            listener = broker.getIndexController().getStreamListener();
            newNode = appendChild(transaction, oldNode.nodeId, new NodeImplRef(previous), getPath(), newChild, listener);
            // reindex if required
            final DocumentImpl owner = (DocumentImpl)getOwnerDocument();
            broker.storeXMLResource(transaction, owner);
            broker.updateNode(transaction, this, false);
            broker.getIndexController().reindex(transaction, reindexRoot, StreamListener.STORE);
            broker.flush();
        } catch (EXistException e) {
            LOG.warn("Exception while inserting node: " + e.getMessage(), e);
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }

        //return oldChild;	// method is spec'd to return the old child, even though that's probably useless in this case
        return newNode; //returning the newNode is more sensible than returning the oldNode
    }

    private String escapeXml(Node child) {
        final String str = ((Attr) child).getValue();
        StringBuilder buffer = null;
        String entity = null;
        char ch;

        for (int i = 0; i < str.length(); i++) {
            ch = str.charAt(i);
            switch (ch) {
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
                default :
                    entity = null;
                    break;
            }
            if (buffer == null) {
                if (entity != null) {
                    buffer = new StringBuilder(str.length() + 20);
                    buffer.append(str.substring(0, i));
                    buffer.append(entity);
                }
            }
            else {
                if (entity == null)
                    buffer.append(ch);
                else
                    buffer.append(entity);
            }
        }
        return (buffer == null) ? str : buffer.toString();
    }
    
    public void setPreserveSpace(boolean preserveWS) {
        this.preserveWS = preserveWS;
    }
    
    public boolean preserveSpace() {
        return preserveWS;
    }     

	/** ? @see org.w3c.dom.Element#getSchemaTypeInfo()
	 */
	public TypeInfo getSchemaTypeInfo() {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getSchemaTypeInfo() not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Element#setIdAttribute(java.lang.String, boolean)
	 */
	public void setIdAttribute(String name, boolean isId) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setIdAttribute(String name, boolean isId) not implemented on class " + getClass().getName());	
	}

	/** ? @see org.w3c.dom.Element#setIdAttributeNS(java.lang.String, java.lang.String, boolean)
	 */
	public void setIdAttributeNS(String namespaceURI, String localName, boolean isId) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setIdAttributeNS(String namespaceURI, String localName, boolean isId) not implemented on class " + getClass().getName());		
	}

	/** ? @see org.w3c.dom.Element#setIdAttributeNode(org.w3c.dom.Attr, boolean)
	 */
	public void setIdAttributeNode(Attr idAttr, boolean isId) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setIdAttributeNode(Attr idAttr, boolean isId) not implemented on class " + getClass().getName());	
	}

	/** ? @see org.w3c.dom.Node#getBaseURI()
	 */
	public String getBaseURI() {
        String baseURI = getAttributeNS(Namespaces.XML_NS, "base");
        if ( baseURI == null) {
            baseURI = "";
        }
        StoredNode parent = getParentStoredNode();
        while (parent != null && parent.getBaseURI() != null) {
            if ("".equals(baseURI)) {
                baseURI = parent.getBaseURI();
            } else {
                baseURI = parent.getBaseURI() + "/" + baseURI;
            }
            parent = parent.getParentStoredNode();
            if (parent == null) return baseURI;
        }
        if ("".equals(baseURI)) {
            baseURI = getDocument().getBaseURI();
        }
        return baseURI;
	}

	/** ? @see org.w3c.dom.Node#compareDocumentPosition(org.w3c.dom.Node)
	 */
	public short compareDocumentPosition(Node other) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "compareDocumentPosition(Node other) not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#getTextContent()
	 */
	public String getTextContent() throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getTextContent() not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#setTextContent(java.lang.String)
	 */
	public void setTextContent(String textContent) throws DOMException {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setTextContent(String textContent) not implemented on class " + getClass().getName());	
	}

	/** ? @see org.w3c.dom.Node#isSameNode(org.w3c.dom.Node)
	 */
	public boolean isSameNode(Node other) 
	{
		// This function is used by Saxon in some circumstances, and this partial implementation is required for proper Saxon operation.
		if( other instanceof StoredNode ) {
			return( this.nodeId == ((StoredNode)other).nodeId && this.ownerDocument.getDocId() == ((StoredNode)other).ownerDocument.getDocId() );
		} 
		
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "isSameNode(Node other) not implemented on other class " + other.getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#lookupPrefix(java.lang.String)
	 */
	public String lookupPrefix(String namespaceURI) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "lookupPrefix(String namespaceURI) not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#isDefaultNamespace(java.lang.String)
	 */
	public boolean isDefaultNamespace(String namespaceURI) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "isDefaultNamespace(String namespaceURI) not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#lookupNamespaceURI(java.lang.String)
	 */
	public String lookupNamespaceURI(String prefix) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "lookupNamespaceURI(String prefix) not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#isEqualNode(org.w3c.dom.Node)
	 */
	public boolean isEqualNode(Node arg) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "isEqualNode(Node arg) not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#getFeature(java.lang.String, java.lang.String)
	 */
	public Object getFeature(String feature, String version) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getFeature(String feature, String version) not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#setUserData(java.lang.String, java.lang.Object, org.w3c.dom.UserDataHandler)
	 */
	public Object setUserData(String key, Object data, UserDataHandler handler) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "setUserData(String key, Object data, UserDataHandler handler) not implemented on class " + getClass().getName());
	}

	/** ? @see org.w3c.dom.Node#getUserData(java.lang.String)
	 */
	public Object getUserData(String key) {
        throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "getUserData(String key) not implemented on class " + getClass().getName());
	}
    
    public boolean accept(Iterator iterator, NodeVisitor visitor) {
        if (!visitor.visit(this))
            return false;
        if (hasChildNodes()) {
            final int ccount = getChildCount();
            StoredNode next;
            for (int i = 0; i < ccount; i++) {
                next = (StoredNode) iterator.next();            
                if (!next.accept(iterator, visitor))
                    return false;
            }
        }
        return true;
    }
}
