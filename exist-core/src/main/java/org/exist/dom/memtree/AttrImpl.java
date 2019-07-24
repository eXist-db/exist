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
    public Node getNextSibling() {
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
    public void setValue(final String value) throws DOMException {
        document.attrValue[nodeNumber] = value;
    }

    @Override
    public String getNodeValue() throws DOMException {
        return getValue();
    }

    @Override
    public void setNodeValue(final String nodeValue) throws DOMException {
        //This method was added to enable the SQL XQuery Extension Module
        //to change the value of an attribute after the fact - Andrzej
        setValue(nodeValue);
    }

    @Override
    public String getStringValue() throws DOMException {
        return document.attrValue[nodeNumber];
    }

    @Override
    public String getTextContent() throws DOMException {
        return getNodeValue();
    }

    @Override
    public void setTextContent(final String textContent) throws DOMException {
        setNodeValue(textContent);
    }

    @Override
    public Element getOwnerElement() {
        return (Element) document.getNode(document.attrParent[nodeNumber]);
    }

    @Override
    public Node getParentNode() {
        return null;
    }

    @Override
    public void selectDescendantAttributes(final NodeTest test, final Sequence result) throws XPathException {
        if(test.matches(this)) {
            result.add(this);
        }
    }

    @Override
    public Node selectParentNode() {
        return getOwnerElement();
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
        return "in-memory#attribute {" + getQName().getStringValue() + "} {" + getValue() + "} ";
    }

    @Override
    public void selectAttributes(final NodeTest test, final Sequence result)
            throws XPathException {
        //do nothing, which will return an empty sequence
    }

    @Override
    public void selectChildren(final NodeTest test, final Sequence result)
            throws XPathException {
        //do nothing, which will return an empty sequence
    }

    @Override
    public boolean equals(final Object obj) {
        if(!super.equals(obj)) {
            return false;
        }

        if(obj instanceof AttrImpl) {
            final AttrImpl other = ((AttrImpl)obj);
            return other.getQName().equals(getQName())
                    && other.document.attrValue[nodeNumber].equals(document.attrValue[nodeNumber]);
        }

        return false;
    }
}
