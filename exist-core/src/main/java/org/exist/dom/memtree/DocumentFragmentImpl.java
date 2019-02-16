/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.dom.memtree;

import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

public class DocumentFragmentImpl extends NodeImpl<DocumentFragmentImpl> implements DocumentFragment {

    public DocumentFragmentImpl() {
        super(new DocumentImpl(null, true), 0);
    }

    @Override
    public short getNodeType() {
        return DOCUMENT_FRAGMENT_NODE;
    }

    @Override
    public boolean hasChildNodes() {
        return document.hasChildNodes();
    }

    @Override
    public NodeList getChildNodes() {
        return document.getChildNodes();
    }

    @Override
    public NamedNodeMap getAttributes() {
        return document.getAttributes();
    }

    @Override
    public void selectAttributes(final NodeTest test, final Sequence result) throws XPathException {
        document.selectAttributes(test, result);
    }

    @Override
    public void selectDescendantAttributes(final NodeTest test, final Sequence result) throws XPathException {
        document.selectDescendantAttributes(test, result);
    }

    @Override
    public void selectChildren(final NodeTest test, final Sequence result) throws XPathException {
        document.selectChildren(test, result);
    }

    @Override
    public int compareTo(final DocumentFragmentImpl other) {
        return document.compareTo(other.document);
    }
}
