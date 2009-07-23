/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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

import org.exist.dom.QName;
import org.exist.dom.QNameable;
import org.exist.numbering.NodeId;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.TypeInfo;

public class AttributeImpl extends NodeImpl implements Attr, QNameable {

    public static final int ATTR_CDATA_TYPE = 0;
    public static final int ATTR_ID_TYPE = 1;
    public static final int ATTR_IDREF_TYPE = 2;
    public static final int ATTR_IDREFS_TYPE = 3;
    
    /**
	 * @param doc
	 * @param nodeNumber
	 */
	public AttributeImpl(DocumentImpl doc, int nodeNumber) {
		super(doc, nodeNumber);
	}

    public NodeId getNodeId() {
        return document.attrNodeId[nodeNumber];
    }

    public QName getQName() {
		return (QName)document.namePool.get(document.attrName[nodeNumber]);
	}
	
	/* (non-Javadoc)
	 * @see org.w3c.dom.Attr#getName()
	 */
	public String getName() {
		return getQName().getStringValue();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeName()
	 */
	public String getNodeName() {
		return getQName().getStringValue();
	}

	
    /* (non-Javadoc)
     * @see org.w3c.dom.Node#getNodeType()
     */
    public short getNodeType() {
        return Node.ATTRIBUTE_NODE;
    }
    
    
    /* (non-Javadoc)
     * @see org.exist.memtree.NodeImpl#getType()
     */
    public int getType() {
        return Type.ATTRIBUTE;
    }
    
	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getLocalName()
	 */
	public String getLocalName() {
		return getQName().getLocalName();
	}
	
	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return getQName().getNamespaceURI();
	}
	
	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getPrefix()
	 */
	public String getPrefix() {
		return getQName().getPrefix();
	}
	
	public Node getFirstChild() {
		return null;
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
	 * @see org.w3c.dom.Node#getNodeValue()
	 */
	public String getNodeValue() throws DOMException {
		return document.attrValue[nodeNumber];
	}

	public String getStringValue() throws DOMException {
		return document.attrValue[nodeNumber];
	}
	
	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setNodeValue(java.lang.String)
	 */
	public void setNodeValue( String arg0 ) throws DOMException {
		// This method was added to enable the SQL XQuery Exztension Module to change the value of an attribute after the fact - Andrzej
		document.attrValue[nodeNumber] = arg0;
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

    public void selectDescendantAttributes(NodeTest test, Sequence result) throws XPathException {
        if (test.matches(this))
            result.add(this);
    }

    /* (non-Javadoc)
	 * @see org.w3c.dom.Node#getParentNode()
	 */
	public Node getParentNode() {
        int parent = document.attrParent[nodeNumber];
        if (parent > 0)
            return document.getNode(parent);
        return null;
    }

    public Node selectParentNode() {
        return getParentNode();
    }

    /** ? @see org.w3c.dom.Attr#getSchemaTypeInfo()
	 */
	public TypeInfo getSchemaTypeInfo() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Attr#isId()
	 */
	public boolean isId() {
		return document.attrType[nodeNumber] == ATTR_ID_TYPE;
	}
	
	public int getItemType() {
		return Type.ATTRIBUTE;
	}    
	
	public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("in-memory#");
    	result.append("attribute {");
    	result.append(getQName().getStringValue());
    	result.append("} {");        
    	result.append(getValue().toString());        
    	result.append("} ");    	
    	return result.toString();
    }    
}
