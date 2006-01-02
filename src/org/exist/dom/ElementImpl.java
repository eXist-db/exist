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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.storage.GeneralRangeIndexSpec;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.storage.RangeIndexSpec;
import org.exist.storage.Signatures;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.ByteArrayPool;
import org.exist.util.ByteConversion;
import org.exist.util.UTF8;
import org.exist.xquery.Constants;
import org.exist.xquery.value.StringValue;
import org.w3c.dom.Attr;
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

/**
 * ElementImpl.java
 *
 * @author Wolfgang Meier
 */
public class ElementImpl extends NamedNode implements Element {

    protected short attributes = 0;
    protected int children = 0;
    protected long firstChild = StoredNode.NODE_IMPL_UNKNOWN_GID;
    protected Map namespaceMappings = null;
	protected int indexType = RangeIndexSpec.NO_INDEX;
	protected int position = 0;
	protected boolean preserveWS = false;
    
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
     * @see org.exist.dom.NodeImpl#clear()
     */
    public void clear() {
        super.clear();
        firstChild = StoredNode.NODE_IMPL_UNKNOWN_GID;
        //TOUNDERSTAND : what are the semantics of this 0 ? -pb
        super.setGID(0);
        children = 0;
        attributes = 0;
        position = 0;
        if (namespaceMappings != null)
            namespaceMappings = null;
    }

    public static StoredNode deserialize(byte[] data,
                                       int start,
                                       int len,
                                       DocumentImpl doc,
                                       boolean pooled) {
        byte idSizeType = (byte) (data[start] & 0x03);
        boolean hasNamespace = (data[start] & 0x10) == 0x10;

        int children = ByteConversion.byteToInt(data, start + 1);
        short attributes = ByteConversion.byteToShort(data, start + 5);
        int next = start + 7;
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
        node.nodeName = doc.getSymbols().getQName(Node.ELEMENT_NODE, namespace, name, prefix);
        node.children = children;
        node.attributes = attributes;
        node.setOwnerDocument(doc);
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
        ((DocumentImpl)getOwnerDocument()).getSymbols().getNSSymbol(ns);
    }

    /**
     * Append a child to this node. This method does not rearrange the
     * node tree and is only used internally by the parser.
     *
     * @param child
     * @throws DOMException
     */
    public void appendChildInternal(StoredNode child) throws DOMException {
        //TOUNDERSTAND : what are the semantics of this 0 ? -pb
        if (getGID() > 0) {
            child.setGID(firstChildID() + children);            
            if (child.getGID() == StoredNode.NODE_IMPL_UNKNOWN_GID) {
                final int level = ((DocumentImpl)getOwnerDocument()).getTreeLevel(this.getGID());
                final int order = ((DocumentImpl)getOwnerDocument()).getTreeLevelOrder(level);
                throw new DOMException(DOMException.INVALID_STATE_ERR,
                        "internal error: node "
                        + getGID()
                        + "; first-child: "
                        + firstChildID()
                        + "; level: "
                        + level
                        + "; maxDepth: "
                        + ((DocumentImpl)getOwnerDocument()).getMaxDepth()
                        + "; order(level+1): "
                        + order
                        + "; start0: "
                        + ((DocumentImpl)getOwnerDocument()).getLevelStartPoint(level)
                        + "; start1: "
                        + ((DocumentImpl)getOwnerDocument()).getLevelStartPoint(level + 1));
            }
        }
        else
            //TOUNDERSTAND : what are the semantics of this 0 ? -pb
            child.setGID(0);
        ++children;
    }

    /**
     * @see org.w3c.dom.Node#appendChild(org.w3c.dom.Node)
     */
    public Node appendChild(Node child)
            throws DOMException {
        Node node;
        TransactionManager transact = getBroker().getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            DocumentImpl prevDoc = (DocumentImpl)getOwnerDocument();
            long childGid;
            NodeImplRef last = new NodeImplRef();
            if (children == 0) {
                childGid = firstChildID();
                last.node = this;
            }
            else {
                childGid = lastChildID() + 1;
                last.node = getLastNode((StoredNode) ((DocumentImpl)getOwnerDocument()).getNode(childGid - 1));
            }
            try {
                checkTree(1);
            }
            catch (EXistException e) {
                transact.abort(transaction);
                throw new DOMException(DOMException.INVALID_MODIFICATION_ERR,
                        "max. document size exceeded");
            }
            children++;
            node = appendChild(transaction, childGid, last, getPath(), child, true);
            getBroker().update(transaction, this);
            getBroker().reindex(transaction, prevDoc, ((DocumentImpl)getOwnerDocument()), null);
            getBroker().storeDocument(transaction, ((DocumentImpl)getOwnerDocument()));
            transact.commit(transaction);
            return node;
        } catch (TransactionException e) {
            transact.abort(transaction);
            throw new DOMException(DOMException.INVALID_STATE_ERR, e.getMessage());
        }
    }

    private void checkTree(int size)
            throws EXistException {
        // check if the tree structure needs to be changed
        int level = ((DocumentImpl)getOwnerDocument()).getTreeLevel(getGID());
        if (((DocumentImpl)getOwnerDocument()).getMaxDepth() == level + 1) {
            ((DocumentImpl)getOwnerDocument()).incMaxDepth();
            LOG.debug("setting maxDepth = " + ((DocumentImpl)getOwnerDocument()).getMaxDepth() + "; current = " + level);
        }
        if (((DocumentImpl)getOwnerDocument()).getTreeLevelOrder(level + 1) < children + size) {
            // recompute the order of the tree
            ((DocumentImpl)getOwnerDocument()).setTreeLevelOrder(level + 1, children + size +
                    getBroker().getXUpdateGrowthFactor());
            ((DocumentImpl)getOwnerDocument()).calculateTreeLevelStartPoints(false);
            if (((DocumentImpl)getOwnerDocument()).reindex < 0 || ((DocumentImpl)getOwnerDocument()).reindex > level + 1) {
                ((DocumentImpl)getOwnerDocument()).reindex = level + 1;
            }
        }
    }

    public void appendAttributes(Txn transaction, NodeList attribs)
            throws DOMException {
    	NodeList duplicateAttrs = findDupAttributes(attribs);
    	if(duplicateAttrs != null) {
    		removeAppendAttributes(transaction, duplicateAttrs, attribs);
    	} else {
	        DocumentImpl prevDoc = (DocumentImpl)getOwnerDocument();
	        NodeImplRef last = new NodeImplRef(this);
	        StoredNode lastAttrib = getLastAttribute();
	        if (children == 0) {
	            // no children: append a new child
	            appendChildren(transaction, firstChildID(), last, getPath(), attribs, true);
	        }
	        else {
	            final int level = ((DocumentImpl)getOwnerDocument()).getTreeLevel(getGID());
                ((DocumentImpl)getOwnerDocument()).reindex = level + 1;
	            if (lastAttrib != null && lastAttrib.getGID() == lastChildID()) {
	                last.node = lastAttrib;
	                appendChildren(transaction, lastChildID() + 1, last, getPath(), attribs, true);
	            } else {
	                appendChildren(transaction, firstChildID() + 1, last, getPath(), attribs, true);
	            }
	        }
	        attributes += attribs.getLength();
            getBroker().update(transaction, this);
            getBroker().reindex(transaction, prevDoc, ((DocumentImpl)getOwnerDocument()), null);
    	}
    }

    private NodeList checkForAttributes(Txn transaction, NodeList nodes)
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
            appendAttributes(transaction, attribs);
            return rest;
        }
        else
            return nodes;
    }

    public void appendChildren(Txn transaction, NodeList nodes, int child)
            throws DOMException {
    	// attributes are handled differently. Call checkForAttributes to extract them.
        nodes = checkForAttributes(transaction, nodes);
        if (nodes == null || nodes.getLength() == 0) return;
        DocumentImpl prevDoc = (DocumentImpl)getOwnerDocument();
        if (children == 0) {
            // no children: append a new child
            appendChildren(transaction, firstChildID(), new NodeImplRef(this), getPath(), nodes, true);
        }
        else {
            if (child == 1) {
                Node firstChild = getFirstChild();
                insertBefore(transaction, nodes, firstChild);
            }
            else {
                StoredNode prevNode;
                long pos = firstChildID();
                if (0 < child && child <= children) {
                    pos = firstChildID() + child - 2;
                    prevNode = getLastNode((StoredNode) ((DocumentImpl)getOwnerDocument()).getNode(pos));
                    insertAfter(transaction, nodes, prevNode);
                }
                else {
                    prevNode = getLastNode((StoredNode) ((DocumentImpl)getOwnerDocument()).getNode(lastChildID()));
                    appendChildren(transaction, lastChildID() + 1, new NodeImplRef(prevNode), getPath(), nodes, true);
                }
            }
        }
        getBroker().update(transaction, this);
        getBroker().reindex(transaction, prevDoc, ((DocumentImpl)getOwnerDocument()), null);
    }

    /**
     * Internal append.
     *
     * @return Node
     * @throws DOMException
     */
    protected void appendChildren(Txn transaction, long gid, NodeImplRef last, NodePath lastPath, NodeList nodes, 
            boolean index)
            throws DOMException {
        if (last == null || last.node == null || last.node.getOwnerDocument() == null)
            throw new DOMException(DOMException.INVALID_MODIFICATION_ERR, "invalid node");
        try {
            checkTree(nodes.getLength());
        }
        catch (EXistException e) {
            throw new DOMException(DOMException.INVALID_MODIFICATION_ERR,
                    "max. document size exceeded: " + e.getMessage());
        }
        children += nodes.getLength();
        Node child;
        for (int i = 0; i < nodes.getLength(); i++) {
            child = nodes.item(i);
            if (last.node == null)
                throw new DOMException(DOMException.INVALID_MODIFICATION_ERR,
                        "invalid node: null");
            appendChild(transaction, gid + i, last, lastPath, child, index);
        }
    }

    private Node appendChild(Txn transaction, long gid, NodeImplRef last, NodePath lastPath, Node child, boolean index)
            throws DOMException {
        if (last == null || last.node == null)
            throw new DOMException(DOMException.INVALID_MODIFICATION_ERR, "invalid node");
        String ns, prefix;
        Attr attr;
        switch (child.getNodeType()) {
        		case Node.DOCUMENT_FRAGMENT_NODE:
        		    appendChildren(transaction, gid, last, lastPath, child.getChildNodes(),index);
        		    return null;    // TODO: implement document fragments so we can return all newly appended children
            case Node.ELEMENT_NODE:
                // create new element
                final ElementImpl elem =
                        new ElementImpl(new QName(child.getLocalName() == null ? child.getNodeName() : child.getLocalName(),
                                child.getNamespaceURI(),
                                child.getPrefix()));
                elem.setGID(gid);
                elem.setOwnerDocument(((DocumentImpl)getOwnerDocument()));
                final NodeListImpl ch = new NodeListImpl();
                final NamedNodeMap attribs = child.getAttributes();
                for (int i = 0; i < attribs.getLength(); i++) {
                    attr = (Attr) attribs.item(i);
                    ch.add(attr);
                }
				NodeList cl = child.getChildNodes();
				for (int i = 0; i < cl.getLength(); i++) {
					Node n = cl.item(i);
					if (n.getNodeType() != Node.ATTRIBUTE_NODE)
						ch.add(n);
				}
                elem.setChildCount(ch.getLength());
                elem.setAttributes((short) (elem.getAttributesCount() + attribs.getLength()));
                lastPath.addComponent(elem.getQName());
                // insert the node
                getBroker().insertAfter(transaction, last.node, elem);
                // index now?
                if ((((DocumentImpl)getOwnerDocument()).reindex < 0
                        || ((DocumentImpl)getOwnerDocument()).reindex > ((DocumentImpl)getOwnerDocument()).getTreeLevel(gid))
                        && index)
                    getBroker().index(transaction, elem, lastPath);
                elem.setChildCount(0);
                try {
                    elem.checkTree(ch.getLength());
                }
                catch (EXistException e) {
                    throw new DOMException(DOMException.INVALID_MODIFICATION_ERR,
                            "max. document size exceeded");
                }
                // process child nodes
                last.node = elem;
                elem.appendChildren(transaction, elem.firstChildID(), last, lastPath, ch, index);
                if ((((DocumentImpl)getOwnerDocument()).reindex < 0
                        || ((DocumentImpl)getOwnerDocument()).reindex > ((DocumentImpl)getOwnerDocument()).getTreeLevel(gid))
                        && index)
                    getBroker().endElement(elem, lastPath, null);
                lastPath.removeLastComponent();
                return elem;
            case Node.TEXT_NODE:
                final TextImpl text = new TextImpl(((Text) child).getData());
                text.setGID(gid);
                text.setOwnerDocument(((DocumentImpl)getOwnerDocument()));
                // insert the node
                getBroker().insertAfter(transaction, last.node, text);
                if ((((DocumentImpl)getOwnerDocument()).reindex < 0
                        || ((DocumentImpl)getOwnerDocument()).reindex > ((DocumentImpl)getOwnerDocument()).getTreeLevel(gid))
                        && index)
                    getBroker().index(transaction, text, lastPath);
                last.node = text;
                return text;
            case Node.ATTRIBUTE_NODE:
                attr = (Attr) child;
                ns = attr.getNamespaceURI();
                prefix = (Namespaces.XML_NS.equals(ns) ? "xml" : attr.getPrefix());
                String name = attr.getLocalName();
                if (name == null) name = attr.getName();
                QName attrName =
                        new QName(name, ns, prefix);
                final AttrImpl attrib = new AttrImpl(attrName, attr.getValue());
                attrib.setGID(gid);
                attrib.setOwnerDocument(((DocumentImpl)getOwnerDocument()));
                if (ns != null && attrName.compareTo(Namespaces.XML_ID_QNAME) == Constants.EQUAL) {
					// an xml:id attribute. Normalize the attribute and set its type to ID
					attrib.setValue(StringValue.trimWhitespace(StringValue.collapseWhitespace(attrib.getValue())));
					attrib.setType(AttrImpl.ID);
				}
                getBroker().insertAfter(transaction, last.node, attrib);
                // index now?
                if ((((DocumentImpl)getOwnerDocument()).reindex < 0
                        || ((DocumentImpl)getOwnerDocument()).reindex > ((DocumentImpl)getOwnerDocument()).getTreeLevel(gid))
                        && index) {
                    getBroker().index(transaction, attrib, lastPath);
                }
                last.node = attrib;
                return attrib;
            case Node.COMMENT_NODE:
                final CommentImpl comment = new CommentImpl(((Comment) child).getData());
                comment.setGID(gid);
                comment.setOwnerDocument(((DocumentImpl)getOwnerDocument()));
                // insert the node
                getBroker().insertAfter(transaction, last.node, comment);
                if ((((DocumentImpl)getOwnerDocument()).reindex < 0
                        || ((DocumentImpl)getOwnerDocument()).reindex > ((DocumentImpl)getOwnerDocument()).getTreeLevel(gid))
                        && index)
                    getBroker().index(transaction, comment, lastPath);
                last.node = comment;
                return comment;
            case Node.PROCESSING_INSTRUCTION_NODE:
                final ProcessingInstructionImpl pi =
                        new ProcessingInstructionImpl(gid,
                                ((ProcessingInstruction) child).getTarget(),
                                ((ProcessingInstruction) child).getData());
                pi.setOwnerDocument(((DocumentImpl)getOwnerDocument()));
                //			insert the node
                getBroker().insertAfter(transaction, last.node, pi);
                if ((((DocumentImpl)getOwnerDocument()).reindex < 0
                        || ((DocumentImpl)getOwnerDocument()).reindex > ((DocumentImpl)getOwnerDocument()).getTreeLevel(gid))
                        && index)
                    getBroker().index(transaction, pi, lastPath);
                last.node = pi;
                return pi;
            default :
                throw new DOMException(DOMException.INVALID_MODIFICATION_ERR,
                        "unknown node type: "
                        + child.getNodeType()
                        + " "
                        + child.getNodeName());
        }
    }

	public void setIndexType(int idxType) {
		this.indexType = idxType;
	}
	
	public int getIndexType() {
		return indexType;
	}
	
	public void setPreserveSpace(boolean preserve) {
		this.preserveWS = preserve;
	}
	
	public boolean preserveSpace() {
		return preserveWS;
	}
	
    public void setPosition(int pos) {
        position = pos;
    }
    
    public int getPosition() {
        return position;
    }
    
    public boolean declaresNamespacePrefixes() {
        if (namespaceMappings == null)
            return false;
        return (namespaceMappings.size() > 0);
    }

    /**
     * @see org.exist.dom.NodeImpl#firstChildID()
     */
    public long firstChildID() {
        //TOUNDERSTAND : what are the semantics of this 0 ? -pb
        if (getGID() == 0)
            return 0;
        if (firstChild != StoredNode.NODE_IMPL_UNKNOWN_GID)
            return firstChild;
        firstChild = XMLUtil.getFirstChildId(((DocumentImpl)getOwnerDocument()), getGID());
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
            Node child = ((DocumentImpl)getOwnerDocument()).getNode(i);
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
            Node child = ((DocumentImpl)getOwnerDocument()).getNode(i);
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
            Node child = ((DocumentImpl)getOwnerDocument()).getNode(i);
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
            Node child = ((DocumentImpl)getOwnerDocument()).getNode(i);
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
            Node child = ((DocumentImpl)getOwnerDocument()).getNode(i);
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
        NodeProxy p = new NodeProxy(((DocumentImpl)getOwnerDocument()), getGID(), getInternalAddress());
        Iterator iter = getBroker().getNodeIterator(p);
        iter.next();
        for (long i = start; i < start + attributes && iter.hasNext(); i++) {
            StoredNode child = (StoredNode) iter.next();
            child.setGID(i);
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
        if (getAttributesCount() > 0) {
	        for (long i = start; i < start + children; i++) {
	            Node child = ((DocumentImpl)getOwnerDocument()).getNode(i);
	            if (child != null && child.getNodeType() == Node.ATTRIBUTE_NODE)
	                map.setNamedItem(child);
	        }
        }
        if(declaresNamespacePrefixes()) {
            for(Iterator i = namespaceMappings.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry entry = (Map.Entry) i.next();
                String prefix = entry.getKey().toString();
                String ns = entry.getValue().toString();
                QName attrName = new QName(prefix, "http://www.w3.org/XML/1998/namespace", "xmlns");
                AttrImpl attr = new AttrImpl(attrName, ns);
                map.setNamedItem(attr);
            }
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
            childList.add(((DocumentImpl)getOwnerDocument()).getNode(first));
            return childList;
        }
        NodeList result = ((DocumentImpl)getOwnerDocument()).getRange(first, first + children - 1);
        return result;
    }

    /**
     * @see org.w3c.dom.Element#getElementsByTagName(java.lang.String)
     */
    public NodeList getElementsByTagName(String tagName) {
        QName qname = new QName(tagName, "", null);
        return (NodeSet) ((DocumentImpl)getOwnerDocument()).findElementsByTagName(this, qname);
    }

    /**
     * @see org.w3c.dom.Element#getElementsByTagNameNS(java.lang.String, java.lang.String)
     */
    public NodeList getElementsByTagNameNS(String namespaceURI, String localName) {
        QName qname = new QName(localName, namespaceURI, null);
        return (NodeSet) ((DocumentImpl)getOwnerDocument()).findElementsByTagName(this, qname);
    }

    /**
     * @see org.w3c.dom.Node#getFirstChild()
     */
    public Node getFirstChild() {
        if (!hasChildNodes() || getChildCount() == getAttributesCount())
            return null;
        long first = firstChildID() + getAttributesCount();
        return ((DocumentImpl)getOwnerDocument()).getNode(first);
    }

    /**
     * @see org.w3c.dom.Node#getLastChild()
     */
    public Node getLastChild() {
        if (!hasChildNodes())
            return null;
        return ((DocumentImpl)getOwnerDocument()).getNode(lastChildID());
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
            Node n = ((DocumentImpl)getOwnerDocument()).getNode(first + i);
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
            Node n = ((DocumentImpl)getOwnerDocument()).getNode(first + i);
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
            return StoredNode.NODE_IMPL_UNKNOWN_GID;
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
            if (declaresNamespacePrefixes()) {
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
                        ((DocumentImpl)getOwnerDocument()).getSymbols().getNSSymbol((String) entry.getValue());
                    out.writeShort(nsId);
                }
                prefixData = bout.toByteArray();
            }
            final short id = ((DocumentImpl)getOwnerDocument()).getSymbols().getSymbol(this);
            final boolean hasNamespace = nodeName.needsNamespaceDecl();
            final short nsId =
                    hasNamespace
                    ? ((DocumentImpl)getOwnerDocument()).getSymbols().getNSSymbol(nodeName.getNamespaceURI())
                    : 0;

//            final byte attrSizeType = Signatures.getSizeType(attributes);
            final byte idSizeType = Signatures.getSizeType(id);
            byte signature =
                    (byte) ((Signatures.Elem << 0x5) | idSizeType);
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
                    ByteArrayPool.getByteArray(7
                    + Signatures.getLength(idSizeType)
                    + (hasNamespace ? prefixLen + 4 : 0)
                    + (prefixData != null ? prefixData.length : 0));
            int next = 0;
            data[next++] = signature;
            ByteConversion.intToByte(children, data, next);
            next += 4;
            ByteConversion.shortToByte(attributes, data, next);
            next += 2;
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
            ((DocumentImpl)getOwnerDocument()).getSymbols().getNSSymbol(ns);
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
        StringBuffer buf = new StringBuffer();
        StringBuffer attributes = new StringBuffer();
        StringBuffer children = new StringBuffer();
        buf.append('<');
        buf.append(nodeName);
        if (top) {
            buf.append(" xmlns:exist=\"http://exist.sourceforge.net/NS/exist\"");
            buf.append(" exist:id=\"");
            buf.append(getGID());
            buf.append("\" exist:document=\"");
            buf.append(((DocumentImpl)getOwnerDocument()).getFileName());
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
        if (!(refChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        DocumentImpl prevDoc = (DocumentImpl)getOwnerDocument();
        if (refChild == null)
            return appendChild(newChild);
        StoredNode ref = (StoredNode) refChild;
        long first = firstChildID();
        if (ref.getGID() < first || ref.getGID() > ref.getGID() + children - 1)
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "reference node is not a child of the selected node");
        Node result;
        try {
            TransactionManager transact = getBroker().getBrokerPool().getTransactionManager();
            Txn transaction = transact.beginTransaction();
            if (ref.getGID() == first)
                result = appendChild(null, first, new NodeImplRef(this), getPath(), newChild, false);
            else {
                StoredNode prev = (StoredNode) ref.getPreviousSibling();
                result = appendChild(null, ref.getGID(), new NodeImplRef(getLastNode(prev)), getPath(), newChild, false);
            }
            getBroker().update(null, this);
            getBroker().reindex(null, prevDoc, ((DocumentImpl)getOwnerDocument()), null);
            getBroker().storeDocument(null, ((DocumentImpl)getOwnerDocument()));
            transact.commit(transaction);
            return result;
        } catch(TransactionException e) {
            throw new DOMException(DOMException.NO_MODIFICATION_ALLOWED_ERR, e.getMessage());
        }
    }

    /**
     * Insert a list of nodes at the position before the reference
     * child.
     */
    public void insertBefore(Txn transaction, NodeList nodes, Node refChild)
            throws DOMException {
        if (!(refChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        DocumentImpl prevDoc = (DocumentImpl)getOwnerDocument();
        if (refChild == null) {
            //TODO : use NodeImpl.UNKNOWN_NODE_IMPL_GID ? -pb
            appendChildren(transaction, nodes, -1);
            return;
        }
        StoredNode ref = (StoredNode) refChild;
        final long first = firstChildID();
        if (ref.getGID() < first || ref.getGID() > ref.getGID() + children - 1)
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "reference node is not a child of the selected node");
        final int level = ((DocumentImpl)getOwnerDocument()).getTreeLevel(getGID());
        if (ref.getGID() == first)
            appendChildren(transaction, first, new NodeImplRef(this), getPath(), nodes, false);
        else {
            StoredNode prev = (StoredNode) ref.getPreviousSibling();
            appendChildren(transaction, ref.getGID(), new NodeImplRef(getLastNode(prev)), getPath(), nodes, false);
        }
        getBroker().update(transaction, this);
        if (((DocumentImpl)getOwnerDocument()).reindex > -1) {
            ((DocumentImpl)getOwnerDocument()).reindex = level + 1;
            getBroker().reindex(transaction, prevDoc, ((DocumentImpl)getOwnerDocument()), null);
        }
        else {
            ((DocumentImpl)getOwnerDocument()).reindex = level + 1;
            getBroker().reindex(transaction, prevDoc, ((DocumentImpl)getOwnerDocument()), this);
        }
    }

    /**
     * Insert a list of nodes at the position following the reference
     * child.
     */
    public void insertAfter(Txn transaction, NodeList nodes, Node refChild)
            throws DOMException {
        if (refChild == null) {
            //TODO : use NodeImpl.UNKNOWN_NODE_IMPL_GID ? -pb
            appendChildren(null, nodes, -1);
            return;
        }
        if (!(refChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type: ");
        final DocumentImpl prevDoc = (DocumentImpl)getOwnerDocument();
        final StoredNode ref = (StoredNode) refChild;
        final long first = firstChildID();
        if (ref.getGID() < first || ref.getGID() > ref.getGID() + children - 1)
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "reference node is not a child of the selected node");
        final int level = ((DocumentImpl)getOwnerDocument()).getTreeLevel(getGID());
        appendChildren(transaction, ref.getGID() + 1, new NodeImplRef(getLastNode(ref)), getPath(), nodes, false);
        getBroker().update(transaction, this);
        if (((DocumentImpl)getOwnerDocument()).reindex > -1) {
            ((DocumentImpl)getOwnerDocument()).reindex = level + 1;
            getBroker().reindex(transaction, prevDoc, ((DocumentImpl)getOwnerDocument()), null);
        }
        else {
            ((DocumentImpl)getOwnerDocument()).reindex = level + 1;
            getBroker().reindex(transaction, prevDoc, ((DocumentImpl)getOwnerDocument()), this);
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
        final DocumentImpl prevDoc = (DocumentImpl)getOwnerDocument();
        // remove old child nodes
        NodeList nodes = getChildNodes();
        StoredNode child, last = this;
        long firstChildId = firstChildID();
        int i = nodes.getLength();
        for (; i > 0; i--) {
            child = (StoredNode) nodes.item(i - 1);
            if (child.getNodeType() == Node.ATTRIBUTE_NODE) {
                firstChildId = child.getGID() + 1;
                last = child;
                break;
            }
            if (child.getNodeType() == Node.ELEMENT_NODE)
                path.addComponent(child.getQName());
            getBroker().removeAll(transaction, child, path);
            if (child.getNodeType() == Node.ELEMENT_NODE)
                path.removeLastComponent();
        }
        getBroker().endRemove();
        children = i;
        // append new content
        appendChildren(transaction, firstChildId, new NodeImplRef(last), getPath(), newContent, true);
        getBroker().update(transaction, this);
        // reindex if required
        getBroker().reindex(transaction, prevDoc, (DocumentImpl)getOwnerDocument(), null);
    }

    /**
     * Update a child node. This method will only update the child node
     * but not its potential descendant nodes.
     *
     * @param oldChild
     * @param newChild
     * @throws DOMException
     */
    public void updateChild(Txn transaction, Node oldChild, Node newChild)
            throws DOMException {
        if (!(oldChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        if (!(newChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        StoredNode old = (StoredNode) oldChild;
        StoredNode newNode = (StoredNode) newChild;
        if (old.getParentGID() != getGID())
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
        
        StoredNode previous = (StoredNode) old.getPreviousSibling();
        if (previous == null)
            previous = this;
        else
            previous = getLastNode(previous);
        getBroker().removeNode(transaction, old, old.getPath(), null);
        getBroker().endRemove();
        newNode.setGID(old.getGID());
        getBroker().insertAfter(transaction, previous, newNode);
        NodePath path = newNode.getPath();
        getBroker().index(transaction, newNode, path);
		if (newNode.getNodeType() == Node.ELEMENT_NODE)
            getBroker().endElement(newNode, path, null);
        getBroker().flush();
    }
    
    /**
     * @see org.w3c.dom.Node#removeChild(org.w3c.dom.Node)
     */
    public Node removeChild(Txn transaction, Node oldChild)
            throws DOMException {
        if (!(oldChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        StoredNode old = (StoredNode) oldChild;
        if (old.getParentGID() != getGID())
            throw new DOMException(DOMException.NOT_FOUND_ERR,
                    "node is not a child of this element");
        final int level = ((DocumentImpl)getOwnerDocument()).getTreeLevel(getGID());
        final DocumentImpl prevDoc = (DocumentImpl)getOwnerDocument();
        final long lastChild = lastChildID();
//        removeAll(transaction, old, old.getPath());
        getBroker().removeAll(transaction, old, old.getPath());
        --children;
        getBroker().endRemove();
        getBroker().update(transaction, this);
        if (old.getGID() < lastChild) {
            ((DocumentImpl)getOwnerDocument()).reindex = level + 1;
            getBroker().reindex(transaction, prevDoc, ((DocumentImpl)getOwnerDocument()), this);
        }
//		ownerDocument.broker.checkTree(ownerDocument);
        return old;
    }

    private void removeAll(Txn transaction, StoredNode node, NodePath currentPath) {
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
				String content = null;
				IndexSpec idxSpec = 
                    ((DocumentImpl)getOwnerDocument()).getCollection().getIdxConf(((DocumentImpl)getOwnerDocument()).broker);
				if (idxSpec != null) {
					GeneralRangeIndexSpec spec = idxSpec.getIndexByPath(currentPath);
					RangeIndexSpec qnIdx = idxSpec.getIndexByQName(node.getQName());
					if (spec != null || qnIdx != null) {
						NodeProxy p = new NodeProxy((DocumentImpl)node.getOwnerDocument(), node.getGID(), node.getInternalAddress());
						content = getBroker().getNodeValue(p, false);
					}
				}
                NodeList children = node.getChildNodes();
                StoredNode child;
                for (int i = children.getLength() - 1; i >= 0; i--) {
                    child = (StoredNode) children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        currentPath.addComponent(((ElementImpl) child).getQName());
                        removeAll(transaction, child, currentPath);
                        currentPath.removeLastComponent();
                    }
                    else
                        removeAll(transaction, child, currentPath);
                }
                getBroker().removeNode(transaction, node, currentPath, content);
                break;
            default :
                getBroker().removeNode(transaction, node, currentPath, null);
                break;
            //TODO : manage unknown type ! -pb
        }
    }
	
	public void removeAppendAttributes(Txn transaction, NodeList removeList, NodeList appendList) {
		final int level = ((DocumentImpl)getOwnerDocument()).getTreeLevel(getGID());
		final DocumentImpl prevDoc = (DocumentImpl)getOwnerDocument();
		final long lastChild = lastChildID();
		
		try {
			try {
				for (int i=0; i<removeList.getLength(); i++) {
					Node oldChild = removeList.item(i);
					if (!(oldChild instanceof StoredNode))
						throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
					StoredNode old = (StoredNode) oldChild;
					if (old.getParentGID() != getGID())
						throw new DOMException(DOMException.NOT_FOUND_ERR, "node is not a child of this element");
                    getBroker().removeNode(transaction, old, old.getPath(), null);
					if(old.getGID() < lastChild) ((DocumentImpl)getOwnerDocument()).reindex = level + 1;
					children--;
					attributes--;
				}
			} finally {
                getBroker().endRemove();
			}
			
			if (children == 0) {
			   appendChildren(transaction, firstChildID(), new NodeImplRef(this), getPath(), appendList, true);
			} else {
			    StoredNode lastAttrib = getLastAttribute();
			    if (lastAttrib != null && lastAttrib.getGID() == lastChildID())
			        appendChildren(transaction, lastChildID() + 1, new NodeImplRef(lastAttrib), getPath(), appendList, true);
			    else
			        appendChildren(transaction, firstChildID() + 1, new NodeImplRef(this), getPath(), appendList, true);
			}
			attributes += appendList.getLength();
		} finally {
            getBroker().update(transaction, this);
            getBroker().reindex(transaction, prevDoc, ((DocumentImpl)getOwnerDocument()), null);
		}
	}

    /* (non-Javadoc)
     * @see org.w3c.dom.Node#replaceChild(org.w3c.dom.Node, org.w3c.dom.Node)
     */
    public Node replaceChild(Txn transaction, Node newChild, Node oldChild)
            throws DOMException {
        if (!(oldChild instanceof StoredNode))
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong node type");
        StoredNode old = (StoredNode) oldChild;
        if (old.getParentGID() != getGID())
            throw new DOMException(DOMException.NOT_FOUND_ERR,
                    "node is not a child of this element");
        final DocumentImpl prevDoc = (DocumentImpl)getOwnerDocument();
        StoredNode previous = (StoredNode) old.getPreviousSibling();
        if (previous == null)
            previous = this;
        else
            previous = getLastNode(previous);
        getBroker().removeAll(transaction, old, old.getPath());
        getBroker().endRemove();
        appendChild(transaction, old.getGID(), new NodeImplRef(previous), getPath(), newChild, true);
        // reindex if required
        getBroker().reindex(transaction, prevDoc, ((DocumentImpl)getOwnerDocument()), null);
        getBroker().storeDocument(transaction, ((DocumentImpl)getOwnerDocument()));
        return oldChild;	// method is spec'd to return the old child, even though that's probably useless in this case
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

	/** ? @see org.w3c.dom.Element#getSchemaTypeInfo()
	 */
	public TypeInfo getSchemaTypeInfo() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Element#setIdAttribute(java.lang.String, boolean)
	 */
	public void setIdAttribute(String name, boolean isId) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Element#setIdAttributeNS(java.lang.String, java.lang.String, boolean)
	 */
	public void setIdAttributeNS(String namespaceURI, String localName, boolean isId) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Element#setIdAttributeNode(org.w3c.dom.Attr, boolean)
	 */
	public void setIdAttributeNode(Attr idAttr, boolean isId) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Node#getBaseURI()
	 */
	public String getBaseURI() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#compareDocumentPosition(org.w3c.dom.Node)
	 */
	public short compareDocumentPosition(Node other) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		return Constants.EQUAL;
	}

	/** ? @see org.w3c.dom.Node#getTextContent()
	 */
	public String getTextContent() throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#setTextContent(java.lang.String)
	 */
	public void setTextContent(String textContent) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Node#isSameNode(org.w3c.dom.Node)
	 */
	public boolean isSameNode(Node other) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return false;
	}

	/** ? @see org.w3c.dom.Node#lookupPrefix(java.lang.String)
	 */
	public String lookupPrefix(String namespaceURI) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#isDefaultNamespace(java.lang.String)
	 */
	public boolean isDefaultNamespace(String namespaceURI) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return false;
	}

	/** ? @see org.w3c.dom.Node#lookupNamespaceURI(java.lang.String)
	 */
	public String lookupNamespaceURI(String prefix) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#isEqualNode(org.w3c.dom.Node)
	 */
	public boolean isEqualNode(Node arg) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return false;
	}

	/** ? @see org.w3c.dom.Node#getFeature(java.lang.String, java.lang.String)
	 */
	public Object getFeature(String feature, String version) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#setUserData(java.lang.String, java.lang.Object, org.w3c.dom.UserDataHandler)
	 */
	public Object setUserData(String key, Object data, UserDataHandler handler) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Node#getUserData(java.lang.String)
	 */
	public Object getUserData(String key) {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}
}
