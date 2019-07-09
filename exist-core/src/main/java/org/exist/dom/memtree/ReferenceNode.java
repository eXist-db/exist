/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.dom.memtree;

import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import org.exist.dom.persistent.NodeProxy;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;


/**
 * DOCUMENT ME!
 *
 * @author wolf
 */
public class ReferenceNode extends NodeImpl {

    public ReferenceNode(final DocumentImpl doc, final int nodeNumber) {
        super(doc, nodeNumber);
    }

    public NodeProxy getReference() {
        final int p = document.alpha[nodeNumber];
        return document.references[p];
    }

    @Override
    public String toString() {
        return "reference[ " + getReference().getNode().toString() + " ]";
    }

    @Override
    public String getNamespaceURI() {
        return getReference().getNode().getNamespaceURI();
    }

    @Override
    public String getLocalName() {
        return getReference().getNode().getLocalName();
    }

    @Override
    public NamedNodeMap getAttributes() {
        return getReference().getNode().getAttributes();
    }

    @Override
    public Node getFirstChild() {
        //TODO : how to make this node a reference as well ?
        return getReference().getNode().getFirstChild();
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

    @Override
    public String getNodeValue() throws DOMException {
        return getReference().getNode().getNodeValue();
    }
}