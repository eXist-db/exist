/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
 */
package org.exist.memtree;

import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AttributeImpl extends NodeImpl implements Attr {

	/**
	 * @param doc
	 * @param nodeNumber
	 */
	public AttributeImpl(DocumentImpl doc, int nodeNumber) {
		super(doc, nodeNumber);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Attr#getName()
	 */
	public String getName() {
		return document.attrName[nodeNumber].toString();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeName()
	 */
	public String getNodeName() {
		return document.attrName[nodeNumber].toString();
	}
	
	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getLocalName()
	 */
	public String getLocalName() {
		return document.attrName[nodeNumber].getLocalName();
	}
	
	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return document.attrName[nodeNumber].getNamespaceURI();
	}
	
	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getPrefix()
	 */
	public String getPrefix() {
		return document.attrName[nodeNumber].getPrefix();
	}
	
	/* (non-Javadoc)
	 * @see org.w3c.dom.Attr#getSpecified()
	 */
	public boolean getSpecified() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Attr#getValue()
	 */
	public String getValue() {
		return document.attrValue[nodeNumber];
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Attr#setValue(java.lang.String)
	 */
	public void setValue(String arg0) throws DOMException {
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Attr#getOwnerElement()
	 */
	public Element getOwnerElement() {
		return (Element)document.getNode(document.attrParent[nodeNumber]);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getParentNode()
	 */
	public Node getParentNode() {
		return null;
	}
}
