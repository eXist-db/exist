/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 * 
 */
package org.exist.dom;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.Signatures;
import org.exist.util.ByteArrayPool;
import org.exist.util.ByteConversion;
import org.exist.util.UTF8;
import org.w3c.dom.*;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * ElementImpl.java
 *
 * @author Wolfgang Meier
 */
public class ElementImpl
        extends NamedNode
        implements Element {

    protected short attributes = 0;
    protected int children = 0;
    protected long firstChild = -1;
    protected Map namespaceMappings = null;

    public ElementImpl() {
        super(Node.ELEMENT_NODE);
    }

    /**
     * Constructor for the ElementImpl object
     *
     * @param gid Description of the Parameter
     */
    public ElementImpl(long gid) {
        super(Node.ELEMENT_NODE, gid, null);
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

    /**
     * Constructor for the ElementImpl object
     *
     * @param gid      Description of the Parameter
     * @param nodeName Description of the Parameter
     */
    public ElementImpl(long gid, QName nodeName) {
        super(Node.ELEMENT_NODE, gid, nodeName);
    }

    /**
     * Reset this element to its initial state.
     *
     * @see org.exist.dom.NodeImpl#clear()
     */
    public void clear() {
        super.clear();
        firstChild = -1;
        gid = 0;
        children = 0;
        attributes = 0;
        if (namespaceMappings != null)
            namespaceMappings = null;
    }

    public static NodeImpl deserialize(byte[] data,
                                       int start,
                                       int len,
                                       DocumentImpl doc,
                                       boolean pooled) {
        byte attrSizeType = (byte) ((data[start] & 0x0C) >> 0x2);
        byte idSizeType = (byte) (data[start] & 0x03);
        boolean hasNamespace = (data[start] & 0x10) == 0x10;

        int children = ByteConversion.byteToInt(data, start + 1);
        short attributes = (short) Signatures.read(attrSizeType, data, start + 5);
        int next = start + 5 + Signatures.getLength(attrSizeType);
        int end = start + len;
        short id = (short) Signatures.read(idSizeType, data, next);
        next += Signatures.getLength(idSizeType);
        short nsId = 0;
        String prefix = null;
        if (hasNamespace) {
            nsId = ByteConversion.byteToShort(data, next);
            next += 2;
            int prefixLen = ByteConversion.byteToShort(data, next);
            next += 2;
            if (prefixLen > 0)
                prefix = UTF8.decode(data, next, prefixLen).toString();
            next += prefixLen;
        }

        String name = doc.getSymbols().getName(id);
        String namespace = nsId == 0 ? "" : doc.getSymbols().getNamespace(nsId);
        ElementImpl node;
        if (pooled)
            node = (ElementImpl) NodeObjectPool.getInstance().borrowNode(ElementImpl.class);
        else
            node = new ElementImpl();
        node.nodeName = doc.getSymbols().getQName(namespace, name, prefix);
        node.children = children;
        node.attributes = attributes;
        node.ownerDocument = doc;
        if (end > next) {
            byte[] pfxData = new byte[end - next];
            System.arraycopy(data, next, pfxData, 0, end - next);
            ByteArrayInputStream bin = new ByteArrayInputStream(pfxData);
            DataInputStream in = new DataInputStream(bin);
            try {
                short prefixCount = in.readShort();
                for (int i = 0; i < prefixCount; i++) {
                    prefix = in.readUTF();
                    nsId = in.readShort();
                    node.addNamespaceMapping(prefix, doc.getSymbols().getNamespace(nsId));
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        return node;
    }

    public void addNamespaceMapping(String prefix, String ns) {
        if (prefix == null)
            return;
        if (namespaceMappings == null)
            namespaceMappings = new HashMap(1);
        else if (namespaceMappings.containsKey(prefix))
            return;
        namespaceMappings.put(prefix, ns);
        ownerDocument.getSymbols().getNSSymbol(ns);
    }

    /**
     * Append a child to this node. This method does not rearrange the
     * node tree and is only used internally by the parser.
     *
     * @param child
     * @throws DOMException
     */
    public void appendChildInternal(NodeImpl child)
            throws DOMException {
        if (gid > 0) {
            child.setGID(firstChildID() + children);
            if (child.getGID() < 0) {
                final int level = ownerDocument.getTreeLevel(gid);
                final int order = ownerDocument.getTreeLevelOrder(level);
                throw new DOMException(DOMException.INVALID_STATE_ERR,
                        "internal error: node "
                        + gid
                        + "; first-child: "
                        + firstChildID()
                        + "; level: "
                        + level
                        + "; maxDepth: "
                        + ownerDocument.maxDepth
                        + "; order(level+1): "
                        + order
                        + "; start0: "
                        + ownerDocument.getLevelStartPoint(level)
                        + "; start1: "
                        + ownerDocument.getLevelStartPoint(level + 1));
            }
        }
        else
            child.setGID(0);
        ++children;
    }

    /**
     * @see org.w3c.dom.Node#appendChild(org.w3c.dom.Node)
     */
    public Node appendChild(Node child)
            throws DOMException {
        DocumentImpl prevDoc = new DocumentImpl(ownerDocument);
        long childGid;
        NodeImpl lastNode;
        if (children == 0) {
            childGid = firstChildID();
            lastNode = this;
        }
        else {
            childGid = lastChildID() + 1;
            lastNode = getLastNode((NodeImpl) ownerDocument.getNode(childGid - 1));
        }
        try {
            checkTree(1);
        }
        catch (EXistException e) {
            throw new DOMException(DOMException.INVALID_MODIFICATION_ERR,
                    "max. document size exceeded");
        }
        children++;
        Node node = appendChild(childGid, lastNode, getPath(), child, true);
        ownerDocument.broker.update(this);
        ownerDocument.broker.reindex(prevDoc, ownerDocument, null);
        try {
            ownerDocument.broker.saveCollection(ownerDocument.getCollection());
        }
        catch (PermissionDeniedException e) {
            throw new DOMException(DOMException.INVALID_ACCESS_ERR, e.getMessage());
        }
        return child;
    }

    private void checkTree(int size)
            throws EXistException {
        // check if the tree structure needs to be changed
        int level = ownerDocument.getTreeLevel(gid);
        if (ownerDocument.getMaxDepth() == level + 1) {
            ownerDocument.incMaxDepth();
            LOG.debug("setting maxDepth = " + ownerDocument.getMaxDepth());
        }
        if (ownerDocument.getTreeLevelOrder(level + 1) < children + size) {
            // recompute the order of the tree
            ownerDocument.setTreeLevelOrder(level + 1, children + size +
                    ownerDocument.broker.getXUpdateGrowthFactor());
            ownerDocument.calculateTreeLevelStartPoints(false);
            if (ownerDocument.reindex < 0 || ownerDocument.reindex > level + 1) {
                ownerDocument.reindex = level + 1;
            }
        }
    }

    public Node appendAttributes(NodeList attribs)
            throws DOMException {
    	NodeList duplicateAttrs = findDupAttributes(attribs);
    	if(duplicateAttrs != null) {
    		removeAppendAttributes(duplicateAttrs, attribs);
    		return null;
    	} else {
	        DocumentImpl prevDoc = new DocumentImpl(ownerDocument);
	        Node node = null;
	        NodeImpl lastAttrib = getLastAttribute();
	        if (children == 0) {
	            // no children: append a new child
	            node = appendChildren(firstChildID(), this, getPath(), attribs, true);
	        }
	        else {
	            if (lastAttrib != null && lastAttrib.gid == lastChildID())
	                node = appendChildren(lastChildID() + 1, lastAttrib, getPath(), attribs, true);
	            else
	                node = appendChildren(firstChildID() + 1, this, getPath(), attribs, true);
	        }
	        ownerDocument.broker.update(this);
	        ownerDocument.broker.reindex(prevDoc, ownerDocument, null);
	        return node;
    	}
    }

    private NodeList checkForAttributes(NodeList nodes)
            throws DOMException {
        NodeListImpl attribs = null;
        NodeListImpl rest = null;
        for (int i = 0; i < nodes.getLength(); i++) {
            Node next = nodes.item(i);
            if (next.getNodeType() == Node.ATTRIBUTE_NODE) {
                if (attribs == null)
                    attribs = new NodeListImpl();
                attribs.add(next);
            }
            else if (attribs != null) {
                if (rest == null) rest = new NodeListImpl();
                rest.add(next);
            }
        }
        if (attribs != null) {
            appendAttributes(attribs);
            return rest;
        }
        else
            return nodes;
    }

    public Node appendChildren(NodeList nodes, int child)
            throws DOMException {
    	// attributes are handled differently. Call checkForAttributes to extract them.
        nodes = checkForAttributes(nodes);
        if (nodes == null || nodes.getLength() == 0)
            return null;
        DocumentImpl prevDoc = new DocumentImpl(ownerDocument);
        Node node = null;
        if (children == 0) {
            // no children: append a new child
            node = appendChildren(firstChildID(), this, getPath(), nodes, true);
        }
        else {
            if (child == 1) {
                Node firstChild = getFirstChild();
                insertBefore(nodes, firstChild);
            }
            else {
                NodeImpl prevNode;
                long pos = firstChildID();
                if (0 < child && child <= children) {
                    pos = firstChildID() + child - 2;
                    prevNode = getLastNode((NodeImpl) ownerDocument.getNode(pos));
                    node = insertAfter(nodes, prevNode);
                }
                else {
                    prevNode = getLastNode((NodeImpl) ownerDocument.getNode(lastChildID()));
                    node = appendChildren(lastChildID() + 1, prevNode, getPath(), nodes, true);
                }
            }
        }
        ownerDocument.broker.update(this);
        ownerDocument.broker.reindex(prevDoc, ownerDocument, null);
        return node;
    }

    /**
     * Internal append.
     *
     * @return Node
     * @throws DOMException
     */
    protected Node appendChildren(long gid, NodeImpl last, NodePath lastPath, NodeList nodes, boolean index)
            throws DOMException {
        if (last == null || last.ownerDocument == null)
            throw new DOMException(DOMException.INVALID_MODIFICATION_ERR, "invalid node");
        try {
            checkTree(nodes.getLength());
        }
        catch (EXistException e) {
            throw new DOMException(DOMException.INVALID_MODIFICATION_ERR,
                    "max. document size exceeded");
        }
        children += nodes.getLength();
        Node child;
        for (int i = 0; i < nodes.getLength(); i++) {
            child = nodes.item(i);
            if (last == null)
                throw new DOMException(DOMException.INVALID_MODIFICATION_ERR,
                        "invalid node: null");
            last = (NodeImpl) appendChild(gid + i, last, lastPath, child, index);
        }
        return last;
    }

    private Node appendChild(long gid, NodeImpl last, NodePath lastPath, Node child, boolean index)
            throws DOMException {
        if (last == null)
            throw new DOMException(DOMException.INVALID_MODIFICATION_ERR, "invalid node");
        String ns, prefix;
        Attr attr;
        switch (child.getNodeType()) {
            case Node.ELEMENT_NODE:
                // create new element
                Element childElem = (Element) child;
                final ElementImpl elem =
                        new ElementImpl(new QName(child.getLocalName(),
                                child.getNamespaceURI(),
                                child.getPrefix()));
                elem.setGID(gid);
                elem.setOwnerDocument(ownerDocument);
                final NodeListImpl ch = new NodeListImpl();
                final NamedNodeMap attribs = child.getAttributes();
                for (int i = 0; i < attribs.getLength(); i++) {
                    attr = (Attr) attribs.item(i);
                    ch.add(attr);
                }
                ch.addAll(child.getChildNodes());
                elem.setChildCount(ch.getLength());
                elem.setAttributes((short) (elem.getAttributesCount() + attribs.getLength()));
                lastPath.addComponent(elem.getQName());
                // insert the node
                ownerDocument.broker.insertAfter(last, elem);
                // index now?
                if ((ownerDocument.reindex < 0
                        || ownerDocument.reindex > ownerDocument.getTreeLevel(gid))
                        && index)
                    ownerDocument.broker.index(elem, lastPath);
                elem.setChildCount(0);
                try {
                    elem.checkTree(ch.getLength());
                }
                catch (EXistException e) {
                    throw new DOMException(DOMException.INVALID_MODIFICATION_ERR,
                            "max. document size exceeded");
                }
                // process child nodes
                last =
                        (NodeImpl) elem.appendChildren(elem.firstChildID(), elem, lastPath, ch, index);
                lastPath.removeLastComponent();
                return last;
            case Node.TEXT_NODE:
                final TextImpl text = new TextImpl(((Text) child).getData());
                text.setGID(gid);
                text.setOwnerDocument(ownerDocument);
                // insert the node
                ownerDocument.broker.insertAfter(last, text);
                if ((ownerDocument.reindex < 0
                        || ownerDocument.reindex > ownerDocument.getTreeLevel(gid))
                        && index)
                    ownerDocument.broker.index(text, lastPath);
                return text;
            case Node.ATTRIBUTE_NODE:
                attr = (Attr) child;
                ns = attr.getNamespaceURI();
                prefix = (ns != null && ns.equals("http://www.w3.org/XML/1998/namespace") ? "xml" : attr.getPrefix());
                String name = attr.getLocalName();
                if (name == null) name = attr.getName();
                QName attrName =
                        new QName(name, ns, prefix);
                final AttrImpl attrib = new AttrImpl(attrName, attr.getValue());
                attrib.setGID(gid);
                attrib.setOwnerDocument(ownerDocument);
                ownerDocument.broker.insertAfter(last, attrib);
                // index now?
                if ((ownerDocument.reindex < 0
                        || ownerDocument.reindex > ownerDocument.getTreeLevel(gid))
                        && index) {
                    ownerDocument.broker.index(attrib, lastPath);
                }
                return attrib;
            case Node.COMMENT_NODE:
                final CommentImpl comment = new CommentImpl(((Comment) child).getData());
                comment.setGID(gid);
                comment.setOwnerDocument(ownerDocument);
                // insert the node
                ownerDocument.broker.insertAfter(last, comment);
                if ((ownerDocument.reindex < 0
                        || ownerDocument.reindex > ownerDocument.getTreeLevel(gid))
                        && index)
                    ownerDocument.broker.index(comment, lastPath);
                return comment;
            case Node.PROCESSING_INSTRUCTION_NODE:
                final ProcessingInstructionImpl pi =
                        new ProcessingInstructionImpl(gid,
                                ((ProcessingInstruction) child).getTarget(),
                                ((ProcessingInstruction) child).getData());
                pi.setOwnerDocument(ownerDocument);
                //			insert the node
                ownerDocument.broker.insertAfter(last, pi);
                if ((ownerDocument.reindex < 0
                        || ownerDocument.reindex > ownerDocument.getTreeLevel(gid))
                        && index)
                    ownerDocument.broker.index(pi, lastPath);
                return pi;
            default :
                throw new DOMException(DOMException.INVALID_MODIFICATION_ERR,
                        "unknown node type: "
                        + child.getNodeType()
                        + " "
                        + child.getNodeName());
        }
    }

    public boolean declaresNamespacePrefixes() {
        return namespaceMappings != null && namespaceMappings.size() > 0;
    }

    /**
     * @see org.exist.dom.NodeImpl#firstChildID()
     */
    public long firstChildID() {
        if (gid == 0)
            return 0;
        if (firstChild > -1)
            return firstChild;
        firstChild = XMLUtil.getFirstChildId(ownerDocument, gid);
        return firstChild;
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
        long start = firstChildID();
        for (long i = start; i < start + children; i++) {
            Node child = ownerDocument.getNode(i);
            if (child != null
                    && child.getNodeType() == Node.ATTRIBUTE_NODE
                    && child.getNodeName().equals(name))
                return ((AttrImpl) child).getValue();
        }
        return null;
    }

    /**
     * @see org.w3c.dom.Element#getAttributeNS(java.lang.String, java.lang.String)
     */
    public String getAttributeNS(String namespaceURI, String localName) {
        // altheim: 2003-12-02
        long start = firstChildID();
        for (long i = start; i < start + children; i++) {
            Node child = ownerDocument.getNode(i);
            if (child != null
                    && child.getNodeType() == Node.ATTRIBUTE_NODE
                    && (child.getNamespaceURI() == null
                    || child.getNamespaceURI().equals(namespaceURI))
                    && child.getLocalName().equals(localName))
                return ((AttrImpl) child).getValue();
        }
        return "";
    }

    /**
     * @see org.w3c.dom.Element#getAttributeNode(java.lang.String)
     */
    public Attr getAttributeNode(String name) {
        long start = firstChildID();
        for (long i = start; i < start + children; i++) {
            Node child = ownerDocument.getNode(i);
            if (child != null
                    && child.getNodeType() == Node.ATTRIBUTE_NODE
                    && child.getNodeName().equals(name))
                return (Attr) child;
        }
        return null;
    }

    /**
     * Check if an attribute is already present in the node's attribute list. Throws a
     * DOMException if yes. Otherwise, returns the last attribute in the attribute list.
     * 
     * @param attrs
     * @return
     * @throws DOMException
     */
    private AttrImpl getLastAttribute() throws DOMException {
        long start = firstChildID();
        AttrImpl attr = null;
        for (long i = start; i < start + children; i++) {
            Node child = ownerDocument.getNode(i);
            if (child != null) {
            	if(child.getNodeType() == Node.ATTRIBUTE_NODE)
            		attr = (AttrImpl) child;
            } else
            	break;
        }
        return attr;
    }

    /**
     * Returns a list of all attribute nodes in attrs that are already present
     * in the current element.
     * 
     * @param attrs
     * @return
     * @throws DOMException
     */
    private NodeList findDupAttributes(NodeList attrs) throws DOMException {
    	NodeListImpl dupList = null;
    	long start = firstChildID();
        for (long i = start; i < start + children; i++) {
            Node child = ownerDocument.getNode(i);
            if(child.getNodeType() != Node.ATTRIBUTE_NODE)
            	break;
            Node duplicate = findAttribute(child, attrs);
            if(duplicate != null) {
            	LOG.debug("Found a duplicate attribute: " + child.getLocalName());
            	if(dupList == null)
            		dupList = new NodeListImpl();
            	dupList.add(child);
            }
        }
        return dupList;
    }
    
    private static Node findAttribute(Node child, NodeList attrs) throws DOMException {
    	String childNS = child.getNamespaceURI();
    	if(childNS == null)
    		childNS = "";
    	for(int i = 0; i < attrs.getLength(); i++) {
    		Node current = (Node) attrs.item(i);
    		if(current == null || current.getNodeType() != Node.ATTRIBUTE_NODE)
    			continue;
    		String currentNS = current.getNamespaceURI();
    		if(currentNS == null)
    			currentNS = "";
    		if(child.getLocalName().equals(current.getLocalName()) &&
    				childNS.equals(currentNS))
    			return current;
    	}
    	return null;
    }
    
    /**
     * @see org.w3c.dom.Element#getAttributeNodeNS(java.lang.String, java.lang.String)
     */
    public Attr getAttributeNodeNS(String namespaceURI, String localName) {
        // altheim: 2003-12-02
        long start = firstChildID();
        for (long i = start; i < start + children; i++) {
            Node child = ownerDocument.getNode(i);
            if (child != null
                    && child.getNodeType() == Node.ATTRIBUTE_NODE
                    && (child.getNamespaceURI() == null
                    || child.getNamespaceURI().equals(namespaceURI))
                    && child.getLocalName().equals(localName)) {
                return (Attr) child;
            }
        }
        return null;
    }

    /**
     * @see org.w3c.dom.Node#getAttributes()
     */
    public NamedNodeMap getAttributes() {
        NamedNodeMapImpl map = new NamedNodeMapImpl();
        long start = firstChildID();
        if (getAttributesCount() == 0)
            return map;
        for (long i = start; i < start + children; i++) {
            Node child = ownerDocument.getNode(i);
            if (child != null && child.getNodeType() == Node.ATTRIBUTE_NODE)
                map.setNamedItem(child);

        }
        return map;
    }

    /**
     * @see org.exist.dom.NodeImpl#getChildCount()
     */
    public int getChildCount() {
        return children;
    }

    /**
     * @see org.w3c.dom.Node#getChildNodes()
     */
    public NodeList getChildNodes() {
        if (children == 0)
            return new NodeListImpl();
        long first = firstChildID();
        if (children == 1) {
            NodeListImpl childList = new NodeListImpl(1);
            childList.add(ownerDocument.getNode(first));
            return childList;
        }
        NodeList result = ownerDocument.getRange(first, first + children - 1);
        return result;
    }

    /**
     * @see org.w3c.dom.Element#getElementsByTagName(java.lang.String)
     */
    public NodeList getElementsByTagName(String tagName) {
        QName qname = new QName(tagName, "", null);
        return (NodeSet) ownerDocument.findElementsByTagName(this, qname);
    }

    /**
     * @see org.w3c.dom.Element#getElementsByTagNameNS(java.lang.String, java.lang.String)
     */
    public NodeList getElementsByTagNameNS(String namespaceURI, String localName) {
        QName qname = new QName(localName, namespaceURI, null);
        return (NodeSet) ownerDocument.findElementsByTagName(this, qname);
    }

    /**
     * @see org.w3c.dom.Node#getFirstChild()
     */
    public Node getFirstChild() {
        if (!hasChildNodes() || getChildCount() == getAttributesCount())
            return null;
        long first = firstChildID() + getAttributesCount();
        return ownerDocument.getNode(first);
    }

    /**
     * @see org.w3c.dom.Node#getLastChild()
     */
    public Node getLastChild() {
        if (!hasChildNodes())
            return null;
        return ownerDocument.getNode(lastChildID());
    }

    /**
     * @see org.w3c.dom.Node#getNodeValue()
     */
    public String getNodeValue()
            throws DOMException {
        return null;
    }

    /**
     * @see org.w3c.dom.Element#getTagName()
     */
    public String getTagName() {
        return nodeName.toString();
    }

    /**
     * @see org.w3c.dom.Element#hasAttribute(java.lang.String)
     */
    public boolean hasAttribute(String name) {
        long first = firstChildID();
        for (int i = 0; i < children; i++) {
            Node n = ownerDocument.getNode(first + i);
            if (n.getNodeType() == Node.ATTRIBUTE_NODE && n.getNodeName().equals(name))
                return true;
        }
        return false;
    }

    /**
     * @see org.w3c.dom.Element#hasAttributeNS(java.lang.String, java.lang.String)
     */
    public boolean hasAttributeNS(String namespaceURI, String localName) {
        // altheim: 2003-12-02
        long first = firstChildID();
        for (int i = 0; i < children; i++) {
            Node n = ownerDocument.getNode(first + i);
            if (n.getNodeType() == Node.ATTRIBUTE_NODE
                    && (n.getNamespaceURI() == null || n.getNamespaceURI().equals(namespaceURI))
                    && n.getLocalName().equals(localName)) {
                return true;
            }
        }
        return false;
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

    public long lastChildID() {
        if (!hasChildNodes())
            return -1;
        return firstChildID() + children - 1;
    }

    /**
     * @see org.w3c.dom.Element#removeAttribute(java.lang.String)
     */
    public void removeAttribute(String name)
            throws DOMException {
    }

    /**
     * @see org.w3c.dom.Element#removeAttributeNS(java.lang.String, java.lang.String)
     */
    public void removeAttributeNS(String namespaceURI, String name)
            throws DOMException {
    }

    public Attr removeAttributeNode(Attr oldAttr)
            throws DOMException {
        return null;
    }

    public byte[] serialize() {
        try {
            byte[] prefixData = null;
            // serialize namespace prefixes declared in this element
            if (namespaceMappings != null && namespaceMappings.size() > 0) {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bout);
                out.writeShort(namespaceMappings.size());
                Map.Entry entry;
                short nsId;
                for (Iterator i = namespaceMappings.entrySet().iterator();
                     i.hasNext();
                        ) {
                    entry = (Map.Entry) i.next();
                    out.writeUTF((String) entry.getKey());
                    nsId =
                            ownerDocument.getSymbols().getNSSymbol((String) entry.getValue());
                    out.writeShort(nsId);
                }
                prefixData = bout.toByteArray();
            }
            final short id = ownerDocument.getSymbols().getSymbol(this);
            final boolean hasNamespace = nodeName.needsNamespaceDecl();
            final short nsId =
                    hasNamespace
                    ? ownerDocument.getSymbols().getNSSymbol(nodeName.getNamespaceURI())
                    : 0;

            final byte attrSizeType = Signatures.getSizeType(attributes);
            final byte idSizeType = Signatures.getSizeType(id);
            byte signature =
                    (byte) ((Signatures.Elem << 0x5) | (attrSizeType << 0x2) | idSizeType);
            int prefixLen = 0;
            if (hasNamespace) {
                prefixLen =
                        nodeName.getPrefix() != null
                        && nodeName.getPrefix().length() > 0
                        ? UTF8.encoded(nodeName.getPrefix())
                        : 0;
                signature |= 0x10;
            }
            byte[] data =
                    ByteArrayPool.getByteArray(5
                    + Signatures.getLength(attrSizeType)
                    + Signatures.getLength(idSizeType)
                    + (hasNamespace ? prefixLen + 4 : 0)
                    + (prefixData != null ? prefixData.length : 0));
            int next = 0;
            data[next++] = signature;
            ByteConversion.intToByte(children, data, next);
            next += 4;
            Signatures.write(attrSizeType, attributes, data, next);
            next += Signatures.getLength(attrSizeType);
            Signatures.write(idSizeType, id, data, next);
            next += Signatures.getLength(idSizeType);
            if (hasNamespace) {
                ByteConversion.shortToByte(nsId, data, next);
                next += 2;
                ByteConversion.shortToByte((short) prefixLen, data, next);
                next += 2;
                if (nodeName.getPrefix() != null && nodeName.getPrefix().length() > 0)
                    UTF8.encode(nodeName.getPrefix(), data, next);
                next += prefixLen;
            }

            if (prefixData != null)
                System.arraycopy(prefixData, 0, data, next, prefixData.length);
            return data;
        }
        catch (IOException e) {
            return null;
        }
    }

    public void setAttribute(String name, String value)
            throws DOMException {
    }

    public void setAttributeNS(String namespaceURI, String qualifiedName, String value)
            throws DOMException {
    }

    public Attr setAttributeNode(Attr newAttr)
            throws DOMException {
        return null;
    }

    public Attr setAttributeNodeNS(Attr newAttr) {
        return null;
    }

    public void setChildCount(int count) {
        children = count;
    }

    public void setNamespaceMappings(Map map) {
        namespaceMappings = new HashMap(map);
        String ns;
        for (Iterator i = namespaceMappings.values().iterator(); i.hasNext();) {
            ns = (String) i.next();
            ownerDocument.getSymbols().getNSSymbol(ns);
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

    public void toSAX(ContentHandler contentHandler,
                      LexicalHandler lexicalHandler,
                      boolean first,
                      Set namespaces)
            throws SAXException {
        NodeList childNodes = getChildNodes();
        NodeImpl child = null;
        DBBroker broker = ownerDocument.getBroker();
        AttributesImpl attributes = new AttributesImpl();
        ArrayList myPrefixes = null;
        String defaultNS = null;
        if (declaresNamespacePrefixes()) {
            // declare namespaces used by this element
            Map.Entry entry;
            for (Iterator i = namespaceMappings.entrySet().iterator(); i.hasNext();) {
                entry = (Map.Entry) i.next();
                contentHandler.startPrefixMapping((String) entry.getKey(),
                        (String) entry.getValue());
            }
        }
        if (nodeName.needsNamespaceDecl()
                && (!namespaces.contains(nodeName.getNamespaceURI())))
            contentHandler.startPrefixMapping(nodeName.getPrefix(),
                    nodeName.getNamespaceURI());
        if (first) {
            attributes.addAttribute("http://exist.sourceforge.net/NS/exist",
                    "id",
                    "exist:id",
                    "CDATA",
                    Long.toString(gid));
            attributes.addAttribute("http://exist.sourceforge.net/NS/exist",
                    "source",
                    "exist:source",
                    "CDATA",
                    ownerDocument.getFileName());
        }
        int i = 0;
        while (i < childNodes.getLength()) {
            child = (NodeImpl) childNodes.item(i);
            if (child.getNodeType() == Node.ATTRIBUTE_NODE) {
                attributes.addAttribute(child.getNamespaceURI(),
                        child.getLocalName(),
                        child.getNodeName(),
                        "CDATA",
                        ((AttrImpl) child).getValue());
                i++;
            }
            else
                break;
        }
        String ns = defaultNS == null ? getNamespaceURI() : defaultNS;
        contentHandler.startElement(ns, getLocalName(), getNodeName(), attributes);
        while (i < childNodes.getLength()) {
            child.toSAX(contentHandler, lexicalHandler, false, namespaces);
            i++;
            if (i < childNodes.getLength())
                child = (NodeImpl) childNodes.item(i);
            else
                break;
        }
        contentHandler.endElement(ns, getLocalName(), getNodeName());
        if (declaresNamespacePrefixes() && myPrefixes != null) {
            String prefix;
            for (Iterator j = namespaceMappings.keySet().iterator(); j.hasNext();) {
                prefix = (String) j.next();
                contentHandler.endPrefixMapping(prefix);
            }
        }
        if (nodeName.needsNamespaceDecl()
                && (!namespaces.contains(nodeName.getNamespaceURI())))
            contentHandler.endPrefixMapping(nodeName.getPrefix());
    }

    /**
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return toString(true);
    }

    /**
     * @see org.exist.dom.NodeImpl#toString(boolean)
     */
    public String toString(boolean top) {
        return toString(top, new TreeSet());
    }

    /**
     * Method toString.
     *
     */
    public String toString(boolean top, TreeSet namespaces) {
        DBBroker broker = ownerDocument.getBroker();
        StringBuffer buf = new StringBuffer();
        StringBuffer attributes = new StringBuffer();
        StringBuffer children = new StringBuffer();
        buf.append('<');
        buf.append(nodeName);
        if (top) {
            buf.append(" xmlns:exist=\"http://exist.sourceforge.net/NS/exist\"");
            buf.append(" exist:id=\"");
            buf.append(gid);
            buf.append("\" exist:document=\"");
            buf.append(ownerDocument.getFileName());
            buf.append("\"");
        }
        ArrayList myPrefixes = null;
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
                    buf.append(namespace);
                }
                else {
                    buf.append(" xmlns:");
                    buf.append(prefix);
                    buf.append("=\"");
                    buf.append(namespace);
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
        Node child;
        for (int i = 0; i < childNodes.getLength(); i++) {
            child = childNodes.item(i);
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
    public Node insertBefore(Node newChild, Node refChild)
            throws DOMException {
        if (!(refChild instanceof NodeImpl))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        DocumentImpl prevDoc = new DocumentImpl(ownerDocument);
        if (refChild == null)
            return appendChild(newChild);
        NodeImpl ref = (NodeImpl) refChild;
        long first = firstChildID();
        if (ref.gid < first || ref.gid > ref.gid + children - 1)
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "reference node is not a child of the selected node");
        Node result;
        if (ref.gid == first)
            result = appendChild(first, this, getPath(), newChild, false);
        else {
            NodeImpl prev = (NodeImpl) ref.getPreviousSibling();
            result = appendChild(ref.gid, getLastNode(prev), getPath(), newChild, false);
        }
        ownerDocument.broker.update(this);
        ownerDocument.broker.reindex(prevDoc, ownerDocument, null);
        try {
            ownerDocument.broker.saveCollection(ownerDocument.getCollection());
        }
        catch (PermissionDeniedException e) {
        }
        return result;
    }

    /**
     * Insert a list of nodes at the position before the reference
     * child.
     */
    public Node insertBefore(NodeList nodes, Node refChild)
            throws DOMException {
        if (!(refChild instanceof NodeImpl))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        DocumentImpl prevDoc = new DocumentImpl(ownerDocument);
        if (refChild == null)
            return appendChildren(nodes, -1);
        NodeImpl ref = (NodeImpl) refChild;
        final long first = firstChildID();
        if (ref.gid < first || ref.gid > ref.gid + children - 1)
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "reference node is not a child of the selected node");
        final int level = ownerDocument.getTreeLevel(gid);
        Node result;
        if (ref.gid == first)
            result = appendChildren(first, this, getPath(), nodes, false);
        else {
            NodeImpl prev = (NodeImpl) ref.getPreviousSibling();
            result = appendChildren(ref.gid, getLastNode(prev), getPath(), nodes, false);
        }
        ownerDocument.broker.update(this);
        if (ownerDocument.reindex > -1) {
            ownerDocument.reindex = level + 1;
            ownerDocument.broker.reindex(prevDoc, ownerDocument, null);
        }
        else {
            ownerDocument.reindex = level + 1;
            ownerDocument.broker.reindex(prevDoc, ownerDocument, this);
        }
        return result;
    }

    /**
     * Insert a list of nodes at the position following the reference
     * child.
     */
    public Node insertAfter(NodeList nodes, Node refChild)
            throws DOMException {
        if (refChild == null)
            return appendChildren(nodes, -1);
        if (!(refChild instanceof NodeImpl))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type: ");
        final DocumentImpl prevDoc = new DocumentImpl(ownerDocument);
        final NodeImpl ref = (NodeImpl) refChild;
        final long first = firstChildID();
        if (ref.gid < first || ref.gid > ref.gid + children - 1)
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "reference node is not a child of the selected node");
        final int level = ownerDocument.getTreeLevel(gid);
        Node result = appendChildren(ref.gid + 1, getLastNode(ref), getPath(), nodes, false);
        ownerDocument.broker.update(this);
        if (ownerDocument.reindex > -1) {
            ownerDocument.reindex = level + 1;
            ownerDocument.broker.reindex(prevDoc, ownerDocument, null);
        }
        else {
            ownerDocument.reindex = level + 1;
            ownerDocument.broker.reindex(prevDoc, ownerDocument, this);
        }
        return result;
    }

    /**
     * Update the contents of this element. The passed list of nodes
     * becomes the new content.
     *
     * @param newContent
     * @throws DOMException
     */
    public void update(NodeList newContent)
            throws DOMException {
        final NodePath path = getPath();
        final DocumentImpl prevDoc = new DocumentImpl(ownerDocument);
        // remove old child nodes
        NodeList nodes = getChildNodes();
        NodeImpl child, last = this;
        long firstChildId = firstChildID();
        int i = nodes.getLength();
        for (; i > 0; i--) {
            child = (NodeImpl) nodes.item(i - 1);
            if (child.getNodeType() == Node.ATTRIBUTE_NODE) {
                firstChildId = child.gid + 1;
                last = child;
                break;
            }
            if (child.getNodeType() == Node.ELEMENT_NODE)
                path.addComponent(child.getQName());
            removeAll(child, path);
            if (child.getNodeType() == Node.ELEMENT_NODE)
                path.removeLastComponent();
        }
        ownerDocument.broker.endRemove();
        children = i;
        // append new content
        appendChildren(firstChildId, last, getPath(), newContent, true);
        ownerDocument.broker.update(this);
        // reindex if required
        ownerDocument.broker.reindex(prevDoc, ownerDocument, null);
    }

    /**
     * Update a child node. This method will only update the child node
     * but not its potential descendant nodes.
     *
     * @param oldChild
     * @param newChild
     * @throws DOMException
     */
    public void updateChild(Node oldChild, Node newChild)
            throws DOMException {
        if (!(oldChild instanceof NodeImpl))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        NodeImpl old = (NodeImpl) oldChild;
        NodeImpl newNode = (NodeImpl) newChild;
        if (old.getParentGID() != gid)
            throw new DOMException(DOMException.NOT_FOUND_ERR,
                    "node is not a child of this element");
        NodeImpl previous = (NodeImpl) old.getPreviousSibling();
        if (previous == null)
            previous = this;
        else
            previous = getLastNode(previous);
        ownerDocument.broker.removeNode(old, old.getPath());
        ownerDocument.broker.endRemove();
        newNode.gid = old.gid;
        ownerDocument.broker.insertAfter(previous, newNode);
        ownerDocument.broker.index(newNode, newNode.getPath());
        ownerDocument.broker.flush();
    }

    /**
     * @see org.w3c.dom.Node#removeChild(org.w3c.dom.Node)
     */
    public Node removeChild(Node oldChild)
            throws DOMException {
        if (!(oldChild instanceof NodeImpl))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        NodeImpl old = (NodeImpl) oldChild;
        if (old.getParentGID() != gid)
            throw new DOMException(DOMException.NOT_FOUND_ERR,
                    "node is not a child of this element");
        final int level = ownerDocument.getTreeLevel(gid);
        final DocumentImpl prevDoc = new DocumentImpl(ownerDocument);
        final long lastChild = lastChildID();
        removeAll(old, old.getPath());
        --children;
        ownerDocument.broker.endRemove();
        ownerDocument.broker.update(this);
        if (old.gid < lastChild) {
            ownerDocument.reindex = level + 1;
            ownerDocument.broker.reindex(prevDoc, ownerDocument, this);
        }
//		ownerDocument.broker.checkTree(ownerDocument);
        return old;
    }

    private void removeAll(NodeImpl node, NodePath currentPath) {
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
                NodeList children = node.getChildNodes();
                NodeImpl child;
                for (int i = children.getLength() - 1; i > -1; i--) {
                    child = (NodeImpl) children.item(i);
                    if (child.nodeType == Node.ELEMENT_NODE) {
                        currentPath.addComponent(((ElementImpl) child).getQName());
                        removeAll(child, currentPath);
                        currentPath.removeLastComponent();
                    }
                    else
                        removeAll(child, currentPath);
                }
                ownerDocument.broker.removeNode(node, currentPath);
                break;
            default :
                ownerDocument.broker.removeNode(node, currentPath);
                break;
        }
    }
	
	public void removeAppendAttributes(NodeList removeList, NodeList appendList) {
		final int level = ownerDocument.getTreeLevel(gid);
		final DocumentImpl prevDoc = new DocumentImpl(ownerDocument);
		final long lastChild = lastChildID();
		
		try {
			try {
				for (int i=0; i<removeList.getLength(); i++) {
					Node oldChild = removeList.item(i);
					if (!(oldChild instanceof NodeImpl))
						throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
					NodeImpl old = (NodeImpl) oldChild;
					if (old.getParentGID() != gid)
						throw new DOMException(DOMException.NOT_FOUND_ERR, "node is not a child of this element");
					ownerDocument.broker.removeNode(old, old.getPath());
					if(old.gid < lastChild) ownerDocument.reindex = level + 1;
					children--;
				}
			} finally {
				ownerDocument.broker.endRemove();
			}
			
			if (children == 0) {
			   appendChildren(firstChildID(), this, getPath(), appendList, true);
			} else {
			    NodeImpl lastAttrib = getLastAttribute();
			    if (lastAttrib != null && lastAttrib.gid == lastChildID())
			        appendChildren(lastChildID() + 1, lastAttrib, getPath(), appendList, true);
			    else
			        appendChildren(firstChildID() + 1, this, getPath(), appendList, true);
			}
			
		} finally {
			ownerDocument.broker.update(this);
			ownerDocument.broker.reindex(prevDoc, ownerDocument, null);
		}
	}

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#replaceChild(org.w3c.dom.Node, org.w3c.dom.Node)
     */
    public Node replaceChild(Node newChild, Node oldChild)
            throws DOMException {
        if (!(oldChild instanceof NodeImpl))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        NodeImpl old = (NodeImpl) oldChild;
        if (old.getParentGID() != gid)
            throw new DOMException(DOMException.NOT_FOUND_ERR,
                    "node is not a child of this element");
        final DocumentImpl prevDoc = new DocumentImpl(ownerDocument);
        NodeImpl previous = (NodeImpl) old.getPreviousSibling();
        if (previous == null)
            previous = this;
        else
            previous = getLastNode(previous);
        removeAll(old, old.getPath());
        ownerDocument.broker.endRemove();
        appendChild(old.gid, previous, getPath(), newChild, true);
        // reindex if required
        ownerDocument.broker.reindex(prevDoc, ownerDocument, null);
        try {
            ownerDocument.broker.saveCollection(ownerDocument.getCollection());
        }
        catch (PermissionDeniedException e) {
            throw new DOMException(DOMException.INVALID_ACCESS_ERR, e.getMessage());
        }
        return newChild;
    }

    private String escapeXml(Node child) {

        final String str = ((Attr) child).getValue();
        StringBuffer buffer = null;
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
                    buffer = new StringBuffer(str.length() + 20);
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
}
