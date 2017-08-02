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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.dom.memtree;

import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.CharacterData;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;


public abstract class AbstractCharacterData extends NodeImpl implements CharacterData {

    public AbstractCharacterData(final DocumentImpl doc, final int nodeNumber) {
        super(doc, nodeNumber);
    }

    @Override
    public int getLength() {
        return document.alphaLen[nodeNumber];
    }

    @Override
    public String getData() throws DOMException {
        return new String(document.characters, document.alpha[nodeNumber],
            document.alphaLen[nodeNumber]);
    }

    @Override
    public String substringData(final int offset, final int count) throws DOMException {
        if(offset < 0 || count < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        final int length = document.alphaLen[nodeNumber];
        final int inDocOffset = document.alpha[nodeNumber];
        if(offset > length) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        if(offset + count > length) {
            return new String(document.characters, inDocOffset + offset, length - offset);
        } else {
            return new String(document.characters, inDocOffset + offset, length);
        }
    }

    @Override
    public void replaceData(final int offset, final int count, final String arg) throws DOMException {
        if(offset < 0 || count < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }
        throw new UnsupportedOperationException("Operation is unsupported on node type: " + this.getNodeType());
    }

    @Override
    public void insertData(final int offset, final String arg) throws DOMException {
        if(offset < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        throw new UnsupportedOperationException("Operation is unsupported on node type: " + this.getNodeType());
    }

    @Override
    public void appendData(final String arg) throws DOMException {
    }

    @Override
    public void setData(final String data) throws DOMException {
    }

    @Override
    public void deleteData(final int offset, final int count) throws DOMException {
        if(offset < 0 || count < 0) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        final int length = document.alphaLen[nodeNumber];
        if(offset > length) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        final int inDocOffset = document.alpha[nodeNumber];
        if(offset > length) {
            throw new DOMException(DOMException.INDEX_SIZE_ERR, "offset is out of bounds");
        }

        if(offset + count > length) {
            document.alpha[nodeNumber] = inDocOffset + offset;
            document.alphaLen[nodeNumber] = length - offset;
        } else {
            document.alpha[nodeNumber] = inDocOffset + offset;
            document.alphaLen[nodeNumber] = count;
        }
    }

    @Override
    public Node getFirstChild() {
        return null;
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
