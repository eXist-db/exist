/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
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
 *  $Id:
 * 
 */
package org.exist.dom;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.DOMException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collection;
import org.exist.util.*;
import org.exist.storage.*;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    30. Juni 2002
 */
public class ElementImpl extends NodeImpl implements Element {

	protected int children = 0;
	protected long firstChild = -1;
	protected boolean loaded = false;
	protected ArrayList prefixes = null;

	/**  Constructor for the ElementImpl object */
	public ElementImpl() {
		super();
	}

	/**
	 *  Constructor for the ElementImpl object
	 *
	 *@param  gid  Description of the Parameter
	 */
	public ElementImpl(long gid) {
		super(Node.ELEMENT_NODE, gid);
	}

	/**
	 *  Constructor for the ElementImpl object
	 *
	 *@param  nodeName  Description of the Parameter
	 */
	public ElementImpl(String nodeName) {
		super(Node.ELEMENT_NODE, nodeName);
		loaded = true;
	}

	/**
	 *  Constructor for the ElementImpl object
	 *
	 *@param  gid       Description of the Parameter
	 *@param  nodeName  Description of the Parameter
	 */
	public ElementImpl(long gid, String nodeName) {
		super(Node.ELEMENT_NODE, nodeName, gid);
		loaded = true;
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
		loaded = false;
		children = 0;
		if (prefixes != null)
			prefixes = null;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  data  Description of the Parameter
	 *@param  doc   Description of the Parameter
	 *@return       Description of the Return Value
	 */
	public static NodeImpl deserialize(byte[] data, DocumentImpl doc) {
		byte attrSizeType = (byte) ((data[0] & 0x0C) >> 0x2);
		byte idSizeType = (byte) (data[0] & 0x03);
		int children = ByteConversion.byteToInt(data, 1);
		short attributes = (short) Signatures.read(attrSizeType, data, 5);
		int next = 5 + Signatures.getLength(attrSizeType);
		short id = (short) Signatures.read(idSizeType, data, next);
		String name = doc.getSymbols().getName(id);
		ElementImpl node = new ElementImpl(0, name);
		node.children = children;
		node.attributes = attributes;
		node.ownerDocument = doc;
		next += Signatures.getLength(idSizeType);
		if (data.length > next) {
			byte[] pfxData = new byte[data.length - next];
			System.arraycopy(data, next, pfxData, 0, data.length - next);
			ByteArrayInputStream bin = new ByteArrayInputStream(pfxData);
			DataInputStream in = new DataInputStream(bin);
			try {
				short prefixCount = in.readShort();
				String prefix;
				for (int i = 0; i < prefixCount; i++) {
					prefix = in.readUTF();
					node.addNamespacePrefix(prefix);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return node;
	}

	public int getSymbol() {
		return ownerDocument.getSymbols().getSymbol(this);
	}

	/**
	 *  Adds a feature to the NamespacePrefix attribute of the DocumentImpl
	 *  object
	 *
	 *@param  prefix  The feature to be added to the NamespacePrefix attribute
	 */
	public void addNamespacePrefix(String prefix) {
		if (prefix == null)
			return;
		if (prefixes == null)
			prefixes = new ArrayList(1);

		String temp;
		for (Iterator i = prefixes.iterator(); i.hasNext();)
			if (((String) i.next()).equals(prefix))
				return;

		prefixes.add(prefix);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  child             Description of the Parameter
	 *@exception  DOMException  Description of the Exception
	 */
	public void appendChildInternal(NodeImpl child) throws DOMException {
		if (gid > 0)
			child.setGID(firstChildID() + children);
		else
			child.setGID(0);
		++children;
	}

	public Node appendChild(Node child) throws DOMException {
		System.out.println("append Child called");
		if(!(child instanceof NodeImpl))
			throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "wrong implementation");
		NodeImpl last = (NodeImpl) getLastChild();
		++children;
		int level = ownerDocument.getTreeLevel(gid);
		if (ownerDocument.getTreeLevelOrder(level + 1) < children)
			ownerDocument.setTreeLevelOrder(level + 1, children);
		((NodeImpl)child).setGID(lastChildID());
		ownerDocument.broker.insertAfter(last, (NodeImpl)child);
		return child;
	}

	public String getNamespaceURI() {
		if (nodeName != null && nodeName.indexOf(':') < 0 &&
			declaresNamespacePrefixes()) {
			// check for default namespaces
			String ns;
			for (Iterator i = prefixes.iterator(); i.hasNext();) {
				ns = (String) i.next();
				if (ns.startsWith("#"))
					return ownerDocument.broker.getNamespaceURI(ns);
			}
		}
		if (nodeName != null && nodeName.indexOf(':') > -1) {
			String prefix = nodeName.substring(0, nodeName.indexOf(':'));
			if (!prefix.equals("xml")) {
				return ownerDocument.broker.getNamespaceURI(prefix);
			}
		}
		return "";
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public boolean declaresNamespacePrefixes() {
		return prefixes != null && prefixes.size() > 0;
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public long firstChildID() {
		if (gid == 0)
			return 0;
		if (firstChild > -1)
			return firstChild;
		firstChild = XMLUtil.getFirstChildId(ownerDocument, gid);
		return firstChild;
	}

	/**
	 *  Gets the attribute attribute of the ElementImpl object
	 *
	 *@param  name  Description of the Parameter
	 *@return       The attribute value
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
	 *  Gets the attributeNS attribute of the ElementImpl object
	 *
	 *@param  namespaceURI  Description of the Parameter
	 *@param  localName     Description of the Parameter
	 *@return               The attributeNS value
	 */
	public String getAttributeNS(String namespaceURI, String localName) {
		return getAttribute(localName);
	}

	/**
	 *  Gets the attributeNode attribute of the ElementImpl object
	 *
	 *@param  name  Description of the Parameter
	 *@return       The attributeNode value
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
	 *  Gets the attributeNodeNS attribute of the ElementImpl object
	 *
	 *@param  namespaceURI  Description of the Parameter
	 *@param  localName     Description of the Parameter
	 *@return               The attributeNodeNS value
	 */
	public Attr getAttributeNodeNS(String namespaceURI, String localName) {
		return getAttributeNode(localName);
	}

	/**
	 *  Gets the attributes attribute of the ElementImpl object
	 *
	 *@return    The attributes value
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
	 *  Gets the childCount attribute of the ElementImpl object
	 *
	 *@return    The childCount value
	 */
	public int getChildCount() {
		return children;
	}

	/**
	 *  Gets the childNodes attribute of the ElementImpl object
	 *
	 *@return    The childNodes value
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
		ownerDocument.broker.setRetrvMode(RelationalBroker.PRELOAD);
		NodeList result = ownerDocument.getRange(first, first + children - 1);
		ownerDocument.broker.setRetrvMode(RelationalBroker.SINGLE);
		return result;
	}

	/**
	 *  Gets the elementsByTagName attribute of the ElementImpl object
	 *
	 *@param  tagName  Description of the Parameter
	 *@return          The elementsByTagName value
	 */
	public NodeList getElementsByTagName(String tagName) {
		return (NodeSet) ownerDocument.findElementsByTagName(this, tagName);
	}

	/**
	 *  Gets the elementsByTagNameNS attribute of the ElementImpl object
	 *
	 *@param  namespaceURI  Description of the Parameter
	 *@param  localName     Description of the Parameter
	 *@return               The elementsByTagNameNS value
	 */
	public NodeList getElementsByTagNameNS(
		String namespaceURI,
		String localName) {
		String prefix = ownerDocument.broker.getNamespacePrefix(namespaceURI);
		String qname = (prefix != null) ? prefix + ':' + localName : localName;
		return getElementsByTagName(qname);
	}

	/**
	 *  Gets the firstChild attribute of the ElementImpl object
	 *
	 *@return    The firstChild value
	 */
	public Node getFirstChild() {
		if (!hasChildNodes())
			return null;
		long first = firstChildID() + getAttributesCount();
		return ownerDocument.getNode(first);
		/*
		 *  long last = first + children + 1;
		 *  Node n = ownerDocument.getNode(first);
		 *  while(n.getNodeType() == Node.ATTRIBUTE_NODE &&
		 *  first <= last)
		 *  n = ownerDocument.getNode(++first);
		 *  return  first == last ? null : n;
		 */
	}

	/**
	 *  Gets the lastChild attribute of the ElementImpl object
	 *
	 *@return    The lastChild value
	 */
	public Node getLastChild() {
		if (!hasChildNodes())
			return null;
		return ownerDocument.getNode(lastChildID());
	}

	/**
	 *  Gets the namespacePrefixes attribute of the DocumentImpl object
	 *
	 *@return    The namespacePrefixes value
	 */
	public Iterator getNamespacePrefixes() {
		return prefixes == null ? null : prefixes.iterator();
	}

	/**
	 *  Gets the nodeName attribute of the ElementImpl object
	 *
	 *@return    The nodeName value
	 */
	public String getNodeName() {
		if (!loaded)
			loaded = ownerDocument.broker.elementWith(this);

		return nodeName;
	}

	/**
	 *  Gets the nodeValue attribute of the ElementImpl object
	 *
	 *@return                   The nodeValue value
	 *@exception  DOMException  Description of the Exception
	 */
	public String getNodeValue() throws DOMException {
		return null;
		//        if ( !loaded )
		//            loaded = ownerDocument.broker.elementWith( this );
		//
		//        StringBuffer buf = new StringBuffer();
		//        long start = firstChildID();
		//        Node child;
		//        String childData;
		//        for ( long i = start; i < start + children; i++ ) {
		//            child = ownerDocument.getNode( i );
		//            if ( child.getNodeType() == Node.TEXT_NODE ||
		//                child.getNodeType() == Node.ELEMENT_NODE ) {
		//                childData = child.getNodeValue();
		//                if ( childData != null )
		//                    buf.append( childData );
		//
		//            }
		//        }
		//        return buf.toString();
	}

	/**
	 *  Gets the tagName attribute of the ElementImpl object
	 *
	 *@return    The tagName value
	 */
	public String getTagName() {
		if (!loaded)
			loaded = ownerDocument.broker.elementWith(this);

		return nodeName;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  name  Description of the Parameter
	 *@return       Description of the Return Value
	 */
	public boolean hasAttribute(String name) {
		long first = firstChildID();
		for (int i = 0; i < children; i++) {
			Node n = ownerDocument.getNode(first + i);
			if (n.getNodeType() == Node.ATTRIBUTE_NODE
				&& n.getNodeName().equals(name))
				return true;
		}
		return false;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  namespaceURI  Description of the Parameter
	 *@param  localName     Description of the Parameter
	 *@return               Description of the Return Value
	 */
	public boolean hasAttributeNS(String namespaceURI, String localName) {
		return hasAttribute(localName);
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public boolean hasAttributes() {
		return (getAttributesCount() > 0);
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public boolean hasChildNodes() {
		if (children > 0)
			return true;
		return false;
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public long lastChildID() {
		if (!hasChildNodes())
			return -1;
		return firstChildID() + children - 1;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  name              Description of the Parameter
	 *@exception  DOMException  Description of the Exception
	 */
	public void removeAttribute(String name) throws DOMException {
	}

	/**
	 *  Description of the Method
	 *
	 *@param  namespaceURI      Description of the Parameter
	 *@param  name              Description of the Parameter
	 *@exception  DOMException  Description of the Exception
	 */
	public void removeAttributeNS(String namespaceURI, String name)
		throws DOMException {
	}

	/**
	 *  Description of the Method
	 *
	 *@param  oldAttr           Description of the Parameter
	 *@return                   Description of the Return Value
	 *@exception  DOMException  Description of the Exception
	 */
	public Attr removeAttributeNode(Attr oldAttr) throws DOMException {
		return null;
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public byte[] serialize() {
		try {
			byte[] prefixData = null;
			// serialize namespace prefixes declared in this element
			if (prefixes != null && prefixes.size() > 0) {
				ByteArrayOutputStream bout = new ByteArrayOutputStream();
				DataOutputStream out = new DataOutputStream(bout);
				out.writeShort(prefixes.size());
				String pfx;
				for (Iterator i = prefixes.iterator(); i.hasNext();) {
					pfx = (String) i.next();
					out.writeUTF(pfx);
				}
				prefixData = bout.toByteArray();
			}
			final short id = ownerDocument.getSymbols().getSymbol(this);
			final byte attrSizeType = Signatures.getSizeType(attributes);
			final byte idSizeType = Signatures.getSizeType(id);
			final byte signature =
				(byte) ((Signatures.Elem << 0x5)
					| (attrSizeType << 0x2)
					| idSizeType);
			final byte[] data =
				new byte[5
					+ Signatures.getLength(attrSizeType)
					+ Signatures.getLength(idSizeType)
					+ (prefixData != null ? prefixData.length : 0)];
			data[0] = signature;
			ByteConversion.intToByte(children, data, 1);
			Signatures.write(attrSizeType, attributes, data, 5);
			Signatures.write(
				idSizeType,
				id,
				data,
				5 + Signatures.getLength(attrSizeType));
			if (prefixData != null)
				System.arraycopy(
					prefixData,
					0,
					data,
					5
						+ Signatures.getLength(attrSizeType)
						+ Signatures.getLength(idSizeType),
					prefixData.length);

			return data;
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 *  Sets the attribute attribute of the ElementImpl object
	 *
	 *@param  name              The new attribute value
	 *@param  value             The new attribute value
	 *@exception  DOMException  Description of the Exception
	 */
	public void setAttribute(String name, String value) throws DOMException {
	}

	/**
	 *  Sets the attributeNS attribute of the ElementImpl object
	 *
	 *@param  namespaceURI      The new attributeNS value
	 *@param  qualifiedName     The new attributeNS value
	 *@param  value             The new attributeNS value
	 *@exception  DOMException  Description of the Exception
	 */
	public void setAttributeNS(
		String namespaceURI,
		String qualifiedName,
		String value)
		throws DOMException {
	}

	/**
	 *  Sets the attributeNode attribute of the ElementImpl object
	 *
	 *@param  newAttr           The new attributeNode value
	 *@return                   Description of the Return Value
	 *@exception  DOMException  Description of the Exception
	 */
	public Attr setAttributeNode(Attr newAttr) throws DOMException {
		return null;
	}

	/**
	 *  Sets the attributeNodeNS attribute of the ElementImpl object
	 *
	 *@param  newAttr  The new attributeNodeNS value
	 *@return          Description of the Return Value
	 */
	public Attr setAttributeNodeNS(Attr newAttr) {
		return null;
	}

	/**
	 *  Sets the childCount attribute of the ElementImpl object
	 *
	 *@param  count  The new childCount value
	 */
	public void setChildCount(int count) {
		children = count;
	}

	/**
	 *  Sets the nodeName attribute of the ElementImpl object
	 *
	 *@param  name  The new nodeName value
	 */
	public void setNodeName(String name) {
		loaded = true;
		nodeName = name;
	}

	/**
	 *  Sets the prefixes attribute of the ElementImpl object
	 *
	 *@param  pfx  The new prefixes value
	 */
	public void setPrefixes(Collection pfx) {
		if (prefixes == null)
			prefixes = new ArrayList(pfx.size());

		this.prefixes.addAll(pfx);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  contentHandler    Description of the Parameter
	 *@param  lexicalHandler    Description of the Parameter
	 *@param  first             Description of the Parameter
	 *@param  prefixes          Description of the Parameter
	 *@exception  SAXException  Description of the Exception
	 */
	public void toSAX(
		ContentHandler contentHandler,
		LexicalHandler lexicalHandler,
		boolean first,
		ArrayList prefixes)
		throws SAXException {
		if (!loaded)
			loaded = ownerDocument.broker.elementWith(this);

		NodeList childNodes = getChildNodes();
		NodeImpl child = null;
		DBBroker broker = ownerDocument.getBroker();
		AttributesImpl attributes = new AttributesImpl();
		ArrayList myPrefixes = null;
		String defaultNS = null;
		if (declaresNamespacePrefixes()) {
			// declare namespaces used by this element
			String prefix;
			myPrefixes = new ArrayList();
			for (Iterator i = getNamespacePrefixes(); i.hasNext();) {
				prefix = (String) i.next();
				if (!prefixes.contains(prefix)) {
					if (prefix.startsWith("#")) {
						defaultNS = broker.getNamespaceURI(prefix);
						contentHandler.startPrefixMapping(
							"",
							defaultNS);
					} else
						contentHandler.startPrefixMapping(
							prefix,
							broker.getNamespaceURI(prefix));

					prefixes.add(prefix);
					myPrefixes.add(prefix);
				}
			}
		}
		if (first) {
			attributes.addAttribute(
				"http://exist.sourceforge.net/NS/exist",
				"id",
				"exist:id",
				"CDATA",
				Long.toString(gid));
			attributes.addAttribute(
				"http://exist.sourceforge.net/NS/exist",
				"source",
				"exist:source",
				"CDATA",
				ownerDocument.getFileName());
		}
		int i = 0;
		while (i < childNodes.getLength()) {
			child = (NodeImpl) childNodes.item(i);
			if (child.getNodeType() == Node.ATTRIBUTE_NODE) {
				attributes.addAttribute(
					child.getNamespaceURI(),
					child.getLocalName(),
					child.getNodeName(),
					"CDATA",
					((AttrImpl) child).getValue());
				i++;
			} else
				break;
		}
		String ns = defaultNS == null ? getNamespaceURI() :
			defaultNS; 
		contentHandler.startElement(
			ns,
			getLocalName(),
			getNodeName(),
			attributes);
		while (i < childNodes.getLength()) {
			child.toSAX(contentHandler, lexicalHandler, false, prefixes);
			i++;
			if (i < childNodes.getLength())
				child = (NodeImpl) childNodes.item(i);
			else
				break;
		}
		contentHandler.endElement(
			ns,
			getLocalName(),
			getNodeName());
		if (declaresNamespacePrefixes() && myPrefixes != null) {
			String prefix;
			for (Iterator pi = myPrefixes.iterator(); pi.hasNext();) {
				prefix = (String) pi.next();
				contentHandler.endPrefixMapping(prefix);
				prefixes.remove(prefix);
			}
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public String toString() {
		return toString(true);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  top  Description of the Parameter
	 *@return      Description of the Return Value
	 */
	public String toString(boolean top) {
		return toString(top, new ArrayList(5));
	}

	/**
	 *  Description of the Method
	 *
	 *@param  top       Description of the Parameter
	 *@param  prefixes  Description of the Parameter
	 *@return           Description of the Return Value
	 */
	public String toString(boolean top, ArrayList prefixes) {
		if (!loaded)
			loaded = ownerDocument.broker.elementWith(this);

		DBBroker broker = ownerDocument.getBroker();
		StringBuffer buf = new StringBuffer();
		StringBuffer attributes = new StringBuffer();
		StringBuffer children = new StringBuffer();
		buf.append('<');
		buf.append(nodeName);
		if (top) {
			buf.append(
				" xmlns:exist=\"http://exist.sourceforge.net/NS/exist\"");
			buf.append(" exist:id=\"");
			buf.append(gid);
			buf.append("\" exist:document=\"");
			buf.append(ownerDocument.getFileName());
			buf.append("\"");
		}
		ArrayList myPrefixes = null;
		if (declaresNamespacePrefixes()) {
			// declare namespaces used by this element
			String prefix;
			myPrefixes = new ArrayList();
			for (Iterator i = getNamespacePrefixes(); i.hasNext();) {
				prefix = (String) i.next();
				if (!prefixes.contains(prefix)) {
					if (prefix.startsWith("#")) {
						buf.append("xmlns=\"");
						buf.append(broker.getNamespaceURI(prefix));
					} else {
						buf.append("xmlns:");
						buf.append(prefix);
						buf.append("=\"");
						buf.append(broker.getNamespaceURI(prefix));
					}
					buf.append("\" ");
					prefixes.add(prefix);
					myPrefixes.add(prefix);
				}
			}
		}
		NodeList childNodes = getChildNodes();
		Node child;
		for (int i = 0; i < childNodes.getLength(); i++) {
			child = childNodes.item(i);
			switch (child.getNodeType()) {
				case Node.ATTRIBUTE_NODE :
					attributes.append(' ');
					attributes.append(((Attr) child).getName());
					attributes.append("=\"");
					attributes.append(((Attr) child).getValue());
					attributes.append("\"");
					break;
				case Node.ELEMENT_NODE :
					children.append(
						((ElementImpl) child).toString(false, prefixes));
					break;
				default :
					children.append(child.toString());
			}
		}
		if (attributes.length() > 0)
			buf.append(attributes);

		if (childNodes.getLength() > 0) {
			buf.append(">");
			buf.append(children);
			buf.append("</");
			buf.append(nodeName);
			buf.append(">");
		} else
			buf.append("/>");

		return buf.toString();
	}

}
