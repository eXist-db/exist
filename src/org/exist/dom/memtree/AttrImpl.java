/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.dom.memtree;

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


public class AttrImpl extends NodeImpl implements Attr {

    public static final int ATTR_CDATA_TYPE = 0;
    public static final int ATTR_ID_TYPE = 1;
    public static final int ATTR_IDREF_TYPE = 2;
    public static final int ATTR_IDREFS_TYPE = 3;

    /**
     * Creates a new AttributeImpl object.
     *
     * @param doc
     * @param nodeNumber
     */
    public AttrImpl(final DocumentImpl doc, final int nodeNumber) {
        super(doc, nodeNumber);
    }

    @Override
    public NodeId getNodeId() {
        return document.attrNodeId[nodeNumber];
    }

    @Override
    public String getName() {
        return getQName().getStringValue();
    }

    @Override
    public short getNodeType() {
        return Node.ATTRIBUTE_NODE;
    }

    @Override
    public int getType() {
        return Type.ATTRIBUTE;
    }

    @Override
    public String getBaseURI() {
        final Node parent = document.getNode(document.attrParent[nodeNumber]);
        if(parent == null) {
            return null;
        }
        return parent.getBaseURI();
    }

    @Override
    public Node getFirstChild() {
        return null;
    }

    @Override
    public boolean getSpecified() {
        return true;
    }

    @Override
    public String getValue() {
        return document.attrValue[nodeNumber];
    }

    @Override
    public String getNodeValue() throws DOMException {
        return document.attrValue[nodeNumber];
    }

    @Override
    public String getStringValue() throws DOMException {
        return document.attrValue[nodeNumber];
    }

    @Override
    public void setNodeValue(final String nodeValue) throws DOMException {
        //This method was added to enable the SQL XQuery Extension Module
        //to change the value of an attribute after the fact - Andrzej
        document.attrValue[nodeNumber] = nodeValue;
    }

    @Override
    public void setValue(final String value) throws DOMException {
        document.attrValue[nodeNumber] = value;
    }

    @Override
    public Element getOwnerElement() {
        return (Element) document.getNode(document.attrParent[nodeNumber]);
    }

    @Override
    public void selectDescendantAttributes(final NodeTest test, final Sequence result) throws XPathException {
        if(test.matches(this)) {
            result.add(this);
        }
    }

    @Override
    public Node getParentNode() {
        final int parent = document.attrParent[nodeNumber];
        if(parent > 0) {
            return document.getNode(parent);
        }
        return null;
    }

    @Override
    public Node selectParentNode() {
        return getParentNode();
    }

    @Override
    public void selectAncestors(boolean includeSelf, NodeTest test, Sequence result) throws XPathException {
        if (test.matches(this)) {
            result.add(this);
        }
        ((NodeImpl)getOwnerElement()).selectAncestors(true, test, result);
    }

    @Override
    public TypeInfo getSchemaTypeInfo() {
        return null;
    }

    @Override
    public boolean isId() {
        return (document.attrType[nodeNumber] == ATTR_ID_TYPE);
    }

    @Override
    public int getItemType() {
        return Type.ATTRIBUTE;
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("in-memory#");
        result.append("attribute {");
        result.append(getQName().getStringValue());
        result.append("} {");
        result.append(getValue());
        result.append("} ");
        return result.toString();
    }

    @Override
    public void selectAttributes(final NodeTest test, final Sequence result)
        throws XPathException {
        throw new UnsupportedOperationException("selectAttributes is not yet implemented!");
    }

    @Override
    public void selectChildren(final NodeTest test, final Sequence result)
        throws XPathException {
        throw new UnsupportedOperationException("selectChildren is not yet implemented!");
    }
}
