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

import org.exist.xquery.Expression;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.*;


/**
 * A dynamically constructed namespace node. Used to track namespace declarations in elements. Implements Attr, so it can be treated as a normal
 * attribute.
 *
 * @author wolf
 */
public class NamespaceNode extends NodeImpl implements Attr {

    public NamespaceNode(final DocumentImpl doc, final int nodeNumber) {
        this(null, doc, nodeNumber);
    }

    public NamespaceNode(final Expression expression, final DocumentImpl doc, final int nodeNumber) {
        super(expression, doc, nodeNumber);
    }

    @Override
    public short getNodeType() {
        //TOUNDERSTAND : return value
        //XQuery doesn't support namespace nodes
        //so, mapping as an attribute at *serialization tile*  makes sense
        //however, the Query parser should not accept them in constructors !
        return NodeImpl.NAMESPACE_NODE;
    }

    @Override
    public int getType() {
        return Type.NAMESPACE;
    }

    @Override
    public boolean getSpecified() {
        return true;
    }

    @Override
    public String getName() {
        return getQName().getStringValue();
    }

    @Override
    public String getValue() {
        return getQName().getNamespaceURI();
    }

    @Override
    public void setValue(final String value) throws DOMException {
    }

    @Override
    public Node getFirstChild() {
        return null;
    }

    @Override
    public Node getLastChild() {
        return null;
    }

    @Override
    public String getNodeValue() throws DOMException {
        return getQName().getNamespaceURI();
    }

    @Override
    public Element getOwnerElement() {
        return ((Element) document.getNode(document.namespaceParent[nodeNumber]));
    }

    @Override
    public TypeInfo getSchemaTypeInfo() {
        return null;
    }

    @Override
    public boolean isId() {
        return false;
    }

    @Override
    public int getItemType() {
        return Type.NAMESPACE;
    }

    @Override
    public String toString() {
        return "in-memory#namespace {" + getPrefix() + "}{" + getValue() + "} ";
    }

    @Override
    public void selectAttributes(final NodeTest test, final Sequence result)
        throws XPathException {
    }

    @Override
    public void selectChildren(final NodeTest test, final Sequence result)
        throws XPathException {
    }

    @Override
    public void selectDescendantAttributes(final NodeTest test, final Sequence result)
        throws XPathException {
    }
}
